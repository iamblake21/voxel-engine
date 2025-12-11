package engine.entity.model;

/**
 * A cube in a model bone.
 */
public class ModelCube {
    
    // Position (Blockbench "origin")
    private float minX, minY, minZ;
    
    // Size
    private float sizeX, sizeY, sizeZ;
    
    // UV coordinates
    private float uvX, uvY;
    
    // Inflation (grow cube by this amount)
    private float inflate = 0;
    
    // Mirror UVs
    private boolean mirror = false;
    
    public ModelCube() {}
    
    // ==================== POSITION ====================
    
    public float getMinX() { return minX; }
    public float getMinY() { return minY; }
    public float getMinZ() { return minZ; }
    
    public void setOrigin(float x, float y, float z) {
        this.minX = x;
        this.minY = y;
        this.minZ = z;
    }
    
    // ==================== SIZE ====================
    
    public float getSizeX() { return sizeX; }
    public float getSizeY() { return sizeY; }
    public float getSizeZ() { return sizeZ; }
    
    public void setSize(float x, float y, float z) {
        this.sizeX = x;
        this.sizeY = y;
        this.sizeZ = z;
    }
    
    // ==================== UV ====================
    
    public float getUvX() { return uvX; }
    public float getUvY() { return uvY; }
    
    public void setUv(float u, float v) {
        this.uvX = u;
        this.uvY = v;
    }
    
    // ==================== INFLATE ====================
    
    public float getInflate() { return inflate; }
    public void setInflate(float inflate) { this.inflate = inflate; }
    
    // ==================== MIRROR ====================
    
    public boolean isMirror() { return mirror; }
    public void setMirror(boolean mirror) { this.mirror = mirror; }
}
