package engine.world.blockentity;

import engine.world.BlockPos;
import engine.world.World;
import engine.world.block.Block;
import engine.world.item.nbt.NBTTagCompound;

/**
 * Base class for block entities (tile entities).
 * 
 * Block entities store additional data for blocks that need more than
 * just an ID (chests, furnaces, signs, etc.).
 * 
 * Lifecycle:
 * 1. Created when block is placed (Block.createBlockEntity())
 * 2. setWorld() called when added to world
 * 3. load() called if loading from save
 * 4. tick() called each game tick (if ITickableBlockEntity)
 * 5. save() called when saving world
 * 6. onRemoved() called when block is broken
 */
public abstract class BlockEntity {
    
    protected final BlockEntityType<?> type;
    protected World world;
    protected BlockPos pos;
    protected boolean removed = false;
    
    // Cached block state
    protected Block cachedBlock;
    
    public BlockEntity(BlockEntityType<?> type, BlockPos pos) {
        this.type = type;
        this.pos = pos;
    }
    
    // ==================== LIFECYCLE ====================
    
    /**
     * Called when this block entity is added to the world.
     */
    public void setWorld(World world) {
        this.world = world;
    }
    
    /**
     * Called when the block entity is removed from the world.
     * Override to drop items, cleanup, etc.
     */
    public void onRemoved() {
        this.removed = true;
    }
    
    /**
     * Check if this block entity has been removed.
     */
    public boolean isRemoved() {
        return removed;
    }
    
    /**
     * Mark this block entity as needing to save its data.
     * Call this when internal state changes.
     */
    public void setChanged() {
        // In a full implementation, this would mark the chunk dirty
        // For now, it's a no-op placeholder
    }
    
    // ==================== SERIALIZATION ====================
    
    /**
     * Save block entity data to NBT.
     * Subclasses should call super.save() first.
     */
    public NBTTagCompound save() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("id", type.getRegistryId().toString());
        nbt.setInt("x", pos.getX());
        nbt.setInt("y", pos.getY());
        nbt.setInt("z", pos.getZ());
        saveAdditional(nbt);
        return nbt;
    }
    
    /**
     * Override to save additional data.
     */
    protected void saveAdditional(NBTTagCompound nbt) {
        // Override in subclasses
    }
    
    /**
     * Load block entity data from NBT.
     * Subclasses should call super.load() first.
     */
    public void load(NBTTagCompound nbt) {
        // Position is usually set by constructor, but can be overridden
        if (nbt.hasKey("x") && nbt.hasKey("y") && nbt.hasKey("z")) {
            this.pos = new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
        }
        loadAdditional(nbt);
    }
    
    /**
     * Override to load additional data.
     */
    protected void loadAdditional(NBTTagCompound nbt) {
        // Override in subclasses
    }
    
    // ==================== GETTERS ====================
    
    public BlockEntityType<?> getType() {
        return type;
    }
    
    public BlockPos getPos() {
        return pos;
    }
    
    public World getWorld() {
        return world;
    }
    
    public int getX() { return pos.getX(); }
    public int getY() { return pos.getY(); }
    public int getZ() { return pos.getZ(); }
    
    /**
     * Get the block at this position.
     * Cached for performance.
     */
    public Block getBlock() {
        if (cachedBlock == null && world != null) {
            cachedBlock = world.getBlockType(pos.getX(), pos.getY(), pos.getZ());
        }
        return cachedBlock;
    }
    
    /**
     * Invalidate cached block (call when block changes).
     */
    public void invalidateBlockCache() {
        cachedBlock = null;
    }
    
    // ==================== UTILITY ====================
    
    /**
     * Check if a player is within interaction range.
     */
    public boolean isInRange(float playerX, float playerY, float playerZ, float maxDistance) {
        float dx = pos.getCenterX() - playerX;
        float dy = pos.getCenterY() - playerY;
        float dz = pos.getCenterZ() - playerZ;
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + pos + "}";
    }
}
