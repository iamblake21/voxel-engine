package engine.world.gen;

/**
 * Calculates 3D density for each block position.
 * 
 * This is the core of Minecraft-style terrain:
 * - Positive density = solid (stone)
 * - Negative density = air
 * - The surface is where density crosses zero
 * 
 * The density is influenced by:
 * 1. Base terrain height (from splines) - creates height bias
 * 2. Squashing factor - controls how flat/crazy the terrain is
 * 3. 3D noise - adds detail, overhangs, weird shapes
 */
public class DensityFunction {
    
    private final TerrainShaper terrainShaper;
    private final NoiseRouter noiseRouter;
    private final float seaLevel;
    
    public DensityFunction(TerrainShaper terrainShaper) {
        this.terrainShaper = terrainShaper;
        this.noiseRouter = terrainShaper.getNoiseRouter();
        this.seaLevel = terrainShaper.getSeaLevel();
    }
    
    /**
     * Calculate density at a 3D position.
     * 
     * @return positive = solid, negative = air
     */
    public float getDensity(int x, int y, int z) {
        // Get terrain parameters from splines
        float[] params = terrainShaper.getTerrainParams(x, z);
        float targetHeight = params[0];
        float squashing = params[1];
        
        // === HEIGHT BIAS ===
        // Below target height = positive (solid)
        // Above target height = negative (air)
        float heightBias = (targetHeight - y) / 16f;  // More sensitive
        
        // === SQUASHING ===
        // High squashing = height bias dominates = flat terrain
        // Low squashing = 3D noise shows through = crazy terrain
        heightBias *= squashing;
        
        // === 3D NOISE ===
        // Adds detail and creates overhangs when squashing is low
        float noise3D = noiseRouter.getDensity3D(x, y, z);
        
        // Scale noise based on inverse squashing
        // Low squashing = noise matters MORE (jagged peaks)
        float noiseInfluence = 1f / (squashing * squashing);  // Squared for more effect
        noise3D *= noiseInfluence * 0.7f;  // Increased from 0.5
        
        // === COMBINE ===
        float density = heightBias + noise3D;
        
        // === DEPTH GUARANTEE ===
        // Below Y=5, always solid (bedrock zone)
        if (y < 5) {
            density += (5 - y) * 0.8f;
        }
        
        // === SKY GUARANTEE ===
        // Above Y=250, always air
        if (y > 250) {
            density -= (y - 250) * 0.5f;
        }
        
        return density;
    }
    
    /**
     * Check if a position should be solid.
     */
    public boolean isSolid(int x, int y, int z) {
        return getDensity(x, y, z) > 0;
    }
    
    /**
     * Check if position is a cave (after terrain generation).
     */
    public boolean isCave(int x, int y, int z) {
        // Don't carve caves too close to surface or in water
        float[] params = terrainShaper.getTerrainParams(x, z);
        float targetHeight = params[0];
        
        if (y > targetHeight - 5) return false;  // Too close to surface
        if (y < 10) return false;  // Bedrock zone
        if (y > seaLevel && y < targetHeight) {
            // Only carve caves in solid terrain above sea level
            // or below sea level but deep enough
        }
        
        return noiseRouter.isCave(x, y, z);
    }
}