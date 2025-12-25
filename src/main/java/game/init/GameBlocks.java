package game.init;

import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.Blocks;
import engine.world.item.ToolItem.ToolType;
import game.block.ChestBlock;
import game.block.DoorBlock;
import game.block.FurnaceBlock;
import game.block.CraftingTableBlock;
import engine.world.block.MultiTextureBlock;

/**
 * All blocks for the game.
 * 
 * These are registered during the content registration phase.
 * The order here doesn't matter - blocks are looked up by ID.
 */
public final class GameBlocks {

        // Basic terrain
        public static Block STONE;
        public static Block DIRT;
        public static Block GRASS;
        public static Block SAND;
        public static Block SNOW;
        public static Block COBBLESTONE;

        // Structure
        public static Block STONE_BRICKS;
        public static Block PLANKS_OAK;
        public static Block GLASS;
        public static Block LOG_OAK;

        // Nature
        public static Block WOOD;
        public static Block LEAVES;

        // Liquids
        public static Block WATER;

        // Debug
        public static Block LIGHTDEUG;
        public static Block FLOWERDEBUG;
        public static Block TORCHDEBUG;
        public static Block RED_LAMP;
        public static Block GREEN_LAMP;
        public static Block BEDROCK;
        public static Block BLUE_LAMP;

        // Interactions
        public static Block CHEST;
        public static Block FURNACE;
        public static Block CRAFTING_TABLE;

        // Interactions
        public static Block DOOR;

        private GameBlocks() {
        }

        /**
         * Register all game blocks.
         * Called by game during init, BEFORE registries are frozen.
         */
        public static void register() {
                System.out.println("[GameBlocks] Registering blocks...");

                // Stone - basic underground block
                STONE = Blocks.register("game:stone",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(1.5f)
                                                .requiredTool(ToolType.PICKAXE)
                                                .minTier(0) // Wood or better
                                                .tile(1, 0)));

                // Bedrock - Indestructible bottom layer
                BEDROCK = Blocks.register("game:bedrock",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(-1.0f)
                                                .unbreakable(true)
                                                .textureAll("game:textures/blocks/bedrock.png")));

                CHEST = Blocks.register("game:chest",
                                new ChestBlock(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(2.5f)
                                                .requiredTool(ToolType.AXE) // Chest è di legno!
                                                .minTier(0)
                                                .tile(1, 1)));

                // Stessa cosa per FURNACE
                FURNACE = Blocks.register("game:furnace",
                                new FurnaceBlock(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(3.5f)
                                                .requiredTool(ToolType.PICKAXE)
                                                .minTier(0)
                                                .tile(1, 2), false));

                // Crafting Table: Top(11,3), Bottom(4,0=Planks), Side(12,3)
                // Texture coords are mostly guesses based on loose standard, assuming texture
                // atlas support.
                COBBLESTONE = Blocks.register("game:cobblestone",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(2.5f)
                                                .textureAll("game:textures/blocks/cobblestone.png")
                                                .requiredTool(ToolType.PICKAXE)
                                                .minTier(0)));
                CRAFTING_TABLE = Blocks.register("game:crafting_table", new CraftingTableBlock(BlockProperties.create()
                                .standardSolid()
                                .hardness(2.5f)
                                .requiredTool(ToolType.AXE)
                                .minTier(0)
                                .textureTop("game:textures/blocks/crafting_table_top.png")
                                .textureSide("game:textures/blocks/crafting_table_side.png")
                                .textureBottom("game:textures/blocks/oak_planks.png") // Reuse planks for bottom
                                .tile(11, 3))); // Fallback/Icon tile index
                                                // by CraftingTableBlock constructor if it takes them.
                // Dirt - under grass
                DIRT = Blocks.register("game:dirt",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(0.5f)
                                                .requiredTool(ToolType.SHOVEL)
                                                .tile(2, 0)));

                // Grass - top of terrain, multi-texture
                GRASS = Blocks.register("game:grass",
                                MultiTextureBlock.builder(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(0.6f)
                                                .requiredTool(ToolType.SHOVEL)
                                                .tintGrass())
                                                .top(0, 0) // Grass top
                                                .bottom(2, 0) // Dirt bottom
                                                .side(3, 0) // Grass side
                                                .build());

                // Stone bricks
                STONE_BRICKS = Blocks.register("game:stone_bricks",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(4.0f)
                                                .requiredTool(ToolType.PICKAXE)
                                                .minTier(0)
                                                .tile(0, 4)));

                // Planks oak
                PLANKS_OAK = Blocks.register("game:planks_oak",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(2.0f)
                                                .requiredTool(ToolType.PICKAXE)
                                                .tile(2, 4)));

                // Glass
                GLASS = Blocks.register("game:glass",
                                new Block(BlockProperties.create()
                                                .transparentSolid()
                                                .hardness(0.5f)
                                                .tile(3, 4)));

                // Sand - beaches and deserts
                SAND = Blocks.register("game:sand",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(0.5f)
                                                .requiredTool(ToolType.SHOVEL)
                                                .tile(7, 0)));

                // Wood - tree trunks
                WOOD = Blocks.register("game:log_oak",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(2.0f)
                                                .requiredTool(ToolType.AXE)
                                                .tile(4, 0)));

                // Leaves - tree foliage
                LEAVES = Blocks.register("game:leaves",
                                new Block(BlockProperties.create()
                                                .transparentSolid()
                                                .hardness(0.2f)
                                                .tintFoliage()
                                                .tile(5, 0)));

                // Water - the liquid
                WATER = Blocks.register("game:water",
                                new Block(BlockProperties.create()
                                                .liquidLike()
                                                .hardness(100.0f) // Not minable normally
                                                .tile(6, 0)));
                SNOW = Blocks.register("game:snow",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(0.1f)
                                                .requiredTool(ToolType.SHOVEL)
                                                .tile(0, 1)));
                LIGHTDEUG = Blocks.register("game:light",
                                new Block(
                                                BlockProperties.create()
                                                                .standardSolid()
                                                                .hardness(0.3f)
                                                                .tile(1, 1)
                                                                .lightLevel(15)));
                FLOWERDEBUG = Blocks.register("game:poppy", new Block(
                                BlockProperties.create()
                                                .solid(false)
                                                .opaque(false)
                                                .hardness(0.0f) // Instant
                                                .model("block/poppy")
                                                .tile(0, 2)));
                TORCHDEBUG = Blocks.register("game:torch", new game.block.TorchBlock(
                                BlockProperties.create()
                                                .solid(false)
                                                .opaque(false)
                                                .hardness(0.0f) // Instant
                                                .tile(1, 2)
                                                .lightColor(15, 11, 6))); // Orange/Yellow torch

                RED_LAMP = Blocks.register("game:red_lamp",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(0.3f)
                                                .tile(1, 1) // Uses same texture as light block for now
                                                .lightColor(15, 0, 0)));

                GREEN_LAMP = Blocks.register("game:green_lamp",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(0.3f)
                                                .tile(1, 1)
                                                .lightColor(0, 15, 0)));

                BLUE_LAMP = Blocks.register("game:blue_lamp",
                                new Block(BlockProperties.create()
                                                .standardSolid()
                                                .hardness(0.3f)
                                                .tile(1, 1)
                                                .lightColor(0, 0, 15)));

                DOOR = Blocks.register("game:door", new DoorBlock(
                                BlockProperties.create()
                                                .solid(false)
                                                .opaque(false)
                                                .hardness(1.0f)));

                System.out.println("[GameBlocks] Registered " +
                                engine.registry.Registries.BLOCKS.size() + " blocks total");
        }
}
