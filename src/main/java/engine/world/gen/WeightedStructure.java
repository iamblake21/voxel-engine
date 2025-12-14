package engine.world.gen;

import engine.registry.ResourceLocation;

/**
 * A reference to a structure with generation weights and rules.
 * Used in Biomes to define what can spawn.
 */
public class WeightedStructure {

    private final ResourceLocation structureId;
    private final int weight;

    // Placement rules
    private final float density; // Chance to spawn per attempt (0.0 - 1.0)

    // If we want detailed placement rules (surface, underground, air)
    // we can add an enum Type { SURFACE, UNDERGROUND, AIR }

    public WeightedStructure(ResourceLocation structureId, int weight, float density) {
        this.structureId = structureId;
        this.weight = weight;
        this.density = density;
    }

    public WeightedStructure(String structureId, int weight, float density) {
        this(ResourceLocation.of(structureId), weight, density);
    }

    public ResourceLocation getStructureId() {
        return structureId;
    }

    public int getWeight() {
        return weight;
    }

    public float getDensity() {
        return density;
    }
}
