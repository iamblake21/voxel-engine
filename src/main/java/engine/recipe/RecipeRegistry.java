package engine.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import engine.entity.inventory.Inventory;
import engine.world.item.ItemStack;

/**
 * Registry for all crafting recipes.
 */
public class RecipeRegistry {

    private final List<Recipe> recipes = new ArrayList<>();

    public void register(Recipe recipe) {
        recipes.add(recipe);
    }

    /**
     * Find a matching recipe for the given crafting grid.
     */
    public Optional<Recipe> getRecipe(Inventory craftingGrid) {
        // Iterate relevant recipes
        // Optimization: Could filter by recipe type or size later
        for (Recipe recipe : recipes) {
            if (recipe.matches(craftingGrid)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    /**
     * Get the crafting result for a grid.
     * Returns ItemStack.EMPTY if no match.
     */
    public ItemStack getCraftingResult(Inventory craftingGrid) {
        return getRecipe(craftingGrid)
                .map(recipe -> recipe.getResult(craftingGrid))
                .orElse(ItemStack.EMPTY);
    }

    public List<Recipe> getAllRecipes() {
        return new ArrayList<>(recipes);
    }
}
