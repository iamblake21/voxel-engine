package engine.world.blockentity;

import engine.registry.ResourceLocation;
import engine.world.BlockPos;
import engine.world.block.Block;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Defines a block entity type with factory.
 * 
 * Usage:
 *   BlockEntityType<ChestBlockEntity> CHEST = BlockEntityTypes.register("game:chest",
 *       BlockEntityType.builder(ChestBlockEntity::new)
 *           .validBlocks(GameBlocks.CHEST, GameBlocks.TRAPPED_CHEST)
 *           .build());
 */
public class BlockEntityType<T extends BlockEntity> {
    
    private final BiFunction<BlockEntityType<T>, BlockPos, T> factory;
    private final Set<Block> validBlocks;
    
    // Registry info
    private ResourceLocation registryId;
    private int numericId = -1;
    
    private BlockEntityType(Builder<T> builder) {
        this.factory = builder.factory;
        this.validBlocks = builder.validBlocks;
    }
    
    // ==================== FACTORY ====================
    
    /**
     * Create a new block entity of this type.
     */
    public T create(BlockPos pos) {
        return factory.apply(this, pos);
    }
    
    /**
     * Check if this block entity type is valid for a given block.
     */
    public boolean isValidBlock(Block block) {
        return validBlocks.isEmpty() || validBlocks.contains(block);
    }
    
    // ==================== REGISTRY ====================
    
    public ResourceLocation getRegistryId() {
        return registryId;
    }
    
    public int getNumericId() {
        return numericId;
    }
    
    public void setRegistryInfo(ResourceLocation id, int numericId) {
        this.registryId = id;
        this.numericId = numericId;
    }
    
    @Override
    public String toString() {
        return "BlockEntityType{" + registryId + "}";
    }
    
    // ==================== BUILDER ====================
    
    public static <T extends BlockEntity> Builder<T> builder(BiFunction<BlockEntityType<T>, BlockPos, T> factory) {
        return new Builder<>(factory);
    }
    
    public static class Builder<T extends BlockEntity> {
        private final BiFunction<BlockEntityType<T>, BlockPos, T> factory;
        private final Set<Block> validBlocks = new HashSet<>();
        
        private Builder(BiFunction<BlockEntityType<T>, BlockPos, T> factory) {
            this.factory = factory;
        }
        
        /**
         * Specify which blocks can have this block entity.
         */
        public Builder<T> validBlocks(Block... blocks) {
            validBlocks.addAll(Arrays.asList(blocks));
            return this;
        }
        
        public BlockEntityType<T> build() {
            return new BlockEntityType<>(this);
        }
    }
}
