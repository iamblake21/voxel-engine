package engine.world;

import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.rendering.Mesh;

import java.util.Arrays;
import java.util.Collection;

import java.util.HashMap;
import java.util.Map;

import engine.world.BlockPos;
import engine.world.blockentity.BlockEntity;
import engine.world.blockentity.ITickableBlockEntity;
import java.util.Collection;

/**
 * A chunk of the world - 16x256x16 blocks.
 *
 * In questa versione il chunk supporta più LOD:
 * - LOD 0: massimo dettaglio (quello "normale")
 * - LOD 1..3: versioni semplificate del mesh (meno triangoli)
 */
public class Chunk {

    public static final int SIZE = 16;
    public static final int HEIGHT = 256;
    private volatile boolean meshPending = false;
    private boolean userModified = false;
    private final Map<Long, BlockEntity> blockEntities = new HashMap<>();

    /** Numero massimo di livelli di dettaglio per chunk. */
    public static final int MAX_LOD_LEVELS = 4;

    private volatile boolean lightPending = false;
    private volatile boolean lightStable = false;

    // Chunk coordinates in chunk-space
    private final int chunkX;
    private final int chunkZ;

    // Raw block data: flattened 3D array [y][z][x]
    private final int[] blocks;

    // Heightmap (topmost solid block per (x,z))
    private final int[] heightMap;

    // Mesh per LOD:
    // solidLOD[0] = mesh normale
    private final Mesh[] solidLOD = new Mesh[MAX_LOD_LEVELS];
    private final Mesh[] transparentLOD = new Mesh[MAX_LOD_LEVELS];
    private final Mesh[] waterLOD = new Mesh[MAX_LOD_LEVELS];
    // Packed light: bits 15-4 = RGB blocklight (0xRGB), bits 3-0 = skylight (0-15)
    private final short[] light;
    private final byte[] fluidData; // 0..15 (fluid level)

    public enum Phase {
        EMPTY, // Chunk non generato
        TERRAIN, // Terreno generato
        FEATURES, // Features (alberi, etc.) aggiunte
        LIGHT_DONE, // Luce calcolata
        MESH_DONE; // Mesh generata
    }

    private Phase phase = Phase.EMPTY;

    /** Quando true, il chunk deve rigenerare la mesh. */
    private boolean dirty = true;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;

        this.blocks = new int[SIZE * HEIGHT * SIZE];
        this.heightMap = new int[SIZE * SIZE];
        this.light = new short[SIZE * HEIGHT * SIZE];
        this.fluidData = new byte[SIZE * HEIGHT * SIZE];

        // Initialize with air
        int airId = Blocks.AIR().getNumericId();
        Arrays.fill(blocks, airId);
        Arrays.fill(heightMap, -1);
        Arrays.fill(light, (short) 0);
        Arrays.fill(fluidData, (byte) 0);

