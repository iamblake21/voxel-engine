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
    public void generateTerrain(int chunkX, int chunkZ, int[] blockData, int[] heightMap) {
        final int baseX = chunkX * chunkSize;
        final int baseZ = chunkZ * chunkSize;
        
        int airId = Blocks.AIR().getNumericId();
        int stoneId = Blocks.get("game:stone").getNumericId();
        int waterId = Blocks.get("game:water").getNumericId();
        
        Arrays.fill(blockData, airId);
        Arrays.fill(heightMap, 0);
        
        // === PASS 1: Generate base terrain using 3D density ===
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
        carveCaves(chunkX, chunkZ, blockData, heightMap);
        
        // === PASS 3: Apply surface blocks ===
        applySurface(chunkX, chunkZ, blockData, heightMap);
        
        // === PASS 4: Fill water ===
        fillWater(chunkX, chunkZ, blockData, heightMap);
    }
    
    /**
     * Carve caves using spaghetti noise.
     */
    private void carveCaves(int chunkX, int chunkZ, int[] blockData, int[] heightMap) {
        final int baseX = chunkX * chunkSize;
        final int baseZ = chunkZ * chunkSize;
        int airId = Blocks.AIR().getNumericId();
        
        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                int surface = heightMap[z * chunkSize + x];
                
                for (int y = 10; y < surface - 4 && y < chunkHeight; y++) {
                    if (densityFunction.isCave(wx, y, wz)) {
                        int current = getBlock(blockData, x, y, z);
                        if (!Blocks.isLiquid(current) && !Blocks.isAir(current)) {
                            setBlock(blockData, x, y, z, airId);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Apply surface blocks based on biome.
     */
    private void applySurface(int chunkX, int chunkZ, int[] blockData, int[] heightMap) {
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
                
                if (surfaceY < 0) continue;
                
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
    private void fillWater(int chunkX, int chunkZ, int[] blockData, int[] heightMap) {
        int airId = Blocks.AIR().getNumericId();
        int waterId = Blocks.get("game:water").getNumericId();
        
        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                for (int y = 0; y <= (int) seaLevel; y++) {
                    if (getBlock(blockData, x, y, z) == airId) {
                        setBlock(blockData, x, y, z, waterId);
                    }
                }
            }
        }
    }
    
    // === BLOCK HELPERS ===
    
    private int getBlock(int[] data, int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return Blocks.AIR().getNumericId();
        }
        return data[(y * chunkSize + z) * chunkSize + x];
    }
    
    private void setBlock(int[] data, int x, int y, int z, int blockId) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize) {
            return;
        }
        data[(y * chunkSize + z) * chunkSize + x] = blockId;
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
