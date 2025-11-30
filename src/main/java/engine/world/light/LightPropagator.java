package engine.world.light;

import engine.world.World;
import engine.world.Chunk;
import engine.world.block.Block;
import engine.world.block.Blocks;
import java.util.Arrays;


import java.util.ArrayDeque;
import java.util.Queue;

public class LightPropagator {

    private static final int MAX_LIGHT = 15;

    private static final int[][] DIRS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1},
    };

        /**
 * Aggiorna la blockLight quando viene piazzato un blocco emissivo.
 * Propaga la luce ai chunk vicini.
 */
public static void addBlockLight(World world, int wx, int wy, int wz, int lightLevel) {
    if (lightLevel <= 0) return;
    
    Chunk chunk = world.getChunkAtWorld(wx, wz);
    if (chunk == null) return;
    
    int lx = floorMod(wx, Chunk.SIZE);
    int lz = floorMod(wz, Chunk.SIZE);
    
    // Imposta la luce del blocco sorgente
    chunk.setBlockLight(lx, wy, lz, lightLevel);
    chunk.setDirty(true);
    
    // Propaga ai vicini
    Queue<Node> queue = new ArrayDeque<>();
    queue.add(new Node(wx, wy, wz, lightLevel));
    
    while (!queue.isEmpty()) {
        Node n = queue.poll();
        if (n.level <= 1) continue;
        
        for (int[] d : DIRS) {
            int nx = n.x + d[0];
            int ny = n.y + d[1];
            int nz = n.z + d[2];
            
            if (ny < 0 || ny >= world.getConfig().worldHeight) continue;
            
            int neighborBlockId = world.peekBlock(nx, ny, nz);
            Block nb = Blocks.get(neighborBlockId);
            if (nb.isOpaque() && !nb.isTransparent()) continue;
            
            Chunk nChunk = world.getChunkIfLoaded(
                Math.floorDiv(nx, Chunk.SIZE),
                Math.floorDiv(nz, Chunk.SIZE)
            );
            if (nChunk == null) continue;
            
            int nlx = floorMod(nx, Chunk.SIZE);
            int nlz = floorMod(nz, Chunk.SIZE);
            
            int old = nChunk.getBlockLight(nlx, ny, nlz);
            int newLevel = n.level - 1;
            
            if (newLevel > old) {
                nChunk.setBlockLight(nlx, ny, nlz, newLevel);
                queue.add(new Node(nx, ny, nz, newLevel));
                nChunk.setDirty(true);
            }
        }
    }
}

/**
 * Rimuove la blockLight quando viene rimosso un blocco emissivo.
 * Usa un algoritmo a due fasi: removal + re-propagation.
 */
