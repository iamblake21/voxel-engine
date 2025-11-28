package engine.world.gen;

import engine.registry.Registries;
import engine.world.biome.Biome;
import engine.world.biome.Biomes;

/**
 * Selects biomes based on noise values.
 * 
 * Uses 5 parameters (like Minecraft):
 * - Continentalness
 * - Erosion  
 * - Peaks & Valleys
 * - Temperature
 * - Humidity
 */
public class BiomeProvider {
    
    private final NoiseRouter noiseRouter;
    private final TerrainShaper terrainShaper;
    private final float seaLevel;
    
    public BiomeProvider(NoiseRouter noiseRouter, TerrainShaper terrainShaper, float seaLevel) {
        this.noiseRouter = noiseRouter;
        this.terrainShaper = terrainShaper;
        this.seaLevel = seaLevel;
    }
    
    /**
     * Get biome at position.
     */
    public Biome getBiome(int x, int z) {
        float c = noiseRouter.getContinentalness(x, z);
        float e = noiseRouter.getErosion(x, z);
        float pv = noiseRouter.getPeaks(x, z);
        float temp = noiseRouter.getTemperature(x, z);
        float humid = noiseRouter.getHumidity(x, z);
        
        // Get actual terrain height
        float[] params = terrainShaper.getTerrainParams(x, z);
        float terrainHeight = params[0];
        
        // === OCEAN ===
        if (c < -0.3f) {
            return tryGet("game:ocean");
        }
        
        // === BEACH ===
        if (c < 0.0f && terrainHeight < seaLevel + 5) {
            return tryGet("game:beach");
        }
        
        // === MOUNTAINS ===
        // High PV + Low erosion + High continental = peaks
        if (e < -0.3f && terrainHeight > seaLevel + 60) {
            return tryGet("game:mountains");
        }
        
        // === LAND BIOMES based on temperature and humidity ===
        return selectLandBiome(temp, humid, e, terrainHeight);
    }
    
    /**
     * Select land biome based on climate.
     */
    private Biome selectLandBiome(float temp, float humid, float erosion, float height) {
        // Hot + Dry = Desert
        if (temp > 0.3f && humid < -0.2f) {
            return tryGet("game:desert");
        }
        
        // Humid = Forest
        if (humid > 0.2f) {
            return tryGet("game:forest");
        }
        
        // Default = Plains
        return tryGet("game:plains");
    }
    
    private Biome tryGet(String id) {
        return Registries.BIOMES.get(id).orElse(Biomes.DEFAULT());
    }
    
    // === GETTERS for debug ===
    
    public float getTemperature(int x, int z) {
        return (noiseRouter.getTemperature(x, z) + 1f) * 0.5f;
    }
    
    public float getHumidity(int x, int z) {
        return (noiseRouter.getHumidity(x, z) + 1f) * 0.5f;
    }
}
