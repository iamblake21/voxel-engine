package engine.utils;

/**
 * Immutable chunk position with proper hashing
 */
public final class ChunkPos {
    public final int x, z;
    
    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos)) return false;
        ChunkPos pos = (ChunkPos) o;
        return x == pos.x && z == pos.z;
    }
    
    @Override
    public int hashCode() {
        int h = 1469598101;
        h ^= x;
        h *= 16777619;
        h ^= z;
        h *= 16777619;
        return h;
    }
    
    @Override
    public String toString() {
        return "ChunkPos(" + x + ", " + z + ")";
    }
    
    /**
     * Get chunk position from world coordinates
     */
    public static ChunkPos fromWorld(int worldX, int worldZ, int chunkSize) {
        return new ChunkPos(floorDiv(worldX, chunkSize), floorDiv(worldZ, chunkSize));
    }
    
    /**
     * Floor division (handles negatives correctly)
     */
    public static int floorDiv(int a, int b) {
        int q = a / b;
        int r = a % b;
        if ((r != 0) && ((a ^ b) < 0)) q--;
        return q;
    }
    
    /**
     * Modulo (handles negatives correctly)
     */
    public static int mod(int a, int b) {
        int m = a % b;
        if (m < 0) m += b;
        return m;
    }
}