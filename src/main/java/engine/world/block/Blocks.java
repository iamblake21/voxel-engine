package engine.world.block;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper class for quick block access.
 * Provides convenience methods for common operations.
 * 
 * Usage:
 *   Block stone = Blocks.get("stone");
 *   Block air = Blocks.AIR;
 *   boolean isSolid = Blocks.isSolid(blockId);
 */
public final class Blocks {
    
    private Blocks() {} // No instantiation
    
    // ==================== ENGINE BLOCKS (always present) ====================
    
    /** 
     * Air block - the default, registered by engine.
     * Cached for fast access since it's checked constantly.
     */
    private static Block AIR_CACHED;
    
    public static Block AIR() {
        if (AIR_CACHED == null) {
            AIR_CACHED = Registries.BLOCKS.getOrDefault("engine:air");
        }
        return AIR_CACHED;
    }
    
    // ==================== REGISTRATION ====================
    
    /**
     * Register a block. Call during content registration phase.
     * 
     * @param id Block ID (e.g., "stone", "game:stone")
     * @param block Block instance
     * @return The registered block (same instance)
     */
    public static Block register(String id, Block block) {
        ResourceLocation loc = ResourceLocation.of(id);
        RegistryEntry<Block> entry = Registries.BLOCKS.register(loc, block);
        block.setRegistryInfo(loc, entry.getNumericId());
        return block;
    }
    
    public static Block register(ResourceLocation id, Block block) {
        RegistryEntry<Block> entry = Registries.BLOCKS.register(id, block);
        block.setRegistryInfo(id, entry.getNumericId());
        return block;
    }
    
    // ==================== LOOKUP ====================
    
    /**
     * Get block by ID, or empty if not found
     */
    public static Optional<Block> tryGet(String id) {
        return Registries.BLOCKS.get(id);
    }
    
    /**
     * Get block by ID, or AIR if not found
     */
    public static Block get(String id) {
        return Registries.BLOCKS.getOrDefault(id);
    }
    
    /**
     * Get block by numeric ID, or AIR if not found
     */
    public static Block get(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId);
    }
    
    /**
     * Get numeric ID for a block
     */
    public static int getId(Block block) {
        return block.getNumericId();
    }
    
    // ==================== PROPERTY CHECKS ====================
    // These work with numeric IDs for performance in world operations
    
    public static boolean isSolid(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId).isSolid();
    }
    
    public static boolean isOpaque(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId).isOpaque();
    }
    
    public static boolean isHard(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId).isHard();
    }
    
    public static boolean isTransparent(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId).isTransparent();
    }
    
    public static boolean isLiquid(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId).isLiquid();
    }
    
    public static boolean isAir(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId).isAir();
    }
    
    public static boolean isReplaceable(int numericId) {
        return Registries.BLOCKS.getByNumericIdOrDefault(numericId).isReplaceable();
    }
    
    // ==================== INITIALIZATION ====================
    
    /**
     * Register engine's built-in blocks (called by engine, not game)
     */
    public static void registerEngineBlocks() {
        // AIR is the only engine-provided block
        Block air = new Block(BlockProperties.create().airLike().tile(0, 0));
        register(new ResourceLocation("engine", "air"), air);
        
        // Set AIR as the default
        Registries.BLOCKS.setDefault("engine:air");
        
        // Cache it
        AIR_CACHED = air;
        
        System.out.println("[Blocks] Engine blocks registered");
    }
}
