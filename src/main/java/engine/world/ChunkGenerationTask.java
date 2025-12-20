package engine.world;

/**
 * Represents a chunk generation task to be executed on a worker thread.
 */
public class ChunkGenerationTask {

    public final int chunkX;
    public final int chunkZ;
    public final Priority priority;

    // Results (filled by worker thread)
    public volatile short[] blockData;
    public volatile int[] heightMap;
    public volatile byte[] fluidData;
    public volatile boolean complete = false;
    public volatile boolean cancelled = false;

    public enum Priority {
        CRITICAL, // Pre-generation ahead of camera - highest priority
        HIGH, // Player is close, generate ASAP
        NORMAL, // Standard loading
        LOW // Preloading distant chunks
    }

    public ChunkGenerationTask(int chunkX, int chunkZ, Priority priority) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.priority = priority;
    }

    public long getChunkKey() {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ChunkGenerationTask that = (ChunkGenerationTask) o;
        return chunkX == that.chunkX && chunkZ == that.chunkZ;
    }

    @Override
    public int hashCode() {
        return 31 * chunkX + chunkZ;
    }
}