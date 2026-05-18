package engine.world;

import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.rendering.Mesh;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import engine.world.blockentity.BlockEntity;
import engine.world.blockentity.ITickableBlockEntity;

/**
 * A chunk column: 16 × HEIGHT × 16 blocks.
 *
 * Block/light/fluid storage is section-based after compaction.
 * Sections are 16×16×16 slices; empty (all-air) sections are kept null.
 * This cuts resident memory by 50-85 % compared to always-allocated flat arrays,
 * because ocean/sky columns have many empty slices.
 *
 * Life cycle:
 *   EMPTY/TERRAIN phase  → flat arrays (lazily allocated, used by terrain gen)
 *   FEATURES phase       → compactToSections() frees flat arrays, switches to sections
 *   LIGHT_DONE/MESH_DONE → sections only; flat arrays are null
 */
public class Chunk {

    public static final int SIZE   = 16;
    public static final int HEIGHT = 384;
    public static final int SECTION_COUNT = HEIGHT / ChunkSection.SIZE; // 24

    public static final int MAX_LOD_LEVELS = 4;

    // ---- coordinates ----
    private final int chunkX;
    private final int chunkZ;

    // ---- section-based storage (used after compaction) ----
    private final ChunkSection[] sections = new ChunkSection[SECTION_COUNT];
    private boolean compacted = false;
    // Highest section index (0-based) that contains any block; -1 if none.
    // Null sections above this index return skylight=15 (open sky).
    private int topFilledSection = -1;

    // ---- flat arrays (used during TERRAIN/FEATURES phases, null after compaction) ----
    private short[] blocks;
    private short[] light;
    private byte[]  fluidData;

    // ---- height map (tiny, always resident) ----
    private final int[] heightMap = new int[SIZE * SIZE];

    // ---- mesh storage ----
    private final Mesh[] solidLOD       = new Mesh[MAX_LOD_LEVELS];
    private final Mesh[] transparentLOD = new Mesh[MAX_LOD_LEVELS];
    private final Mesh[] waterLOD       = new Mesh[MAX_LOD_LEVELS];
    private final Map<String, Mesh> customMeshes = new HashMap<>();
    private final Mesh[][] solidSectionLOD       = new Mesh[SECTION_COUNT][MAX_LOD_LEVELS];
    private final Mesh[][] transparentSectionLOD = new Mesh[SECTION_COUNT][MAX_LOD_LEVELS];
    private final Mesh[][] waterSectionLOD       = new Mesh[SECTION_COUNT][MAX_LOD_LEVELS];
    @SuppressWarnings("unchecked")
    private final Map<String, Mesh>[] customSectionMeshes = new Map[SECTION_COUNT];
    private final boolean[] sectionMeshReady = new boolean[SECTION_COUNT];

    // ---- state flags ----
    public enum Phase {
        EMPTY, TERRAIN, FEATURES, LIGHT_DONE, MESH_DONE
    }
    private Phase phase = Phase.EMPTY;
    private boolean dirty = true;
    private volatile boolean needsSaving = true;
    private volatile boolean meshPending  = false;
    private volatile boolean lightPending = false;
    private volatile boolean lightStable  = false;
    private int meshBatchId = 0;
    private int pendingMeshSections = 0;
    private boolean userModified = false;

    // ---- block entities ----
    private final Map<Long, BlockEntity> blockEntities = new HashMap<>();

    // ==========================================================
    // CONSTRUCTOR
    // ==========================================================

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        Arrays.fill(heightMap, -1);

