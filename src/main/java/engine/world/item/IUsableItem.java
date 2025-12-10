package engine.world.item;

import engine.entity.Player;
import engine.world.World;

/**
 * Interface for items that can be "used" (right-click action).
 * 
 * Examples:
 * - BlockItem: Places a block in the world
 * - FoodItem: Consumes the food
 * - ToolItem: May have special abilities
 */
public interface IUsableItem {

    /**
     * Use this item.
     * Called when the player right-clicks with this item.
     * 
     * @param world    World reference
     * @param player   Player using the item
     * @param stack    The ItemStack being used
     * @param hitX     Block X position that was hit (NaN if no block hit)
     * @param hitY     Block Y position that was hit (NaN if no block hit)
     * @param hitZ     Block Z position that was hit (NaN if no block hit)
     * @param lastAirX Last air block X before hit (for placement)
     * @param lastAirY Last air block Y before hit (for placement)
     * @param lastAirZ Last air block Z before hit (for placement)
     * @return true if the item was successfully used (will shrink stack)
     */
    boolean use(World world, Player player, ItemStack stack,
            float hitX, float hitY, float hitZ,
            int lastAirX, int lastAirY, int lastAirZ);
}
