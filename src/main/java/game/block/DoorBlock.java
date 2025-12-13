package game.block;

import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.Blocks;
import engine.world.block.state.BlockState;
import engine.world.block.state.StateDefinition;
import engine.world.block.state.property.BooleanProperty;
import engine.world.block.state.property.DoubleBlockHalf;
import engine.world.block.state.property.EnumProperty;
import engine.world.Direction;
import engine.world.World;

public class DoorBlock extends Block {

    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.create("half", DoubleBlockHalf.class);
    // Use EnumProperty<Direction> because DirectionProperty class was not
    // explicitly created in this session, assume EnumProperty works.
    public static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class);

    public DoorBlock(BlockProperties properties) {
        super(properties);
        this.setDefaultState(this.getStateDefinition().any()
                .with(OPEN, false)
                .with(HALF, DoubleBlockHalf.LOWER)
                .with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateDefinition.Builder builder) {
        builder.add(OPEN, HALF, FACING);
    }

    @Override
    public void onPlace(World world, int x, int y, int z) {
        BlockState state = Block.STATE_IDS.get(world.getBlock(x, y, z));
        // Safety check to prevent crash if world update failed or state is invalid
        if (state == null || state.getBlock() == Blocks.AIR())
            return;

        // Check if we are Lower half and need to place Upper
        if (state.get(HALF) == DoubleBlockHalf.LOWER) {

            // Check if top block is replaceable (AIR or similar)
            if (world.getBlockType(x, y + 1, z).isReplaceable()) {
                BlockState upper = state.with(HALF, DoubleBlockHalf.UPPER);
                world.setBlock(x, y + 1, z, Block.STATE_IDS.getId(upper));
            }
            // CRITICAL FIX: If top block is ALREADY a Door (this), we are valid.
            // Do not self-destruct during state updates!
            else if (world.getBlockType(x, y + 1, z) == this) {
                return;
            } else {
                // Cannot place? Remove self.
                world.setBlock(x, y, z, Blocks.AIR().getNumericId());
            }
        }
    }

    @Override
    public void onRemove(World world, int x, int y, int z, BlockState state) {
        // Since World updates chunk before calling onRemove, we can check if we are
        // still here.
        // If we are still here, it's just a state change, so don't remove the partner.
        if (Blocks.get(world.getBlock(x, y, z)) == this) {
            return;
        }

        if (state.get(HALF) == DoubleBlockHalf.LOWER) {
            // Remove Upper
            BlockState upperState = Block.STATE_IDS.get(world.getBlock(x, y + 1, z));
            if (upperState != null && upperState.getBlock() == this
                    && upperState.get(HALF) == DoubleBlockHalf.UPPER) {
                world.setBlock(x, y + 1, z, Blocks.AIR().getNumericId());
            }
        } else {
            // Remove Lower
            BlockState lowerState = Block.STATE_IDS.get(world.getBlock(x, y - 1, z));
            if (lowerState != null && lowerState.getBlock() == this
                    && lowerState.get(HALF) == DoubleBlockHalf.LOWER) {
                world.setBlock(x, y - 1, z, Blocks.AIR().getNumericId());
            }
        }
    }

    @Override
    public boolean onInteract(World world, int x, int y, int z, engine.entity.Player player) {
        BlockState state = Block.STATE_IDS.get(world.getBlock(x, y, z));
        if (state != null) {
            boolean open = state.get(OPEN);
            world.setBlock(x, y, z, Block.STATE_IDS.getId(state.with(OPEN, !open)));

            DoubleBlockHalf half = state.get(HALF);
            int oy = (half == DoubleBlockHalf.LOWER) ? 1 : -1;
            BlockState other = Block.STATE_IDS.get(world.getBlock(x, y + oy, z));
            if (other != null && other.getBlock() == this && other.get(HALF) != half) {
                world.setBlock(x, y + oy, z, Block.STATE_IDS.getId(other.with(OPEN, !open)));
            }
            return true;
        }
        return false;
    }

}
