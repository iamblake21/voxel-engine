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
