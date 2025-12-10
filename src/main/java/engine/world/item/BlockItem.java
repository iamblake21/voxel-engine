package engine.world.item;

import engine.entity.Player;
import engine.world.World;
import engine.world.block.Block;

/**
 * An item that can place a block in the world.
 * 
 * This is the standard item type for blocks (stone item -> stone block).
 */
public class BlockItem extends Item implements IUsableItem {

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
     * Use this block item to place a block in the world
     */
    @Override
    public boolean use(World world, Player player, ItemStack stack,
            float hitX, float hitY, float hitZ,
            int lastAirX, int lastAirY, int lastAirZ) {
        // Check if we hit a block
        if (Float.isNaN(hitX)) {
            return false; // No block hit, can't place
        }

        // Place block at last air position (the face we clicked on)
        int placeX = lastAirX;
        int placeY = lastAirY;
        int placeZ = lastAirZ;

        // Check if position is valid (not inside player)
        if (wouldCollideWithPlayer(player, placeX, placeY, placeZ)) {
            return false;
        }

        // Place the block
        world.setBlock(placeX, placeY, placeZ, blockToPlace.getNumericId());

        // If it's a fluid, set it to max level
        if (blockToPlace.isLiquid()) {
            world.setFluidLevel(placeX, placeY, placeZ, blockToPlace.getMaxFluidLevel());
        }

        return true; // Successfully placed
    }

    /**
     * Check if placing a block at this position would collide with the player
     */
    private boolean wouldCollideWithPlayer(Player player, int bx, int by, int bz) {
        // Get player bounding box
        float px = player.getX();
        float py = player.getY();
        float pz = player.getZ();
        float hw = 0.3f; // Half width
        float hh = 1.8f; // Height

        // Check if block overlaps with player
        float minX = px - hw;
        float maxX = px + hw;
        float minY = py;
        float maxY = py + hh;
        float minZ = pz - hw;
        float maxZ = pz + hw;

        // Block bounds
        float blockMinX = bx;
        float blockMaxX = bx + 1;
        float blockMinY = by;
        float blockMaxY = by + 1;
        float blockMinZ = bz;
        float blockMaxZ = bz + 1;

        // AABB collision test
        return !(maxX < blockMinX || minX > blockMaxX ||
                maxY < blockMinY || minY > blockMaxY ||
                maxZ < blockMinZ || minZ > blockMaxZ);
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
