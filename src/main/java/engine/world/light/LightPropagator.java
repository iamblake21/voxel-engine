package engine.world.light;

import engine.world.World;
import engine.world.Chunk;
import engine.world.gen.ChunkSnapshot;
import engine.world.block.Block;
import engine.world.block.Blocks;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Sistema di illuminazione per motore voxel.
 * 
 * SKYLIGHT:
 * - Valore 15 dal cielo, propaga verso il basso SENZA decremento in aria
 * - Propagazione orizzontale/verso l'alto: decrementa di 1
 * 
 * BLOCKLIGHT:
 * - Sorgenti (torce, lava): emettono luce
 * - Decrementa di 1 in TUTTE le direzioni
 */
public class LightPropagator {

    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNK_MASK = 0xF;

    // Direzioni: +X, -X, +Y, -Y, +Z, -Z
    private static final int[][] DIRS = {
            { 1, 0, 0 }, { -1, 0, 0 },
            { 0, 1, 0 }, { 0, -1, 0 },
            { 0, 0, 1 }, { 0, 0, -1 }
    };

    // =================================================================================
    // NODO CODA (semplice, no packing problematico)
    // =================================================================================

    private static class LightNode {
        final int x, y, z;
        final int level;

        LightNode(int x, int y, int z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }

    // =================================================================================
    // CALCOLO LUCE PER SNAPSHOT (Generazione Asincrona)
    // =================================================================================

    /**
     * Calcola TUTTA la luce per un chunk snapshot.
     * Questo è il metodo principale chiamato dal worker thread.
     */
    public static void computeFullLightForSnapshot(
            ChunkSnapshot snapshot,
            int chunkSize,
            int chunkHeight,
            List<Long> neighborsToPropagate) {

        Set<Long> neighborsSet = new HashSet<>();

        // === FASE 1: SKYLIGHT ===
        computeSkyLightForSnapshot(snapshot, chunkSize, chunkHeight, neighborsSet);

        // === FASE 2: BLOCKLIGHT ===
        computeBlockLightForSnapshot(snapshot, chunkSize, chunkHeight, neighborsSet);

        neighborsToPropagate.addAll(neighborsSet);
    }

    /**
     * Calcola la skylight per lo snapshot.
     */
    private static void computeSkyLightForSnapshot(
            ChunkSnapshot snapshot,
            int chunkSize,
            int chunkHeight,
            Set<Long> neighborsSet) {

        LightIntQueue queue = new LightIntQueue();

        int wx0 = snapshot.getWorldX();
        int wz0 = snapshot.getWorldZ();

        // STEP 1: Raycast verticale - inizializza sorgenti a 15
        for (int lx = 0; lx < chunkSize; lx++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                boolean blocked = false;

                for (int y = chunkHeight - 1; y >= 0; y--) {
                    int blockId = snapshot.getBlock(lx, y, lz);

                    if (Blocks.get(blockId).isOpaque()) {
                        blocked = true;
                        snapshot.setSkyLight(lx, y, lz, 0);
                    } else if (!blocked) {
                        // Cielo aperto: luce 15
                        snapshot.setSkyLight(lx, y, lz, 15);
                        // Aggiungi alla coda per propagazione orizzontale
                        queue.add(lx, y, lz, 15);
                    } else {
                        // Sotto un blocco opaco: inizia a 0, riceverà luce dai lati
                        snapshot.setSkyLight(lx, y, lz, 0);
                    }
                }
            }
        }

        // STEP 2: Importa luce dai bordi dei chunk vicini
        importSkyLightFromNeighbors(snapshot, queue, chunkSize, chunkHeight);

        // STEP 3: Propaga con BFS
        propagateSkyLightBFS(snapshot, queue, chunkSize, chunkHeight, neighborsSet);
    }

