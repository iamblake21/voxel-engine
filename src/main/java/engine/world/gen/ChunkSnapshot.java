package engine.world.gen;

import engine.world.Chunk;
import engine.world.biome.Biome;

import java.util.Arrays;

/**
 * Snapshot immutabile di un chunk e dei suoi 8 vicini.
 * Usato per il calcolo asincrono di luce e mesh.
 * 
 * IMPLEMENTA WorldAccess per essere usato direttamente da MeshBuilder.
 */
public class ChunkSnapshot implements MeshBuilder.WorldAccess, MeshBuilder.ChunkData {

    // Coordinate del chunk centrale
    public final int centerX;
    public final int centerZ;

    // Dimensioni
    private final int chunkSize;
    private final int chunkHeight;

    // Buffer di SCRITTURA per il chunk centrale (modificabili durante il calcolo
    // luce)
    private final byte[] skyLightWrite;
    private final byte[] blockLightWrite;

    // Dati dei 9 chunk (3x3): neighbors[idx] dove idx = (dz+1)*3 + (dx+1)
    // idx 4 = chunk centrale
    private final int[][] neighborBlocks;
    private final byte[][] neighborSkyLight;
    private final byte[][] neighborBlockLight;
    private final byte[][] neighborFluidData;

    // Flag per sapere quali vicini esistono
    private final boolean[] neighborExists;

    private final BiomeProvider biomeProvider;

