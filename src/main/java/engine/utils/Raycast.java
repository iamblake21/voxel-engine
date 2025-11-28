package engine.utils;

import engine.world.World;
import engine.world.block.Blocks;

/**
 * Raycasting utilities for block selection
 */
public class Raycast {
    
    /**
     * Cast a ray and find the first solid block hit
     */
    public static RayHit cast(World world, float ox, float oy, float oz,
                              float dx, float dy, float dz, float maxDist) {
        
        // Prevent division by zero
        if (dx == 0) dx = Float.MIN_VALUE;
        if (dy == 0) dy = Float.MIN_VALUE;
        if (dz == 0) dz = Float.MIN_VALUE;
        
        int x = (int) Math.floor(ox);
        int y = (int) Math.floor(oy);
        int z = (int) Math.floor(oz);
        
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        
        float tMaxX = grid(ox, dx, stepX);
        float tMaxY = grid(oy, dy, stepY);
        float tMaxZ = grid(oz, dz, stepZ);
        
        float tDeltaX = Math.abs(1 / dx);
        float tDeltaY = Math.abs(1 / dy);
        float tDeltaZ = Math.abs(1 / dz);
        
        int nx = 0, ny = 0, nz = 0;
        float t = 0;
        
        for (int i = 0; i < 256; i++) {
            int blockId = world.getBlock(x, y, z);
            
            // Check if solid (not air, not liquid)
            if (!Blocks.isAir(blockId) && Blocks.isSolid(blockId)) {
                RayHit hit = new RayHit();
                hit.hit = true;
                hit.blockX = x;
                hit.blockY = y;
                hit.blockZ = z;
                hit.normalX = -nx;
                hit.normalY = -ny;
                hit.normalZ = -nz;
                hit.distance = t;
                return hit;
            }
            
            // Step to next voxel
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    nx = stepX;
                    ny = 0;
                    nz = 0;
                    t = tMaxX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    nx = 0;
                    ny = 0;
                    nz = stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    nx = 0;
                    ny = stepY;
                    nz = 0;
                    t = tMaxY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    nx = 0;
                    ny = 0;
                    nz = stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            }
            
            if (t > maxDist) break;
        }
        
        return new RayHit(); // No hit
    }
    
    private static float grid(float o, float d, int s) {
        if (d > 0) {
            return ((float) Math.floor(o) + 1 - o) / d;
        } else {
            return (o - (float) Math.floor(o)) / (-d);
        }
    }
    
    /**
     * Result of a raycast
     */
    public static class RayHit {
        public boolean hit = false;
        public int blockX, blockY, blockZ;
        public int normalX, normalY, normalZ;
        public float distance;
    }
}