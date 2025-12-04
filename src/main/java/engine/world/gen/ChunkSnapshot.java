package engine.world.gen;

import engine.world.Chunk;
import engine.world.block.Blocks;
import engine.world.gen.MeshBuilder.WorldAccess;
import java.util.Arrays; // Aggiunto per l'inizializzazione

/**
 * Snapshot thread-safe di un'area 3x3 chunk per la generazione asincrona di mesh e luce.
 * Ora include i buffer di luce (Block e Sky) dei vicini per una corretta propagazione cross-chunk.
 */
public class ChunkSnapshot implements WorldAccess {

    public final int centerX;
    public final int centerZ;
    private final int chunkSize;
    private final int chunkHeight;

    // Cache 3x3 di blocchi [dz][dx][index]
    private final int[][][] blocksCache = new int[3][3][];
    
    // Cache 3x3 di altezze [dz][dx][index]
    private final int[][][] heightMapCache = new int[3][3][];

    // NUOVO: Cache 3x3 dei buffer di Block Light dei vicini (SOLO LETTURA)
    private final byte[][][] blockLightBuffersCache = new byte[3][3][]; 
    // NUOVO: Cache 3x3 dei buffer di Sky Light dei vicini (SOLO LETTURA)
    private final byte[][][] skyLightBuffersCache = new byte[3][3][]; 
    
    // Buffer di luce scrivibile per il chunk centrale
    private final byte[] blockLightBuffer; 
    private final byte[] skyLightBuffer; 

    public ChunkSnapshot(int cx, int cz, Chunk[][] neighbors, int chunkSize, int chunkHeight) {
        this.centerX = cx;
        this.centerZ = cz;
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        
        int bufferSize = chunkSize * chunkHeight * chunkSize;
        this.blockLightBuffer = new byte[bufferSize]; 
        this.skyLightBuffer = new byte[bufferSize];

        // Copia i dati dai chunk vicini
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Chunk c = neighbors[dz + 1][dx + 1];
                if (c != null) {
                    this.blocksCache[dz + 1][dx + 1] = c.getBlockData(); 
                    this.heightMapCache[dz + 1][dx + 1] = c.getHeightMapData();

                    // 1. COPIA DEI RIFERIMENTI LUCE DEI VICINI (SOLO LETTURA)
                    this.blockLightBuffersCache[dz + 1][dx + 1] = c.getBlockLightData(); 
                    this.skyLightBuffersCache[dz + 1][dx + 1] = c.getSkyLightData();
                    
                    // 2. INIZIALIZZAZIONE DEL BUFFER CENTRALE (scrivibile)
                    if (dx == 0 && dz == 0) {
                        // Inizializza il buffer scrivibile con i dati esistenti del chunk
                        System.arraycopy(c.getBlockLightData(), 0, this.blockLightBuffer, 0, bufferSize);
                        System.arraycopy(c.getSkyLightData(), 0, this.skyLightBuffer, 0, bufferSize);
                    }
                }
            }
        }
    }

    public int getIndex(int x, int y, int z) {
        // La dimensione SIZE qui è chunkSize
        return (y * chunkSize + z) * chunkSize + x;
    }

    // =======================================================
    // WORLD ACCESS (READS)
    // =======================================================
    
    @Override
    public int peekBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= chunkHeight) return 0;

        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);

        int offX = cx - centerX;
        int offZ = cz - centerZ;

        if (offX < -1 || offX > 1 || offZ < -1 || offZ > 1) {
            return 0; // Out of bounds snapshot
        }

        int[] data = blocksCache[offZ + 1][offX + 1];
        if (data == null) return 0;

        int lx = Math.floorMod(worldX, chunkSize);
        int lz = Math.floorMod(worldZ, chunkSize);
        
        return data[getIndex(lx, worldY, lz)];
    }

    @Override
    public int peekSkyLight(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= chunkHeight) return 15;
        
        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);
        
        int offX = cx - centerX;
        int offZ = cz - centerZ;

        if (offX < -1 || offX > 1 || offZ < -1 || offZ > 1) {
            return 15; // Fallback fuori dall'area 3x3
        }

        int lx = Math.floorMod(worldX, chunkSize);
        int lz = Math.floorMod(worldZ, chunkSize);
        int idx = getIndex(lx, worldY, lz);

        // Chunk centrale (legge dal buffer scrivibile)
        if (offX == 0 && offZ == 0) {
            return skyLightBuffer[idx] & 0xFF; 
        }

        // Vicini (legge dal buffer di cache)
        byte[] neighborLight = skyLightBuffersCache[offZ + 1][offX + 1];
        if (neighborLight != null) {
            return neighborLight[idx] & 0xFF;
        }

        return 15; // Fallback se il vicino non è caricato
    }

    @Override
    public int peekBlockLight(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= chunkHeight) return 0;

        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);
        
        int offX = cx - centerX;
        int offZ = cz - centerZ;

        if (offX < -1 || offX > 1 || offZ < -1 || offZ > 1) {
            return 0; // Fallback fuori dall'area 3x3
        }

        int lx = Math.floorMod(worldX, chunkSize);
        int lz = Math.floorMod(worldZ, chunkSize);
        int idx = getIndex(lx, worldY, lz);
        
        // Chunk centrale
        if (offX == 0 && offZ == 0) {
            return blockLightBuffer[idx] & 0xFF;
        }
        
        // Vicini
        byte[] neighborLight = blockLightBuffersCache[offZ + 1][offX + 1];
        if (neighborLight != null) {
            return neighborLight[idx] & 0xFF;
        }

        return 0; // Fallback se il vicino non è caricato
    }
    
    // =======================================================
    // LIGHT PROPAGATOR ACCESS (WRITES)
    // =======================================================
    
    /**
     * Setta il livello di Sky Light nel buffer scrivibile (chunk centrale).
     */
    public void setSkyLight(int x, int y, int z, int val) {
        int idx = getIndex(x, y, z);
        skyLightBuffer[idx] = (byte) Math.max(0, Math.min(15, val));
    }
    
    /**
     * Setta il livello di Block Light nel buffer scrivibile (chunk centrale).
     */
    public void setBlockLight(int x, int y, int z, int val) {
        int idx = getIndex(x, y, z);
        blockLightBuffer[idx] = (byte) Math.max(0, Math.min(15, val));
    }
    
    public int getBlock(int x, int y, int z) {
        // Accesso veloce locale (chunk centrale)
        if (blocksCache[1][1] == null) return 0;
        return blocksCache[1][1][getIndex(x, y, z)];
    }
    
    public byte[] getSkyLightWriteBuffer() {
        return skyLightBuffer;
    }

    public byte[] getBlockLightWriteBuffer() {
        return blockLightBuffer;
    }
}