        for (int i = 0; i < MAX_LOD_LEVELS; i++) {
            solidLOD[i]       = new Mesh();
            transparentLOD[i] = new Mesh();
            waterLOD[i]       = new Mesh();
        }
        // blocks / light / fluidData intentionally NOT allocated here.
        // They are allocated lazily via getBlockData() / getLightData() / getFluidData()
        // and freed after compactToSections().
    }

    // ==========================================================
    // FLAT-ARRAY LAZY ACCESSORS (pre-compaction)
    // ==========================================================

    /**
     * Returns the backing flat blocks array (lazily allocated).
     * Valid only before compaction (TERRAIN/FEATURES phases).
     * After compaction, builds a temporary flat copy from sections
     * (use sparingly – only for legacy read paths like FeatureGenerator).
     */
    public short[] getBlockData() {
        if (compacted) {
            return buildFlatBlocks();
        }
        if (blocks == null) {
            blocks = new short[SIZE * HEIGHT * SIZE];
            Arrays.fill(blocks, (short) Blocks.AIR().getNumericId());
        }
        return blocks;
    }

    /** Same as above for light data. */
    public short[] getLightData() {
        if (compacted) {
            return buildFlatLight();
        }
        if (light == null) {
            light = new short[SIZE * HEIGHT * SIZE];
        }
        return light;
    }

    /** Same as above for fluid data. */
    public byte[] getFluidData() {
        if (compacted) {
            return buildFlatFluid();
        }
        if (fluidData == null) {
            fluidData = new byte[SIZE * HEIGHT * SIZE];
        }
        return fluidData;
    }

    public void setFluidData(byte[] data) {
        if (compacted) {
            int N = ChunkSection.TOTAL;
            for (int sy = 0; sy < SECTION_COUNT; sy++) {
                int base = sy * N;
                boolean hasFluid = false;
                for (int i = 0; i < N; i++) {
                    if (data[base + i] != 0) { hasFluid = true; break; }
                }
                if (hasFluid || sections[sy] != null) {
                    ChunkSection sec = getOrCreateSection(sy);
                    System.arraycopy(data, base, sec.fluidData, 0, N);
                    if (hasFluid && sy > topFilledSection) {
                        topFilledSection = sy;
                    }
                }
            }
        } else {
            if (fluidData == null) fluidData = new byte[SIZE * HEIGHT * SIZE];
            int len = Math.min(data.length, fluidData.length);
            System.arraycopy(data, 0, fluidData, 0, len);
        }
        markDirty();
    }

    // ==========================================================
    // SECTION COMPACTION
    // ==========================================================

    /**
     * Converts flat arrays to section-based storage.
     * Skips sections that are entirely air (null sections = 0 bytes).
     * Frees flat arrays so they can be GC'd.
     *
     * Call after ensureFeatures() / ChunkSerializer.loadInto().
     */
    public void compactToSections() {
        if (compacted) return;

        int airId = Blocks.AIR().getNumericId();
        int N = ChunkSection.TOTAL;

        boolean[] hasMaterial = new boolean[SECTION_COUNT];
        int computedTopFilledSection = -1;

        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            int base = sy * N;

            boolean hasBlocks = false;
            if (blocks != null) {
                for (int i = 0; i < N; i++) {
                    if ((blocks[base + i] & 0xFFFF) != airId) { hasBlocks = true; break; }
                }
            }

            boolean hasFluid = false;
            if (fluidData != null) {
                for (int i = 0; i < N; i++) {
                    if (fluidData[base + i] != 0) { hasFluid = true; break; }
                }
            }

            hasMaterial[sy] = hasBlocks || hasFluid;
            if (hasMaterial[sy]) {
                computedTopFilledSection = sy;
            }
        }

        topFilledSection = computedTopFilledSection;

        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            int base = sy * N;
            int defaultSky = defaultSkyForSection(sy);

            boolean hasLight = false;
            if (light != null) {
                if (!(sy > computedTopFilledSection && isZeroFilledLightSection(light, base))) {
                    for (int i = 0; i < N; i++) {
                        if ((light[base + i] & 0xFFFF) != defaultSky) { hasLight = true; break; }
                    }
                }
            }

            if (hasMaterial[sy] || hasLight) {
                ChunkSection sec = new ChunkSection(airId);
                if (blocks    != null) System.arraycopy(blocks,    base, sec.blocks,    0, N);
                if (fluidData != null) System.arraycopy(fluidData, base, sec.fluidData, 0, N);
                if (hasLight) {
                    sec.light = new short[N];
                    System.arraycopy(light, base, sec.light, 0, N);
                }
                sections[sy] = sec;
            }
        }

        // Free flat arrays.
        blocks    = null;
        light     = null;
        fluidData = null;
        compacted = true;
    }

    public boolean isCompacted() { return compacted; }

    /** Exposed so ChunkSnapshot can hold zero-copy references. */
    public ChunkSection[] getSections() { return sections; }

    public int getTopFilledSection() { return topFilledSection; }

    // ==========================================================
    // INTERNAL HELPERS
    // ==========================================================

    private int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    private ChunkSection getOrCreateSection(int sy) {
        if (sections[sy] == null) {
            sections[sy] = new ChunkSection(Blocks.AIR().getNumericId());
        }
        return sections[sy];
    }

    private int defaultSkyForSection(int sy) {
        return sy > topFilledSection ? 15 : 0;
    }

    private boolean sectionHasNonDefaultLight(short[] lightBuffer, int base, int defaultSky) {
        for (int i = 0; i < ChunkSection.TOTAL; i++) {
            if ((lightBuffer[base + i] & 0xFFFF) != defaultSky) {
                return true;
            }
        }
        return false;
    }

    private boolean isZeroFilledLightSection(short[] lightBuffer, int base) {
        for (int i = 0; i < ChunkSection.TOTAL; i++) {
            if (lightBuffer[base + i] != 0) {
                return false;
            }
        }
        return true;
    }

    private void refreshTopFilledSection() {
        int airId = Blocks.AIR().getNumericId();
        topFilledSection = -1;
        for (int sy = SECTION_COUNT - 1; sy >= 0; sy--) {
            ChunkSection sec = sections[sy];
            if (sec != null && sec.hasMaterial(airId)) {
                topFilledSection = sy;
                return;
            }
        }
    }

    private short[] buildFlatBlocks() {
        int airId = Blocks.AIR().getNumericId();
        short[] flat = new short[SIZE * HEIGHT * SIZE];
        Arrays.fill(flat, (short) airId);
        int N = ChunkSection.TOTAL;
        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            if (sections[sy] != null) {
                System.arraycopy(sections[sy].blocks, 0, flat, sy * N, N);
            }
        }
        return flat;
    }

    private short[] buildFlatLight() {
        short[] flat = new short[SIZE * HEIGHT * SIZE];
        int N = ChunkSection.TOTAL;
        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            if (sections[sy] != null && sections[sy].light != null) {
                System.arraycopy(sections[sy].light, 0, flat, sy * N, N);
            } else if (defaultSkyForSection(sy) != 0) {
                Arrays.fill(flat, sy * N, (sy + 1) * N, (short) defaultSkyForSection(sy));
            }
        }
        return flat;
    }

    private byte[] buildFlatFluid() {
        byte[] flat = new byte[SIZE * HEIGHT * SIZE];
        int N = ChunkSection.TOTAL;
        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            if (sections[sy] != null) {
                System.arraycopy(sections[sy].fluidData, 0, flat, sy * N, N);
            }
        }
        return flat;
    }

    // ==========================================================
    // BLOCK ACCESS
    // ==========================================================

    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE)
            return Blocks.AIR().getNumericId();
        if (compacted) {
            ChunkSection sec = sections[y >> 4];
            return sec == null ? Blocks.AIR().getNumericId() : sec.getBlock(x, y & 15, z);
        }
        if (blocks == null) return Blocks.AIR().getNumericId();
        return blocks[index(x, y, z)] & 0xFFFF;
    }

    public void setBlock(int x, int y, int z, int blockId) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        if (compacted) {
            int sy = y >> 4;
            int ly = y & 15;
            if (blockId == Blocks.AIR().getNumericId()) {
                ChunkSection sec = sections[sy];
                if (sec != null) {
                    sec.setBlock(x, ly, z, blockId);
                    if (sy == topFilledSection && !sec.hasMaterial(Blocks.AIR().getNumericId())) {
                        refreshTopFilledSection();
                    }
                }
            } else {
                getOrCreateSection(sy).setBlock(x, ly, z, blockId);
                if (sy > topFilledSection) topFilledSection = sy;
            }
        } else {
            getBlockData()[index(x, y, z)] = (short) blockId;
        }
        markDirty();
    }

    public Block getBlockType(int x, int y, int z) {
        return Blocks.get(getBlock(x, y, z));
    }

    // ==========================================================
    // LIGHT
    // ==========================================================

    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return 0;
        if (compacted) {
            ChunkSection sec = sections[y >> 4];
            return sec == null ? 0 : sec.getBlockLight(x, y & 15, z);
        }
        if (light == null) return 0;
        return (light[index(x, y, z)] >> 4) & 0xFFF;
    }

    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        if (compacted) {
            int sy = y >> 4;
            ChunkSection sec = sections[sy];
            if (sec == null && (level & 0xFFF) == 0) return;
            getOrCreateSection(sy).setBlockLight(x, y & 15, z, level);
        } else {
            int idx = index(x, y, z);
            int sky = getLightData()[idx] & 0xF;
            getLightData()[idx] = (short) (((level & 0xFFF) << 4) | sky);
        }
        markDirty();
    }

    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return 0;
        if (compacted) {
            int sy = y >> 4;
            ChunkSection sec = sections[sy];
            if (sec == null) return (sy > topFilledSection) ? 15 : 0;
            if (sec.getLightArray() == null) return defaultSkyForSection(sy);
            return sec.getSkyLight(x, y & 15, z);
        }
        if (light == null) return 0;
        return light[index(x, y, z)] & 0xF;
    }

    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        int clamped = Math.max(0, Math.min(15, level));
        if (compacted) {
            int sy = y >> 4;
            ChunkSection sec = sections[sy];
            if (sec == null && clamped == defaultSkyForSection(sy)) return;
            getOrCreateSection(sy).setSkyLight(x, y & 15, z, clamped);
        } else {
            int idx = index(x, y, z);
            int rgb = (getLightData()[idx] >> 4) & 0xFFF;
            getLightData()[idx] = (short) ((rgb << 4) | clamped);
        }
        markDirty();
    }

    /**
     * Bulk-apply the packed light buffer computed by the light worker.
     * Distributes into sections (lazy-creating them for non-zero light).
     */
    public void applyLightData(short[] lightBuffer) {
        if (lightBuffer.length != SIZE * HEIGHT * SIZE) {
            System.out.println("[ERROR] applyLightData size mismatch: got " + lightBuffer.length);
            return;
        }
        invalidateSectionMeshReadiness();
        if (compacted) {
            int N = ChunkSection.TOTAL;
            for (int sy = 0; sy < SECTION_COUNT; sy++) {
                int base = sy * N;
                int defaultSky = defaultSkyForSection(sy);
                boolean hasNonDefaultLight = sectionHasNonDefaultLight(lightBuffer, base, defaultSky);
                if (sections[sy] != null) {
                    if (hasNonDefaultLight) {
                        sections[sy].applyLightData(lightBuffer, base);
                    } else if (sections[sy].hasMaterial(Blocks.AIR().getNumericId())) {
                        sections[sy].clearLight();
                    } else {
                        cleanupSectionMeshes(sy);
                        sections[sy] = null;
                    }
                } else {
                    // Create a section only for light that differs from the implicit section default.
                    if (hasNonDefaultLight) {
                        ChunkSection sec = getOrCreateSection(sy);
                        sec.applyLightData(lightBuffer, base);
                    }
                }
            }
        } else {
            if (light == null) light = new short[SIZE * HEIGHT * SIZE];
            System.arraycopy(lightBuffer, 0, light, 0, lightBuffer.length);
        }
    }

    // ==========================================================
    // FLUID
    // ==========================================================

    public int getFluidLevel(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return 0;
        if (compacted) {
            ChunkSection sec = sections[y >> 4];
            return sec == null ? 0 : sec.getFluidLevel(x, y & 15, z);
        }
        if (fluidData == null) return 0;
        return fluidData[index(x, y, z)] & 0xFF;
    }

    public void setFluidLevel(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) return;
        if (compacted) {
            byte newLevel = (byte) Math.max(0, Math.min(15, level));
            int sy = y >> 4;
            if (newLevel == 0 && sections[sy] == null) return;
            ChunkSection sec = (newLevel != 0) ? getOrCreateSection(sy) : sections[sy];
            if (sec != null && sec.fluidData[ChunkSection.index(x, y & 15, z)] != newLevel) {
                sec.fluidData[ChunkSection.index(x, y & 15, z)] = newLevel;
                if (newLevel != 0 && sy > topFilledSection) {
                    topFilledSection = sy;
                } else if (newLevel == 0 && sy == topFilledSection && !sec.hasMaterial(Blocks.AIR().getNumericId())) {
                    refreshTopFilledSection();
                }
                markDirty();
            }
        } else {
            byte newLevel = (byte) Math.max(0, Math.min(15, level));
            if (fluidData == null) fluidData = new byte[SIZE * HEIGHT * SIZE];
            if (fluidData[index(x, y, z)] != newLevel) {
                fluidData[index(x, y, z)] = newLevel;
                markDirty();
            }
        }
    }

    // ==========================================================
    // HEIGHT MAP
    // ==========================================================

    public int getHeight(int x, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return -1;
        return heightMap[z * SIZE + x];
    }

    public void setHeight(int x, int z, int height) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return;
        heightMap[z * SIZE + x] = height;
    }

    public int[] getHeightMapData() { return heightMap; }

    // ==========================================================
    // SERIALIZATION STATE
    // ==========================================================

    public boolean needsSaving()  { return needsSaving; }
    public void    markSaved()    { this.needsSaving = false; }

    public void markDirty() {
        this.needsSaving = true;
        this.dirty = true;
    }

    // ==========================================================
    // MESH (LOD)
    // ==========================================================

    public void uploadMeshLOD(int lod, float[] solidData, float[] transparentData, float[] waterData) {
        int level = clampLod(lod);
        uploadColumnMeshSlot(solidLOD, level, solidData, false);
        uploadColumnMeshSlot(transparentLOD, level, transparentData, true);
        uploadColumnMeshSlot(waterLOD, level, waterData, true);
    }

    public void uploadMesh(float[] solidData, float[] transparentData, float[] waterData) {
        uploadMeshLOD(0, solidData, transparentData, waterData);
        this.dirty = false;
    }

    public int beginSectionMeshBatch(int sectionCount) {
        meshBatchId++;
        pendingMeshSections = sectionCount;
        meshPending = sectionCount > 0;
        dirty = sectionCount > 0;
        clearColumnMeshes();
        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            if (!hasRenderableSection(sy)) {
                cleanupSectionMeshes(sy);
            }
        }
        return meshBatchId;
    }

    public boolean isCurrentMeshBatch(int batchId) {
        return batchId == meshBatchId;
    }

    public boolean finishSectionMesh(int batchId) {
        if (batchId != meshBatchId) {
            return false;
        }
        pendingMeshSections = Math.max(0, pendingMeshSections - 1);
        if (pendingMeshSections == 0) {
            meshPending = false;
            dirty = false;
            return true;
        }
        return false;
    }

    public boolean hasRenderableSection(int sectionY) {
        if (sectionY < 0 || sectionY >= SECTION_COUNT) return false;
        ChunkSection sec = sections[sectionY];
        return sec != null && sec.hasMaterial(Blocks.AIR().getNumericId());
    }

    public void uploadSectionMesh(int sectionY, int lod, float[] solidData, float[] transparentData, float[] waterData) {
        int level = clampLod(lod);
        uploadSectionMeshSlot(solidSectionLOD, sectionY, level, solidData, false);
        uploadSectionMeshSlot(transparentSectionLOD, sectionY, level, transparentData, true);
        uploadSectionMeshSlot(waterSectionLOD, sectionY, level, waterData, true);
        sectionMeshReady[sectionY] = true;
    }

    public void uploadSectionCustomMeshes(int sectionY, Map<String, float[]> customData) {
        Map<String, Mesh> meshes = customSectionMeshes[sectionY];
        if ((customData == null || customData.isEmpty()) && meshes == null) {
            return;
        }
        if (customData == null) {
            customData = java.util.Collections.emptyMap();
        }
        if (meshes == null) {
            meshes = new HashMap<>();
            customSectionMeshes[sectionY] = meshes;
        }
        final Map<String, float[]> newCustomData = customData;
        for (Map.Entry<String, float[]> entry : newCustomData.entrySet()) {
            if (isEmptyMeshData(entry.getValue())) {
                continue;
            }
            Mesh mesh = meshes.computeIfAbsent(entry.getKey(), k -> new Mesh());
            mesh.upload(entry.getValue(), true);
        }
        meshes.entrySet().removeIf(e -> {
            if (!newCustomData.containsKey(e.getKey()) || isEmptyMeshData(newCustomData.get(e.getKey()))) {
                e.getValue().cleanup();
                return true;
            }
            return false;
        });
    }

    private Mesh getOrCreateMesh(Mesh[][] meshes, int sectionY, int lod) {
        Mesh mesh = meshes[sectionY][lod];
        if (mesh == null) {
            mesh = new Mesh();
            meshes[sectionY][lod] = mesh;
        }
        return mesh;
    }

    private void uploadColumnMeshSlot(Mesh[] meshes, int lod, float[] data, boolean transparent) {
        if (isEmptyMeshData(data)) {
            if (meshes[lod] != null) {
                meshes[lod].cleanup();
                meshes[lod] = null;
            }
            return;
        }
        if (meshes[lod] == null) {
            meshes[lod] = new Mesh();
        }
        meshes[lod].upload(data, transparent);
    }

    private void uploadSectionMeshSlot(Mesh[][] meshes, int sectionY, int lod, float[] data, boolean transparent) {
        if (isEmptyMeshData(data)) {
            if (meshes[sectionY][lod] != null) {
                meshes[sectionY][lod].cleanup();
                meshes[sectionY][lod] = null;
            }
            return;
        }
        getOrCreateMesh(meshes, sectionY, lod).upload(data, transparent);
    }

    private boolean isEmptyMeshData(float[] data) {
        return data == null || data.length == 0;
    }

    private void clearColumnMeshes() {
        for (int i = 0; i < MAX_LOD_LEVELS; i++) {
            if (solidLOD[i] != null) solidLOD[i].cleanup();
            if (transparentLOD[i] != null) transparentLOD[i].cleanup();
            if (waterLOD[i] != null) waterLOD[i].cleanup();
            solidLOD[i] = null;
            transparentLOD[i] = null;
            waterLOD[i] = null;
        }
        for (Mesh mesh : customMeshes.values()) mesh.cleanup();
        customMeshes.clear();
    }

    public void clearDirty() { this.dirty = false; }

    public Mesh getSolidMesh()        { return solidLOD[0]; }
    public Mesh getTransparentMesh()  { return transparentLOD[0]; }
    public Mesh getWaterMesh()        { return waterLOD[0]; }
    public Mesh getSolidMesh(int lod)       { return solidLOD[clampLod(lod)]; }
    public Mesh getTransparentMesh(int lod) { return transparentLOD[clampLod(lod)]; }
    public Mesh getWaterMesh(int lod)       { return waterLOD[clampLod(lod)]; }
    public Mesh getSolidSectionMesh(int sectionY, int lod)       { return solidSectionLOD[sectionY][clampLod(lod)]; }
    public Mesh getTransparentSectionMesh(int sectionY, int lod) { return transparentSectionLOD[sectionY][clampLod(lod)]; }
    public Mesh getWaterSectionMesh(int sectionY, int lod)       { return waterSectionLOD[sectionY][clampLod(lod)]; }

    public Map<String, Mesh> getCustomSectionMeshes(int sectionY) {
        Map<String, Mesh> meshes = customSectionMeshes[sectionY];
        return meshes != null ? meshes : java.util.Collections.emptyMap();
    }

    public boolean hasSectionMeshes() {
        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            for (int lod = 0; lod < MAX_LOD_LEVELS; lod++) {
                if ((solidSectionLOD[sy][lod] != null && !solidSectionLOD[sy][lod].isEmpty())
                        || (transparentSectionLOD[sy][lod] != null && !transparentSectionLOD[sy][lod].isEmpty())
                        || (waterSectionLOD[sy][lod] != null && !waterSectionLOD[sy][lod].isEmpty())) {
                    return true;
                }
            }
            Map<String, Mesh> custom = customSectionMeshes[sy];
            if (custom != null) {
                for (Mesh mesh : custom.values()) {
                    if (mesh != null && !mesh.isEmpty()) return true;
                }
            }
        }
        return false;
    }

    public boolean hasRenderableMesh() {
        return hasSectionMeshes();
    }

    public int getAllocatedSectionCount() {
        int count = 0;
        for (ChunkSection section : sections) {
            if (section != null) {
                count++;
            }
        }
        return count;
    }

    public int getMeshedSectionCount() {
        int count = 0;
        for (boolean ready : sectionMeshReady) {
            if (ready) {
                count++;
            }
        }
        return count;
    }

    public long getEstimatedSectionBytes() {
        long bytes = 0;
        for (ChunkSection section : sections) {
            if (section != null) {
                bytes += ChunkSection.TOTAL * Short.BYTES;
                bytes += ChunkSection.TOTAL;
                if (section.getLightArray() != null) {
                    bytes += ChunkSection.TOTAL * Short.BYTES;
                }
            }
        }
        return bytes;
    }

    public long getEstimatedVboBytes() {
        long bytes = 0;
        for (int lod = 0; lod < MAX_LOD_LEVELS; lod++) {
            bytes += estimatedMeshBytes(solidLOD[lod]);
            bytes += estimatedMeshBytes(transparentLOD[lod]);
            bytes += estimatedMeshBytes(waterLOD[lod]);
        }
        for (Mesh mesh : customMeshes.values()) {
            bytes += estimatedMeshBytes(mesh);
        }
        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            for (int lod = 0; lod < MAX_LOD_LEVELS; lod++) {
                bytes += estimatedMeshBytes(solidSectionLOD[sy][lod]);
                bytes += estimatedMeshBytes(transparentSectionLOD[sy][lod]);
                bytes += estimatedMeshBytes(waterSectionLOD[sy][lod]);
            }
            Map<String, Mesh> custom = customSectionMeshes[sy];
            if (custom != null) {
                for (Mesh mesh : custom.values()) {
                    bytes += estimatedMeshBytes(mesh);
                }
            }
        }
        return bytes;
    }

    private long estimatedMeshBytes(Mesh mesh) {
        return mesh == null ? 0 : mesh.getEstimatedVboBytes();
    }

    public boolean isSectionMeshReady(int sectionY) {
        return sectionY >= 0 && sectionY < SECTION_COUNT && sectionMeshReady[sectionY];
    }

    public boolean hasAllRenderableSectionMeshes() {
        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            if (hasRenderableSection(sy) && !sectionMeshReady[sy]) {
                return false;
            }
        }
        return true;
    }

    public void invalidateSectionMeshReadiness() {
        Arrays.fill(sectionMeshReady, false);
    }

    public boolean evictSectionMesh(int sectionY) {
        if (!isSectionMeshReady(sectionY)) {
            return false;
        }
        cleanupSectionMeshes(sectionY);
        return true;
    }

    private int clampLod(int lod) {
        return Math.max(0, Math.min(MAX_LOD_LEVELS - 1, lod));
    }

    // ==========================================================
    // STATE
    // ==========================================================

    public Phase getPhase()             { return phase; }
    public void  setPhase(Phase phase)  { this.phase = phase; }

    public boolean isMeshPending()           { return meshPending; }
    public boolean isLightPending()          { return lightPending; }
    public void    setMeshPending(boolean p) {
        this.meshPending = p;
        if (!p) pendingMeshSections = 0;
    }
    public void    setLightPending(boolean p){ this.lightPending = p; }

    // ==========================================================
    // COORDINATES
    // ==========================================================

    public int getX()       { return chunkX; }
    public int getZ()       { return chunkZ; }
    public int getWorldX()  { return chunkX * SIZE; }
    public int getWorldZ()  { return chunkZ * SIZE; }

    // ==========================================================
    // BLOCK ENTITIES
    // ==========================================================

    public BlockEntity getBlockEntity(int localX, int y, int localZ) {
        return blockEntities.get(packLocalPos(localX, y, localZ));
    }

    public void setBlockEntity(int localX, int y, int localZ, BlockEntity blockEntity) {
        long key = packLocalPos(localX, y, localZ);
        BlockEntity old = blockEntities.remove(key);
        if (old != null) old.onRemoved();
        if (blockEntity != null) blockEntities.put(key, blockEntity);
        markDirty();
    }

    public BlockEntity removeBlockEntity(int localX, int y, int localZ) {
        BlockEntity removed = blockEntities.remove(packLocalPos(localX, y, localZ));
        if (removed != null) removed.onRemoved();
        markDirty();
        return removed;
    }

    public Collection<BlockEntity> getBlockEntities()  { return blockEntities.values(); }
    public boolean                 hasBlockEntities()   { return !blockEntities.isEmpty(); }

    private long packLocalPos(int x, int y, int z) {
        return ((long) (y & 0xFFFF) << 8) | ((z & 0xF) << 4) | (x & 0xF);
    }

    public void tickBlockEntities() {
        for (BlockEntity be : blockEntities.values()) {
            if (be instanceof ITickableBlockEntity && !be.isRemoved()) {
                ((ITickableBlockEntity) be).tick();
            }
        }
    }

    // ==========================================================
    // CUSTOM MESHES
    // ==========================================================

    public void uploadCustomMeshes(Map<String, float[]> customData) {
        if (customData == null) {
            customData = java.util.Collections.emptyMap();
        }
        final Map<String, float[]> newCustomData = customData;
        for (Map.Entry<String, float[]> entry : customData.entrySet()) {
            if (isEmptyMeshData(entry.getValue())) {
                continue;
            }
            String texture = entry.getKey();
            Mesh mesh = customMeshes.computeIfAbsent(texture, k -> new Mesh());
            mesh.upload(entry.getValue(), true);
        }
        customMeshes.entrySet().removeIf(e -> {
            if (!newCustomData.containsKey(e.getKey()) || isEmptyMeshData(newCustomData.get(e.getKey()))) {
                e.getValue().cleanup();
                return true;
            }
            return false;
        });
    }

    public Map<String, Mesh> getCustomMeshes() { return customMeshes; }

    // ==========================================================
    // CLEANUP
    // ==========================================================

    public void cleanup() {
        for (int i = 0; i < MAX_LOD_LEVELS; i++) {
            if (solidLOD[i]       != null) solidLOD[i].cleanup();
            if (transparentLOD[i] != null) transparentLOD[i].cleanup();
            if (waterLOD[i]       != null) waterLOD[i].cleanup();
        }
        for (Mesh mesh : customMeshes.values()) mesh.cleanup();
        customMeshes.clear();

        for (int sy = 0; sy < SECTION_COUNT; sy++) {
            cleanupSectionMeshes(sy);
        }

        for (BlockEntity be : blockEntities.values()) be.onRemoved();
        blockEntities.clear();

        // Free flat arrays if still present.
        blocks    = null;
        light     = null;
        fluidData = null;
    }

    private void cleanupSectionMeshes(int sectionY) {
        for (int i = 0; i < MAX_LOD_LEVELS; i++) {
            if (solidSectionLOD[sectionY][i] != null) solidSectionLOD[sectionY][i].cleanup();
            if (transparentSectionLOD[sectionY][i] != null) transparentSectionLOD[sectionY][i].cleanup();
            if (waterSectionLOD[sectionY][i] != null) waterSectionLOD[sectionY][i].cleanup();
            solidSectionLOD[sectionY][i] = null;
            transparentSectionLOD[sectionY][i] = null;
            waterSectionLOD[sectionY][i] = null;
        }
        Map<String, Mesh> custom = customSectionMeshes[sectionY];
        if (custom != null) {
            for (Mesh mesh : custom.values()) mesh.cleanup();
            custom.clear();
            customSectionMeshes[sectionY] = null;
        }
        sectionMeshReady[sectionY] = false;
    }
}
