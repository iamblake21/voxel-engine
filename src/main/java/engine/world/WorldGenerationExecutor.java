package engine.world;

import engine.world.gen.WorldGenerator;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages multi-threaded world generation.
 * 
 * Architecture:
 * - Main thread: submits tasks, receives completed chunks
 * - Worker threads: generate terrain (CPU intensive)
 * 
 * Thread-safe communication via concurrent queues.
 */
public class WorldGenerationExecutor {
    
    // Thread pool for generation
    private final ExecutorService executor;
    private final int numWorkers;
    
    // World generator (thread-safe for read operations)
    private final WorldGenerator worldGenerator;
    private final int chunkSize;
    private final int chunkHeight;
    
    // Task management
    private final PriorityBlockingQueue<ChunkGenerationTask> pendingTasks;
    private final ConcurrentLinkedQueue<ChunkGenerationTask> completedTasks;
    private final Map<Long, ChunkGenerationTask> activeTasksMap;
    
    // Stats
    private volatile int chunksGenerated = 0;
    private volatile int chunksQueued = 0;
    
    // Shutdown flag
    private volatile boolean shutdown = false;
    
    public WorldGenerationExecutor(WorldGenerator worldGenerator, int chunkSize, int chunkHeight) {
        // Default: use available processors minus 1
        this(worldGenerator, chunkSize, chunkHeight, 
             Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }
    
    public WorldGenerationExecutor(WorldGenerator worldGenerator, int chunkSize, int chunkHeight, int numWorkers) {
        this.worldGenerator = worldGenerator;
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.numWorkers = Math.max(1, numWorkers);
        
        // Priority queue - CRITICAL/HIGH priority first (lower ordinal = higher priority)
        this.pendingTasks = new PriorityBlockingQueue<>(64, 
            Comparator.comparingInt(t -> t.priority.ordinal()));
        
        this.completedTasks = new ConcurrentLinkedQueue<>();
        this.activeTasksMap = new ConcurrentHashMap<>();
        
        // Create thread pool with named threads
        this.executor = Executors.newFixedThreadPool(this.numWorkers, r -> {
            Thread t = new Thread(r, "WorldGen-Worker");
            t.setDaemon(true);  // Don't prevent JVM shutdown
            return t;
        });
        
        // Start worker threads
        for (int i = 0; i < this.numWorkers; i++) {
            executor.submit(this::workerLoop);
        }
        
        System.out.println("[WorldGenExecutor] Started with " + this.numWorkers + " worker threads");
    }
    
    /**
     * Worker thread main loop.
     */
    private void workerLoop() {
        while (!shutdown) {
            try {
                // Wait for task (blocks until available or timeout)
                ChunkGenerationTask task = pendingTasks.poll(100, TimeUnit.MILLISECONDS);
                
                if (task == null) continue;
                if (task.cancelled) continue;
                
                // Generate the chunk
                generateChunk(task);
                
                // Move to completed queue
                task.complete = true;
                completedTasks.offer(task);
                activeTasksMap.remove(task.getChunkKey());
                chunksGenerated++;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[WorldGen Worker] Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Generate chunk data (runs on worker thread).
     */
    private void generateChunk(ChunkGenerationTask task) {
        // Allocate arrays
        int[] blockData = new int[chunkSize * chunkSize * chunkHeight];
        int[] heightMap = new int[chunkSize * chunkSize];
        
        // Generate terrain
        worldGenerator.generateTerrain(task.chunkX, task.chunkZ, blockData, heightMap);
        
        // Store results
        task.blockData = blockData;
        task.heightMap = heightMap;
    }
    
    /**
     * Submit a chunk for generation (call from main thread).
     * 
     * @return true if submitted, false if already queued/generating
     */
    public boolean submit(int chunkX, int chunkZ, ChunkGenerationTask.Priority priority) {
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        
        // Already generating?
        if (activeTasksMap.containsKey(key)) {
            return false;
        }
        
        ChunkGenerationTask task = new ChunkGenerationTask(chunkX, chunkZ, priority);
        activeTasksMap.put(key, task);
        pendingTasks.offer(task);
        chunksQueued++;
        
        return true;
    }
    
    /**
     * Submit with normal priority.
     */
    public boolean submit(int chunkX, int chunkZ) {
        return submit(chunkX, chunkZ, ChunkGenerationTask.Priority.NORMAL);
    }
    
    /**
     * Cancel a pending task (if not already generating).
     */
    public void cancel(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        ChunkGenerationTask task = activeTasksMap.get(key);
        if (task != null) {
            task.cancelled = true;
        }
    }
    
    /**
     * Poll for completed chunks (call from main thread).
     * Returns null if no chunks ready.
     */
    public ChunkGenerationTask pollCompleted() {
        return completedTasks.poll();
    }
    
    /**
     * Check if a chunk is currently being generated.
     */
    public boolean isGenerating(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        return activeTasksMap.containsKey(key);
    }
    
    /**
     * Get number of pending + active tasks.
     */
    public int getQueueSize() {
        return pendingTasks.size() + activeTasksMap.size();
    }
    
    /**
     * Get number of completed tasks waiting to be polled.
     */
    public int getCompletedCount() {
        return completedTasks.size();
    }
    
    /**
     * Get total chunks generated since start.
     */
    public int getChunksGenerated() {
        return chunksGenerated;
    }
    
    /**
     * Get number of worker threads.
     */
    public int getNumWorkers() {
        return numWorkers;
    }
    
    /**
     * Shutdown the executor (call on game exit).
     */
    public void shutdown() {
        shutdown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        System.out.println("[WorldGenExecutor] Shutdown complete. Generated " + chunksGenerated + " chunks.");
    }
}