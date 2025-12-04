package engine.world.light;

import engine.world.World;
import engine.world.Chunk;
import engine.world.gen.ChunkSnapshot;
import engine.world.block.Block;
import engine.world.block.Blocks;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class LightPropagator {

    // --- COSTANTI BITWISE (Assumendo Chunk.SIZE = 16) ---
    // Se la tua Chunk.SIZE è diversa (es. 32), modifica SHIFT a 5 e MASK a 31
    private static final int CHUNK_SHIFT = 4; 
    private static final int CHUNK_MASK = 0xF; 

    // --- COSTANTI PACKING (Per coordinate globali in un long) ---
    // Struttura Long: [Level 4b][Y 12b][Z 24b][X 24b]
    private static final int BITS_X = 24;
    private static final int BITS_Z = 24;
    private static final int BITS_Y = 12; // Supporta altezza fino a 4096
    private static final int BITS_LEVEL = 4;
    
    private static final long MASK_COORD_X = (1L << BITS_X) - 1;
    private static final long MASK_COORD_Z = (1L << BITS_Z) - 1;
    private static final long MASK_COORD_Y = (1L << BITS_Y) - 1;
    private static final long MASK_LIGHT_LEVEL = 0xF;

    // --- COSTANTI PACKING SNAPSHOT (Per coordinate locali in un int) ---
    private static final int S_SHIFT_X = 0;
    private static final int S_SHIFT_Z = 5;
    private static final int S_SHIFT_Y = 10;
    private static final int S_SHIFT_LEVEL = 20;
    private static final int S_MASK_XZ = 0x1F;
    private static final int S_MASK_Y  = 0x3FF;
    private static final int S_MASK_LEVEL = 0xF;

    // Direzioni "appiattite" per iterazione veloce
    private static final int[][] DIRS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1}
    };

    /**
     * Coda circolare primitiva per evitare allocazione di oggetti Node.
     * Si espande automaticamente se necessario.
     */
    private static class LongLightQueue {
        long[] data = new long[1024];
        int head = 0;
        int tail = 0;

        void add(long val) {
            data[tail] = val;
            tail = (tail + 1) & (data.length - 1);
            if (tail == head) resize();
        }

        long poll() {
            long val = data[head];
            head = (head + 1) & (data.length - 1);
            return val;
        }

        boolean isEmpty() { return head == tail; }

        private void resize() {
            long[] newData = new long[data.length << 1];
            int len = data.length;
            int r = len - head;
            System.arraycopy(data, head, newData, 0, r);
            System.arraycopy(data, 0, newData, r, tail);
            head = 0;
            tail = len;
            data = newData;
        }
    }

    // =================================================================================
    //  METODI WORLD (Globali)
    // =================================================================================

    public static void addBlockLight(World world, int wx, int wy, int wz, int lightLevel) {
        if (lightLevel <= 0) return;

        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null) return;

        // Usa operazioni bitwise invece di floorMod (molto più veloci)
        chunk.setBlockLight(wx & CHUNK_MASK, wy, wz & CHUNK_MASK, lightLevel);
        chunk.setDirty(true);

        LongLightQueue queue = new LongLightQueue();
        queue.add(packWorld(wx, wy, wz, lightLevel));

        propagateAdd(world, queue);
    }

    public static void removeBlockLight(World world, int wx, int wy, int wz) {
        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null) return;

        int oldLight = chunk.getBlockLight(wx & CHUNK_MASK, wy, wz & CHUNK_MASK);
        if (oldLight == 0) return;

        // Code separate per le due fasi
        LongLightQueue removalQueue = new LongLightQueue();
        LongLightQueue addQueue = new LongLightQueue();

        // Imposta a 0 e avvia rimozione
        chunk.setBlockLight(wx & CHUNK_MASK, wy, wz & CHUNK_MASK, 0);
        chunk.setDirty(true);
        removalQueue.add(packWorld(wx, wy, wz, oldLight));

        // --- FASE 1: RIMOZIONE ---
        int worldHeight = world.getConfig().worldHeight;

        while (!removalQueue.isEmpty()) {
            long val = removalQueue.poll();
            int level = unpackLevel(val);
            int x = unpackX(val);
            int y = unpackY(val);
            int z = unpackZ(val);

            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];

                if (ny < 0 || ny >= worldHeight) continue;

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null) continue;

                int neighborLight = nChunk.getBlockLight(nx & CHUNK_MASK, ny, nz & CHUNK_MASK);

                if (neighborLight != 0 && neighborLight < level) {
                    // Era illuminato da noi -> spegni e propaga spegnimento
                    nChunk.setBlockLight(nx & CHUNK_MASK, ny, nz & CHUNK_MASK, 0);
                    nChunk.setDirty(true);
                    removalQueue.add(packWorld(nx, ny, nz, neighborLight));
                } else if (neighborLight >= level) {
                    // È una sorgente esterna -> aggiungi alla coda di riempimento
                    addQueue.add(packWorld(nx, ny, nz, neighborLight));
                }
            }
        }

        // --- FASE 2: RE-PROPAGAZIONE ---
        propagateAdd(world, addQueue);
    }

    // Logica di propagazione condivisa (Add e Re-fill fase 2)
    private static void propagateAdd(World world, LongLightQueue queue) {
        int worldHeight = world.getConfig().worldHeight;

        while (!queue.isEmpty()) {
            long val = queue.poll();
            int level = unpackLevel(val);
            if (level <= 1) continue;

            int x = unpackX(val);
            int y = unpackY(val);
            int z = unpackZ(val);

            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];

                if (ny < 0 || ny >= worldHeight) continue;

                // Controllo Opacity rapido prima di getChunk se possibile, 
                // ma dobbiamo recuperare il blocco dal mondo.
                int neighborBlockId = world.peekBlock(nx, ny, nz);
                if (Blocks.get(neighborBlockId).isOpaque()) continue; // isOpaque include già !isTransparent solitamente

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null) continue;

                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;

                int oldLevel = nChunk.getBlockLight(nlx, ny, nlz);
                int newLevel = level - 1;

                if (newLevel > oldLevel) {
                    nChunk.setBlockLight(nlx, ny, nlz, newLevel);
                    nChunk.setDirty(true);
                    queue.add(packWorld(nx, ny, nz, newLevel));
                }
            }
        }
    }

    // =================================================================================
    //  METODI CHUNK / SNAPSHOT (Locali)
    // =================================================================================

    public static void recomputeChunkSkyLightVertical(World world, Chunk chunk) {
        if (chunk == null) return;
        
        // Accesso diretto ai dati se possibile sarebbe meglio, ma usiamo i setter
        int wx0 = chunk.getWorldX();
        int wz0 = chunk.getWorldZ();
        int worldHeight = world.getConfig().worldHeight;

        for (int lx = 0; lx < Chunk.SIZE; lx++) {
            for (int lz = 0; lz < Chunk.SIZE; lz++) {
                int wx = wx0 + lx;
                int wz = wz0 + lz;
                
                // Ottimizzazione: Scansione verticale
                // Appena troviamo un blocco opaco, settiamo tutto sotto a 0 in un colpo solo 
                // (se l'array di dati lo permette) o continuiamo il loop velocemente.
                boolean blocked = false;
                for (int y = worldHeight - 1; y >= 0; y--) {
                    if (blocked) {
                        chunk.setSkyLight(lx, y, lz, 0);
                        continue;
                    }

                    int blockId = world.getBlock(wx, y, wz);
                    if (Blocks.get(blockId).isOpaque()) {
                        blocked = true;
                        chunk.setSkyLight(lx, y, lz, 0);
                    } else {
                        chunk.setSkyLight(lx, y, lz, 15);
                    }
                }
            }
        }
        chunk.setDirty(true);
    }

    /**
     * Calcola la luce completa (Sky + Block) sullo Snapshot.
     * Versione ottimizzata con Array Int e Packing.
     */
    public static void computeLightForSnapshot(ChunkSnapshot snapshot, int chunkSize, int chunkHeight, List<Long> neighborsToPropagate) {
        Set<Long> neighborsSet = new HashSet<>();
        
        // Coda su array int per performance massima
        int[] queue = new int[chunkSize * chunkHeight * chunkSize * 2];
        int head = 0;
        int tail = 0;

        // --- 1. SKY LIGHT (Vertical + Init Queue) ---
        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                int light = 15;
                for (int y = chunkHeight - 1; y >= 0; y--) {
                    int blockId = snapshot.getBlock(x, y, z);
                    if (Blocks.get(blockId).isOpaque()) light = 0;
                    
                    snapshot.setSkyLight(x, y, z, light);
                    if (light > 0) {
                        queue[tail++] = packSnapshot(x, y, z, light);
                    }
                }
            }
        }

        // --- 2. SKY LIGHT (Propagazione) ---
        runBfsSnapshot(snapshot, queue, head, tail, true, chunkSize, chunkHeight, neighborsSet);

        // --- 3. BLOCK LIGHT (Init + Propagazione) ---
        head = 0; tail = 0; // Reset coda
        for (int x = 0; x < chunkSize; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < chunkSize; z++) {
                    int blockId = snapshot.getBlock(x, y, z);
                    int emission = Blocks.get(blockId).getLightLevel();
                    if (emission > 0) {
                        snapshot.setBlockLight(x, y, z, emission);
                        queue[tail++] = packSnapshot(x, y, z, emission);
                    } else {
                        snapshot.setBlockLight(x, y, z, 0);
                    }
                }
            }
        }
        runBfsSnapshot(snapshot, queue, head, tail, false, chunkSize, chunkHeight, neighborsSet);

        neighborsToPropagate.addAll(neighborsSet);
    }

    private static void runBfsSnapshot(ChunkSnapshot snapshot, int[] queue, int head, int tail, 
                                     boolean isSky, int chunkSize, int chunkHeight, Set<Long> neighborsSet) {
        int cx = snapshot.centerX;
        int cz = snapshot.centerZ;
        int worldOffsetX = cx * chunkSize;
        int worldOffsetZ = cz * chunkSize;

        while (head != tail) {
            int val = queue[head++];
            int level = (val >> S_SHIFT_LEVEL) & S_MASK_LEVEL;
            if (level <= 1) continue;

            int x = (val >> S_SHIFT_X) & S_MASK_XZ;
            int z = (val >> S_SHIFT_Z) & S_MASK_XZ;
            int y = (val >> S_SHIFT_Y) & S_MASK_Y;

            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];

                if (ny < 0 || ny >= chunkHeight) continue;

                if (nx < 0 || nx >= chunkSize || nz < 0 || nz >= chunkSize) {
                    int neighborCX = cx + (nx < 0 ? -1 : (nx >= chunkSize ? 1 : 0));
                    int neighborCZ = cz + (nz < 0 ? -1 : (nz >= chunkSize ? 1 : 0));
                    neighborsSet.add(((long) neighborCX << 32) | (neighborCZ & 0xFFFFFFFFL));
                    continue;
                }

                int blockId = snapshot.getBlock(nx, ny, nz);
                if (Blocks.get(blockId).isOpaque()) continue;

                int globalX = worldOffsetX + nx;
                int globalZ = worldOffsetZ + nz;
                
                // Usa peekSkyLight/BlockLight esistenti che accettano coord globali
                int currentLight = isSky ? snapshot.peekSkyLight(globalX, ny, globalZ) 
                                         : snapshot.peekBlockLight(globalX, ny, globalZ);
                
                int newLevel = level - 1;
                // Esempio penalità liquidi
                // if (Blocks.get(blockId).isLiquid()) newLevel -= 2;

                if (newLevel > currentLight) {
                    if (isSky) snapshot.setSkyLight(nx, ny, nz, newLevel);
                    else       snapshot.setBlockLight(nx, ny, nz, newLevel);
                    
                    queue[tail++] = packSnapshot(nx, ny, nz, newLevel);
                }
            }
        }
    }

    // =================================================================================
    //  HELPER BIT-PACKING
    // =================================================================================

    // Packing per coordinate GLOBALI (Long)
    // X e Z sono offsettati per gestire i numeri negativi (aggiungiamo un bias se necessario, 
    // ma qui usiamo mask, assumendo che i bit siano sufficienti per il casting)
    private static long packWorld(int x, int y, int z, int level) {
        return ((long)level << (BITS_Y + BITS_Z + BITS_X)) | 
               ((long)(y & MASK_COORD_Y) << (BITS_Z + BITS_X)) | 
               ((long)(z & MASK_COORD_Z) << BITS_X) | 
               (x & MASK_COORD_X);
    }

    private static int unpackX(long val) {
        // Sign extension trick: cast to int with proper shifting if needed. 
        // Qui assumiamo che i 24 bit siano sufficienti e gestiamo il segno manualmente se x è negativo.
        // Metodo semplice: cast to int, then shift back up/down to restore sign if needed.
        int raw = (int)(val & MASK_COORD_X);
        // Fix per segno se usiamo 24 bit: se il 24esimo bit è 1, estendi il segno
        if ((raw & (1 << 23)) != 0) raw |= ~MASK_COORD_X;
        return raw;
    }
    private static int unpackZ(long val) {
        int raw = (int)((val >>> BITS_X) & MASK_COORD_Z);
        if ((raw & (1 << 23)) != 0) raw |= ~MASK_COORD_Z;
        return raw;
    }
    private static int unpackY(long val) { return (int)((val >>> (BITS_X + BITS_Z)) & MASK_COORD_Y); }
    private static int unpackLevel(long val) { return (int)((val >>> (BITS_X + BITS_Z + BITS_Y)) & MASK_LIGHT_LEVEL); }

    // Packing per coordinate SNAPSHOT/LOCALI (Int)
    private static int packSnapshot(int x, int y, int z, int level) {
        return (x << S_SHIFT_X) | (z << S_SHIFT_Z) | (y << S_SHIFT_Y) | (level << S_SHIFT_LEVEL);
    }
}