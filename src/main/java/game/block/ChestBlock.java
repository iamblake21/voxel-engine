package game.block;

import engine.world.BlockPos;
import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.blockentity.BlockEntity;
import engine.world.blockentity.BlockEntityType;
import game.init.GameBlockEntities;

/**
 * Chest block - has a block entity for storage.
 */
public class ChestBlock extends Block {
    
    public ChestBlock(BlockProperties properties) {
        super(properties);
    }
    
    public ChestBlock() {
        this(BlockProperties.create()
            .standardSolid()
            .hardness(2.5f));
    }
    
    @Override
    public boolean hasBlockEntity() {
        return true;
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos) {
        return GameBlockEntities.CHEST.create(pos);
    }
    
    @Override
    public BlockEntityType<?> getBlockEntityType() {
        return GameBlockEntities.CHEST;
    }
}
