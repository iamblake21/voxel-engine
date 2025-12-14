package engine.world.gen;

/**
 * Routes all noise values for terrain generation.
 * Like Minecraft's DensityFunctions / NoiseRouter.
 * 
 * Each position in the world has values for:
 * - Continentalness: ocean (-1) to inland (+1)
 * - Erosion: flat (+1) to dramatic (-1)
 * - Peaks & Valleys (PV): valleys (-1) to peaks (+1)
 * - Temperature: cold (-1) to hot (+1)
 * - Humidity: dry (-1) to wet (+1)
 */
public class NoiseRouter {

    private final FastNoiseLite continentalnessNoise;
    private final FastNoiseLite erosionNoise;
    private final FastNoiseLite peaksNoise;
    private final FastNoiseLite temperatureNoise;
    private final FastNoiseLite humidityNoise;

    // 3D noise for density/caves
    private final FastNoiseLite densityNoise;
    private final FastNoiseLite caveNoise1;
    private final FastNoiseLite caveNoise2;

    public NoiseRouter(long seed) {
        int s = (int) seed;

        // === CONTINENTALNESS ===
        // Large scale blobs for ocean vs land
        continentalnessNoise = new FastNoiseLite(s);
        continentalnessNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        continentalnessNoise.SetFrequency(0.0008f);
        continentalnessNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        continentalnessNoise.SetFractalOctaves(3);
        continentalnessNoise.SetFractalLacunarity(2.0f);
        continentalnessNoise.SetFractalGain(0.5f);

        // === EROSION ===
        // Controls flat vs mountainous
        erosionNoise = new FastNoiseLite(s + 1000);
        erosionNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        erosionNoise.SetFrequency(0.001f);
        erosionNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        erosionNoise.SetFractalOctaves(3);
        erosionNoise.SetFractalLacunarity(2.0f);
        erosionNoise.SetFractalGain(0.5f);

        // === PEAKS & VALLEYS ===
        // High frequency detail for ridges
        peaksNoise = new FastNoiseLite(s + 2000);
        peaksNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        peaksNoise.SetFrequency(0.004f);
        peaksNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        peaksNoise.SetFractalOctaves(2);
        peaksNoise.SetFractalLacunarity(2.5f);
        peaksNoise.SetFractalGain(2.5f);

        // === TEMPERATURE ===
        temperatureNoise = new FastNoiseLite(s + 3000);
        temperatureNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        temperatureNoise.SetFrequency(0.0005f);
        temperatureNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        temperatureNoise.SetFractalOctaves(2);

        // === HUMIDITY ===
        humidityNoise = new FastNoiseLite(s + 4000);
        humidityNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        humidityNoise.SetFrequency(0.0006f);
        humidityNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidityNoise.SetFractalOctaves(2);

        // === 3D DENSITY NOISE ===
        // For terrain shape details, overhangs, weird formations
        densityNoise = new FastNoiseLite(s + 5000);
        densityNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        densityNoise.SetFrequency(0.015f);
        densityNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        densityNoise.SetFractalOctaves(3);
        densityNoise.SetFractalLacunarity(2.0f);
        densityNoise.SetFractalGain(0.5f);

        // === CAVE NOISE (Spaghetti caves) ===
        caveNoise1 = new FastNoiseLite(s + 6000);
        caveNoise1.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        caveNoise1.SetFrequency(0.03f);
        caveNoise1.SetFractalType(FastNoiseLite.FractalType.FBm);
        caveNoise1.SetFractalOctaves(2);

        caveNoise2 = new FastNoiseLite(s + 7000);
        caveNoise2.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        caveNoise2.SetFrequency(0.03f);
        caveNoise2.SetFractalType(FastNoiseLite.FractalType.FBm);
        caveNoise2.SetFractalOctaves(2);
    }

    // === 2D NOISE SAMPLERS (for terrain shaping) ===

    public float getContinentalness(int x, int z) {
        return continentalnessNoise.GetNoise(x, z);
    }

    public float getErosion(int x, int z) {
        return erosionNoise.GetNoise(x, z);
    }

    public float getPeaks(int x, int z) {
        return peaksNoise.GetNoise(x, z);
    }

    public float getTemperature(int x, int z) {
        return temperatureNoise.GetNoise(x, z);
    }

    public float getHumidity(int x, int z) {
        return humidityNoise.GetNoise(x, z);
    }

    // === 3D NOISE SAMPLERS ===

    public float getDensity3D(int x, int y, int z) {
        return densityNoise.GetNoise(x, y, z);
    }

    /**
     * Spaghetti cave check - returns true if this is a cave
     */
    public boolean isCave(int x, int y, int z) {
        float n1 = caveNoise1.GetNoise(x, y, z);
        float n2 = caveNoise2.GetNoise(x, y, z);

        // Spaghetti: cave where both noises are near zero
        float threshold = 0.1f;
        return Math.abs(n1) < threshold && Math.abs(n2) < threshold;
    }
}
