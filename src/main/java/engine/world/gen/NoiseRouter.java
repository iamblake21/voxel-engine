package engine.world.gen;

/**
 * Routes all noise values for terrain generation.
 * Like Minecraft's DensityFunctions / NoiseRouter.
 * 
 * Each position in the world has values for:
 * - Continentalness: ocean (-1) to inland (+1)
 * - Erosion: flat (+1) to dramatic (-1)
 * - Weirdness (Peaks & Valleys): variation
 * - Temperature: cold (-1) to hot (+1)
 * - Humidity: dry (-1) to wet (+1)
 */
public class NoiseRouter {

    private final FastNoiseLite continentalnessNoise;
    private final FastNoiseLite erosionNoise;
    private final FastNoiseLite weirdnessNoise; // Formerly Peaks
    private final FastNoiseLite temperatureNoise;
    private final FastNoiseLite humidityNoise;

    // 3D noise for density/caves
    private final FastNoiseLite densityNoise;

    // Cave Noises (Cheese, Spaghetti, Noodle)
    private final FastNoiseLite caveCheeseNoise;
    private final FastNoiseLite caveSpaghettiNoiseA;
    private final FastNoiseLite caveSpaghettiNoiseB;
    private final FastNoiseLite caveNoodleNoiseA;
    private final FastNoiseLite caveNoodleNoiseB;

    public NoiseRouter(long seed) {
        int s = (int) seed;

        // === CONTINENTALNESS ===
        // Large scale blobs for ocean vs land
        continentalnessNoise = new FastNoiseLite(s);
        continentalnessNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        continentalnessNoise.SetFrequency(0.0004f); // Keep low for big oceans
        continentalnessNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        continentalnessNoise.SetFractalOctaves(7);
        continentalnessNoise.SetFractalLacunarity(2.0f);
        continentalnessNoise.SetFractalGain(0.5f);

        // === EROSION ===
        // Controls flat vs mountainous
        // Increased frequency to prevent "miles of flatness"
        erosionNoise = new FastNoiseLite(s + 1000);
        erosionNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        erosionNoise.SetFrequency(0.0015f);
        erosionNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        erosionNoise.SetFractalOctaves(5);
        erosionNoise.SetFractalLacunarity(2.0f);
        erosionNoise.SetFractalGain(0.5f);

        // === WEIRDNESS (PEAKS & VALLEYS) ===
        // High frequency detail for ridges / weird shapes
        // Increased frequency for local details
        weirdnessNoise = new FastNoiseLite(s + 2000);
        weirdnessNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        weirdnessNoise.SetFrequency(0.003f);
        weirdnessNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        weirdnessNoise.SetFractalOctaves(5);
        weirdnessNoise.SetFractalLacunarity(2.0f);
        weirdnessNoise.SetFractalGain(0.5f);

        // === TEMPERATURE ===
        temperatureNoise = new FastNoiseLite(s + 3000);
        temperatureNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        temperatureNoise.SetFrequency(0.0005f);
        temperatureNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        temperatureNoise.SetFractalOctaves(2);

        // === HUMIDITY ===
        humidityNoise = new FastNoiseLite(s + 4000);
        humidityNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        humidityNoise.SetFrequency(0.0005f);
        humidityNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        humidityNoise.SetFractalOctaves(2);

        // === 3D DENSITY NOISE (Jaggedness) ===
        // For terrain shape details, overhangs, weird formations
        densityNoise = new FastNoiseLite(s + 5000);
        densityNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        densityNoise.SetFrequency(0.015f);
        densityNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        densityNoise.SetFractalOctaves(3);

        // === CAVE NOISES ===
        // Cheese: Moderate frequency 3D noise
        caveCheeseNoise = new FastNoiseLite(s + 6000);
        caveCheeseNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        caveCheeseNoise.SetFrequency(0.01f); // Larger cheese caves
        caveCheeseNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        caveCheeseNoise.SetFractalOctaves(2);

        // Spaghetti: Low frequency, look for ridges (abs value near 0)
        caveSpaghettiNoiseA = new FastNoiseLite(s + 7000);
        caveSpaghettiNoiseA.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        caveSpaghettiNoiseA.SetFrequency(0.015f); // Larger tunnels
        caveSpaghettiNoiseA.SetFractalType(FastNoiseLite.FractalType.FBm);
        caveSpaghettiNoiseA.SetFractalOctaves(2);

        caveSpaghettiNoiseB = new FastNoiseLite(s + 8000);
        caveSpaghettiNoiseB.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        caveSpaghettiNoiseB.SetFrequency(0.015f);
        caveSpaghettiNoiseB.SetFractalType(FastNoiseLite.FractalType.FBm);
        caveSpaghettiNoiseB.SetFractalOctaves(2);

        // Noodle: Higher frequency spaghetti
        caveNoodleNoiseA = new FastNoiseLite(s + 9000);
        caveNoodleNoiseA.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        caveNoodleNoiseA.SetFrequency(0.05f);

        caveNoodleNoiseB = new FastNoiseLite(s + 10000);
        caveNoodleNoiseB.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        caveNoodleNoiseB.SetFrequency(0.05f);
    }

    // === 2D NOISE SAMPLERS (for terrain shaping) ===

    public float getContinentalness(int x, int z) {
        return continentalnessNoise.GetNoise(x, z);
    }

    public float getErosion(int x, int z) {
        return erosionNoise.GetNoise(x, z);
    }

    public float getWeirdness(int x, int z) {
        return weirdnessNoise.GetNoise(x, z);
    }

    // Legacy alias
    public float getPeaks(int x, int z) {
        return getWeirdness(x, z);
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

    // Added separate getters for cave noises
    public float getCheeseNoise(int x, int y, int z) {
        return caveCheeseNoise.GetNoise(x, y, z);
    }

    public float getSpaghettiNoiseA(int x, int y, int z) {
        return caveSpaghettiNoiseA.GetNoise(x, y, z);
    }

    public float getSpaghettiNoiseB(int x, int y, int z) {
        return caveSpaghettiNoiseB.GetNoise(x, y, z);
    }

    public float getNoodleNoiseA(int x, int y, int z) {
        return caveNoodleNoiseA.GetNoise(x, y, z);
    }

    public float getNoodleNoiseB(int x, int y, int z) {
        return caveNoodleNoiseB.GetNoise(x, y, z);
    }

    /**
     * Legacy simple cave check
     */
    public boolean isCave(int x, int y, int z) {
        // Use new spaghetti implementation behavior roughly
        return Math.abs(getSpaghettiNoiseA(x, y, z)) < 0.1 && Math.abs(getSpaghettiNoiseB(x, y, z)) < 0.1;
    }
}
