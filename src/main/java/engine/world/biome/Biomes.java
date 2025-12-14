package engine.world.biome;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper class for biome registration and access.
 */
public final class Biomes {

    private Biomes() {
    }

    // Cached default biome
    private static Biome DEFAULT_CACHED;

    public static Biome DEFAULT() {
        if (DEFAULT_CACHED == null) {
            DEFAULT_CACHED = Registries.BIOMES.getOrDefault("engine:default");
        }
        return DEFAULT_CACHED;
    }

    // List of registered biome points for lookup
    public static final java.util.List<BiomeParameterPoint> BIOME_POINTS = new java.util.ArrayList<>();

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

    /**
     * Add a parameter point for an existing biome.
     */
    public static void addPoint(Biome biome, float c, float e, float t, float h, float w) {
        BIOME_POINTS.add(new BiomeParameterPoint(biome, c, e, t, h, w));
    }

    /**
     * Register a biome with a parameter point for generation
     */
    public static Biome register(String id, Biome biome, BiomeParameterPoint point) {
        Biome b = register(id, biome);
        BIOME_POINTS.add(point);
        return b;
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

        // Register default biome as a fallback point (usually near 0,0,0,0,0)
        // so if nothing else matches near 0, we have something.
        BIOME_POINTS.add(new BiomeParameterPoint(defaultBiome, 0, 0, 0, 0, 0));

        // Set as default
        Registries.BIOMES.setDefault("engine:default");
        DEFAULT_CACHED = defaultBiome;

        System.out.println("[Biomes] Engine biomes registered");
    }
}
