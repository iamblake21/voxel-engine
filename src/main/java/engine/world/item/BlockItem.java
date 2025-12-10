package engine.world.item;

import engine.world.block.Block;

/**
 * An item that can place a block in the world.
 * 
 * This is the standard item type for blocks (stone item -> stone block).
 */
public class BlockItem extends Item {

    private final Block blockToPlace;

    /**
     * Create a BlockItem with custom properties
     */
    public BlockItem(Block blockToPlace, ItemProperties properties) {
        super(properties);
        this.blockToPlace = blockToPlace;
    }

    /**
     * Create a BlockItem with default properties
     */
    public BlockItem(Block blockToPlace) {
        this(blockToPlace, ItemProperties.create().defaultItem());
    }

    /**
     * Get the block this item places
     */
    public Block getBlock() {
        return blockToPlace;
    }

    /**
     * Helper method to create a BlockItem from a block with default properties
     */
    public static BlockItem fromBlock(Block block) {
        return new BlockItem(block);
    }

    /**
     * Helper method to create a BlockItem from a block with custom stack size
     */
    public static BlockItem fromBlock(Block block, int maxStackSize) {
        return new BlockItem(block, ItemProperties.create().maxStackSize(maxStackSize));
    }

    @Override
    public String toString() {
        return "BlockItem{" + getRegistryId() + " -> " + blockToPlace.getRegistryId() + "}";
    }
}
