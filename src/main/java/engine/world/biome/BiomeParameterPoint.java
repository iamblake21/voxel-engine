package engine.world.biome;

/**
 * A point in the multi-dimensional biome parameter space.
 * Used to lookup the nearest biome for a given set of noise values.
 */
public class BiomeParameterPoint {

    public final Biome biome;
    public final float continentalness;
    public final float erosion;
    public final float temperature;
    public final float humidity;
    public final float weirdness;

    /**
     * @param biome           The biome to register at this point
     * @param continentalness Continentalness parameter (-1.0 to 1.0)
     * @param erosion         Erosion parameter (-1.0 to 1.0)
     * @param temperature     Temperature parameter (0.0 to 1.0 mostly, can go neg)
     * @param humidity        Humidity parameter (0.0 to 1.0 mostly)
     * @param weirdness       Weirdness/Depth parameter
     */
    public BiomeParameterPoint(Biome biome, float continentalness, float erosion, float temperature, float humidity,
            float weirdness) {
        this.biome = biome;
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.temperature = temperature;
        this.humidity = humidity;
        this.weirdness = weirdness;
    }

    public float getDistanceSquared(float c, float e, float t, float h, float w) {
        return sq(this.continentalness - c) +
                sq(this.erosion - e) +
                sq(this.temperature - t) +
                sq(this.humidity - h) +
                sq(this.weirdness - w);
    }

    private static float sq(float x) {
        return x * x;
    }
}
