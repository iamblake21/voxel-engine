package engine.world.gen;

import engine.world.biome.Biome;
import engine.world.block.Block;
import engine.world.block.Blocks;

import java.util.Arrays;

/**
 * Generates terrain using 3D density functions.
 * 
 * Unlike simple heightmap generation, this:
 * 1. Calculates density for each 3D position
 * 2. Solid where density > 0, air where density < 0
 * 3. Creates overhangs, caves, weird formations
 * 4. Applies surface blocks based on biome
 */
public class TerrainGenerator {

    private final NoiseRouter noiseRouter;
    private final TerrainShaper terrainShaper;
    private final DensityFunction densityFunction;
    private final BiomeProvider biomeProvider;

    private final int chunkSize;
    private final int chunkHeight;
    private final float seaLevel;

    public TerrainGenerator(long seed, int chunkSize, int chunkHeight, float seaLevel) {
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.seaLevel = seaLevel;

        this.noiseRouter = new NoiseRouter(seed);
        this.terrainShaper = new TerrainShaper(noiseRouter, seaLevel);
        this.densityFunction = new DensityFunction(terrainShaper);
        this.biomeProvider = new BiomeProvider(noiseRouter, terrainShaper, seaLevel);
    }

    /**
     * Generate terrain for a chunk using 3D density.
     */
    public void generateTerrain(int chunkX, int chunkZ, short[] blockData, int[] heightMap, byte[] fluidData) {
        final int baseX = chunkX * chunkSize;
        final int baseZ = chunkZ * chunkSize;

        int airId = Blocks.AIR().getNumericId();
        int stoneId = Blocks.get("game:stone").getNumericId();

        Arrays.fill(blockData, (short) airId);
        Arrays.fill(heightMap, 0);
        if (fluidData != null)
            Arrays.fill(fluidData, (byte) 0);

        // === PASS 1: Generate base terrain using 3D density ===
        // Now includes CAVES automatically via DensityFunction
        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                int wx = baseX + x;
                int wz = baseZ + z;

                int highestSolid = 0;

                for (int y = 0; y < chunkHeight; y++) {
                    boolean solid = densityFunction.isSolid(wx, y, wz);

                    if (solid) {
                        setBlock(blockData, x, y, z, stoneId);
                        highestSolid = y;
                    }
                }

                heightMap[z * chunkSize + x] = highestSolid;
            }
        }

        // === PASS 2: Carve caves ===
        // Removed: DensityFunction now handles Noise Caves (Cheese, Spaghetti, Noodle)
        // carveCaves(chunkX, chunkZ, blockData, heightMap);

        // === PASS 3: Apply surface blocks ===
        applySurface(chunkX, chunkZ, blockData, heightMap);

        // === PASS 4: Fill water ===
        fillWater(chunkX, chunkZ, blockData, heightMap, fluidData);
        // === PASS 5: Bedrock ===
        placeBedrock(chunkX, chunkZ, blockData);
    }

    /**
     * Place bedrock at the bottom of the world.
     */
    private void placeBedrock(int chunkX, int chunkZ, short[] blockData) {
        int bedrockId = Blocks.get("game:bedrock").getNumericId();
        // Fallback to stone if bedrock not found (e.g. before registration complete?)
        if (bedrockId == -1)
            bedrockId = Blocks.get("game:stone").getNumericId();

        int baseX = chunkX * chunkSize;
        int baseZ = chunkZ * chunkSize;

        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                // Layer 0 is always bedrock
                setBlock(blockData, x, 0, z, bedrockId);

                // Layers 1-4 mixed bedrock/stone
                for (int y = 1; y <= 4; y++) {
                    int wx = baseX + x;
                    int wz = baseZ + z;

                    // Simple deterministic noise
                    float noise = hash(wx, y, wz);

                    if (noise < (5 - y) / 5.0f) {
                        setBlock(blockData, x, y, z, bedrockId);
                    }
                }
            }
        }
    }

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0x7fffffff) / (float) 0x7fffffff;
    }

    /**
     * Carve caves using spaghetti noise.
     * 
     * @deprecated Now handled by DensityFunction (Noise Caves)
     */
    private void carveCaves(int chunkX, int chunkZ, short[] blockData, int[] heightMap) {
        /*
         * final int baseX = chunkX * chunkSize;
         * final int baseZ = chunkZ * chunkSize;
         * int airId = Blocks.AIR().getNumericId();
         * 
         * for (int x = 0; x < chunkSize; x++) {
         * for (int z = 0; z < chunkSize; z++) {
         * int wx = baseX + x;
         * int wz = baseZ + z;
         * int surface = heightMap[z * chunkSize + x];
         * 
         * for (int y = 10; y < surface - 4 && y < chunkHeight; y++) {
         * if (densityFunction.isCave(wx, y, wz)) {
         * int current = getBlock(blockData, x, y, z);
         * if (!Blocks.isLiquid(current) && !Blocks.isAir(current)) {
         * setBlock(blockData, x, y, z, airId);
         * }
         * }
         * }
         * }
         * }
         */
    }

    /**
     * Apply surface blocks based on biome.
     */
    private void applySurface(int chunkX, int chunkZ, short[] blockData, int[] heightMap) {
        final int baseX = chunkX * chunkSize;
        final int baseZ = chunkZ * chunkSize;

        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                int wx = baseX + x;
                int wz = baseZ + z;

                Biome biome = biomeProvider.getBiome(wx, wz);

                Block surfaceBlock = biome.getSurfaceBlock();
                Block subsurfaceBlock = biome.getSubsurfaceBlock();

                // Find actual surface (highest non-air)
                int surfaceY = -1;
                for (int y = chunkHeight - 1; y >= 0; y--) {
                    if (!Blocks.isAir(getBlock(blockData, x, y, z))) {
                        surfaceY = y;
                        break;
                    }
                }

                if (surfaceY < 0)
                    continue;

                // Update heightmap
                heightMap[z * chunkSize + x] = surfaceY;

                // Check if underwater
                if (surfaceY < seaLevel - 1) {
                    surfaceBlock = biome.getUnderwaterBlock();
                    subsurfaceBlock = biome.getUnderwaterBlock();
                }

                // Apply surface (top block)
                setBlock(blockData, x, surfaceY, z, surfaceBlock.getNumericId());

                // Apply subsurface (3 blocks below)
                for (int y = surfaceY - 1; y >= surfaceY - 3 && y >= 0; y--) {
                    int current = getBlock(blockData, x, y, z);
                    if (!Blocks.isAir(current) && !Blocks.isLiquid(current)) {
                        setBlock(blockData, x, y, z, subsurfaceBlock.getNumericId());
                    }
                }
            }
        }
    }

    /**
     * Fill water below sea level.
     */
    private void fillWater(int chunkX, int chunkZ, short[] blockData, int[] heightMap, byte[] fluidData) {
        int airId = Blocks.AIR().getNumericId();
        Block waterBlock = Blocks.get("game:water");
        int waterId = waterBlock.getNumericId();
        int maxFluid = waterBlock.getMaxFluidLevel();

        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                for (int y = 0; y <= (int) seaLevel; y++) {
                    if (getBlock(blockData, x, y, z) == airId) {
                        setBlock(blockData, x, y, z, waterId);
                        if (fluidData != null) {
                            fluidData[(y * chunkSize + z) * chunkSize + x] = (byte) maxFluid;
                        }
                    }
                }
            }
        }
    }

    // === BLOCK HELPERS ===

    private int getBlock(short[] data, int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return Blocks.AIR().getNumericId();
        }
        return data[(y * chunkSize + z) * chunkSize + x];
    }

    private void setBlock(short[] data, int x, int y, int z, int blockId) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return;
        }
        data[(y * chunkSize + z) * chunkSize + x] = (short) blockId;
    }

    // === GETTERS ===

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
