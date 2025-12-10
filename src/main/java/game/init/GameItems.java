package game.init;

import engine.world.item.*;
import engine.world.item.ToolItem.ToolTier;
import engine.world.item.ToolItem.ToolType;

/**
 * All items for the game.
 * 
 * These are registered during the content registration phase.
 * The order here doesn't matter - items are looked up by ID.
 */
public final class GameItems {

    // Block items - correspond to blocks
    public static Item STONE;
    public static Item DIRT;
    public static Item GRASS;
    public static Item SAND;
    public static Item SNOW;
    public static Item WOOD;
    public static Item LEAVES;
    public static Item WATER_BUCKET; // Water as item (bucket)
    public static Item LIGHT_BLOCK;
    public static Item POPPY;
    public static Item TORCH;

    // Tools - Wooden
    public static ToolItem WOODEN_PICKAXE;
    public static ToolItem WOODEN_AXE;
    public static ToolItem WOODEN_SHOVEL;
    public static ToolItem WOODEN_SWORD;

    // Tools - Stone
    public static ToolItem STONE_PICKAXE;
    public static ToolItem STONE_AXE;
    public static ToolItem STONE_SHOVEL;
    public static ToolItem STONE_SWORD;

    // Tools - Iron
    public static ToolItem IRON_PICKAXE;
    public static ToolItem IRON_AXE;
    public static ToolItem IRON_SHOVEL;
    public static ToolItem IRON_SWORD;

    // Tools - Diamond
    public static ToolItem DIAMOND_PICKAXE;
    public static ToolItem DIAMOND_AXE;
    public static ToolItem DIAMOND_SHOVEL;
    public static ToolItem DIAMOND_SWORD;

    // Food items
    public static FoodItem APPLE;
    public static FoodItem BREAD;
    public static FoodItem COOKED_MEAT;
    public static FoodItem RAW_MEAT;

    private GameItems() {
    }

    /**
     * Register all game items.
     * Called by game during init, BEFORE registries are frozen.
     */
    public static void register() {
        System.out.println("[GameItems] Registering items...");

        // ==================== BLOCK ITEMS ====================

        STONE = Items.register("game:stone",
                new BlockItem(GameBlocks.STONE));

        DIRT = Items.register("game:dirt",
                new BlockItem(GameBlocks.DIRT));

        GRASS = Items.register("game:grass",
                new BlockItem(GameBlocks.GRASS));

        SAND = Items.register("game:sand",
                new BlockItem(GameBlocks.SAND));

        SNOW = Items.register("game:snow",
                new BlockItem(GameBlocks.SNOW));

        WOOD = Items.register("game:wood",
                new BlockItem(GameBlocks.WOOD));

        LEAVES = Items.register("game:leaves",
                new BlockItem(GameBlocks.LEAVES));

        LIGHT_BLOCK = Items.register("game:light",
                new BlockItem(GameBlocks.LIGHTDEUG));

        POPPY = Items.register("game:poppy",
                new BlockItem(GameBlocks.FLOWERDEBUG));

        TORCH = Items.register("game:torch",
                new BlockItem(GameBlocks.TORCHDEBUG));

        // Water bucket placeholder (not a direct block item)
        WATER_BUCKET = Items.register("game:water_bucket",
                new Item(ItemProperties.create().maxStackSize(1)));

        // ==================== WOODEN TOOLS ====================

        WOODEN_PICKAXE = (ToolItem) Items.register("game:wooden_pickaxe",
                new ToolItem(ToolType.PICKAXE, ToolTier.WOOD));

        WOODEN_AXE = (ToolItem) Items.register("game:wooden_axe",
                new ToolItem(ToolType.AXE, ToolTier.WOOD));

        WOODEN_SHOVEL = (ToolItem) Items.register("game:wooden_shovel",
                new ToolItem(ToolType.SHOVEL, ToolTier.WOOD));

        WOODEN_SWORD = (ToolItem) Items.register("game:wooden_sword",
                new ToolItem(ToolType.SWORD, ToolTier.WOOD));

        // ==================== STONE TOOLS ====================

        STONE_PICKAXE = (ToolItem) Items.register("game:stone_pickaxe",
                new ToolItem(ToolType.PICKAXE, ToolTier.STONE));

        STONE_AXE = (ToolItem) Items.register("game:stone_axe",
                new ToolItem(ToolType.AXE, ToolTier.STONE));

        STONE_SHOVEL = (ToolItem) Items.register("game:stone_shovel",
                new ToolItem(ToolType.SHOVEL, ToolTier.STONE));

        STONE_SWORD = (ToolItem) Items.register("game:stone_sword",
                new ToolItem(ToolType.SWORD, ToolTier.STONE));

        // ==================== IRON TOOLS ====================

        IRON_PICKAXE = (ToolItem) Items.register("game:iron_pickaxe",
                new ToolItem(ToolType.PICKAXE, ToolTier.IRON));

        IRON_AXE = (ToolItem) Items.register("game:iron_axe",
                new ToolItem(ToolType.AXE, ToolTier.IRON));

        IRON_SHOVEL = (ToolItem) Items.register("game:iron_shovel",
                new ToolItem(ToolType.SHOVEL, ToolTier.IRON));

        IRON_SWORD = (ToolItem) Items.register("game:iron_sword",
                new ToolItem(ToolType.SWORD, ToolTier.IRON));

        // ==================== DIAMOND TOOLS ====================

        DIAMOND_PICKAXE = (ToolItem) Items.register("game:diamond_pickaxe",
                new ToolItem(ToolType.PICKAXE, ToolTier.DIAMOND));

        DIAMOND_AXE = (ToolItem) Items.register("game:diamond_axe",
                new ToolItem(ToolType.AXE, ToolTier.DIAMOND));

        DIAMOND_SHOVEL = (ToolItem) Items.register("game:diamond_shovel",
                new ToolItem(ToolType.SHOVEL, ToolTier.DIAMOND));

        DIAMOND_SWORD = (ToolItem) Items.register("game:diamond_sword",
                new ToolItem(ToolType.SWORD, ToolTier.DIAMOND));

        // ==================== FOOD ITEMS ====================

        APPLE = (FoodItem) Items.register("game:apple",
                new FoodItem(4, 2.4f));

        BREAD = (FoodItem) Items.register("game:bread",
                new FoodItem(5, 6.0f));

        COOKED_MEAT = (FoodItem) Items.register("game:cooked_meat",
                new FoodItem(8, 12.8f));

        RAW_MEAT = (FoodItem) Items.register("game:raw_meat",
                new FoodItem(3, 1.8f));

        System.out.println("[GameItems] Registered " +
                engine.registry.Registries.ITEMS.size() + " items total");
    }
}
