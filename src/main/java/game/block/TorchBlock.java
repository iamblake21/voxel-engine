package game.block;

import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.state.BlockState;
import engine.world.block.state.StateDefinition;
import engine.world.block.state.property.EnumProperty;
import engine.world.Direction;
import engine.world.World;
import engine.world.BlockPos;
import engine.entity.Player;

public class TorchBlock extends Block {

    public static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class,
            java.util.Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP));

    public TorchBlock(BlockProperties properties) {
        super(properties);
        // Default state is UP (standing on ground)
        setDefaultState(getStateDefinition().any().with(FACING, Direction.UP));
    }

    @Override
    protected void appendProperties(StateDefinition.Builder builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(World world, BlockPos pos, Player player, Direction face) {
        // If placed on top of a block (interacting with UP face of block below), face
        // is UP.
        // If placed on side, face is NORTH/SOUTH/EAST/WEST.
        // Torches cannot be placed on ceiling (DOWN).

        if (face == Direction.DOWN) {
            return defaultState(); // Fallback or invalid? Let's default to UP (standing)
        }

        return defaultState().with(FACING, face);
    }

    private BlockState defaultState() {
        return getStateDefinition().any().with(FACING, Direction.UP);
    }
}
