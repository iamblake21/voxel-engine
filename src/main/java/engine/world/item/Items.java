package engine.world.item;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Static helper class for quick item access.
 * Provides convenience methods for common operations.
 * 
 * Usage:
 * Item stone = Items.get("stone");
 * Item apple = Items.APPLE;
 */
public final class Items {

    private Items() {
    } // No instantiation

    // ==================== REGISTRATION ====================

    /**
     * Register an item. Call during content registration phase.
     * 
     * @param id   Item ID (e.g., "stone", "game:stone")
     * @param item Item instance
     * @return The registered item (same instance)
     */
    public static Item register(String id, Item item) {
        ResourceLocation loc = ResourceLocation.of(id);
        RegistryEntry<Item> entry = Registries.ITEMS.register(loc, item);
        item.setRegistryInfo(loc, entry.getNumericId());
        return item;
    }

    public static Item register(ResourceLocation id, Item item) {
        RegistryEntry<Item> entry = Registries.ITEMS.register(id, item);
        item.setRegistryInfo(id, entry.getNumericId());
        return item;
    }

    // ==================== LOOKUP ====================

    /**
     * Get item by ID, or empty if not found
     */
    public static Optional<Item> tryGet(String id) {
        return Registries.ITEMS.get(id);
    }

    /**
     * Get item by ID, or throw if not found
     */
    public static Item get(String id) {
        return Registries.ITEMS.get(id).orElseThrow(
                () -> new IllegalArgumentException("No item registered with ID: " + id));
    }

    /**
     * Get item by numeric ID, or empty if not found
     */
    public static Optional<Item> get(int numericId) {
        return Registries.ITEMS.getByNumericId(numericId);
    }

    /**
     * Get numeric ID for an item
     */
    public static int getId(Item item) {
        return item.getNumericId();
    }
}