    /**
     * Crea uno snapshot di un chunk e dei suoi vicini.
     * 
     * @param cx          Coordinata X del chunk centrale
     * @param cz          Coordinata Z del chunk centrale
     * @param neighbors   Matrice 3x3 di chunk [dz+1][dx+1], può contenere null
     * @param chunkSize   Dimensione del chunk (tipicamente 16)
     * @param chunkHeight Altezza del chunk (tipicamente 256)
     */
    public ChunkSnapshot(int cx, int cz, Chunk[][] neighbors, int chunkSize, int chunkHeight,
            BiomeProvider biomeProvider) {
        this.centerX = cx;
        this.centerZ = cz;
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.biomeProvider = biomeProvider;

        int arraySize = chunkSize * chunkSize * chunkHeight;

        // Alloca buffer di scrittura per il chunk centrale
        this.skyLightWrite = new byte[arraySize];
        this.blockLightWrite = new byte[arraySize];

        // Alloca array per i 9 chunk
        this.neighborBlocks = new int[9][];
        this.neighborSkyLight = new byte[9][];
        this.neighborBlockLight = new byte[9][];
        this.neighborFluidData = new byte[9][];
        this.neighborExists = new boolean[9];

        // Copia i dati dai chunk
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int idx = (dz + 1) * 3 + (dx + 1);
                Chunk c = neighbors[dz + 1][dx + 1];

                if (c != null) {
                    neighborExists[idx] = true;
                    // Copia i dati (snapshot immutabile)
                    neighborBlocks[idx] = c.getBlockData().clone();
                    neighborSkyLight[idx] = c.getSkyLightData().clone();
                    neighborBlockLight[idx] = c.getBlockLightData().clone();
                    neighborFluidData[idx] = c.getFluidData().clone();
                } else {
                    neighborExists[idx] = false;
                    // Chunk non caricato - usa array vuoti (aria, luce 0)
                    neighborBlocks[idx] = new int[arraySize];
                    neighborSkyLight[idx] = new byte[arraySize];
                    neighborBlockLight[idx] = new byte[arraySize];
                    neighborFluidData[idx] = new byte[arraySize];
                    Arrays.fill(neighborBlocks[idx], 0); // AIR
                }
            }
        }

        // Inizializza i buffer di scrittura con i dati del chunk centrale
        int centerIdx = 4; // (0+1)*3 + (0+1) = 4
        System.arraycopy(neighborSkyLight[centerIdx], 0, skyLightWrite, 0, arraySize);
        System.arraycopy(neighborBlockLight[centerIdx], 0, blockLightWrite, 0, arraySize);
    }

    // =================================================================================
    // INDICIZZAZIONE
    // =================================================================================

    private int localIndex(int x, int y, int z) {
        return (y * chunkSize + z) * chunkSize + x;
    }

    // =================================================================================
    // METODI GET/SET LOCALI (per il chunk centrale, coordinate 0..chunkSize-1)
    // Questi leggono/scrivono nei buffer di SCRITTURA
    // =================================================================================

    /**
     * Legge un blocco dal chunk centrale (coordinate locali).
     */
    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return 0; // AIR
        }
        return neighborBlocks[4][localIndex(x, y, z)];
    }

    /**
     * Legge la skylight dal buffer di SCRITTURA (coordinate locali).
     */
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return 0;
        }
        return skyLightWrite[localIndex(x, y, z)] & 0xFF;
    }

    /**
     * Scrive la skylight nel buffer di SCRITTURA (coordinate locali).
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return;
        }
        skyLightWrite[localIndex(x, y, z)] = (byte) Math.max(0, Math.min(15, level));
    }

    /**
     * Legge la blocklight dal buffer di SCRITTURA (coordinate locali).
     */
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return 0;
        }
        return blockLightWrite[localIndex(x, y, z)] & 0xFF;
    }

    /**
     * Scrive la blocklight nel buffer di SCRITTURA (coordinate locali).
     */
    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return;
        }
        blockLightWrite[localIndex(x, y, z)] = (byte) Math.max(0, Math.min(15, level));
    }

    public int getFluidLevel(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return 0;
        }
        return neighborFluidData[4][localIndex(x, y, z)] & 0xFF;
    }

    // =================================================================================
    // METODI PEEK (per leggere da qualsiasi chunk, coordinate GLOBALI)
    // Questi leggono dai dati ORIGINALI dei vicini o dal buffer di scrittura per il
    // centro
    // =================================================================================

    /**
     * Legge un blocco da qualsiasi posizione (inclusi i vicini).
     * Coordinate globali (world coordinates).
     * 
     * IMPLEMENTA MeshBuilder.WorldAccess
     */
    @Override
    public int peekBlock(int globalX, int globalY, int globalZ) {
        if (globalY < 0 || globalY >= chunkHeight) {
            return 0; // AIR
        }

        int chunkX = Math.floorDiv(globalX, chunkSize);
        int chunkZ = Math.floorDiv(globalZ, chunkSize);
        int localX = Math.floorMod(globalX, chunkSize);
        int localZ = Math.floorMod(globalZ, chunkSize);

        int dx = chunkX - centerX;
        int dz = chunkZ - centerZ;

        // Fuori dal range 3x3
        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
            return 0; // AIR per chunk non disponibili
        }

        int idx = (dz + 1) * 3 + (dx + 1);

        if (!neighborExists[idx]) {
            return 0; // AIR
        }

        return neighborBlocks[idx][localIndex(localX, globalY, localZ)];
    }

    /**
     * Legge la skylight da qualsiasi posizione (inclusi i vicini).
     * Coordinate globali.
     */
    public int peekSkyLight(int globalX, int globalY, int globalZ) {
        if (globalY < 0)
            return 0;
        if (globalY >= chunkHeight)
            return 15; // Sopra il mondo = cielo pieno

        int chunkX = Math.floorDiv(globalX, chunkSize);
        int chunkZ = Math.floorDiv(globalZ, chunkSize);
        int localX = Math.floorMod(globalX, chunkSize);
        int localZ = Math.floorMod(globalZ, chunkSize);

        int dx = chunkX - centerX;
        int dz = chunkZ - centerZ;

        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
            return 0;
        }

        int idx = (dz + 1) * 3 + (dx + 1);

        // Se è il chunk centrale, leggi dal buffer di scrittura
        if (idx == 4) {
            return skyLightWrite[localIndex(localX, globalY, localZ)] & 0xFF;
        }

        // Altrimenti leggi dai dati originali del vicino
        if (!neighborExists[idx]) {
            return 0;
        }

        return neighborSkyLight[idx][localIndex(localX, globalY, localZ)] & 0xFF;
    }

    /**
     * Legge la blocklight da qualsiasi posizione (inclusi i vicini).
     * Coordinate globali.
     */
    public int peekBlockLight(int globalX, int globalY, int globalZ) {
        if (globalY < 0 || globalY >= chunkHeight) {
            return 0;
        }

        int chunkX = Math.floorDiv(globalX, chunkSize);
        int chunkZ = Math.floorDiv(globalZ, chunkSize);
        int localX = Math.floorMod(globalX, chunkSize);
        int localZ = Math.floorMod(globalZ, chunkSize);

        int dx = chunkX - centerX;
        int dz = chunkZ - centerZ;

        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
            return 0;
        }

        int idx = (dz + 1) * 3 + (dx + 1);

        // Se è il chunk centrale, leggi dal buffer di scrittura
        if (idx == 4) {
            return blockLightWrite[localIndex(localX, globalY, localZ)] & 0xFF;
        }

        // Altrimenti leggi dai dati originali del vicino
        if (!neighborExists[idx]) {
            return 0;
        }

        return neighborBlockLight[idx][localIndex(localX, globalY, localZ)] & 0xFF;
    }

    public int peekFluidLevel(int globalX, int globalY, int globalZ) {
        if (globalY < 0 || globalY >= chunkHeight) {
            return 0;
        }

        int chunkX = Math.floorDiv(globalX, chunkSize);
        int chunkZ = Math.floorDiv(globalZ, chunkSize);
        int localX = Math.floorMod(globalX, chunkSize);
        int localZ = Math.floorMod(globalZ, chunkSize);

        int dx = chunkX - centerX;
        int dz = chunkZ - centerZ;

        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
            return 0;
        }

        int idx = (dz + 1) * 3 + (dx + 1);

        if (!neighborExists[idx]) {
            return 0;
        }

        return neighborFluidData[idx][localIndex(localX, globalY, localZ)] & 0xFF;
    }

    @Override
    public Biome getBiome(int worldX, int worldZ) {
        if (biomeProvider == null)
            return engine.world.biome.Biomes.DEFAULT();
        return biomeProvider.getBiome(worldX, worldZ);
    }

    // =================================================================================
    // METODI PER OTTENERE I BUFFER (usati da World.integrateCompletedLight)
    // =================================================================================

    /**
     * Restituisce il buffer di skylight calcolato (da copiare nel Chunk).
     */
    public byte[] getSkyLightWriteBuffer() {
        return skyLightWrite;
    }

    /**
     * Restituisce il buffer di blocklight calcolato (da copiare nel Chunk).
     */
    public byte[] getBlockLightWriteBuffer() {
        return blockLightWrite;
    }

    // =================================================================================
    // UTILITY
    // =================================================================================

    /**
     * Coordinate world X dell'angolo del chunk centrale.
     */
    public int getWorldX() {
        return centerX * chunkSize;
    }

    /**
     * Coordinate world Z dell'angolo del chunk centrale.
     */
    public int getWorldZ() {
        return centerZ * chunkSize;
    }

    /**
     * Dimensione del chunk.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Altezza del chunk.
     */
    public int getChunkHeight() {
        return chunkHeight;
    }
}