package engine.world.item;

/**
 * A consumable food item with nutrition values.
 * Restores hunger and saturation when consumed.
 */
public class FoodItem extends Item {

    private final int hunger;
    private final float saturation;

    /**
     * Create a food item
     * 
     * @param hunger     Hunger points restored (each half-drumstick is 1 point)
     * @param saturation Saturation value (hidden stat that delays hunger)
     * @param properties Custom item properties
     */
    public FoodItem(int hunger, float saturation, ItemProperties properties) {
        super(properties.consumable());
        this.hunger = hunger;
        this.saturation = saturation;
    }

    /**
     * Create a food item with default properties
     */
    public FoodItem(int hunger, float saturation) {
        this(hunger, saturation, ItemProperties.create().foodItem());
    }

    /**
     * Create a simple food item (saturation = hunger * 0.6)
     */
    public FoodItem(int hunger) {
        this(hunger, hunger * 0.6f);
    }

    // ==================== GETTERS ====================

    /**
     * Get hunger points restored
     */
    public int getHunger() {
        return hunger;
    }

    /**
     * Get saturation value
     */
    public float getSaturation() {
        return saturation;
    }

    // ==================== CONSUMPTION ====================

    /**
     * Consume this food item.
     * In a full implementation, this would:
     * - Restore player hunger
     * - Add saturation
     * - Play eating sound/animation
     * - Shrink the item stack
     * 
     * For now, this is just a placeholder for game logic.
     * 
     * @param stack The item stack being consumed
     * @return true if consumption was successful
     */
    public boolean consume(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != this) {
            return false;
        }

        // In a real implementation:
        // player.addHunger(hunger);
        // player.addSaturation(saturation);
        // stack.shrink(1);
        // playEatingSound();

        return true;
    }

    @Override
    public String toString() {
        return "FoodItem{" + getRegistryId() +
                ", hunger=" + hunger +
                ", saturation=" + saturation + "}";
    }
}
