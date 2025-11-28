package engine.rendering;

/**
 * Frustum for view frustum culling.
 * 
 * Extracts 6 planes from the view-projection matrix and tests
 * bounding boxes against them.
 */
public class Frustum {
    
    // 6 planes: left, right, bottom, top, near, far
    // Each plane: [A, B, C, D] where Ax + By + Cz + D = 0
    private final float[][] planes = new float[6][4];
    
    /**
     * Update frustum planes from combined view-projection matrix.
     * 
     * @param m Column-major 4x4 matrix (OpenGL style)
     */
    public void update(float[] m) {
        // Left plane
        planes[0][0] = m[3] + m[0];
        planes[0][1] = m[7] + m[4];
        planes[0][2] = m[11] + m[8];
        planes[0][3] = m[15] + m[12];
        normalizePlane(0);
        
        // Right plane
        planes[1][0] = m[3] - m[0];
        planes[1][1] = m[7] - m[4];
        planes[1][2] = m[11] - m[8];
        planes[1][3] = m[15] - m[12];
        normalizePlane(1);
        
        // Bottom plane
        planes[2][0] = m[3] + m[1];
        planes[2][1] = m[7] + m[5];
        planes[2][2] = m[11] + m[9];
        planes[2][3] = m[15] + m[13];
        normalizePlane(2);
        
        // Top plane
        planes[3][0] = m[3] - m[1];
        planes[3][1] = m[7] - m[5];
        planes[3][2] = m[11] - m[9];
        planes[3][3] = m[15] - m[13];
        normalizePlane(3);
        
        // Near plane
        planes[4][0] = m[3] + m[2];
        planes[4][1] = m[7] + m[6];
        planes[4][2] = m[11] + m[10];
        planes[4][3] = m[15] + m[14];
        normalizePlane(4);
        
        // Far plane
        planes[5][0] = m[3] - m[2];
        planes[5][1] = m[7] - m[6];
        planes[5][2] = m[11] - m[10];
        planes[5][3] = m[15] - m[14];
        normalizePlane(5);
    }
    
    private void normalizePlane(int i) {
        float len = (float) Math.sqrt(
            planes[i][0] * planes[i][0] +
            planes[i][1] * planes[i][1] +
            planes[i][2] * planes[i][2]
        );
        if (len > 0) {
            planes[i][0] /= len;
            planes[i][1] /= len;
            planes[i][2] /= len;
            planes[i][3] /= len;
        }
    }
    
    /**
     * Test if an AABB is inside or intersects the frustum.
     * 
     * @return true if visible (inside or intersecting)
     */
    public boolean testAABB(float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            
            // Find the corner most aligned with plane normal (p-vertex)
            float px = p[0] > 0 ? maxX : minX;
            float py = p[1] > 0 ? maxY : minY;
            float pz = p[2] > 0 ? maxZ : minZ;
            
            // If p-vertex is outside, entire box is outside
            if (p[0] * px + p[1] * py + p[2] * pz + p[3] < 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Test if a chunk is visible.
     * 
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param chunkSize Size of chunk (e.g., 16)
     * @param worldHeight World height (e.g., 256)
     */
    public boolean testChunk(int chunkX, int chunkZ, int chunkSize, int worldHeight) {
        float minX = chunkX * chunkSize;
        float minZ = chunkZ * chunkSize;
        float maxX = minX + chunkSize;
        float maxZ = minZ + chunkSize;
        
        return testAABB(minX, 0, minZ, maxX, worldHeight, maxZ);
    }
    
    /**
     * Test if a sphere is inside or intersects the frustum.
     */
    public boolean testSphere(float x, float y, float z, float radius) {
        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            float dist = p[0] * x + p[1] * y + p[2] * z + p[3];
            if (dist < -radius) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Test if a point is inside the frustum.
     */
    public boolean testPoint(float x, float y, float z) {
        for (int i = 0; i < 6; i++) {
            float[] p = planes[i];
            if (p[0] * x + p[1] * y + p[2] * z + p[3] < 0) {
                return false;
            }
        }
        return true;
    }
}
