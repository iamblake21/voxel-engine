package engine.world.gen;

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

    public BiomeProvider(NoiseRouter noiseRouter, TerrainShaper terrainShaper, float seaLevel) {
        this.noiseRouter = noiseRouter;
    }

    /**
     * Get biome at position using parameter points.
     */
    public Biome getBiome(int x, int z) {
        float c = noiseRouter.getContinentalness(x, z);
        float e = noiseRouter.getErosion(x, z);
        float pv = noiseRouter.getPeaks(x, z);
        float temp = noiseRouter.getTemperature(x, z);
        float humid = noiseRouter.getHumidity(x, z);

        return findNearestBiome(c, e, temp, humid, pv);
    }

    private Biome findNearestBiome(float c, float e, float t, float h, float w) {
        engine.world.biome.BiomeParameterPoint bestPoint = null;
        float bestDist = Float.MAX_VALUE;

        for (engine.world.biome.BiomeParameterPoint point : Biomes.BIOME_POINTS) {
            float dist = point.getDistanceSquared(c, e, t, h, w);
            if (dist < bestDist) {
                bestDist = dist;
                bestPoint = point;
            }
        }

        if (bestPoint != null) {
            return bestPoint.biome;
        }

        return Biomes.DEFAULT();
    }

    // === GETTERS for debug ===

    public float getTemperature(int x, int z) {
        return (noiseRouter.getTemperature(x, z) + 1f) * 0.5f;
    }

    public float getHumidity(int x, int z) {
        return (noiseRouter.getHumidity(x, z) + 1f) * 0.5f;
    }
}
