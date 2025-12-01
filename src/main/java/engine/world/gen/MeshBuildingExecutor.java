package engine.world.gen;

import engine.world.Chunk;
import java.util.concurrent.*;
import java.util.*;

public class MeshBuildingExecutor {
    private final ExecutorService executor;
    private final MeshBuilder meshBuilder;
    private final ConcurrentLinkedQueue<MeshBuildTask> completedTasks;
    
    public MeshBuildingExecutor(MeshBuilder meshBuilder, int numWorkers) {
        this.meshBuilder = meshBuilder;
        this.executor = Executors.newFixedThreadPool(numWorkers, r -> {
            Thread t = new Thread(r, "MeshBuilder-Worker");
            t.setDaemon(true);
            return t;
        });
        this.completedTasks = new ConcurrentLinkedQueue<>();
    }
    
    public void submit(Chunk chunk, MeshBuilder.WorldAccess world) {
        executor.submit(() -> {
            try {
                MeshBuilder.MeshData meshData = meshBuilder.buildMesh(
                    new ChunkDataSnapshot(chunk), 
                    world
                );
                completedTasks.offer(new MeshBuildTask(chunk, meshData));
            } catch (Exception e) {
                System.err.println("Mesh build failed: " + e.getMessage());
            }
        });
    }
    
    public MeshBuildTask pollCompleted() {
        return completedTasks.poll();
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    public static class MeshBuildTask {
        public final Chunk chunk;
        public final MeshBuilder.MeshData meshData;
        
        MeshBuildTask(Chunk chunk, MeshBuilder.MeshData meshData) {
            this.chunk = chunk;
            this.meshData = meshData;
        }
    }
    
    // Snapshot immutabile del chunk per evitare race conditions
    private static class ChunkDataSnapshot implements MeshBuilder.ChunkData {
        private final int[] blockData;
        private final byte[] skyLight;
        private final byte[] blockLight;
        private final int worldX, worldZ;
        
        ChunkDataSnapshot(Chunk chunk) {
            this.blockData = chunk.getBlockData().clone();
            this.skyLight = chunk.getSkyLightData().clone();
            this.blockLight = chunk.getBlockLightData().clone();
            this.worldX = chunk.getWorldX();
            this.worldZ = chunk.getWorldZ();
        }
        
        @Override
        public int getBlock(int x, int y, int z) {
            return blockData[(y * 16 + z) * 16 + x];
        }
        
        @Override
        public int getWorldX() { return worldX; }
        @Override
        public int getWorldZ() { return worldZ; }
        
        @Override
        public int getBlockLight(int x, int y, int z) {
            return blockLight[(y * 16 + z) * 16 + x] & 0xFF;
        }
        
        @Override
        public int getSkyLight(int x, int y, int z) {
            return skyLight[(y * 16 + z) * 16 + x] & 0xFF;
        }
    }
}