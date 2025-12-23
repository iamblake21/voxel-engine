package game.init;

import engine.loot.*;
import engine.world.block.Block;

/**
 * Configures loot tables for all blocks and entities.
 * 
 * Called AFTER GameBlocks and GameItems are registered.
 */
public final class GameLootTables {

    private GameLootTables() {
    }

    /**
     * Register all loot tables and assign them to blocks.
     * Call this AFTER GameBlocks.register() and GameItems.register().
     */
    public static void register() {
        System.out.println("[GameLootTables] Configuring loot tables...");

        // ==================== TERRAIN ====================

        // Stone drops itself
        setBlockLoot(GameBlocks.STONE, LootTable.singleDrop(GameItems.COBBLESTONE));

        setBlockLoot(GameBlocks.COBBLESTONE, LootTable.singleDrop(GameItems.COBBLESTONE));

        // Dirt drops itself
        setBlockLoot(GameBlocks.DIRT, LootTable.singleDrop(GameItems.DIRT));

        // Grass drops dirt (like Minecraft)
        setBlockLoot(GameBlocks.GRASS, LootTable.singleDrop(GameItems.DIRT));

        // Sand drops itself
        setBlockLoot(GameBlocks.SAND, LootTable.singleDrop(GameItems.SAND));

        // Snow drops itself
        setBlockLoot(GameBlocks.SNOW, LootTable.singleDrop(GameItems.SNOW));

        // ==================== NATURE ====================

        // Wood drops itself
        setBlockLoot(GameBlocks.WOOD, LootTable.singleDrop(GameItems.WOOD));

        // Leaves - chance to drop apple or nothing
        setBlockLoot(GameBlocks.LEAVES, LootTable.builder()
                .pool(LootPool.builder()
                        .mode(LootPool.Mode.ALL)
                        // Small chance for apple
                        .add(LootEntry.builder(GameItems.APPLE).chance(0.05f))
                // TODO: Add sapling when you have one
                // .add(LootEntry.builder(GameItems.OAK_SAPLING).chance(0.05f))
                )
                .build());

        // ==================== DECORATIONS ====================

        // Poppy drops itself
        setBlockLoot(GameBlocks.FLOWERDEBUG, LootTable.singleDrop(GameItems.POPPY));

        setBlockLoot(GameBlocks.PLANKS_OAK, LootTable.singleDrop(GameItems.PLANKS_OAK));
        setBlockLoot(GameBlocks.LOG_OAK, LootTable.singleDrop(GameItems.WOOD));
        setBlockLoot(GameBlocks.GLASS, LootTable.singleDrop(GameItems.GLASS));
        setBlockLoot(GameBlocks.STONE_BRICKS, LootTable.singleDrop(GameItems.STONE_BRICKS));

        // Torch drops itself
        setBlockLoot(GameBlocks.TORCHDEBUG, LootTable.singleDrop(GameItems.TORCH));

        // Light block drops itself
        setBlockLoot(GameBlocks.LIGHTDEUG, LootTable.singleDrop(GameItems.LIGHT_BLOCK));

        // ==================== CONTAINERS ====================

        // Chest drops itself (contents handled separately by block entity)
        setBlockLoot(GameBlocks.CHEST, LootTable.singleDrop(GameItems.CHEST));

        // Furnace drops itself
        setBlockLoot(GameBlocks.FURNACE, LootTable.singleDrop(GameItems.FURNACE));

        setBlockLoot(GameBlocks.DOOR, LootTable.singleDrop(GameItems.DOOR));

        // ==================== LIQUIDS ====================

        // Water doesn't drop anything (can't be mined normally)
        // No loot table needed

        System.out.println("[GameLootTables] Loot tables configured");
    }

    /**
     * Helper to set loot table on a block's properties.
     */
    private static void setBlockLoot(Block block, LootTable loot) {
        if (block != null && block.getProperties() != null) {
            block.getProperties().loot(loot);
        }
    }
}