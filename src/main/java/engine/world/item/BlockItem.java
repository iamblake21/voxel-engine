package engine.world.item;

import engine.entity.Player;
import engine.interaction.InteractionResult;
import engine.world.BlockPos;
import engine.world.World;
import engine.world.block.Block;

/**
 * An item that can place a block in the world.
 * 
 * Updated to use the new interaction system.
 */
public class BlockItem extends Item implements IUsableItem {

    private final Block blockToPlace;

    public BlockItem(Block blockToPlace, ItemProperties properties) {
        super(properties);
        this.blockToPlace = blockToPlace;
    }

    public BlockItem(Block blockToPlace) {
        this(blockToPlace, ItemProperties.create().defaultItem());
    }

    public Block getBlock() {
        return blockToPlace;
    }

    // ==================== NEW INTERACTION SYSTEM ====================

    @Override
    public InteractionResult onUseOnBlock(World world, Player player, ItemStack stack,
            BlockPos blockPos, BlockPos.Direction face, BlockPos placePos) {
        // Place block at the adjacent position
        int placeX = placePos.getX();
        int placeY = placePos.getY();
        int placeZ = placePos.getZ();

        // Check if position is valid (not inside player)
        if (wouldCollideWithPlayer(player, placeX, placeY, placeZ)) {
            return InteractionResult.FAIL;
        }

        // Check if the position is replaceable
        Block existingBlock = world.getBlockType(placeX, placeY, placeZ);
        if (!existingBlock.isAir() && !existingBlock.isReplaceable()) {
            return InteractionResult.FAIL;
        }

        // Place the block
        // Get the state based on placement context (rotation, etc.)
        engine.world.Direction engineFace = engine.world.Direction.fromVector(face.getOffsetX(), face.getOffsetY(),
                face.getOffsetZ());
        engine.world.block.state.BlockState state = blockToPlace.getStateForPlacement(world, placePos, player,
                engineFace);

        // Map state to ID (Block.STATE_IDS.getId(state))
        world.setBlock(placeX, placeY, placeZ, Block.STATE_IDS.getId(state));

        // If it's a fluid, set max level
        if (blockToPlace.isLiquid()) {
            world.setFluidLevel(placeX, placeY, placeZ, blockToPlace.getMaxFluidLevel());
        }

        // Create block entity if needed
        if (blockToPlace.hasBlockEntity()) {
            world.createBlockEntity(new BlockPos(placeX, placeY, placeZ), blockToPlace);
        }

        return InteractionResult.CONSUME; // Item should be consumed
    }

    // ==================== OLD INTERFACE (for backwards compatibility)
    // ====================

    @Override
    public boolean use(World world, Player player, ItemStack stack,
            float hitX, float hitY, float hitZ,
            int lastAirX, int lastAirY, int lastAirZ) {
        if (Float.isNaN(hitX)) {
            return false;
        }

        if (wouldCollideWithPlayer(player, lastAirX, lastAirY, lastAirZ)) {
            return false;
        }

        world.setBlock(lastAirX, lastAirY, lastAirZ, blockToPlace.getNumericId());

        if (blockToPlace.isLiquid()) {
            world.setFluidLevel(lastAirX, lastAirY, lastAirZ, blockToPlace.getMaxFluidLevel());
        }

        return true;
    }

    private boolean wouldCollideWithPlayer(Player player, int bx, int by, int bz) {
        float px = player.getX();
        float py = player.getY();
        float pz = player.getZ();
        float hw = 0.3f;
        float hh = 1.8f;

        float minX = px - hw, maxX = px + hw;
        float minY = py, maxY = py + hh;
        float minZ = pz - hw, maxZ = pz + hw;

        return !(maxX < bx || minX > bx + 1 ||
                maxY < by || minY > by + 1 ||
                maxZ < bz || minZ > bz + 1);
    }

    public static BlockItem fromBlock(Block block) {
        return new BlockItem(block);
    }

    public static BlockItem fromBlock(Block block, int maxStackSize) {
        return new BlockItem(block, ItemProperties.create().maxStackSize(maxStackSize));
    }

    @Override
    public String toString() {
        return "BlockItem{" + getRegistryId() + " -> " + blockToPlace.getRegistryId() + "}";
    }
}
