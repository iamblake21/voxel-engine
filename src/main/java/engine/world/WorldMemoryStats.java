package engine.world;

public final class WorldMemoryStats {
    public final int loadedChunks;
    public final int pendingChunks;
    public final int allocatedSections;
    public final int meshedSections;
    public final long estimatedSectionBytes;
    public final long estimatedVboBytes;
    public final long heapUsedBytes;
    public final long heapMaxBytes;
    public final int safeRadius;
    public final int unloadRadius;
    public final int maxResidentChunks;
    public final int maxResidentSectionMeshes;
    public final int terrainQueueSize;
    public final int lightQueueSize;
    public final int meshQueueSize;
    public final int workerCount;

    WorldMemoryStats(
            int loadedChunks,
            int pendingChunks,
            int allocatedSections,
            int meshedSections,
            long estimatedSectionBytes,
            long estimatedVboBytes,
            long heapUsedBytes,
            long heapMaxBytes,
            int safeRadius,
            int unloadRadius,
            int maxResidentChunks,
            int maxResidentSectionMeshes,
            int terrainQueueSize,
            int lightQueueSize,
            int meshQueueSize,
            int workerCount) {
        this.loadedChunks = loadedChunks;
        this.pendingChunks = pendingChunks;
        this.allocatedSections = allocatedSections;
        this.meshedSections = meshedSections;
        this.estimatedSectionBytes = estimatedSectionBytes;
        this.estimatedVboBytes = estimatedVboBytes;
        this.heapUsedBytes = heapUsedBytes;
        this.heapMaxBytes = heapMaxBytes;
        this.safeRadius = safeRadius;
        this.unloadRadius = unloadRadius;
        this.maxResidentChunks = maxResidentChunks;
        this.maxResidentSectionMeshes = maxResidentSectionMeshes;
        this.terrainQueueSize = terrainQueueSize;
        this.lightQueueSize = lightQueueSize;
        this.meshQueueSize = meshQueueSize;
        this.workerCount = workerCount;
    }

    public long estimatedSectionMB() {
        return estimatedSectionBytes / 1024 / 1024;
    }

    public long estimatedVboMB() {
        return estimatedVboBytes / 1024 / 1024;
    }

    public long heapUsedMB() {
        return heapUsedBytes / 1024 / 1024;
    }

    public long heapMaxMB() {
        return heapMaxBytes / 1024 / 1024;
    }
}
