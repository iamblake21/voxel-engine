package engine.world.item;

import engine.registry.Registries;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Represents an item type in the game.
 * 
 * Items are immutable once created - all properties are set via ItemProperties.
 * Each item type is registered once and shared.
 */
public class Item {

    private final ItemProperties properties;

    // Set by registry after registration
    private ResourceLocation registryId;
    private int numericId = -1;

    public Item(ItemProperties properties) {
        this.properties = properties;
    }

    public Item() {
        this(ItemProperties.create());
    }

    // ==================== PROPERTIES ====================

    public int getMaxStackSize() {
        return properties.getMaxStackSize();
    }

    public int getMaxDurability() {
        return properties.getMaxDurability();
    }

    public boolean isStackable() {
        return properties.getMaxStackSize() > 1;
    }

    public boolean isDamageable() {
        return properties.isDamageable();
    }

    public boolean isConsumable() {
        return properties.isConsumable();
    }

    public ItemProperties.Rarity getRarity() {
        return properties.getRarity();
    }

    public ItemProperties getProperties() {
        return properties;
    }

    /**
     * Get the icon texture path for this item
     */
    public String getIconTexture() {
        return properties.getIconTexture();
    }

    // ==================== REGISTRY INFO ====================

    /**
     * Called by registry after registration - do not call manually
     */
    public void setRegistryInfo(ResourceLocation id, int numericId) {
        if (this.registryId != null) {
            throw new IllegalStateException("Item already registered: " + this.registryId);
        }
        this.registryId = id;
        this.numericId = numericId;
    }

    /**
     * Get the registry ID (e.g., "game:stone")
     */
    public ResourceLocation getRegistryId() {
        return registryId;
    }

    /**
     * Get numeric ID for serialization
     */
    public int getNumericId() {
        return numericId;
    }

    /**
     * Check if this item is registered
     */
    public boolean isRegistered() {
        return registryId != null;
    }

    // ==================== STATIC HELPERS ====================

    /**
     * Get an item from the registry by ID
     */
    public static Optional<Item> get(String id) {
        return Registries.ITEMS.get(id);
    }

    /**
     * Get an item or throw if not found
     */
    public static Item getOrThrow(String id) {
        return Registries.ITEMS.get(id).orElseThrow(
                () -> new IllegalArgumentException("No item registered with ID: " + id));
    }

    /**
     * Get an item by numeric ID
     */
    public static Optional<Item> getByNumericId(int id) {
        return Registries.ITEMS.getByNumericId(id);
    }

    @Override
    public String toString() {
        return "Item{" + (registryId != null ? registryId : "unregistered") + "}";
    }
}