    /**
     * Importa skylight dai chunk vicini attraverso i bordi.
     */
    private static void importSkyLightFromNeighbors(
            ChunkSnapshot snapshot,
            LightIntQueue queue,
            int chunkSize,
            int chunkHeight) {

        int wx0 = snapshot.getWorldX();
        int wz0 = snapshot.getWorldZ();

        // Bordo Ovest (lx=0, controlla vicino a lx=-1)
        for (int y = 0; y < chunkHeight; y++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                int neighborLight = snapshot.peekSkyLight(wx0 - 1, y, wz0 + lz);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, 0, y, lz, neighborLight - 1, true);
                }
            }
        }

        // Bordo Est (lx=15, controlla vicino a lx=16)
        for (int y = 0; y < chunkHeight; y++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                int neighborLight = snapshot.peekSkyLight(wx0 + chunkSize, y, wz0 + lz);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, chunkSize - 1, y, lz, neighborLight - 1, true);
                }
            }
        }

        // Bordo Nord (lz=0, controlla vicino a lz=-1)
        for (int y = 0; y < chunkHeight; y++) {
            for (int lx = 0; lx < chunkSize; lx++) {
                int neighborLight = snapshot.peekSkyLight(wx0 + lx, y, wz0 - 1);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, lx, y, 0, neighborLight - 1, true);
                }
            }
        }

        // Bordo Sud (lz=15, controlla vicino a lz=16)
        for (int y = 0; y < chunkHeight; y++) {
            for (int lx = 0; lx < chunkSize; lx++) {
                int neighborLight = snapshot.peekSkyLight(wx0 + lx, y, wz0 + chunkSize);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, lx, y, chunkSize - 1, neighborLight - 1, true);
                }
            }
        }
    }

    /**
     * Tenta di importare luce in una posizione del bordo.
     */
    private static void tryImportLight(
            ChunkSnapshot snapshot,
            LightIntQueue queue,
            int lx, int ly, int lz,
            int incomingLight,
            boolean isSky) {

        // Il blocco deve essere trasparente
        int blockId = snapshot.getBlock(lx, ly, lz);
        if (Blocks.get(blockId).isOpaque())
            return;

        int currentLight = isSky ? snapshot.getSkyLight(lx, ly, lz)
                : snapshot.getBlockLight(lx, ly, lz);

        if (incomingLight > currentLight) {
            if (isSky) {
                snapshot.setSkyLight(lx, ly, lz, incomingLight);
            } else {
                snapshot.setBlockLight(lx, ly, lz, incomingLight);
            }
            queue.add(lx, ly, lz, incomingLight);
        }
    }

    /**
     * Propaga skylight con BFS.
     * REGOLA SPECIALE: verso il basso NON decrementa!
     */
    private static void propagateSkyLightBFS(
            ChunkSnapshot snapshot,
            LightIntQueue queue,
            int chunkSize,
            int chunkHeight,
            Set<Long> neighborsSet) {

        while (!queue.isEmpty()) {
            int val = queue.poll();
            int x = LightIntQueue.unpackX(val);
            int y = LightIntQueue.unpackY(val);
            int z = LightIntQueue.unpackZ(val);
            int level = LightIntQueue.unpackLevel(val);

            if (level <= 0)
                continue;

            for (int d = 0; d < 6; d++) {
                int nx = x + DIRS[d][0];
                int ny = y + DIRS[d][1];
                int nz = z + DIRS[d][2];

                // Fuori dai limiti verticali
                if (ny < 0 || ny >= chunkHeight)
                    continue;

                // Fuori dal chunk -> segnala ai vicini
                if (nx < 0 || nx >= chunkSize || nz < 0 || nz >= chunkSize) {
                    if (level > 1) {
                        int ncx = snapshot.centerX + (nx < 0 ? -1 : (nx >= chunkSize ? 1 : 0));
                        int ncz = snapshot.centerZ + (nz < 0 ? -1 : (nz >= chunkSize ? 1 : 0));
                        neighborsSet.add(packChunkKey(ncx, ncz));
                    }
                    continue;
                }

                // Blocco opaco blocca la luce
                int blockId = snapshot.getBlock(nx, ny, nz);
                if (Blocks.get(blockId).isOpaque())
                    continue;

                // === REGOLA SPECIALE SKYLIGHT ===
                // Verso il basso (d=3, DIRS[3]={0,-1,0}): NON decrementa
                int newLevel;
                if (d == 3) { // DOWN
                    newLevel = level; // Mantiene il livello
                } else {
                    newLevel = level - 1; // Decrementa
                }

                if (newLevel <= 0)
                    continue;

                int currentLight = snapshot.getSkyLight(nx, ny, nz);
                if (newLevel > currentLight) {
                    snapshot.setSkyLight(nx, ny, nz, newLevel);
                    queue.add(nx, ny, nz, newLevel);
                }
            }
        }
    }

    /**
     * Calcola la blocklight per lo snapshot.
     */
    private static void computeBlockLightForSnapshot(
            ChunkSnapshot snapshot,
            int chunkSize,
            int chunkHeight,
            Set<Long> neighborsSet) {

        LightIntQueue queue = new LightIntQueue();

        // STEP 1: Trova tutte le sorgenti di luce
        for (int lx = 0; lx < chunkSize; lx++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                for (int y = 0; y < chunkHeight; y++) {
                    int blockId = snapshot.getBlock(lx, y, lz);
                    int emission = Blocks.get(blockId).getLightLevel();

                    if (emission > 0) {
                        snapshot.setBlockLight(lx, y, lz, emission);
                        queue.add(lx, y, lz, emission);
                    }
                }
            }
        }

        // STEP 2: Importa luce dai bordi
        importBlockLightFromNeighbors(snapshot, queue, chunkSize, chunkHeight);

        // STEP 3: Propaga
        propagateBlockLightBFS(snapshot, queue, chunkSize, chunkHeight, neighborsSet);
    }

    /**
     * Importa blocklight dai chunk vicini.
     */
    private static void importBlockLightFromNeighbors(
            ChunkSnapshot snapshot,
            LightIntQueue queue,
            int chunkSize,
            int chunkHeight) {

        int wx0 = snapshot.getWorldX();
        int wz0 = snapshot.getWorldZ();

        // Stesso pattern di importSkyLightFromNeighbors
        // Bordo Ovest
        for (int y = 0; y < chunkHeight; y++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                int neighborLight = snapshot.peekBlockLight(wx0 - 1, y, wz0 + lz);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, 0, y, lz, neighborLight - 1, false);
                }
            }
        }

        // Bordo Est
        for (int y = 0; y < chunkHeight; y++) {
            for (int lz = 0; lz < chunkSize; lz++) {
                int neighborLight = snapshot.peekBlockLight(wx0 + chunkSize, y, wz0 + lz);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, chunkSize - 1, y, lz, neighborLight - 1, false);
                }
            }
        }

        // Bordo Nord
        for (int y = 0; y < chunkHeight; y++) {
            for (int lx = 0; lx < chunkSize; lx++) {
                int neighborLight = snapshot.peekBlockLight(wx0 + lx, y, wz0 - 1);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, lx, y, 0, neighborLight - 1, false);
                }
            }
        }

        // Bordo Sud
        for (int y = 0; y < chunkHeight; y++) {
            for (int lx = 0; lx < chunkSize; lx++) {
                int neighborLight = snapshot.peekBlockLight(wx0 + lx, y, wz0 + chunkSize);
                if (neighborLight > 1) {
                    tryImportLight(snapshot, queue, lx, y, chunkSize - 1, neighborLight - 1, false);
                }
            }
        }
    }

    /**
     * Propaga blocklight con BFS.
     * Decrementa di 1 in TUTTE le direzioni.
     */
    private static void propagateBlockLightBFS(
            ChunkSnapshot snapshot,
            LightIntQueue queue,
            int chunkSize,
            int chunkHeight,
            Set<Long> neighborsSet) {

        while (!queue.isEmpty()) {
            int val = queue.poll();
            int x = LightIntQueue.unpackX(val);
            int y = LightIntQueue.unpackY(val);
            int z = LightIntQueue.unpackZ(val);
            int level = LightIntQueue.unpackLevel(val);

            if (level <= 1)
                continue; // Non può propagare oltre

            int newLevel = level - 1;

            for (int d = 0; d < 6; d++) {
                int nx = x + DIRS[d][0];
                int ny = y + DIRS[d][1];
                int nz = z + DIRS[d][2];

                if (ny < 0 || ny >= chunkHeight)
                    continue;

                if (nx < 0 || nx >= chunkSize || nz < 0 || nz >= chunkSize) {
                    if (newLevel > 1) {
                        int ncx = snapshot.centerX + (nx < 0 ? -1 : (nx >= chunkSize ? 1 : 0));
                        int ncz = snapshot.centerZ + (nz < 0 ? -1 : (nz >= chunkSize ? 1 : 0));
                        neighborsSet.add(packChunkKey(ncx, ncz));
                    }
                    continue;
                }

                int blockId = snapshot.getBlock(nx, ny, nz);
                if (Blocks.get(blockId).isOpaque())
                    continue;

                int currentLight = snapshot.getBlockLight(nx, ny, nz);
                if (newLevel > currentLight) {
                    snapshot.setBlockLight(nx, ny, nz, newLevel);
                    queue.add(nx, ny, nz, newLevel);
                }
            }
        }
    }

    // =================================================================================
    // METODI WORLD (Per modifiche in tempo reale - piazzamento/rimozione blocchi)
    // =================================================================================

    /**
     * Aggiunge una sorgente di blocklight (es. piazzamento torcia).
     */
    public static void addBlockLight(World world, int wx, int wy, int wz, int lightLevel) {
        if (lightLevel <= 0)
            return;

        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null)
            return;

        int lx = wx & CHUNK_MASK;
        int lz = wz & CHUNK_MASK;

        chunk.setBlockLight(lx, wy, lz, lightLevel);

        ArrayDeque<LightNode> queue = new ArrayDeque<>();
        queue.add(new LightNode(wx, wy, wz, lightLevel));

        propagateBlockLightWorld(world, queue);
    }

    /**
     * Rimuove una sorgente di blocklight.
     */
    public static void removeBlockLight(World world, int wx, int wy, int wz) {
        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null)
            return;

        int lx = wx & CHUNK_MASK;
        int lz = wz & CHUNK_MASK;
        int oldLight = chunk.getBlockLight(lx, wy, lz);

        if (oldLight == 0)
            return;

        chunk.setBlockLight(lx, wy, lz, 0);
        removeLightBFS(world, wx, wy, wz, oldLight, false);
    }

    /**
     * Rimuove luce da una posizione e ripropaga.
     */
    public static void removeLightAt(World world, int wx, int wy, int wz, int lightLevel, boolean isSky) {
        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null)
            return;

        int lx = wx & CHUNK_MASK;
        int lz = wz & CHUNK_MASK;

        if (isSky) {
            chunk.setSkyLight(lx, wy, lz, 0);
        } else {
            chunk.setBlockLight(lx, wy, lz, 0);
        }

        removeLightBFS(world, wx, wy, wz, lightLevel, isSky);
    }

    /**
     * Ricalcola la skylight per una colonna dopo modifica.
     * 
     * Quando tappi un buco nel tetto:
     * 1. Tutta la colonna sotto diventa buia (skylight = 0)
     * 2. La luce che si era propagata ORIZZONTALMENTE da quella colonna viene
     * rimossa
     * 3. La luce dai LATI (altre aperture) viene ri-propagata
     */
    public static void recalculateSkyColumn(World world, int wx, int wz) {
        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null)
            return;

        int worldHeight = world.getConfig().worldHeight;
        int lx = wx & CHUNK_MASK;
        int lz = wz & CHUNK_MASK;

        // PASSO 1: Trova dove inizia il blocco opaco più alto
        int firstOpaqueY = -1;
        for (int y = worldHeight - 1; y >= 0; y--) {
            int blockId = chunk.getBlock(lx, y, lz);
            if (Blocks.get(blockId).isOpaque()) {
                firstOpaqueY = y;
                break;
            }
        }

        // PASSO 2: Raccogli TUTTI i blocchi che devono perdere luce
        // e imposta la colonna ai valori corretti
        ArrayDeque<LightNode> removalQueue = new ArrayDeque<>();
        ArrayDeque<LightNode> propagateQueue = new ArrayDeque<>();

        for (int y = worldHeight - 1; y >= 0; y--) {
            int blockId = chunk.getBlock(lx, y, lz);
            int oldLight = chunk.getSkyLight(lx, y, lz);

            if (Blocks.get(blockId).isOpaque()) {
                // Blocco opaco: luce 0
                chunk.setSkyLight(lx, y, lz, 0);
            } else if (y > firstOpaqueY || firstOpaqueY == -1) {
                // Sopra tutti gli opachi o nessun opaco: cielo aperto = 15
                chunk.setSkyLight(lx, y, lz, 15);
                propagateQueue.add(new LightNode(wx, y, wz, 15));
            } else {
                // Sotto un blocco opaco: la luce diretta è bloccata
                if (oldLight > 0) {
                    // Questo blocco AVEVA luce, ora deve essere 0
                    // Aggiungi alla coda di rimozione per pulire la luce orizzontale
                    chunk.setSkyLight(lx, y, lz, 0);
                    removalQueue.add(new LightNode(wx, y, wz, oldLight));
                }
            }
        }

        // PASSO 3: Rimuovi TUTTA la luce propagata orizzontalmente con UNA SOLA BFS
        if (!removalQueue.isEmpty()) {
            removeMultipleSkyLightBFS(world, removalQueue);
        }

        // PASSO 4: Propaga la luce dalle colonne ancora aperte
        propagateSkyLightWorld(world, propagateQueue);

        // PASSO 5: Per i blocchi sotto l'opaco, cerca luce dai lati
        if (firstOpaqueY > 0) {
            for (int y = firstOpaqueY - 1; y >= 0; y--) {
                int blockId = chunk.getBlock(lx, y, lz);
                if (Blocks.get(blockId).isOpaque())
                    continue;

                // Se questo blocco è ancora a 0, prova a prendere luce dai vicini
                if (chunk.getSkyLight(lx, y, lz) == 0) {
                    fillSkyLightFromNeighbors(world, wx, y, wz);
                }
            }
        }
    }

    /**
     * Rimuove skylight da MULTIPLI punti di partenza con una singola BFS.
     * Questo evita che la ri-propagazione di una chiamata annulli la rimozione di
     * un'altra.
     */
    private static void removeMultipleSkyLightBFS(World world, ArrayDeque<LightNode> startNodes) {
        int worldHeight = world.getConfig().worldHeight;

        ArrayDeque<LightNode> removalQueue = new ArrayDeque<>(startNodes);
        ArrayDeque<LightNode> refillQueue = new ArrayDeque<>();

        while (!removalQueue.isEmpty()) {
            LightNode node = removalQueue.poll();
            int x = node.x;
            int y = node.y;
            int z = node.z;
            int level = node.level;

            for (int d = 0; d < 6; d++) {
                int nx = x + DIRS[d][0];
                int ny = y + DIRS[d][1];
                int nz = z + DIRS[d][2];

                if (ny < 0 || ny >= worldHeight)
                    continue;

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null)
                    continue;

                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;

                int neighborLight = nChunk.getSkyLight(nlx, ny, nlz);
                if (neighborLight == 0)
                    continue;

                // Determina se questa luce dipendeva da noi
                boolean shouldRemove = false;

                if (d == 3) {
                    // Direzione GIÙ: la luce non decrementa scendendo,
                    // quindi neighborLight <= level significa che veniva da sopra
                    shouldRemove = (neighborLight <= level);
                } else if (d == 2) {
                    // Direzione SU: la luce sale con decremento,
                    // quindi neighborLight < level significa che veniva da sotto
                    shouldRemove = (neighborLight < level);
                } else {
                    // Direzioni orizzontali: decrementa di 1,
                    // quindi neighborLight < level significa che veniva da noi
                    shouldRemove = (neighborLight < level);
                }

                if (shouldRemove) {
                    nChunk.setSkyLight(nlx, ny, nlz, 0);
                    removalQueue.add(new LightNode(nx, ny, nz, neighborLight));
                } else if (neighborLight > 0) {
                    // Questa luce NON dipendeva da noi, è una sorgente indipendente
                    // Dovrà ri-propagare dopo
                    refillQueue.add(new LightNode(nx, ny, nz, neighborLight));
                }
            }
        }

        // Ora ri-propaga dalle sorgenti indipendenti
        propagateSkyLightWorld(world, refillQueue);
    }

    /**
     * Rimuove la skylight da un punto e TUTTA la luce che si era propagata
     * da quel punto in QUALSIASI direzione (orizzontale, verticale, ovunque).
     * 
     * Usato quando tappi un buco laterale in una stanza.
     */
    public static void removeSkyLightFrom(World world, int wx, int wy, int wz, int oldLight) {
        if (oldLight <= 0)
            return;

        int worldHeight = world.getConfig().worldHeight;

        ArrayDeque<LightNode> removalQueue = new ArrayDeque<>();
        ArrayDeque<LightNode> refillQueue = new ArrayDeque<>();

        // Il blocco di partenza è già stato messo a 0 dal chiamante
        removalQueue.add(new LightNode(wx, wy, wz, oldLight));

        while (!removalQueue.isEmpty()) {
            LightNode node = removalQueue.poll();
            int x = node.x;
            int y = node.y;
            int z = node.z;
            int level = node.level;

            for (int d = 0; d < 6; d++) {
                int nx = x + DIRS[d][0];
                int ny = y + DIRS[d][1];
                int nz = z + DIRS[d][2];

                if (ny < 0 || ny >= worldHeight)
                    continue;

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null)
                    continue;

                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;

                int neighborLight = nChunk.getSkyLight(nlx, ny, nlz);
                if (neighborLight == 0)
                    continue;

                // Determina se la luce del vicino dipendeva da noi
                boolean shouldRemove = false;

                if (d == 3) {
                    // Direzione GIÙ (noi → vicino sotto)
                    // La skylight non decrementa scendendo, quindi se il vicino
                    // ha luce <= alla nostra, probabilmente veniva da noi
                    shouldRemove = (neighborLight <= level);
                } else if (d == 2) {
                    // Direzione SU (noi → vicino sopra)
                    // La luce sale con decremento, quindi neighborLight < level
                    shouldRemove = (neighborLight < level);
                } else {
                    // Direzioni ORIZZONTALI
                    // La luce si propaga con decremento, neighborLight < level
                    shouldRemove = (neighborLight < level);
                }

                if (shouldRemove) {
                    nChunk.setSkyLight(nlx, ny, nlz, 0);
                    removalQueue.add(new LightNode(nx, ny, nz, neighborLight));
                } else if (neighborLight > 0) {
                    // Questa luce è indipendente (altra sorgente), dovrà ri-propagare
                    refillQueue.add(new LightNode(nx, ny, nz, neighborLight));
                }
            }
        }

        // Ri-propaga dalle sorgenti indipendenti
        propagateSkyLightWorld(world, refillQueue);
    }

    /**
     * Rimuove skylight usando BFS, gestendo la regola speciale verticale.
     */
    private static void removeSkyLightBFS(World world, int startX, int startY, int startZ, int startLevel) {
        int worldHeight = world.getConfig().worldHeight;

        ArrayDeque<LightNode> removalQueue = new ArrayDeque<>();
        ArrayDeque<LightNode> refillQueue = new ArrayDeque<>();

        Chunk startChunk = world.getChunkAtWorld(startX, startZ);
        if (startChunk == null)
            return;

        startChunk.setSkyLight(startX & CHUNK_MASK, startY, startZ & CHUNK_MASK, 0);
        removalQueue.add(new LightNode(startX, startY, startZ, startLevel));

        while (!removalQueue.isEmpty()) {
            LightNode node = removalQueue.poll();
            int x = node.x;
            int y = node.y;
            int z = node.z;
            int level = node.level;

            for (int d = 0; d < 6; d++) {
                int nx = x + DIRS[d][0];
                int ny = y + DIRS[d][1];
                int nz = z + DIRS[d][2];

                if (ny < 0 || ny >= worldHeight)
                    continue;

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null)
                    continue;

                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;

                int neighborLight = nChunk.getSkyLight(nlx, ny, nlz);
                if (neighborLight == 0)
                    continue;

                // Per skylight, la regola è diversa:
                // - Verso il BASSO (d=3): la luce non decrementa, quindi
                // neighborLight == level significa che veniva da noi
                // - Altre direzioni: neighborLight < level significa che veniva da noi

                boolean shouldRemove = false;

                if (d == 3) {
                    // Direzione GIÙ: luce uguale o minore viene da sopra (da noi)
                    shouldRemove = (neighborLight <= level);
                } else if (d == 2) {
                    // Direzione SU: la luce sotto non può aver alimentato sopra
                    // a meno che non sia una sorgente indipendente
                    shouldRemove = (neighborLight < level);
                } else {
                    // Direzioni orizzontali: regola normale
                    shouldRemove = (neighborLight < level);
                }

                if (shouldRemove) {
                    nChunk.setSkyLight(nlx, ny, nlz, 0);
                    removalQueue.add(new LightNode(nx, ny, nz, neighborLight));
                } else if (neighborLight > 0) {
                    // Luce indipendente, dovrà ri-propagare
                    refillQueue.add(new LightNode(nx, ny, nz, neighborLight));
                }
            }
        }

        // Ri-propaga dalle sorgenti indipendenti
        propagateSkyLightWorld(world, refillQueue);
    }

    /**
     * Cerca skylight dai 6 vicini e la importa se disponibile.
     */
    private static void fillSkyLightFromNeighbors(World world, int wx, int wy, int wz) {
        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null)
            return;

        int lx = wx & CHUNK_MASK;
        int lz = wz & CHUNK_MASK;
        int worldHeight = world.getConfig().worldHeight;

        int maxLight = 0;

        for (int[] dir : DIRS) {
            int nx = wx + dir[0];
            int ny = wy + dir[1];
            int nz = wz + dir[2];

            if (ny < 0 || ny >= worldHeight)
                continue;

            int neighborLight = world.peekSkyLight(nx, ny, nz);

            // Se viene dall'alto, non decrementa
            if (dir[1] == 1) {
                maxLight = Math.max(maxLight, neighborLight);
            } else {
                maxLight = Math.max(maxLight, neighborLight - 1);
            }
        }

        if (maxLight > 0) {
            chunk.setSkyLight(lx, wy, lz, maxLight);
            // Propaga ulteriormente
            ArrayDeque<LightNode> queue = new ArrayDeque<>();
            queue.add(new LightNode(wx, wy, wz, maxLight));
            propagateSkyLightWorld(world, queue);
        }
    }

    /**
     * Algoritmo di rimozione luce con ri-propagazione.
     */
    private static void removeLightBFS(World world, int wx, int wy, int wz, int oldLight, boolean isSky) {
        int worldHeight = world.getConfig().worldHeight;

        ArrayDeque<LightNode> removalQueue = new ArrayDeque<>();
        ArrayDeque<LightNode> refillQueue = new ArrayDeque<>();

        removalQueue.add(new LightNode(wx, wy, wz, oldLight));

        while (!removalQueue.isEmpty()) {
            LightNode node = removalQueue.poll();
            int x = node.x;
            int y = node.y;
            int z = node.z;
            int level = node.level;

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (ny < 0 || ny >= worldHeight)
                    continue;

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null)
                    continue;

                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;

                int neighborLight = isSky ? nChunk.getSkyLight(nlx, ny, nlz)
                        : nChunk.getBlockLight(nlx, ny, nlz);

                if (neighborLight == 0)
                    continue;

                if (neighborLight < level) {
                    // Questa luce dipendeva da noi, rimuovila
                    if (isSky) {
                        nChunk.setSkyLight(nlx, ny, nlz, 0);
                    } else {
                        nChunk.setBlockLight(nlx, ny, nlz, 0);
                    }
                    removalQueue.add(new LightNode(nx, ny, nz, neighborLight));
                } else {
                    // Luce indipendente, potrebbe dover ri-propagare
                    if (!isSky) {
                        int blockId = world.peekBlock(nx, ny, nz);
                        int emission = Blocks.get(blockId).getLightLevel();
                        if (emission > 0) {
                            refillQueue.add(new LightNode(nx, ny, nz, emission));
                            continue;
                        }
                    }
                    refillQueue.add(new LightNode(nx, ny, nz, neighborLight));
                }
            }
        }

        // Ri-propaga dalle sorgenti indipendenti
        if (isSky) {
            propagateSkyLightWorld(world, refillQueue);
        } else {
            propagateBlockLightWorld(world, refillQueue);
        }
    }

    /**
     * Riempie luce dai vicini (quando si scava un blocco).
     */
    public static void fillLightFromNeighbors(World world, int wx, int wy, int wz) {
        Chunk chunk = world.getChunkAtWorld(wx, wz);
        if (chunk == null)
            return;

        int worldHeight = world.getConfig().worldHeight;
        int lx = wx & CHUNK_MASK;
        int lz = wz & CHUNK_MASK;

        int maxSky = 0;
        int maxBlock = 0;

        for (int[] dir : DIRS) {
            int nx = wx + dir[0];
            int ny = wy + dir[1];
            int nz = wz + dir[2];

            if (ny < 0 || ny >= worldHeight)
                continue;

            int neighborSky = world.peekSkyLight(nx, ny, nz);
            int neighborBlock = world.peekBlockLight(nx, ny, nz);

            // Skylight: se viene dall'alto, non decrementa
            if (dir[1] == 1) {
                maxSky = Math.max(maxSky, neighborSky);
            } else {
                maxSky = Math.max(maxSky, neighborSky - 1);
            }

            maxBlock = Math.max(maxBlock, neighborBlock - 1);
        }

        if (maxSky > 0) {
            chunk.setSkyLight(lx, wy, lz, maxSky);
            ArrayDeque<LightNode> queue = new ArrayDeque<>();
            queue.add(new LightNode(wx, wy, wz, maxSky));
            propagateSkyLightWorld(world, queue);
        }

        if (maxBlock > 0) {
            chunk.setBlockLight(lx, wy, lz, maxBlock);
            ArrayDeque<LightNode> queue = new ArrayDeque<>();
            queue.add(new LightNode(wx, wy, wz, maxBlock));
            propagateBlockLightWorld(world, queue);
        }
    }

    // =================================================================================
    // PROPAGAZIONE WORLD (coordinate globali)
    // =================================================================================

    private static void propagateBlockLightWorld(World world, ArrayDeque<LightNode> queue) {
        int worldHeight = world.getConfig().worldHeight;

        while (!queue.isEmpty()) {
            LightNode node = queue.poll();
            int x = node.x;
            int y = node.y;
            int z = node.z;
            int level = node.level;

            if (level <= 1)
                continue;

            int newLevel = level - 1;

            for (int[] dir : DIRS) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (ny < 0 || ny >= worldHeight)
                    continue;

                int blockId = world.peekBlock(nx, ny, nz);
                if (Blocks.get(blockId).isOpaque())
                    continue;

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null)
                    continue;

                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;

                int currentLight = nChunk.getBlockLight(nlx, ny, nlz);
                if (newLevel > currentLight) {
                    nChunk.setBlockLight(nlx, ny, nlz, newLevel);
                    queue.add(new LightNode(nx, ny, nz, newLevel));
                }
            }
        }
    }

    private static void propagateSkyLightWorld(World world, ArrayDeque<LightNode> queue) {
        int worldHeight = world.getConfig().worldHeight;

        while (!queue.isEmpty()) {
            LightNode node = queue.poll();
            int x = node.x;
            int y = node.y;
            int z = node.z;
            int level = node.level;

            if (level <= 0)
                continue;

            for (int d = 0; d < 6; d++) {
                int nx = x + DIRS[d][0];
                int ny = y + DIRS[d][1];
                int nz = z + DIRS[d][2];

                if (ny < 0 || ny >= worldHeight)
                    continue;

                int blockId = world.peekBlock(nx, ny, nz);
                if (Blocks.get(blockId).isOpaque())
                    continue;

                Chunk nChunk = world.getChunkIfLoaded(nx >> CHUNK_SHIFT, nz >> CHUNK_SHIFT);
                if (nChunk == null)
                    continue;

                // Regola speciale: verso il basso non decrementa
                int newLevel;
                if (d == 3) { // DOWN
                    newLevel = level;
                } else {
                    newLevel = level - 1;
                }

                if (newLevel <= 0)
                    continue;

                int nlx = nx & CHUNK_MASK;
                int nlz = nz & CHUNK_MASK;

                int currentLight = nChunk.getSkyLight(nlx, ny, nlz);
                if (newLevel > currentLight) {
                    nChunk.setSkyLight(nlx, ny, nlz, newLevel);
                    queue.add(new LightNode(nx, ny, nz, newLevel));
                }
            }
        }
    }

    // =================================================================================
    // METODO LEGACY (per compatibilità - da rimuovere dopo la transizione)
    // =================================================================================

    /**
     * @deprecated Usare computeFullLightForSnapshot nel task di luce.
     */
    @Deprecated
    public static void recomputeChunkSkyLightVertical(World world, Chunk chunk) {
        // NON FARE NULLA - la luce viene calcolata dal LightTask
        // Questo metodo esiste solo per non rompere il codice esistente durante la
        // transizione
    }

    // =================================================================================
    // UTILITY
    // =================================================================================

    private static long packChunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}