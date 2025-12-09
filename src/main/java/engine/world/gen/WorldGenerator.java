package engine.world.gen;

import engine.world.biome.Biome;

/**
 * Main world generator - orchestrates terrain and feature generation.
 */
public class WorldGenerator {

    private final long seed;
    private final int chunkSize;
    private final int chunkHeight;
    private final float waterLevel;

    private final TerrainGenerator terrainGenerator;
    private final FeatureGenerator featureGenerator;
    private final BiomeProvider biomeProvider;
    private final TerrainShaper terrainShaper;
    private final NoiseRouter noiseRouter;

    public WorldGenerator(long seed, int chunkSize, int chunkHeight, float waterLevel) {
        this.seed = seed;
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.waterLevel = waterLevel;

        this.terrainGenerator = new TerrainGenerator(seed, chunkSize, chunkHeight, waterLevel);
        this.biomeProvider = terrainGenerator.getBiomeProvider();
        this.terrainShaper = terrainGenerator.getTerrainShaper();
        this.noiseRouter = terrainGenerator.getNoiseRouter();
        this.featureGenerator = new FeatureGenerator(seed, biomeProvider, this,
                chunkSize, chunkHeight, waterLevel);
    }

    /**
     * Generate terrain for a chunk.
     */
    public void generateTerrain(int chunkX, int chunkZ, int[] blockData, int[] heightMap, byte[] fluidData) {
        terrainGenerator.generateTerrain(chunkX, chunkZ, blockData, heightMap, fluidData);
    }

    /**
     * Generate features for a chunk.
     */
    public void generateFeatures(int chunkX, int chunkZ, int[] blockData, int[] heightMap,
            FeatureGenerator.BlockPlacer placer) {
        featureGenerator.generateFeatures(chunkX, chunkZ, blockData, heightMap, placer);
    }

    /**
     * Get biome at world coordinates.
     */
    public Biome getBiome(int worldX, int worldZ) {
        return biomeProvider.getBiome(worldX, worldZ);
    }

    /**
     * Get terrain height at world coordinates.
     * Uses the spline system to calculate target height.
     */
    public int getHeight(int worldX, int worldZ) {
        float[] params = terrainShaper.getTerrainParams(worldX, worldZ);
        return Math.round(params[0]); // params[0] = target height
    }

    /**
     * Get temperature at world coordinates (0-1).
     */
    public float getTemperature(int worldX, int worldZ) {
        return biomeProvider.getTemperature(worldX, worldZ);
    }

    /**
     * Get humidity at world coordinates (0-1).
     */
    public float getHumidity(int worldX, int worldZ) {
        return biomeProvider.getHumidity(worldX, worldZ);
    }

    /**
     * Check if feature generator has deferred operations.
     */
    public boolean hasDeferredFeatures(int chunkX, int chunkZ) {
        return featureGenerator.hasDeferredOps(chunkX, chunkZ);
    }

    // Getters
    public long getSeed() {
        return seed;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkHeight() {
        return chunkHeight;
    }

    public float getWaterLevel() {
        return waterLevel;
    }

    public BiomeProvider getBiomeProvider() {
        return biomeProvider;
    }

    public TerrainShaper getTerrainShaper() {
        return terrainShaper;
    }

    public NoiseRouter getNoiseRouter() {
        return noiseRouter;
    }
}
