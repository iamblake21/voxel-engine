package engine.world.biome;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper class for biome registration and access.
 */
public final class Biomes {
    
    private Biomes() {}
    
    // Cached default biome
    private static Biome DEFAULT_CACHED;
    
    public static Biome DEFAULT() {
        if (DEFAULT_CACHED == null) {
            DEFAULT_CACHED = Registries.BIOMES.getOrDefault("engine:default");
        }
        return DEFAULT_CACHED;
    }
    
    // ==================== REGISTRATION ====================
    
    /**
     * Register a biome
     */
    public static Biome register(String id, Biome biome) {
        ResourceLocation loc = ResourceLocation.of(id);
        RegistryEntry<Biome> entry = Registries.BIOMES.register(loc, biome);
        biome.setRegistryInfo(loc, entry.getNumericId());
        return biome;
    }
    
    public static Biome register(ResourceLocation id, Biome biome) {
        RegistryEntry<Biome> entry = Registries.BIOMES.register(id, biome);
        biome.setRegistryInfo(id, entry.getNumericId());
        return biome;
    }
    
    // ==================== LOOKUP ====================
    
    public static Optional<Biome> tryGet(String id) {
        return Registries.BIOMES.get(id);
    }
    
    public static Biome get(String id) {
        return Registries.BIOMES.getOrDefault(id);
    }
    
    public static Biome get(int numericId) {
        return Registries.BIOMES.getByNumericIdOrDefault(numericId);
    }

    
    
    // ==================== ENGINE INITIALIZATION ====================
    
    /**
     * Register engine's built-in biomes
     */
    public static void registerEngineBiomes() {
        // Default biome - basic grassland
        Biome defaultBiome = new Biome(BiomeProperties.create().plains());
        register(new ResourceLocation("engine", "default"), defaultBiome);
        
        // Set as default
        Registries.BIOMES.setDefault("engine:default");
        DEFAULT_CACHED = defaultBiome;
        
        System.out.println("[Biomes] Engine biomes registered");
    }
}
