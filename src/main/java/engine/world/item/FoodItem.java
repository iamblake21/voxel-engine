package engine.world.item;

import engine.entity.Player;
import engine.world.World;

/**
 * A consumable food item with nutrition values.
 * Restores hunger and saturation when consumed.
 */
public class FoodItem extends Item implements IUsableItem {

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
     * Use (consume) this food item
     */
    @Override
    public boolean use(World world, Player player, ItemStack stack,
            float hitX, float hitY, float hitZ,
            int lastAirX, int lastAirY, int lastAirZ) {
        if (stack.isEmpty() || stack.getItem() != this) {
            return false;
        }

        // In a real implementation:
        // player.addHunger(hunger);
        // player.addSaturation(saturation);
        // playEatingSound();

        System.out.println("[FoodItem] Player consumed " + getRegistryId() +
                " (+" + hunger + " hunger, +" + saturation + " saturation)");

        return true; // Successfully consumed
    }

    /**
     * Consume this food item.
     * 
     * @deprecated Use use() method instead via IUsableItem interface
     */
    @Deprecated
    public boolean consume(ItemStack stack) {
        return use(null, null, stack, Float.NaN, Float.NaN, Float.NaN, 0, 0, 0);
    }

    @Override
    public String toString() {
        return "FoodItem{" + getRegistryId() +
                ", hunger=" + hunger +
                ", saturation=" + saturation + "}";
    }
}
