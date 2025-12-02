package engine.world.gen;

import engine.world.Chunk;
import engine.world.block.Blocks;
import engine.world.gen.MeshBuilder.WorldAccess;

/**
 * Snapshot thread-safe di un'area 3x3 chunk per la generazione asincrona di mesh e luce.
 */
public class ChunkSnapshot implements WorldAccess {

    public final int centerX;
    public final int centerZ;
    private final int chunkSize;
    private final int chunkHeight;

    // Cache 3x3 di blocchi [dz][dx][index]
    // index = (y * size + z) * size + x
    private final int[][][] blocksCache = new int[3][3][];
    
    // Cache 3x3 di altezze [dz][dx][index]
    private final int[][][] heightMapCache = new int[3][3][];

    // Buffer di luce SOLO per il chunk centrale (quello che stiamo meshando)
    // Per un calcolo luce perfetto servirebbero anche i buffer dei vicini, 
    // ma per ora calcoliamo la luce interna e verticale del chunk centrale.
    private final byte[] lightBuffer; // MSB: Sky, LSB: Block

    public ChunkSnapshot(int cx, int cz, Chunk[][] neighbors, int chunkSize, int chunkHeight) {
        this.centerX = cx;
        this.centerZ = cz;
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.lightBuffer = new byte[chunkSize * chunkHeight * chunkSize];

        // Copia i dati dai chunk vicini
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Chunk c = neighbors[dz + 1][dx + 1];
                if (c != null) {
                    // Nota: Stiamo assumendo che blockData non cambi durante la generazione della mesh
                    // Se il gioco permette modifiche concorrenti, qui servirebbe System.arraycopy
                    this.blocksCache[dz + 1][dx + 1] = c.getBlockData(); 
                    this.heightMapCache[dz + 1][dx + 1] = c.getHeightMapData();
                }
            }
        }
    }

    private int getIndex(int x, int y, int z) {
        return (y * chunkSize + z) * chunkSize + x;
    }

    @Override
    public int peekBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= chunkHeight) return 0;

        // Calcola in quale chunk (relativo) si trova il blocco
        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);

        int offX = cx - centerX;
        int offZ = cz - centerZ;

        // Se siamo fuori dall'area 3x3, ritorniamo aria (o gestiamo edge case)
        if (offX < -1 || offX > 1 || offZ < -1 || offZ > 1) {
            return 0; // Out of bounds snapshot
        }

        int[] data = blocksCache[offZ + 1][offX + 1];
        if (data == null) return 0;

        int lx = Math.floorMod(worldX, chunkSize);
        int lz = Math.floorMod(worldZ, chunkSize);
        
        return data[getIndex(lx, worldY, lz)];
    }

    // Accesso alla luce: 
    // Se siamo nel chunk centrale, leggiamo dal buffer locale che il worker sta calcolando.
    // Se siamo fuori, ritorniamo 15 (cielo) o 0 per evitare occlusione errata, 
    // oppure 0 se non abbiamo dati.
    @Override
    public int peekSkyLight(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= chunkHeight) return 15;
        
        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);
        
        if (cx == centerX && cz == centerZ) {
            int lx = Math.floorMod(worldX, chunkSize);
            int lz = Math.floorMod(worldZ, chunkSize);
            return (lightBuffer[getIndex(lx, worldY, lz)] >> 4) & 0xF;
        }
        return 15; // Assumiamo luce piena dai vicini per non creare ombre nere ai bordi
    }

    @Override
    public int peekBlockLight(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= chunkHeight) return 0;

        int cx = Math.floorDiv(worldX, chunkSize);
        int cz = Math.floorDiv(worldZ, chunkSize);

        if (cx == centerX && cz == centerZ) {
            int lx = Math.floorMod(worldX, chunkSize);
            int lz = Math.floorMod(worldZ, chunkSize);
            return lightBuffer[getIndex(lx, worldY, lz)] & 0xF;
        }
        return 0;
    }
    
    // Metodi per il LightPropagator "Simulato"
    public void setSkyLight(int x, int y, int z, int val) {
        int idx = getIndex(x, y, z);
        byte current = lightBuffer[idx];
        lightBuffer[idx] = (byte) ((current & 0x0F) | (val << 4));
    }
    
    public void setBlockLight(int x, int y, int z, int val) {
        int idx = getIndex(x, y, z);
        byte current = lightBuffer[idx];
        lightBuffer[idx] = (byte) ((current & 0xF0) | (val & 0x0F));
    }
    
    public int getBlock(int x, int y, int z) {
        // Accesso veloce locale (chunk centrale)
        if (blocksCache[1][1] == null) return 0;
        return blocksCache[1][1][getIndex(x, y, z)];
    }
    
    public byte[] getLightBuffer() {
        return lightBuffer;
    }
}