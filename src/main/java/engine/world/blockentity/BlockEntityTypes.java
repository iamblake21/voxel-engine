package engine.world.blockentity;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper for block entity type registration and lookup.
 * 
 * Usage:
 *   // In game's registerContent():
 *   BlockEntityType<ChestBlockEntity> CHEST = BlockEntityTypes.register("game:chest",
 *       BlockEntityType.builder(ChestBlockEntity::new)
 *           .validBlocks(GameBlocks.CHEST)
 *           .build());
 */
public final class BlockEntityTypes {
    
    private BlockEntityTypes() {} // No instantiation
    
    // ==================== REGISTRATION ====================
    
    /**
     * Register a block entity type.
     */
    public static <T extends BlockEntity> BlockEntityType<T> register(String id, BlockEntityType<T> type) {
        ResourceLocation loc = ResourceLocation.of(id);
        @SuppressWarnings("unchecked")
        RegistryEntry<BlockEntityType<?>> entry = Registries.BLOCK_ENTITY_TYPES.register(loc, (BlockEntityType<?>) type);
        type.setRegistryInfo(loc, entry.getNumericId());
        return type;
    }
    
    public static <T extends BlockEntity> BlockEntityType<T> register(ResourceLocation id, BlockEntityType<T> type) {
        @SuppressWarnings("unchecked")
        RegistryEntry<BlockEntityType<?>> entry = Registries.BLOCK_ENTITY_TYPES.register(id, (BlockEntityType<?>) type);
        type.setRegistryInfo(id, entry.getNumericId());
        return type;
    }
    
    // ==================== LOOKUP ====================
    
    /**
     * Get block entity type by ID.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> Optional<BlockEntityType<T>> get(String id) {
        return Registries.BLOCK_ENTITY_TYPES.get(id).map(t -> (BlockEntityType<T>) t);
    }
    
    /**
     * Get block entity type by numeric ID.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> Optional<BlockEntityType<T>> get(int numericId) {
        return Registries.BLOCK_ENTITY_TYPES.getByNumericId(numericId).map(t -> (BlockEntityType<T>) t);
    }
    
    // ==================== ENGINE INIT ====================
    
    /**
     * Register engine's built-in block entity types.
     * Called by EngineBootstrap.
     */
    public static void registerEngineBlockEntityTypes() {
        // Engine doesn't provide any default block entity types
        System.out.println("[BlockEntityTypes] Engine block entity types registered (none)");
    }
}
