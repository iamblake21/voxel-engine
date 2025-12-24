package game.recipe;

import engine.entity.inventory.Inventory;
import engine.recipe.Recipe;
import engine.world.item.ItemStack;
import engine.world.item.Item;
import engine.registry.Registries;
import java.util.HashMap;
import java.util.Map;

/**
 * 3x3 Shaped Recipe.
 */
public class ShapedRecipe implements Recipe {

    private final ItemStack result;
    private final Map<Character, Item> legend;
    private final String[] pattern;
    private final int width;
    private final int height;

    public ShapedRecipe(ItemStack result, String[] pattern, Map<Character, Item> legend) {
        this.result = result;
        this.pattern = pattern;
        this.legend = legend;
        this.height = pattern.length;
        this.width = pattern[0].length();
    }

    @Override
    public boolean matches(Inventory craftingGrid) {
        // Detect grid size from inventory
        int gridSize = craftingGrid.getSize() == 4 ? 2 : 3; // 2x2 or 3x3

        // Recipe must fit in the grid
        if (width > gridSize || height > gridSize) {
            return false;
        }

        // Iterate over all possible starting positions
        for (int startX = 0; startX <= gridSize - width; startX++) {
            for (int startY = 0; startY <= gridSize - height; startY++) {
                if (checkMatch(craftingGrid, startX, startY, gridSize, false)) {
                    return true;
                }
                // Check mirrored
                if (checkMatch(craftingGrid, startX, startY, gridSize, true)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean checkMatch(Inventory grid, int startX, int startY, int gridSize, boolean mirror) {
        // Check pattern match
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gridX = startX + x;
                int gridY = startY + y;
                int slot = gridY * gridSize + gridX;

                char key = pattern[y].charAt(mirror ? width - 1 - x : x);
                Item expected = legend.get(key);
                ItemStack inSlot = grid.getStack(slot);

                if (expected == null) {
                    // Expect empty
                    if (!inSlot.isEmpty())
                        return false;
                } else {
                    // Expect item
                    if (inSlot.isEmpty() || inSlot.getItem() != expected)
                        return false;
                }
            }
        }

        // Check that all other slots are empty
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                // If this slot is part of the pattern, we already checked it
                if (x >= startX && x < startX + width && y >= startY && y < startY + height) {
                    continue;
                }

                // Otherwise it must be empty
                if (!grid.getStack(y * gridSize + x).isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public ItemStack getResult(Inventory craftingGrid) {
        return result.copy();
    }

    @Override
    public ItemStack getResultItem() {
        return result.copy();
    }
}
