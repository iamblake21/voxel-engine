package game.block;

import engine.world.BlockPos;
import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.blockentity.BlockEntity;
import engine.world.blockentity.BlockEntityType;
import game.init.GameBlockEntities;

/**
 * Furnace block - has a tickable block entity for smelting.
 */
public class FurnaceBlock extends Block {
    
    private final boolean lit;
    
    public FurnaceBlock(BlockProperties properties, boolean lit) {
        super(properties);
        this.lit = lit;
    }
    
    public FurnaceBlock() {
        this(BlockProperties.create()
            .standardSolid()
            .hardness(3.5f), false);
    }
    
    public boolean isLit() {
        return lit;
    }
    
    @Override
    public boolean hasBlockEntity() {
        return true;
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos) {
        return GameBlockEntities.FURNACE.create(pos);
    }
    
    @Override
    public BlockEntityType<?> getBlockEntityType() {
        return GameBlockEntities.FURNACE;
    }
}
