package engine.world.biome;

import engine.registry.Registries;
import engine.registry.ResourceLocation;
import engine.world.block.Block;
import engine.world.block.Blocks;

import java.util.Optional;

/**
 * Represents a biome - defines terrain appearance and features.
 * 
 * Biomes control:
 * - Surface blocks (grass, sand, snow, etc.)
 * - Subsurface blocks (dirt, sandstone, etc.)
 * - Feature generation (trees, cacti, etc.)
 * - Climate (temperature, humidity)
 */
public class Biome {

    private final BiomeProperties properties;

    // Registry info (set after registration)
    private ResourceLocation registryId;
    private int numericId = -1;

    public Biome(BiomeProperties properties) {
        this.properties = properties;
    }

    // ==================== FEATURES ====================

    private final java.util.List<engine.world.gen.WeightedStructure> structures = new java.util.ArrayList<>();

    public void addStructure(engine.world.gen.WeightedStructure structure) {
        structures.add(structure);
    }

    public java.util.List<engine.world.gen.WeightedStructure> getStructures() {
        return structures;
    }

    // ==================== TERRAIN BLOCKS ====================

    /**
     * Get the top surface block for this biome.
     * Returns the actual Block, resolving from registry.
     */
    public Block getSurfaceBlock() {
        return resolveBlock(properties.surfaceBlock, "engine:air");
    }

    /**
     * Get the subsurface block (under the top layer).
     */
    public Block getSubsurfaceBlock() {
        return resolveBlock(properties.subsurfaceBlock, "engine:air");
    }

    /**
     * Get the underwater surface block.
     */
    public Block getUnderwaterBlock() {
        return resolveBlock(properties.underwaterBlock, properties.surfaceBlock);
    }

    /**
     * Get the stone/deep block.
     */
    public Block getStoneBlock() {
        return resolveBlock(properties.stoneBlock, "engine:air");
    }

    /**
     * Get the liquid block for this biome (water, lava).
     */
    public Block getLiquidBlock() {
        return resolveBlock(properties.liquidBlock, "engine:air");
    }

    private Block resolveBlock(String blockId, String fallbackId) {
        Optional<Block> block = Blocks.tryGet(blockId);
        if (block.isPresent()) {
            return block.get();
        }
        // Fallback
        Optional<Block> fallback = Blocks.tryGet(fallbackId);
        if (fallback.isPresent()) {
            return fallback.get();
        }
        // Ultimate fallback: air
        return Blocks.AIR();
    }

    // ==================== CLIMATE ====================

    /**
     * Get temperature (0.0 = cold, 1.0 = hot)
     */
    public float getTemperature() {
        return properties.temperature;
    }

    /**
     * Get humidity (0.0 = dry, 1.0 = wet)
     */
    public float getHumidity() {
        return properties.humidity;
    }

    /**
     * Check if this biome has snow
     */
    public boolean hasSnow() {
        return properties.temperature < 0.15f;
    }

    /**
     * Check if this biome is a desert
     */
    public boolean isDesert() {
        return properties.humidity < 0.2f && properties.temperature > 0.8f;
    }

    // ==================== FEATURES ====================

    /**
     * Get tree density (0.0 = no trees, 1.0 = dense forest)
     */
    public float getTreeDensity() {
        return properties.treeDensity;
    }

    /**
     * Get grass/vegetation density
     */
    public float getGrassDensity() {
        return properties.grassDensity;
    }

    // ==================== TERRAIN SHAPE ====================

    /**
     * Get base terrain height offset
     */
    public float getBaseHeight() {
        return properties.baseHeight;
    }

    /**
     * Get terrain height variation
     */
    public float getHeightVariation() {
        return properties.heightVariation;
    }

    // ==================== COLORS ====================

    /**
     * Get grass color tint (RGB as int)
     */
    public int getGrassColor() {
        return properties.grassColor;
    }

    /**
     * Get foliage color tint
     */
    public int getFoliageColor() {
        return properties.foliageColor;
    }

    /**
     * Get water color tint
     */
    public int getWaterColor() {
        return properties.waterColor;
    }

    // ==================== REGISTRY ====================

    public void setRegistryInfo(ResourceLocation id, int numericId) {
        if (this.registryId != null) {
            throw new IllegalStateException("Biome already registered: " + this.registryId);
        }
        this.registryId = id;
        this.numericId = numericId;
    }

    public ResourceLocation getRegistryId() {
        return registryId;
    }

    public int getNumericId() {
        return numericId;
    }

    public BiomeProperties getProperties() {
        return properties;
    }

    // ==================== STATIC HELPERS ====================

    public static Optional<Biome> get(String id) {
        return Registries.BIOMES.get(id);
    }

    public static Biome getOrDefault(String id) {
        return Registries.BIOMES.getOrDefault(id);
    }

    @Override
    public String toString() {
        return "Biome{" + (registryId != null ? registryId : "unregistered") + "}";
    }
}
