package engine.world;

import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.rendering.Mesh;

import java.util.Arrays;

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
    private final byte[] blockLight; // 0..15
    private final byte[] skyLight;
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
        this.blockLight = new byte[SIZE * HEIGHT * SIZE];
        this.skyLight = new byte[SIZE * HEIGHT * SIZE];
        this.fluidData = new byte[SIZE * HEIGHT * SIZE];

        // Initialize with air
        int airId = Blocks.AIR().getNumericId();
        Arrays.fill(blocks, airId);
        Arrays.fill(heightMap, -1);
        Arrays.fill(blockLight, (byte) 0);
        Arrays.fill(skyLight, (byte) 0);
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

    // ==================== BLOCK LIGHT ====================
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return 0;
        }
        return blockLight[index(x, y, z)] & 0xFF;
    }

    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE) {
            return;
        }
        blockLight[index(x, y, z)] = (byte) Math.max(0, Math.min(15, level));
    }

    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE)
            return 0;
        return skyLight[index(x, y, z)] & 0xFF;
    }

    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= HEIGHT || z < 0 || z >= SIZE)
            return;
        int clamped = Math.max(0, Math.min(15, level));
        skyLight[index(x, y, z)] = (byte) clamped;
    }

    public byte[] getSkyLightData() {
        return skyLight;
    }

    public byte[] getBlockLightData() {
        return blockLight;
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

    // ==================== NUOVI METODI PER APPLICARE LA LUCE ====================

    /**
     * Applica i dati di Sky Light dal buffer calcolato nel worker thread.
     */
    public void applySkyLightData(byte[] skyLightBuffer) {
        if (skyLightBuffer.length != this.skyLight.length)
            return;

        // Copia direttamente il buffer aggiornato
        System.arraycopy(skyLightBuffer, 0, this.skyLight, 0, this.skyLight.length);
    }

    /**
     * Applica i dati di Block Light dal buffer calcolato nel worker thread.
     */
    public void applyBlockLightData(byte[] blockLightBuffer) {
        if (blockLightBuffer.length != this.blockLight.length)
            return;

        // Copia direttamente il buffer aggiornato
        System.arraycopy(blockLightBuffer, 0, this.blockLight, 0, this.blockLight.length);
    }

    // Il vecchio metodo applyLightData è stato rimosso.

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
    }
}