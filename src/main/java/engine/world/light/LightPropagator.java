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
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNK_MASK = 0xF;

    // --- COSTANTI PACKING (Per coordinate globali in un long) ---
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
    private static final int S_MASK_Y = 0x3FF;
    private static final int S_MASK_LEVEL = 0xF;

    // Direzioni "appiattite" per iterazione veloce
    private static final int[][] DIRS = {
            { 1, 0, 0 }, { -1, 0, 0 },
            { 0, 1, 0 }, { 0, -1, 0 },
            { 0, 0, 1 }, { 0, 0, -1 }
    };

    /**
     * Coda circolare primitiva per evitare allocazione di oggetti Node.
     */
    private static class LongLightQueue {
        long[] data = new long[1024];
        int head = 0;
        int tail = 0;

        void add(long val) {
            data[tail] = val;
            tail = (tail + 1) & (data.length - 1);
            if (tail == head)
                resize();
        }

        long poll() {
            long val = data[head];
            head = (head + 1) & (data.length - 1);
            return val;
        }

        boolean isEmpty() {
            return head == tail;
        }

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
    // METODI WORLD (Globali)
    // =================================================================================

        public static void addBlockLight(World world, int wx, int wy, int wz, int lightLevel) {
            if (lightLevel <= 0)
                return;

            Chunk chunk = world.getChunkAtWorld(wx, wz);
            if (chunk == null)
                return;

            chunk.setBlockLight(wx & CHUNK_MASK, wy, wz & CHUNK_MASK, lightLevel);
            // ✅ RIMOSSO: chunk.setDirty(true);

            LongLightQueue queue = new LongLightQueue();
            queue.add(packWorld(wx, wy, wz, lightLevel));

            propagateAdd(world, queue);
        }

        public static void removeBlockLight(World world, int wx, int wy, int wz) {
            Chunk chunk = world.getChunkAtWorld(wx, wz);
            if (chunk == null)
                return;

            int oldLight = chunk.getBlockLight(wx & CHUNK_MASK, wy, wz & CHUNK_MASK);
            if (oldLight == 0)
                return;

            LongLightQueue removalQueue = new LongLightQueue();
            LongLightQueue addQueue = new LongLightQueue();

            chunk.setBlockLight(wx & CHUNK_MASK, wy, wz & CHUNK_MASK, 0);
            // ✅ RIMOSSO: chunk.setDirty(true);
            removalQueue.add(packWorld(wx, wy, wz, oldLight));

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

                    if (ny < 0 || ny >= worldHeight)
                        continue;

                    Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                    if (nChunk == null)
                        continue;

                    int neighborLight = nChunk.getBlockLight(nx & CHUNK_MASK, ny, nz & CHUNK_MASK);

                    if (neighborLight != 0 && neighborLight < level) {
                        nChunk.setBlockLight(nx & CHUNK_MASK, ny, nz & CHUNK_MASK, 0);
                        // ✅ RIMOSSO: nChunk.setDirty(true);
                        removalQueue.add(packWorld(nx, ny, nz, neighborLight));
                    } else if (neighborLight >= level) {
                        addQueue.add(packWorld(nx, ny, nz, neighborLight));
                    }
                }
            }

            propagateAdd(world, addQueue);
        }

        private static void propagateAdd(World world, LongLightQueue queue) {
            int worldHeight = world.getConfig().worldHeight;

            while (!queue.isEmpty()) {
                long val = queue.poll();
                int level = unpackLevel(val);
                if (level <= 1)
                    continue;

                int x = unpackX(val);
                int y = unpackY(val);
                int z = unpackZ(val);

                for (int[] d : DIRS) {
                    int nx = x + d[0];
                    int ny = y + d[1];
                    int nz = z + d[2];

                    if (ny < 0 || ny >= worldHeight)
                        continue;

                    int neighborBlockId = world.peekBlock(nx, ny, nz);
                    if (Blocks.get(neighborBlockId).isOpaque())
                        continue;

                    Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                    if (nChunk == null)
                        continue;

                    int nlx = nx & CHUNK_MASK;
                    int nlz = nz & CHUNK_MASK;

                    int oldLevel = nChunk.getBlockLight(nlx, ny, nlz);
                    int newLevel = level - 1;

                    if (newLevel > oldLevel) {
                        nChunk.setBlockLight(nlx, ny, nlz, newLevel);
                        // ✅ RIMOSSO: nChunk.setDirty(true);
                        queue.add(packWorld(nx, ny, nz, newLevel));
                    }
                }
            }
        }

    // =================================================================================
    // METODI CHUNK / SNAPSHOT (Locali)
    // =================================================================================

        public static void recomputeChunkSkyLightVertical(World world, Chunk chunk) {
            if (chunk == null)
                return;

            int wx0 = chunk.getWorldX();
            int wz0 = chunk.getWorldZ();
            int worldHeight = world.getConfig().worldHeight;

            for (int lx = 0; lx < Chunk.SIZE; lx++) {
                for (int lz = 0; lz < Chunk.SIZE; lz++) {
                    int wx = wx0 + lx;
                    int wz = wz0 + lz;

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
        }
    private static long packWorld(int x, int y, int z, int level) {
        return ((long) level << (BITS_Y + BITS_Z + BITS_X)) |
                ((long) (y & MASK_COORD_Y) << (BITS_Z + BITS_X)) |
                ((long) (z & MASK_COORD_Z) << BITS_X) |
                (x & MASK_COORD_X);
    }

    private static int unpackX(long val) {
        int raw = (int) (val & MASK_COORD_X);
        if ((raw & (1 << 23)) != 0)
            raw |= ~MASK_COORD_X;
        return raw;
    }

    private static int unpackZ(long val) {
        int raw = (int) ((val >>> BITS_X) & MASK_COORD_Z);
        if ((raw & (1 << 23)) != 0)
            raw |= ~MASK_COORD_Z;
        return raw;
    }

    private static int unpackY(long val) {
        return (int) ((val >>> (BITS_X + BITS_Z)) & MASK_COORD_Y);
    }

    private static int unpackLevel(long val) {
        return (int) ((val >>> (BITS_X + BITS_Z + BITS_Y)) & MASK_LIGHT_LEVEL);
    }

    private static int packSnapshot(int x, int y, int z, int level) {
        return (x << S_SHIFT_X) | (z << S_SHIFT_Z) | (y << S_SHIFT_Y) | (level << S_SHIFT_LEVEL);
    }

    public static void computeFullLightForSnapshot(ChunkSnapshot snapshot, int chunkSize, int chunkHeight,
            List<Long> neighborsToPropagate) {
        Set<Long> neighborsSet = new HashSet<>();

        int[] queue = new int[chunkSize * chunkHeight * chunkSize * 2];
        int head = 0;
        int tail = 0;

        // --- 1. SKY LIGHT (Vertical + Init Queue) ---
        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                int light = 15;
                for (int y = chunkHeight - 1; y >= 0; y--) {
                    int blockId = snapshot.getBlock(x, y, z);
                    if (Blocks.get(blockId).isOpaque())
                        light = 0;

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
        head = 0;
        tail = 0;
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

    // Sostituisci il tuo metodo propagateLightOnlyForSnapshot con questo:
    public static void propagateLightOnlyForSnapshot(ChunkSnapshot snapshot, int chunkSize, int chunkHeight,
            List<Long> neighborsToPropagate) {
        Set<Long> neighborsSet = new HashSet<>();
        int[] queue = new int[chunkSize * chunkHeight * chunkSize * 2];
        int head = 0;
        int tail = 0;

        int wx = snapshot.getWorldX();
        int wz = snapshot.getWorldZ();

        // ================= 1. SKY LIGHT =================

        // A. Init Interno (Luci già esistenti nel chunk)
        for (int x = 0; x < chunkSize; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < chunkSize; z++) {
                    // NOTA: Usa peekSkyLight con coordinate globali
                    int light = snapshot.peekSkyLight(wx + x, y, wz + z);
                    if (light > 0)
                        queue[tail++] = packSnapshot(x, y, z, light);
                }
            }
        }

        // B. Init Bordi (IMPORTANTE: Tira la luce dai vicini!)
        // Questo è il pezzo che ti mancava!
        for (int y = 0; y < chunkHeight; y++) {
            for (int i = 0; i < chunkSize; i++) {
                // Controlla Ovest (x=-1), Est (x=16), Nord (z=-1), Sud (z=16)
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, 0, y, i, -1, 0, true);
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, chunkSize - 1, y, i, 1, 0, true);
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, i, y, 0, 0, -1, true);
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, i, y, chunkSize - 1, 0, 1, true);
            }
        }

        runBfsSnapshot(snapshot, queue, head, tail, true, chunkSize, chunkHeight, neighborsSet);

        // ================= 2. BLOCK LIGHT =================

        head = 0;
        tail = 0;

        // A. Init Interno
        for (int x = 0; x < chunkSize; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < chunkSize; z++) {
                    int currentLight = snapshot.peekBlockLight(wx + x, y, wz + z);
                    int blockId = snapshot.getBlock(x, y, z);
                    int emission = Blocks.get(blockId).getLightLevel();
                    if (emission > currentLight) {
                        currentLight = emission;
                        snapshot.setBlockLight(x, y, z, emission);
                    }
                    if (currentLight > 0) {
                        queue[tail++] = packSnapshot(x, y, z, currentLight);
                    }
                }
            }
        }

        // B. Init Bordi (Tira la luce dai vicini)
        for (int y = 0; y < chunkHeight; y++) {
            for (int i = 0; i < chunkSize; i++) {
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, 0, y, i, -1, 0, false);
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, chunkSize - 1, y, i, 1, 0, false);
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, i, y, 0, 0, -1, false);
                tail = checkAndPullLight(snapshot, queue, tail, wx, wz, i, y, chunkSize - 1, 0, 1, false);
            }
        }

        runBfsSnapshot(snapshot, queue, head, tail, false, chunkSize, chunkHeight, neighborsSet);
        neighborsToPropagate.addAll(neighborsSet);
    }

    // --- COPIA QUESTO METODO HELPER NELLA CLASSE LightPropagator ---
    private static int checkAndPullLight(ChunkSnapshot snapshot, int[] queue, int tail,
            int wx, int wz, int lx, int ly, int lz,
            int dx, int dz, boolean isSky) {
        // 1. Coordinate globali del VICINO
        int nGlobalX = wx + lx + dx;
        int nGlobalZ = wz + lz + dz;

        // 2. Leggi quanta luce ha il vicino
        int neighborLight = isSky ? snapshot.peekSkyLight(nGlobalX, ly, nGlobalZ)
                : snapshot.peekBlockLight(nGlobalX, ly, nGlobalZ);

        if (neighborLight <= 1)
            return tail; // Vicino buio, inutile continuare

        // 3. Leggi quanta luce abbiamo NOI (nel bordo)
        int currentLight = isSky ? snapshot.peekSkyLight(wx + lx, ly, wz + lz)
                : snapshot.peekBlockLight(wx + lx, ly, wz + lz);

        // Se il blocco locale è solido, la luce non entra
        int blockId = snapshot.getBlock(lx, ly, lz);
        if (Blocks.get(blockId).isOpaque())
            return tail;

        // 4. SE IL VICINO E' PIU' LUMINOSO -> IMPORTA LA LUCE
        if (neighborLight > currentLight + 1) {
            int newLight = neighborLight - 1;

            // Scrivi nel buffer locale
            if (isSky)
                snapshot.setSkyLight(lx, ly, lz, newLight);
            else
                snapshot.setBlockLight(lx, ly, lz, newLight);

            // AGGIUNGI ALLA CODA: La BFS ora propagherà questo valore verso l'interno!
            queue[tail++] = packSnapshot(lx, ly, lz, newLight);
        }
        return tail;
    }


    /**
     * Rimuove luce da una posizione specifica (non necessariamente una sorgente)
     */
    public static void removeLightAt(World world, int wx, int wy, int wz, int lightLevel, boolean isSky) {
        LongLightQueue removalQueue = new LongLightQueue();
        LongLightQueue addQueue = new LongLightQueue();
        
        removalQueue.add(packWorld(wx, wy, wz, lightLevel));
        
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
                
                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;
                
                int neighborLight = isSky ? nChunk.getSkyLight(nlx, ny, nlz) 
                                        : nChunk.getBlockLight(nlx, ny, nlz);
                
                if (neighborLight == 0) continue;
                
                // Se la luce del vicino è più debole, rimuovila (veniva da noi)
                if (neighborLight < level) {
                    if (isSky) nChunk.setSkyLight(nlx, ny, nlz, 0);
                    else nChunk.setBlockLight(nlx, ny, nlz, 0);
                    
                    removalQueue.add(packWorld(nx, ny, nz, neighborLight));
                } 
                // Se la luce del vicino è >= level, potrebbe essere una sorgente indipendente
                else {
                    // Controlla se è una sorgente
                    Block block = Blocks.get(world.peekBlock(nx, ny, nz));
                    if (!isSky && block.getLightLevel() > 0) {
                        // È una sorgente, ri-propagala
                        addQueue.add(packWorld(nx, ny, nz, block.getLightLevel()));
                    } else if (neighborLight >= level) {
                        // Luce forte da altrove, ri-propagala
                        addQueue.add(packWorld(nx, ny, nz, neighborLight));
                    }
                }
            }
        }
        
        // Ri-propaga le sorgenti indipendenti
        propagateAdd(world, addQueue);
    }

    // Sostituisci il tuo metodo runBfsSnapshot con questo:
    private static void runBfsSnapshot(ChunkSnapshot snapshot, int[] queue, int head, int tail,
            boolean isSky, int chunkSize, int chunkHeight, Set<Long> neighborsSet) {

        int cx = snapshot.centerX;
        int cz = snapshot.centerZ;

        // Rimuoviamo worldOffsetX/Z perché lavoriamo in locale dentro il while

        while (head != tail) {
            int val = queue[head++];
            int level = (val >> S_SHIFT_LEVEL) & S_MASK_LEVEL;

            // Se la luce è 1 o 0, non può propagarsi oltre (diventerebbe 0 o -1)
            if (level <= 1)
                continue;

            int x = (val >> S_SHIFT_X) & S_MASK_XZ;
            int z = (val >> S_SHIFT_Z) & S_MASK_XZ;
            int y = (val >> S_SHIFT_Y) & S_MASK_Y;

            for (int[] d : DIRS) {
                int nx = x + d[0];
                int ny = y + d[1];
                int nz = z + d[2];

                if (ny < 0 || ny >= chunkHeight)
                    continue;

                // --- CASO 1: Usciamo dal Chunk (Vicini) ---
                if (nx < 0 || nx >= chunkSize || nz < 0 || nz >= chunkSize) {
                    int neighborCX = cx + (nx < 0 ? -1 : (nx >= chunkSize ? 1 : 0));
                    int neighborCZ = cz + (nz < 0 ? -1 : (nz >= chunkSize ? 1 : 0));
                    neighborsSet.add(((long) neighborCX << 32) | (neighborCZ & 0xFFFFFFFFL));
                    continue;
                }

                // --- CASO 2: Siamo DENTRO il Chunk (Propagazione Interna) ---
                // È QUI CHE FALLIVA PRIMA!

                // 1. Controllo Opacità (Usa coordinate locali)
                int blockId = snapshot.getBlock(nx, ny, nz);
                if (Blocks.get(blockId).isOpaque())
                    continue;

                // 2. Lettura Luce (USA I NUOVI METODI GET LOCALI)
                // NON usare peekSkyLight(global...) qui, usa i metodi diretti che hai aggiunto!
                int currentLight = isSky ? snapshot.getSkyLight(nx, ny, nz)
                        : snapshot.getBlockLight(nx, ny, nz);

                int newLevel = level - 1;

                // 3. Propagazione
                if (newLevel > currentLight) {
                    // Scrittura Luce (Usa i metodi set locali)
                    if (isSky)
                        snapshot.setSkyLight(nx, ny, nz, newLevel);
                    else
                        snapshot.setBlockLight(nx, ny, nz, newLevel);

                    // Aggiungi alla coda per continuare la propagazione verso l'interno
                    queue[tail++] = packSnapshot(nx, ny, nz, newLevel);
                }
            }
        }
    }
}