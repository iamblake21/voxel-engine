package engine.loot;

import engine.registry.Registries;
import engine.registry.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry and lookup for loot tables.
 */
public final class LootTables {
    
    private static final Map<ResourceLocation, LootTable> tables = new HashMap<>();
    
    private LootTables() {}
    
    /**
     * Register a loot table.
     */
    public static LootTable register(String id, LootTable table) {
        ResourceLocation loc = ResourceLocation.of(id);
        table.setRegistryId(loc);
        tables.put(loc, table);
        return table;
    }
    
    public static LootTable register(ResourceLocation id, LootTable table) {
        table.setRegistryId(id);
        tables.put(id, table);
        return table;
    }
    
    /**
     * Get a loot table by ID.
     */
    public static Optional<LootTable> get(String id) {
        return Optional.ofNullable(tables.get(ResourceLocation.of(id)));
    }
    
    public static Optional<LootTable> get(ResourceLocation id) {
        return Optional.ofNullable(tables.get(id));
    }
    
    /**
     * Get or return empty table.
     */
    public static LootTable getOrEmpty(String id) {
        return get(id).orElse(LootTable.EMPTY);
    }
    
    public static LootTable getOrEmpty(ResourceLocation id) {
        return get(id).orElse(LootTable.EMPTY);
    }
}