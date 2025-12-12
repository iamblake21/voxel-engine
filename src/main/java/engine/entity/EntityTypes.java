package engine.entity;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper class for entity type registration and lookup.
 */
public final class EntityTypes {
    
    private EntityTypes() {}
    
    // ==================== BUILT-IN TYPES ====================
    
    public static EntityType<ItemEntity> ITEM;
    
    // ==================== REGISTRATION ====================
    
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
    
    @SuppressWarnings("unchecked")
    public static <T extends Entity> Optional<EntityType<T>> tryGet(String id) {
        return Registries.ENTITY_TYPES.get(id).map(t -> (EntityType<T>) t);
    }
    
    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityType<T> get(String id) {
        return (EntityType<T>) Registries.ENTITY_TYPES.get(id).orElseThrow(
            () -> new IllegalArgumentException("No entity type registered with ID: " + id));
    }
    
    @SuppressWarnings("unchecked")
    public static <T extends Entity> Optional<EntityType<T>> get(int numericId) {
        return Registries.ENTITY_TYPES.getByNumericId(numericId).map(t -> (EntityType<T>) t);
    }
    
    public static int getId(EntityType<?> type) {
        return type.getNumericId();
    }
    
    // ==================== ENGINE DEFAULTS ====================
    
    /**
     * Register engine's built-in entity types.
     * Called by EngineBootstrap.init().
     */
    public static void registerEngineEntityTypes() {
        ITEM = register("engine:item", 
            EntityType.<ItemEntity>builder(ItemEntity::new)
                .size(0.25f, 0.25f)
                .persistent(false)
                .summonable(true)
                .build());
        
        System.out.println("[EntityTypes] Engine entity types registered (item)");
    }
}