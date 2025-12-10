package engine.world.item;

/**
 * Properties for an item type.
 * Use the builder pattern for clean configuration.
 */
public class ItemProperties {

    // Stack properties
    int maxStackSize = 64; // Maximum stack size (1-64)

    // Durability
    int maxDurability = 0; // 0 = not damageable

    // Behavior
    boolean consumable = false; // Can be consumed (food, potions)

    // Rarity/quality
    Rarity rarity = Rarity.COMMON;

    // Icon texture
    String iconTexture = null;

    private ItemProperties() {
    }

    /**
     * Create a new properties builder
     */
    public static ItemProperties create() {
        return new ItemProperties();
    }

    // ==================== BUILDER METHODS ====================

    public ItemProperties maxStackSize(int maxStackSize) {
        this.maxStackSize = Math.max(1, Math.min(64, maxStackSize));
        return this;
    }

    public ItemProperties durability(int maxDurability) {
        this.maxDurability = Math.max(0, maxDurability);
        // Damageable items typically don't stack
        if (maxDurability > 0) {
            this.maxStackSize = 1;
        }
        return this;
    }

    public ItemProperties consumable() {
        this.consumable = true;
        return this;
    }

    public ItemProperties rarity(Rarity rarity) {
        this.rarity = rarity;
        return this;
    }

    /**
     * Set the icon texture path
     */
    public ItemProperties icon(String iconPath) {
        this.iconTexture = iconPath;
        return this;
    }

    // ==================== PRESETS ====================

    /**
     * Preset for standard items
     */
    public ItemProperties defaultItem() {
        this.maxStackSize = 64;
        this.maxDurability = 0;
        this.consumable = false;
        this.rarity = Rarity.COMMON;
        this.iconTexture = null;
        return this;
    }

    /**
     * Preset for tool items
     */
    public ItemProperties toolItem() {
        this.maxStackSize = 1;
        this.consumable = false;
        return this;
    }

    /**
     * Preset for food items
     */
    public ItemProperties foodItem() {
        this.maxStackSize = 16;
        this.consumable = true;
        return this;
    }

    // ==================== GETTERS ====================
    // Getters
    public int getMaxStackSize() {
        return maxStackSize;
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public boolean isConsumable() {
        return consumable;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public String getIconTexture() {
        return iconTexture;
    }

    public boolean isDamageable() {
        return maxDurability > 0;
    }

    /**
     * Item rarity/quality levels
     */
    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC,
        LEGENDARY
    }

    /**
     * Copy properties from another ItemProperties
     */
    public ItemProperties copyFrom(ItemProperties other) {
        this.maxStackSize = other.maxStackSize;
        this.maxDurability = other.maxDurability;
        this.consumable = other.consumable;
        this.rarity = other.rarity;
        return this;
    }
}
