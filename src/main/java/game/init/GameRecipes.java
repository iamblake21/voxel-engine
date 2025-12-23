package game.init;

import engine.registry.Registries;
import engine.world.item.ItemStack;
import engine.world.item.Item;
import game.recipe.ShapedRecipe;
import java.util.HashMap;
import java.util.Map;

public class GameRecipes {

    public static void register() {
        System.out.println("[GameRecipes] Registering recipes...");

        // Example: Torch
        // X
        // I
        // X = Coal, I = Stick
        // Needs Item references.
        // For demonstration, let's look up Items dynamically or assume simple ones.
        // Let's assume we have COAL and STICK.

        // Since I don't know exact Item registration names for sure without checking,
        // I'll try to use existing Items referenced.
        // BlockItems are easy.

        // Let's register: 1 Log -> 4 Planks (Wait, usually 1 item -> 4 items is
        // shapeless)
        // ShapedRecipe expects a grid.

        // Let's try: 4 Dirt -> 1 Stone (Cheap transmutation for checking)
        // D D
        // D D

        Registries.ITEMS.get("game:dirt").ifPresent(dirt -> {
            Registries.BLOCKS.get("game:stone").ifPresent(stone -> {
                Registries.ITEMS.get(stone.getRegistryId()).ifPresent(stoneItem -> {
                    Map<Character, Item> legend = new HashMap<>();
                    legend.put('D', dirt);

                    Registries.RECIPES.register(new ShapedRecipe(
                            new ItemStack(stoneItem),
                            new String[] {
                                    "DD",
                                    "DD"
                            },
                            legend));
                    System.out.println("Registered Dirt->Stone recipe");
                });
            });
        });
        // WOOD_PICKAXE
        Registries.RECIPES.register(new ShapedRecipe(
                new ItemStack(Registries.ITEMS.get("game:wooden_pickaxe").get()),
                new String[] {
                        "XXX",
                        " I ",
                        " I ",
                },
                Map.of('X', Registries.ITEMS.get("game:planks_oak").get(), 'I',
                        Registries.ITEMS.get("game:stick").get())));

        // WOODEN_AXE
        Registries.RECIPES.register(new ShapedRecipe(
                new ItemStack(Registries.ITEMS.get("game:wooden_axe").get()),
                new String[] {
                        "XX ",
                        "XI ",
                        " I ",
                },
                Map.of('X', Registries.ITEMS.get("game:planks_oak").get(), 'I',
                        Registries.ITEMS.get("game:stick").get())));

        // WOODEN_SHOVEL
        Registries.RECIPES.register(new ShapedRecipe(
                new ItemStack(Registries.ITEMS.get("game:wooden_shovel").get()),
                new String[] {
                        " X ",
                        " I ",
                        " I ",
                },
                Map.of('X', Registries.ITEMS.get("game:planks_oak").get(), 'I',
                        Registries.ITEMS.get("game:stick").get())));

        Registries.RECIPES.register(new ShapedRecipe(
                new ItemStack(Registries.ITEMS.get("game:planks_oak").get(), 4),
                new String[] {
                        "X",
                },
                Map.of('X', Registries.ITEMS.get("game:log_oak").get())));

        Registries.RECIPES.register(new ShapedRecipe(
                new ItemStack(Registries.ITEMS.get("game:stick").get(), 4),
                new String[] {
                        "X",
                        "X",
                },
                Map.of('X', Registries.ITEMS.get("game:planks_oak").get())));
    }

}
