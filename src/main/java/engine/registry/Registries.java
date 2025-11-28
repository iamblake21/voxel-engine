package engine.registry;

import engine.world.block.Block;
import engine.world.biome.Biome;
import engine.entity.EntityType;

import java.util.*;

/**
 * Central container for all game registries.
 * 
 * This is the single source of truth for all registered content.
 * The engine creates registries, games fill them.
 * 
 * Lifecycle:
 * 1. Engine creates registries (static init)
 * 2. Game registers content (registerContent phase)
 * 3. Engine freezes registries (freeze phase)
 * 4. Game runs (registries are read-only)
 */
public final class Registries {
    
    // ==================== BUILT-IN REGISTRIES ====================
    
    /** Registry of all block types */
    public static final Registry<Block> BLOCKS = new Registry<>("engine:blocks");
    
    /** Registry of all biome types */
    public static final Registry<Biome> BIOMES = new Registry<>("engine:biomes");
    
    /** Registry of all entity types */
    public static final Registry<EntityType<?>> ENTITY_TYPES = new Registry<>("engine:entity_types");
    
    // ==================== REGISTRY OF REGISTRIES ====================
    
    private static final Map<ResourceLocation, Registry<?>> ALL_REGISTRIES = new LinkedHashMap<>();
    
    static {
        // Register built-in registries
        registerRegistry(BLOCKS);
        registerRegistry(BIOMES);
        registerRegistry(ENTITY_TYPES);
    }
    
    private Registries() {} // No instantiation
    
    /**
     * Register a custom registry (for mods/extensions)
     */
    public static <T> void registerRegistry(Registry<T> registry) {
        ResourceLocation id = registry.getRegistryId();
        if (ALL_REGISTRIES.containsKey(id)) {
            throw new IllegalArgumentException("Registry already exists: " + id);
        }
        ALL_REGISTRIES.put(id, registry);
        System.out.println("[Registries] Registered registry: " + id);
    }
    
    /**
     * Get a registry by ID
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<Registry<T>> getRegistry(ResourceLocation id) {
        return Optional.ofNullable((Registry<T>) ALL_REGISTRIES.get(id));
    }
    
    public static <T> Optional<Registry<T>> getRegistry(String id) {
        return getRegistry(ResourceLocation.of(id));
    }
    
    /**
     * Get all registered registries
     */
    public static Collection<Registry<?>> getAllRegistries() {
        return Collections.unmodifiableCollection(ALL_REGISTRIES.values());
    }
    
    /**
     * Freeze all registries - call after registration phase
     */
    public static void freezeAll() {
        System.out.println("[Registries] Freezing all registries...");
        for (Registry<?> registry : ALL_REGISTRIES.values()) {
            if (!registry.isFrozen()) {
                registry.freeze();
            }
        }
        System.out.println("[Registries] All registries frozen.");
    }
    
    /**
     * Check if all registries are frozen
     */
    public static boolean allFrozen() {
        for (Registry<?> registry : ALL_REGISTRIES.values()) {
            if (!registry.isFrozen()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Print debug info about all registries
     */
    public static void dumpInfo() {
        System.out.println("=== Registries Info ===");
        for (Registry<?> registry : ALL_REGISTRIES.values()) {
            System.out.println("  " + registry.getRegistryId() + ": " + 
                registry.size() + " entries" + 
                (registry.isFrozen() ? " [FROZEN]" : " [OPEN]"));
        }
        System.out.println("=======================");
    }
}
