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
        // We need to find if the pattern matches anywhere in the 3x3 grid
        // For simplicity, we assume standard 3x3 grid and check for exact match or
        // smaller sub-match

        // 1. Iterate over all possible starting positions in the 3x3 grid (0,0 to
        // 3-w,3-h)
        for (int startX = 0; startX <= 3 - width; startX++) {
            for (int startY = 0; startY <= 3 - height; startY++) {
                if (checkMatch(craftingGrid, startX, startY, false)) {
                    return true;
                }
                // Optional: Check mirrored? (Not standard MC behavior usually, but helpful)
            }
        }

        return false;
    }

    private boolean checkMatch(Inventory grid, int startX, int startY, boolean mirror) {
        // Check pattern match
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gridX = startX + x;
                int gridY = startY + y;
                int slot = gridY * 3 + gridX;

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
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                // If this slot is part of the pattern, we already checked it
                if (x >= startX && x < startX + width && y >= startY && y < startY + height) {
                    continue;
                }

                // Otherwise it must be empty
                if (!grid.getStack(y * 3 + x).isEmpty()) {
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
