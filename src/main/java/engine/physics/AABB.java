package engine.physics;

/**
 * Axis-Aligned Bounding Box for collision detection
 */
public class AABB {
    
    public float minX, minY, minZ;
    public float maxX, maxY, maxZ;
    
    public AABB() {}
    
    public AABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }
    
    /**
     * Create AABB from center and half-extents
     */
    public static AABB fromCenterAndSize(float cx, float cy, float cz, float halfWidth, float height) {
        return new AABB(
            cx - halfWidth, cy, cz - halfWidth,
            cx + halfWidth, cy + height, cz + halfWidth
        );
    }
    
    /**
     * Check intersection with another AABB
     */
    public boolean intersects(AABB other) {
        return maxX >= other.minX && minX <= other.maxX &&
               maxY >= other.minY && minY <= other.maxY &&
               maxZ >= other.minZ && minZ <= other.maxZ;
    }
    
    /**
     * Check if point is inside
     */
    public boolean contains(float x, float y, float z) {
        return x >= minX && x <= maxX &&
               y >= minY && y <= maxY &&
               z >= minZ && z <= maxZ;
    }
    
    /**
     * Expand by amount
     */
    public AABB expand(float amount) {
        return new AABB(
            minX - amount, minY - amount, minZ - amount,
            maxX + amount, maxY + amount, maxZ + amount
        );
    }
    
    /**
     * Get center
     */
    public float getCenterX() { return (minX + maxX) * 0.5f; }
    public float getCenterY() { return (minY + maxY) * 0.5f; }
    public float getCenterZ() { return (minZ + maxZ) * 0.5f; }
    
    /**
     * Get size
     */
    public float getWidth() { return maxX - minX; }
    public float getHeight() { return maxY - minY; }
    public float getDepth() { return maxZ - minZ; }
    
    @Override
    public String toString() {
        return String.format("AABB[%.2f,%.2f,%.2f -> %.2f,%.2f,%.2f]", 
            minX, minY, minZ, maxX, maxY, maxZ);
    }
}