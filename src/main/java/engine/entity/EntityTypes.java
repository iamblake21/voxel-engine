package engine.entity;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper class for entity type registration and lookup.
 * Follows same pattern as Blocks.java and Items.java.
 * 
 * Usage:
 *   // In game's registerContent():
 *   EntityType<NpcEntity> VILLAGER = EntityTypes.register("game:villager",
 *       EntityType.builder(NpcEntity::new)
 *           .size(0.6f, 1.8f)
 *           .properties(EntityProperties.create()
 *               .humanoid()
 *               .texture("textures/entity/villager.png"))
 *           .build());
 */
public final class EntityTypes {
    
    private EntityTypes() {} // No instantiation
    
    // ==================== REGISTRATION ====================
    
    /**
     * Register an entity type. Call during content registration phase.
     * 
     * @param id Entity type ID (e.g., "villager", "game:villager")
     * @param type EntityType instance
     * @return The registered entity type (same instance)
     */
    public static <T extends Entity> EntityType<T> register(String id, EntityType<T> type) {
        ResourceLocation loc = ResourceLocation.of(id);
        @SuppressWarnings("unchecked")
        RegistryEntry<EntityType<?>> entry = Registries.ENTITY_TYPES.register(loc, (EntityType<?>) type);
        type.setRegistryInfo(loc, entry.getNumericId());
        return type;
    }
    
    public static <T extends Entity> EntityType<T> register(ResourceLocation id, EntityType<T> type) {
        @SuppressWarnings("unchecked")
        RegistryEntry<EntityType<?>> entry = Registries.ENTITY_TYPES.register(id, (EntityType<?>) type);
        type.setRegistryInfo(id, entry.getNumericId());
        return type;
    }
    
    // ==================== LOOKUP ====================
    
    /**
     * Get entity type by ID, or empty if not found.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> Optional<EntityType<T>> tryGet(String id) {
        return Registries.ENTITY_TYPES.get(id).map(t -> (EntityType<T>) t);
    }
    
    /**
     * Get entity type by ID, or throw if not found.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityType<T> get(String id) {
        return (EntityType<T>) Registries.ENTITY_TYPES.get(id).orElseThrow(
            () -> new IllegalArgumentException("No entity type registered with ID: " + id));
    }
    
    /**
     * Get entity type by numeric ID.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> Optional<EntityType<T>> get(int numericId) {
        return Registries.ENTITY_TYPES.getByNumericId(numericId).map(t -> (EntityType<T>) t);
    }
    
    /**
     * Get numeric ID for an entity type.
     */
    public static int getId(EntityType<?> type) {
        return type.getNumericId();
    }
    
    // ==================== ENGINE DEFAULTS ====================
    
    /**
     * Register engine's built-in entity types.
     * Called by EngineBootstrap.init().
     */
    public static void registerEngineEntityTypes() {
        // Engine doesn't provide any default entity types
        // All entity types come from the game
        System.out.println("[EntityTypes] Engine entity types registered (none)");
    }
}
