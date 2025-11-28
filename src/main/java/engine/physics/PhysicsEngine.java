package engine.physics;

import engine.core.Config;
import engine.world.World;
import engine.world.block.Blocks;

/**
 * Physics engine - handles gravity, collision, and movement
 */
public class PhysicsEngine {
    
    private final Config config;
    
    public PhysicsEngine(Config config) {
        this.config = config;
    }
    
    /**
     * Update physics (called by entities)
     */
    public void update(float deltaTime) {
        // Physics is handled per-entity
    }
    
    /**
     * Check collision with world
     */
    public boolean checkCollision(AABB box, World world) {
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int blockId = world.getBlock(x, y, z);
                    if (Blocks.isSolid(blockId)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if AABB overlaps specific block ID
     */
    public boolean checkBlockId(AABB box, World world, int blockId) {
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int block = world.getBlock(x, y, z);
                    if (block == blockId) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Resolve movement with collision
     * Returns actual movement that occurred
     */
    public MovementResult resolveMovement(AABB box, World world, float dx, float dy, float dz) {
        MovementResult result = new MovementResult();
        
        float x = box.getCenterX();
        float y = box.minY;
        float z = box.getCenterZ();
        float hw = box.getWidth() * 0.5f;
        float hh = box.getHeight();
        
        // X movement
        AABB testBox = AABB.fromCenterAndSize(x + dx, y, z, hw, hh);
        if (checkCollision(testBox, world)) {
            dx = 0;
            result.collidedX = true;
        }
        x += dx;
        
        // Y movement
        testBox = AABB.fromCenterAndSize(x, y + dy, z, hw, hh);
        if (checkCollision(testBox, world)) {
            if (dy < 0) result.onGround = true;
            dy = 0;
            result.collidedY = true;
        }
        y += dy;
        
        // Z movement
        testBox = AABB.fromCenterAndSize(x, y, z + dz, hw, hh);
        if (checkCollision(testBox, world)) {
            dz = 0;
            result.collidedZ = true;
        }
        z += dz;
        
        result.finalX = x;
        result.finalY = y;
        result.finalZ = z;
        result.actualDX = dx;
        result.actualDY = dy;
        result.actualDZ = dz;
        
        return result;
    }
    
    /**
     * Result of movement resolution
     */
    public static class MovementResult {
        public float finalX, finalY, finalZ;
        public float actualDX, actualDY, actualDZ;
        public boolean collidedX, collidedY, collidedZ;
        public boolean onGround;
    }
}