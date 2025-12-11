package engine.world.item;

import engine.entity.Player;
import engine.registry.Registries;
import engine.registry.ResourceLocation;
import engine.world.World;

import java.util.Optional;

import engine.entity.Entity;
import engine.entity.Player;
import engine.interaction.InteractionResult;
import engine.world.BlockPos;
import engine.world.World;

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

    // ==================== INTERACTION METHODS ====================

    /**
     * Called when player right-clicks with this item (not on a block).
     * Override for consumables, throwables, etc.
     * 
     * @param world  The world
     * @param player The player using the item
     * @param stack  The item stack being used
     * @return The interaction result
     */
    public InteractionResult onUse(World world, Player player, ItemStack stack) {
        return InteractionResult.PASS;
    }

    /**
     * Called when player right-clicks on a block with this item.
     * Override for items that interact with blocks (hoe, shovel, etc.).
     * 
     * @param world    The world
     * @param player   The player
     * @param stack    The item stack
     * @param blockPos The block that was clicked
     * @param face     The face that was clicked
     * @param placePos The position where a block would be placed
     * @return The interaction result
     */
    public InteractionResult onUseOnBlock(World world, Player player, ItemStack stack,
            BlockPos blockPos, BlockPos.Direction face, BlockPos placePos) {
        return InteractionResult.PASS;
    }

    /**
     * Called when player right-clicks on an entity with this item.
     * Override for items that interact with entities (lead, name tag, etc.).
     * 
     * @param world  The world
     * @param player The player
     * @param stack  The item stack
     * @param target The entity that was clicked
     * @return The interaction result
     */
    public InteractionResult onUseOnEntity(World world, Player player, ItemStack stack, Entity target) {
        return InteractionResult.PASS;
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
