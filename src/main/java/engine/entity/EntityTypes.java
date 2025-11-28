package engine.entity;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper for entity type registration and access.
 */
public final class EntityTypes {
    
    private EntityTypes() {}
    
    // ==================== REGISTRATION ====================
    
    public static <T extends Entity> EntityType<T> register(String id, EntityType<T> type) {
        ResourceLocation loc = ResourceLocation.of(id);
        RegistryEntry<EntityType<?>> entry = Registries.ENTITY_TYPES.register(loc, type);
        type.setRegistryInfo(loc, entry.getNumericId());
        return type;
    }
    
    public static <T extends Entity> EntityType<T> register(ResourceLocation id, EntityType<T> type) {
        RegistryEntry<EntityType<?>> entry = Registries.ENTITY_TYPES.register(id, type);
        type.setRegistryInfo(id, entry.getNumericId());
        return type;
    }
    
    // ==================== LOOKUP ====================
    
    @SuppressWarnings("unchecked")
    public static <T extends Entity> Optional<EntityType<T>> tryGet(String id) {
        return Registries.ENTITY_TYPES.get(id).map(t -> (EntityType<T>) t);
    }
    
    public static Optional<EntityType<?>> get(String id) {
        return Registries.ENTITY_TYPES.get(id);
    }
    
    public static EntityType<?> getOrDefault(String id) {
        return Registries.ENTITY_TYPES.getOrDefault(id);
    }
    
    // ==================== ENGINE INIT ====================
    
    public static void registerEngineEntityTypes() {
        // Engine doesn't define any entity types by default
        // Game defines Player, mobs, etc.
        System.out.println("[EntityTypes] Engine entity types registered (none)");
    }
}
