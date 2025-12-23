package engine.recipe;

import engine.world.item.ItemStack;
import engine.entity.inventory.Inventory;

/**
 * Represents a crafting recipe.
 */
public interface Recipe {

    /**
     * Check if this recipe matches the current crafting inventory.
     * 
     * @param craftingGrid The crafting inventory (usually 3x3)
     * @return true if matches
     */
    boolean matches(Inventory craftingGrid);

    /**
     * Get the result of this recipe.
     * 
     * @param craftingGrid The crafting inventory (in case result depends on inputs)
     * @return The output item stack
     */
    ItemStack getResult(Inventory craftingGrid);

    /**
     * Get the result for display (JEI/NEI style implementations).
     */
    ItemStack getResultItem();

    /**
     * Get the remaining items after crafting (e.g. buckets).
     * By default returns empty for all slots.
     */
    default ItemStack[] getRemainingItems(Inventory craftingGrid) {
        ItemStack[] remaining = new ItemStack[craftingGrid.getSize()];
        for (int i = 0; i < remaining.length; i++) {
            remaining[i] = ItemStack.EMPTY;
        }
        return remaining;
    }
}