        // Initialize empty meshes for all LODs
        for (int i = 0; i < MAX_LOD_LEVELS; i++) {
            solidLOD[i] = new Mesh();
            transparentLOD[i] = new Mesh();
            waterLOD[i] = new Mesh();
        }
    }

    // ==================== INTERNAL INDEXING ====================

    private int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    public boolean isMeshPending() {
        return meshPending;
    }

    public boolean isLightPending() {
        return lightPending;
    }

    public void setLightPending(boolean p) {
        this.lightPending = p;
    }

    public void setMeshPending(boolean p) {
        this.meshPending = p;
    }

    // ==================== BLOCK ACCESS ====================

    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return Blocks.AIR().getNumericId();
        }
        return blocks[index(x, y, z)];
    }

    public void setBlock(int x, int y, int z, int blockId) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return;
        }
        blocks[index(x, y, z)] = blockId;
        dirty = true;
    }

    public Block getBlockType(int x, int y, int z) {
        return Blocks.get(getBlock(x, y, z));
    }

    // ==================== LIGHT (PACKED FORMAT) ====================
    // Bit layout: [15:4] = RGB blocklight (0xRGB), [3:0] = skylight (0-15)

    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return 0;
        }
        return (light[index(x, y, z)] >> 4) & 0xFFF;
    }

    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return;
        }
        int idx = index(x, y, z);
        int sky = light[idx] & 0xF;
        light[idx] = (short) (((level & 0xFFF) << 4) | sky);
    }

    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE)
            return 0;
        return light[index(x, y, z)] & 0xF;
    }

    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE)
            return;
        int idx = index(x, y, z);
        int clamped = Math.max(0, Math.min(15, level));
        int rgb = (light[idx] >> 4) & 0xFFF;
        light[idx] = (short) ((rgb << 4) | clamped);
    }

    /** Returns the packed light array. Format: [15:4] = RGB, [3:0] = sky */
    public short[] getLightData() {
        return light;
    }

    // ==================== FLUID DATA ====================
    public int getFluidLevel(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE)
            return 0;
        return fluidData[index(x, y, z)] & 0xFF;
    }

    public void setFluidLevel(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE)
            return;
        byte newLevel = (byte) Math.max(0, Math.min(15, level));
        if (fluidData[index(x, y, z)] != newLevel) {
            fluidData[index(x, y, z)] = newLevel;
            this.dirty = true;
        }
    }

    public byte[] getFluidData() {
        return fluidData;
    }

    public void setFluidData(byte[] data) {
        if (data.length == fluidData.length) {
            System.arraycopy(data, 0, fluidData, 0, fluidData.length);
        }
    }

    // ==================== HEIGHT MAP ====================

    public int getHeight(int x, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE)
            return -1;
        return heightMap[z * SIZE + x];
    }

    public void setHeight(int x, int z, int height) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE)
            return;
        heightMap[z * SIZE + x] = height;
    }

    // ==================== RAW DATA ACCESS ====================

    public int[] getBlockData() {
        return blocks;
    }

    public int[] getHeightMapData() {
        return heightMap;
    }

    // ==================== MESH (LOD) ====================

    /**
     * Upload mesh data for a specific LOD level.
     *
     * @param lod                      LOD level (0..MAX_LOD_LEVELS-1)
     * @param solidData          vertex data for solid geometry
     * @param transparentData vertex data for transparent blocks
     * @param waterData          vertex data for water
     */
    public void uploadMeshLOD(int lod,
            float[] solidData,
            float[] transparentData,
            float[] waterData) {
        int level = clampLod(lod);

        solidLOD[level].upload(solidData, false);
        transparentLOD[level].upload(transparentData, true);
        waterLOD[level].upload(waterData, true);
    }

    /**
     * Compatibilità con il vecchio codice:
     * questo metodo carica la mesh solo per LOD 0.
     */
    public void uploadMesh(float[] solidData, float[] transparentData, float[] waterData) {
        uploadMeshLOD(0, solidData, transparentData, waterData);
        this.dirty = false;
    }

    /**
     * Chiamato dopo che tutti i LOD sono stati aggiornati.
     */
    public void clearDirty() {
        this.dirty = false;
    }

    public Mesh getSolidMesh() {
        return solidLOD[0];
    }

    public Mesh getTransparentMesh() {
        return transparentLOD[0];
    }

    public Mesh getWaterMesh() {
        return waterLOD[0];
    }

    public Mesh getSolidMesh(int lod) {
        return solidLOD[clampLod(lod)];
    }

    public Mesh getTransparentMesh(int lod) {
        return transparentLOD[clampLod(lod)];
    }

    public Mesh getWaterMesh(int lod) {
        return waterLOD[clampLod(lod)];
    }

    private int clampLod(int lod) {
        if (lod < 0)
            return 0;
        if (lod >= MAX_LOD_LEVELS)
            return MAX_LOD_LEVELS - 1;
        return lod;
    }

    // ==================== METODI PER APPLICARE LA LUCE ====================

    /**
     * Applica i dati di luce packed dal buffer calcolato nel worker thread.
     * Format: [15:4] = RGB blocklight, [3:0] = skylight
     */
    public void applyLightData(short[] lightBuffer) {
        if (lightBuffer.length != this.light.length)
            return;
        System.arraycopy(lightBuffer, 0, this.light, 0, this.light.length);
    }

    // ==================== COORDINATES ====================

    public int getX() {
        return chunkX;
    }

    public int getZ() {
        return chunkZ;
    }

    public int getWorldX() {
        return chunkX * SIZE;
    }

    public int getWorldZ() {
        return chunkZ * SIZE;
    }

    // ==================== STATE ====================

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    // ==================== BLOCK ENTITIES ====================

    /**
     * Get block entity at local position.
     */
    public BlockEntity getBlockEntity(int localX, int y, int localZ) {
        long key = packLocalPos(localX, y, localZ);
        return blockEntities.get(key);
    }

    /**
     * Set block entity at local position.
     */
    public void setBlockEntity(int localX, int y, int localZ, BlockEntity blockEntity) {
        long key = packLocalPos(localX, y, localZ);

        // Remove old block entity
        BlockEntity old = blockEntities.remove(key);
        if (old != null) {
            old.onRemoved();
        }

        // Add new one
        if (blockEntity != null) {
            blockEntities.put(key, blockEntity);
        }
    }

    /**
     * Remove block entity at local position.
     */
    public BlockEntity removeBlockEntity(int localX, int y, int localZ) {
        long key = packLocalPos(localX, y, localZ);
        BlockEntity removed = blockEntities.remove(key);
        if (removed != null) {
            removed.onRemoved();
        }
        return removed;
    }

    /**
     * Get all block entities in this chunk.
     */
    public Collection<BlockEntity> getBlockEntities() {
        return blockEntities.values();
    }

    /**
     * Check if chunk has any block entities.
     */
    public boolean hasBlockEntities() {
        return !blockEntities.isEmpty();
    }

    /**
     * Pack local coordinates into a single long key.
     */
    private long packLocalPos(int x, int y, int z) {
        return ((long) (y & 0xFFFF) << 8) | ((z & 0xF) << 4) | (x & 0xF);
    }

    /**
     * Tick all tickable block entities.
     */
    public void tickBlockEntities() {
        for (BlockEntity be : blockEntities.values()) {
            if (be instanceof ITickableBlockEntity && !be.isRemoved()) {
                ((ITickableBlockEntity) be).tick();
            }
        }
    }

    // ==================== CUSTOM MESHES ====================
    private final Map<String, Mesh> customMeshes = new HashMap<>();

    public void uploadCustomMeshes(Map<String, float[]> customData) {
        // Clear existing custom meshes that are not in the new data
        // For simplicity, we might just recreate them or update them.
        // But Meshes are heavy OpenGL objects.

        // Strategy:
        // 1. For each key in customData:
        // - If exists in customMeshes, update it.
        // - If not, create new Mesh and update it.
        // 2. Remove keys from customMeshes that are NOT in customData.

        // 1. Update/Create
        for (Map.Entry<String, float[]> entry : customData.entrySet()) {
            String texture = entry.getKey();
            float[] data = entry.getValue();

            Mesh mesh = customMeshes.get(texture);
            if (mesh == null) {
                mesh = new Mesh();
                customMeshes.put(texture, mesh);
            }
            mesh.upload(data, true); // Assuming custom meshes might need transparency/alpha check? Or just standard.
            // Using true (dynamic/transparent) to be safe or false?
            // Most custom models (e.g. torch) might use transparency (cutout).
        }

        // 2. Remove unused
        java.util.Iterator<Map.Entry<String, Mesh>> it = customMeshes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Mesh> entry = it.next();
            if (!customData.containsKey(entry.getKey())) {
                entry.getValue().cleanup();
                it.remove();
            }
        }
    }

    public Map<String, Mesh> getCustomMeshes() {
        return customMeshes;
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        for (int i = 0; i < MAX_LOD_LEVELS; i++) {
            if (solidLOD[i] != null)
                solidLOD[i].cleanup();
            if (transparentLOD[i] != null)
                transparentLOD[i].cleanup();
            if (waterLOD[i] != null)
                waterLOD[i].cleanup();
        }
        for (Mesh mesh : customMeshes.values()) {
            mesh.cleanup();
        }
        customMeshes.clear();

        for (BlockEntity be : blockEntities.values()) {
            be.onRemoved();
        }
        blockEntities.clear();
    }
}