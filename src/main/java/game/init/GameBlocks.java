package game.init;

import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.Blocks;
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

    // Nature
    public static Block WOOD;
    public static Block LEAVES;

    // Liquids
    public static Block WATER;

    // Debug
    public static Block LIGHTDEUG;

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
                        .tile(1, 0)));

        // Dirt - under grass
        DIRT = Blocks.register("game:dirt",
                new Block(BlockProperties.create()
                        .standardSolid()
                        .tile(2, 0)));

        // Grass - top of terrain, multi-texture
        GRASS = Blocks.register("game:grass",
                MultiTextureBlock.builder(BlockProperties.create()
                        .standardSolid()
                        .tintGrass())
                        .top(0, 0) // Grass top
                        .bottom(2, 0) // Dirt bottom
                        .side(3, 0) // Grass side
                        .build());

        // Sand - beaches and deserts
        SAND = Blocks.register("game:sand",
                new Block(BlockProperties.create()
                        .standardSolid()
                        .tile(7, 0)));

        // Wood - tree trunks
        WOOD = Blocks.register("game:wood",
                new Block(BlockProperties.create()
                        .standardSolid()
                        .tile(4, 0)));

        // Leaves - tree foliage
        LEAVES = Blocks.register("game:leaves",
                new Block(BlockProperties.create()
                        .transparentSolid()
                        .tintFoliage()
                        .tile(5, 0)));

        // Water - the liquid
        WATER = Blocks.register("game:water",
                new Block(BlockProperties.create()
                        .liquidLike()
                        .tile(6, 0)));
        SNOW = Blocks.register("game:snow",
                new Block(BlockProperties.create()
                        .standardSolid()
                        .tile(0, 1)));
        LIGHTDEUG = Blocks.register("game:light",
                new Block(
                        BlockProperties.create()
                        .standardSolid()
                        .tile(1, 1)
                        .lightLevel(15)
                )
        );

        System.out.println("[GameBlocks] Registered " +
                engine.registry.Registries.BLOCKS.size() + " blocks total");
    }
}