public static void removeBlockLight(World world, int wx, int wy, int wz) {
    Chunk chunk = world.getChunkAtWorld(wx, wz);
    if (chunk == null) return;
    
    int lx = floorMod(wx, Chunk.SIZE);
    int lz = floorMod(wz, Chunk.SIZE);
    
    int oldLight = chunk.getBlockLight(lx, wy, lz);
    if (oldLight == 0) return;
    
    // FASE 1: RIMOZIONE - Trova tutti i blocchi che dipendevano da questa luce
    Queue<Node> removalQueue = new ArrayDeque<>();
    Queue<Node> addQueue = new ArrayDeque<>();
    
    chunk.setBlockLight(lx, wy, lz, 0);
    chunk.setDirty(true);
    removalQueue.add(new Node(wx, wy, wz, oldLight));
    
    while (!removalQueue.isEmpty()) {
        Node n = removalQueue.poll();
        
        for (int[] d : DIRS) {
            int nx = n.x + d[0];
            int ny = n.y + d[1];
            int nz = n.z + d[2];
            
            if (ny < 0 || ny >= world.getConfig().worldHeight) continue;
            
            Chunk nChunk = world.getChunkIfLoaded(
                Math.floorDiv(nx, Chunk.SIZE),
                Math.floorDiv(nz, Chunk.SIZE)
            );
            if (nChunk == null) continue;
            
            int nlx = floorMod(nx, Chunk.SIZE);
            int nlz = floorMod(nz, Chunk.SIZE);
            
            int neighborLight = nChunk.getBlockLight(nlx, ny, nlz);
            
            // Se il vicino aveva luce inferiore, era illuminato da noi
            if (neighborLight != 0 && neighborLight < n.level) {
                nChunk.setBlockLight(nlx, ny, nlz, 0);
                removalQueue.add(new Node(nx, ny, nz, neighborLight));
                nChunk.setDirty(true);
            }
            // Se aveva luce uguale o superiore, è una sorgente indipendente
            else if (neighborLight >= n.level) {
                addQueue.add(new Node(nx, ny, nz, neighborLight));
            }
        }
    }
    
    // FASE 2: RE-PROPAGAZIONE - Ri-propaga la luce dalle sorgenti rimaste
    while (!addQueue.isEmpty()) {
        Node n = addQueue.poll();
        if (n.level <= 1) continue;
        
        for (int[] d : DIRS) {
            int nx = n.x + d[0];
            int ny = n.y + d[1];
            int nz = n.z + d[2];
            
            if (ny < 0 || ny >= world.getConfig().worldHeight) continue;
            
            int neighborBlockId = world.peekBlock(nx, ny, nz);
            Block nb = Blocks.get(neighborBlockId);
            if (nb.isOpaque() && !nb.isTransparent()) continue;
            
            Chunk nChunk = world.getChunkIfLoaded(
                Math.floorDiv(nx, Chunk.SIZE),
                Math.floorDiv(nz, Chunk.SIZE)
            );
            if (nChunk == null) continue;
            
            int nlx = floorMod(nx, Chunk.SIZE);
            int nlz = floorMod(nz, Chunk.SIZE);
            
            int old = nChunk.getBlockLight(nlx, ny, nlz);
            int newLevel = n.level - 1;
            
            if (newLevel > old) {
                nChunk.setBlockLight(nlx, ny, nlz, newLevel);
                addQueue.add(new Node(nx, ny, nz, newLevel));
                nChunk.setDirty(true);
            }
        }
    }
}
    


        public static void recomputeChunkSkyLightVertical(World world, Chunk chunk) {
            if (chunk == null) return;

            byte[] sky = chunk.getSkyLightData();
            Arrays.fill(sky, (byte)0);

            int wx0 = chunk.getWorldX();
            int wz0 = chunk.getWorldZ();
            int worldHeight = world.getConfig().worldHeight;

            for (int lx = 0; lx < Chunk.SIZE; lx++) {
                for (int lz = 0; lz < Chunk.SIZE; lz++) {
                    int wx = wx0 + lx;
                    int wz = wz0 + lz;

                    // dall'alto verso il basso
                    boolean blocked = false;
                    for (int y = worldHeight - 1; y >= 0; y--) {
                        int blockId = world.getBlock(wx, y, wz);
                        Block b = Blocks.get(blockId);

                        if (!blocked && !b.isOpaque()) {
                            // questo blocco "vede" il cielo
                            chunk.setSkyLight(lx, y, lz, 15);
                        } else {
                            // appena troviamo un opaco, da qui in giù buio
                            blocked = true;
                            chunk.setSkyLight(lx, y, lz, 0);
                        }
                    }
                }
            }

            chunk.setDirty(true);
        }


        public static void propagateSkyLightHorizontal(World world, Chunk chunk) {
    Queue<Node> queue = new ArrayDeque<>();
    
    // 1) Trova tutti i blocchi con skyLight > 0 nel chunk
    for (int x = 0; x < Chunk.SIZE; x++) {
        for (int y = 0; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int light = chunk.getSkyLight(x, y, z);
                if (light > 1) { // Almeno livello 2 per propagare
                    int wx = chunk.getWorldX() + x;
                    int wz = chunk.getWorldZ() + z;
                    queue.add(new Node(wx, y, wz, light));
                }
            }
        }
    }
    
    // 2) Propaga usando BFS (stesso algoritmo del blockLight)
    while (!queue.isEmpty()) {
        Node n = queue.poll();
        if (n.level <= 1) continue;

        for (int[] d : DIRS) {
            int nx = n.x + d[0];
            int ny = n.y + d[1];
            int nz = n.z + d[2];

            if (ny < 0 || ny >= world.getConfig().worldHeight) continue;

            int neighborBlockId = world.peekBlock(nx, ny, nz);
            Block nb = Blocks.get(neighborBlockId);
            
            // SkyLight passa attraverso blocchi trasparenti
            if (nb.isOpaque() && !nb.isTransparent()) continue;

            Chunk nChunk = world.getChunkIfLoaded(
                Math.floorDiv(nx, Chunk.SIZE),
                Math.floorDiv(nz, Chunk.SIZE)
            );
            if (nChunk == null) continue;

            int lx = floorMod(nx, Chunk.SIZE);
            int lz = floorMod(nz, Chunk.SIZE);

            int old = nChunk.getSkyLight(lx, ny, lz);
            int newLevel = n.level - 1;
            
            if (newLevel > old) {
                nChunk.setSkyLight(lx, ny, lz, newLevel);
                queue.add(new Node(nx, ny, nz, newLevel));
                nChunk.setDirty(true);
            }
        }
    }
}

    public static void recomputeChunkBlockLight(World world, Chunk chunk) {
        byte[] light = chunk.getBlockLightData();
        // reset
        for (int i = 0; i < light.length; i++) light[i] = 0;

        Queue<Node> queue = new ArrayDeque<>();

        // 1) trova tutti i sorgenti di luce nel chunk
        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int y = 0; y < Chunk.HEIGHT; y++) {
                for (int z = 0; z < Chunk.SIZE; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    Block b = Blocks.get(blockId);
                    int lvl = b.getLightLevel();
                    if (lvl > 0) {
                        chunk.setBlockLight(x, y, z, lvl);
                        queue.add(new Node(chunk.getWorldX() + x, y, chunk.getWorldZ() + z, lvl));
                    }
                }
            }
        }

        // 2) flood fill in world-space (per attraversare i confini di chunk)
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (n.level <= 1) continue; // niente luce rimasta

            for (int[] d : DIRS) {
                int nx = n.x + d[0];
                int ny = n.y + d[1];
                int nz = n.z + d[2];

                if (ny < 0 || ny >= world.getConfig().worldHeight) continue;

                int neighborBlockId = world.peekBlock(nx, ny, nz);
                Block nb = Blocks.get(neighborBlockId);
                // blocchi completamente opachi fermano la luce
                if (nb.isOpaque() && !nb.isTransparent()) continue;

                Chunk nChunk = world.getChunkIfLoaded(
                    Math.floorDiv(nx, Chunk.SIZE),
                    Math.floorDiv(nz, Chunk.SIZE)
                );
                if (nChunk == null) continue;

                int lx = floorMod(nx, Chunk.SIZE);
                int lz = floorMod(nz, Chunk.SIZE);

                int old = nChunk.getBlockLight(lx, ny, lz);
                int newLevel = n.level - 1;
                if (newLevel > old) {
                    nChunk.setBlockLight(lx, ny, lz, newLevel);
                    queue.add(new Node(nx, ny, nz, newLevel));
                    nChunk.setDirty(true); // la mesh va aggiornata
                }
            }
        }
    }

    private static int floorMod(int x, int m) {
        int r = x % m;
        return r < 0 ? r + m : r;
    }

    private static class Node {
        final int x, y, z, level;
        Node(int x, int y, int z, int level) {
            this.x = x; this.y = y; this.z = z; this.level = level;
        }
    }
}
