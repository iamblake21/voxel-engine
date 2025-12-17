package game.init;

import engine.world.block.Block;
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
        public static Item RED_LAMP;
        public static Item GREEN_LAMP;
        public static Item BLUE_LAMP;
        public static Item PLANKS_OAK;
        public static Item GLASS;
        public static Item STONE_BRICKS;

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

        public static Item FURNACE;
        public static Item CHEST;
        public static Item DOOR;

        public static Item WATER_BLOCK;

        private GameItems() {
        }

        /**
         * Register all game items.
         * Called by game during init, BEFORE registries are frozen.
         */
        public static void register() {
                System.out.println("[GameItems] Registering items...");

                // ==================== BLOCK ITEMS ====================
                // Block items automatically use their block's texture from the atlas
                WATER_BLOCK = Items.register("game:water_block",
                                new BlockItem(GameBlocks.WATER, ItemProperties.create()));

                DOOR = Items.register("game:door",
                                new BlockItem(GameBlocks.DOOR,
                                                ItemProperties.create().icon("textures/items/door_wood.png")));

                TORCH = Items.register("game:torch",
                                new BlockItem(GameBlocks.TORCHDEBUG,
                                                ItemProperties.create().icon("textures/items/torch.png")));
                STONE = Items.register("game:stone",
                                new BlockItem(GameBlocks.STONE));

                DIRT = Items.register("game:dirt",
                                new BlockItem(GameBlocks.DIRT));
                FURNACE = Items.register("game:furnace",
                                new BlockItem(GameBlocks.FURNACE));
                CHEST = Items.register("game:chest",
                                new BlockItem(GameBlocks.CHEST));

                GRASS = Items.register("game:grass",
                                new BlockItem(GameBlocks.GRASS));

                SAND = Items.register("game:sand",
                                new BlockItem(GameBlocks.SAND));

                SNOW = Items.register("game:snow",
                                new BlockItem(GameBlocks.SNOW));
                WOOD = Items.register("game:log_oak",
                                new BlockItem(GameBlocks.WOOD));

                PLANKS_OAK = Items.register("game:planks_oak",
                                new BlockItem(GameBlocks.PLANKS_OAK));
                STONE_BRICKS = Items.register("game:stone_bricks",
                                new BlockItem(GameBlocks.STONE_BRICKS));

                LEAVES = Items.register("game:leaves",
                                new BlockItem(GameBlocks.LEAVES));

                LIGHT_BLOCK = Items.register("game:light",
                                new BlockItem(GameBlocks.LIGHTDEUG));

                POPPY = Items.register("game:poppy",
                                new BlockItem(GameBlocks.FLOWERDEBUG));

                RED_LAMP = Items.register("game:red_lamp",
                                new BlockItem(GameBlocks.RED_LAMP));
                GREEN_LAMP = Items.register("game:green_lamp",
                                new BlockItem(GameBlocks.GREEN_LAMP));
                BLUE_LAMP = Items.register("game:blue_lamp",
                                new BlockItem(GameBlocks.BLUE_LAMP));

                // Water bucket placeholder (not a direct block item)
                WATER_BUCKET = Items.register("game:water_bucket",
                                new Item(ItemProperties.create().maxStackSize(1)
                                                .icon("textures/items/water_bucket.png")));

                // ==================== WOODEN TOOLS ====================

                WOODEN_PICKAXE = (ToolItem) Items.register("game:wooden_pickaxe",
                                new ToolItem(ToolType.PICKAXE, ToolTier.WOOD,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/wooden_pickaxe.png")));

                WOODEN_AXE = (ToolItem) Items.register("game:wooden_axe",
                                new ToolItem(ToolType.AXE, ToolTier.WOOD,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/wooden_axe.png")));

                WOODEN_SHOVEL = (ToolItem) Items.register("game:wooden_shovel",
                                new ToolItem(ToolType.SHOVEL, ToolTier.WOOD,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/wooden_shovel.png")));

                WOODEN_SWORD = (ToolItem) Items.register("game:wooden_sword",
                                new ToolItem(ToolType.SWORD, ToolTier.WOOD,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/wooden_sword.png")));

                // ==================== STONE TOOLS ====================

                STONE_PICKAXE = (ToolItem) Items.register("game:stone_pickaxe",
                                new ToolItem(ToolType.PICKAXE, ToolTier.STONE,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/stone_pickaxe.png")));

                STONE_AXE = (ToolItem) Items.register("game:stone_axe",
                                new ToolItem(ToolType.AXE, ToolTier.STONE,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/stone_axe.png")));

                STONE_SHOVEL = (ToolItem) Items.register("game:stone_shovel",
                                new ToolItem(ToolType.SHOVEL, ToolTier.STONE,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/stone_shovel.png")));

                STONE_SWORD = (ToolItem) Items.register("game:stone_sword",
                                new ToolItem(ToolType.SWORD, ToolTier.STONE,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/stone_sword.png")));

                // ==================== IRON TOOLS ====================

                IRON_PICKAXE = (ToolItem) Items.register("game:iron_pickaxe",
                                new ToolItem(ToolType.PICKAXE, ToolTier.IRON,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/iron_pickaxe.png")));

                IRON_AXE = (ToolItem) Items.register("game:iron_axe",
                                new ToolItem(ToolType.AXE, ToolTier.IRON,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/iron_axe.png")));

                IRON_SHOVEL = (ToolItem) Items.register("game:iron_shovel",
                                new ToolItem(ToolType.SHOVEL, ToolTier.IRON,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/iron_shovel.png")));

                IRON_SWORD = (ToolItem) Items.register("game:iron_sword",
                                new ToolItem(ToolType.SWORD, ToolTier.IRON,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/iron_sword.png")));

                // ==================== DIAMOND TOOLS ====================

                DIAMOND_PICKAXE = (ToolItem) Items.register("game:diamond_pickaxe",
                                new ToolItem(ToolType.PICKAXE, ToolTier.DIAMOND,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/diamond_pickaxe.png")));

                DIAMOND_AXE = (ToolItem) Items.register("game:diamond_axe",
                                new ToolItem(ToolType.AXE, ToolTier.DIAMOND,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/diamond_axe.png")));

                DIAMOND_SHOVEL = (ToolItem) Items.register("game:diamond_shovel",
                                new ToolItem(ToolType.SHOVEL, ToolTier.DIAMOND,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/diamond_shovel.png")));

                DIAMOND_SWORD = (ToolItem) Items.register("game:diamond_sword",
                                new ToolItem(ToolType.SWORD, ToolTier.DIAMOND,
                                                ItemProperties.create().toolItem()
                                                                .icon("textures/items/diamond_sword.png")));

                // ==================== FOOD ITEMS ====================

                APPLE = (FoodItem) Items.register("game:apple",
                                new FoodItem(4, 2.4f,
                                                ItemProperties.create().foodItem().icon("textures/items/apple.png")));

                BREAD = (FoodItem) Items.register("game:bread",
                                new FoodItem(5, 6.0f,
                                                ItemProperties.create().foodItem().icon("textures/items/bread.png")));

                COOKED_MEAT = (FoodItem) Items.register("game:cooked_meat",
                                new FoodItem(8, 12.8f,
                                                ItemProperties.create().foodItem()
                                                                .icon("textures/items/cooked_meat.png")));

                RAW_MEAT = (FoodItem) Items.register("game:raw_meat",
                                new FoodItem(3, 1.8f,
                                                ItemProperties.create().foodItem()
                                                                .icon("textures/items/raw_meat.png")));

                System.out.println("[GameItems] Registered " +
                                engine.registry.Registries.ITEMS.size() + " items total");
        }
}
