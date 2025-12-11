package engine.world;

import java.util.Objects;

/**
 * Immutable 3D position for blocks.
 * Provides utility methods for navigation and hashing.
 */
public final class BlockPos {
    
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);
    
    private final int x;
    private final int y;
    private final int z;
    
    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    // ==================== GETTERS ====================
    
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    
    // ==================== NAVIGATION ====================
    
    public BlockPos above() {
        return new BlockPos(x, y + 1, z);
    }
    
    public BlockPos above(int n) {
        return new BlockPos(x, y + n, z);
    }
    
    public BlockPos below() {
        return new BlockPos(x, y - 1, z);
    }
    
    public BlockPos below(int n) {
        return new BlockPos(x, y - n, z);
    }
    
    public BlockPos north() {
        return new BlockPos(x, y, z - 1);
    }
    
    public BlockPos south() {
        return new BlockPos(x, y, z + 1);
    }
    
    public BlockPos east() {
        return new BlockPos(x + 1, y, z);
    }
    
    public BlockPos west() {
        return new BlockPos(x - 1, y, z);
    }
    
    public BlockPos offset(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return this;
        return new BlockPos(x + dx, y + dy, z + dz);
    }
    
    public BlockPos offset(Direction dir) {
        return offset(dir.getOffsetX(), dir.getOffsetY(), dir.getOffsetZ());
    }
    
    // ==================== CHUNK UTILITIES ====================
    
    /**
     * Get the chunk X coordinate this position is in.
     */
    public int getChunkX() {
        return x >> 4; // Equivalent to floorDiv(x, 16)
    }
    
    /**
     * Get the chunk Z coordinate this position is in.
     */
    public int getChunkZ() {
        return z >> 4;
    }
    
    /**
     * Get local X within chunk (0-15).
     */
    public int getLocalX() {
        return x & 15;
    }
    
    /**
     * Get local Z within chunk (0-15).
     */
    public int getLocalZ() {
        return z & 15;
    }
    
    // ==================== DISTANCE ====================
    
    public double distanceTo(BlockPos other) {
        return Math.sqrt(distanceToSq(other));
    }
    
    public double distanceToSq(BlockPos other) {
        int dx = x - other.x;
        int dy = y - other.y;
        int dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
    
    public int manhattanDistance(BlockPos other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }
    
    // ==================== CONVERSION ====================
    
    /**
     * Create from entity position (floors the values).
     */
    public static BlockPos fromEntityPos(float ex, float ey, float ez) {
        return new BlockPos(
            (int) Math.floor(ex),
            (int) Math.floor(ey),
            (int) Math.floor(ez)
        );
    }
    
    /**
     * Get center of this block position.
     */
    public float getCenterX() { return x + 0.5f; }
    public float getCenterY() { return y + 0.5f; }
    public float getCenterZ() { return z + 0.5f; }
    
    // ==================== HASHING ====================
    
    /**
     * Pack into a single long for use as map key.
     * Supports coordinates from -33554432 to 33554431 for X/Z,
     * and 0 to 4095 for Y.
     */
    public long asLong() {
        return asLong(x, y, z);
    }
    
    public static long asLong(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
    
    public static BlockPos fromLong(long packed) {
        int x = (int)(packed >> 38);
        int y = (int)((packed >> 26) & 0xFFF);
        int z = (int)(packed & 0x3FFFFFF);
        // Sign extend x and z
        if (x >= 0x2000000) x -= 0x4000000;
        if (z >= 0x2000000) z -= 0x4000000;
        return new BlockPos(x, y, z);
    }
    
    // ==================== OBJECT METHODS ====================
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockPos)) return false;
        BlockPos other = (BlockPos) obj;
        return x == other.x && y == other.y && z == other.z;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
    
    @Override
    public String toString() {
        return "BlockPos{" + x + ", " + y + ", " + z + "}";
    }
    
    // ==================== DIRECTION ENUM ====================
    
    public enum Direction {
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);
        
        private final int offsetX, offsetY, offsetZ;
        
        Direction(int x, int y, int z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
        }
        
        public int getOffsetX() { return offsetX; }
        public int getOffsetY() { return offsetY; }
        public int getOffsetZ() { return offsetZ; }
        
        public Direction getOpposite() {
            switch (this) {
                case DOWN: return UP;
                case UP: return DOWN;
                case NORTH: return SOUTH;
                case SOUTH: return NORTH;
                case WEST: return EAST;
                case EAST: return WEST;
                default: return this;
            }
        }
        
        /**
         * Get direction from face normal.
         */
        public static Direction fromNormal(int nx, int ny, int nz) {
            if (ny > 0) return UP;
            if (ny < 0) return DOWN;
            if (nz < 0) return NORTH;
            if (nz > 0) return SOUTH;
            if (nx < 0) return WEST;
            if (nx > 0) return EAST;
            return UP;
        }
    }
}
