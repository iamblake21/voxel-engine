package engine.world.block;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;
import engine.world.BlockPos;
import engine.world.blockentity.BlockEntity;
import engine.world.blockentity.BlockEntityType;
import engine.world.blockentity.ITickableBlockEntity;
import java.util.Collection;
import engine.loot.LootTable;
import engine.utils.IdMapper;
import engine.world.block.state.BlockState;
import engine.world.item.ToolItem;
import java.util.Optional;
import engine.world.item.ToolItem.ToolType;

/**
 * Represents a block type in the game.
 * 
 * Blocks are immutable once created - all properties are set via Builder.
 * Each block type is registered once and shared.
 */
public class Block {

    public static final IdMapper<BlockState> STATE_IDS = new IdMapper<>();

    private final BlockProperties properties;
    private final engine.world.block.state.StateDefinition stateDefinition;
    private engine.world.block.state.BlockState defaultState;

    // Set by registry after registration
    private ResourceLocation registryId;
    private int numericId = -1;

    public Block(BlockProperties properties) {
        this.properties = properties;

        engine.world.block.state.StateDefinition.Builder builder = new engine.world.block.state.StateDefinition.Builder(
                this);
        appendProperties(builder);
        this.stateDefinition = builder.build();
        this.defaultState = stateDefinition.any();
    }

    public Block() {
        this(BlockProperties.create());
    }

    protected void appendProperties(engine.world.block.state.StateDefinition.Builder builder) {
        // Override in subclasses to add properties
    }

    public final engine.world.block.state.StateDefinition getStateDefinition() {
        return stateDefinition;
    }

    public final engine.world.block.state.BlockState getDefaultState() {
        return defaultState;
    }

    // Used by subclasses to set the default state in constructor if any() is not
    // enough
    protected final void setDefaultState(engine.world.block.state.BlockState state) {
        this.defaultState = state;
    }

    // ==================== PROPERTIES ====================

    public boolean isSolid() {
        return properties.solid;
    }

    public boolean isOpaque() {
        return properties.opaque;
    }

    public boolean isHard() {
        return properties.hard;
    }

    public boolean isTransparent() {
        return properties.transparent;
    }

    public boolean isLiquid() {
        return properties.liquid;
    }

    public int getViscosity() {
        return properties.viscosity;
    }

    public int getFluidTickRate() {
        return properties.fluidTickRate;
    }

    public int getMaxFluidLevel() {
        return properties.maxFluidLevel;
    }

    public boolean isAir() {
        return properties.air;
    }

    public boolean isReplaceable() {
        return properties.replaceable;
    }

    public BlockProperties getProperties() {
        return properties;
    }

    public boolean isLightSource() {
        return properties.getLightLevel() > 0;
    }

    public int getLightLevel() {
        return properties.getLightLevel();
    }

    public LootTable getLootTable() {
        return properties.getLootTable();
    }

    public boolean hasLoot() {
        return properties.hasLoot();
    }

    public float getHardness() {
        return properties.hardness;
    }

    public ToolType getRequiredToolType() {
        return properties.requiredToolType;
    }

    public int getMinToolTier() {
        return properties.minToolTier;
    }

    // ==================== TEXTURES ====================

    /**
     * Get texture tile X for a face.
     * Override in subclasses for multi-texture blocks.
     * 
     * @param normalX Face normal X (-1, 0, or 1)
     * @param normalY Face normal Y (-1, 0, or 1)
     * @param normalZ Face normal Z (-1, 0, or 1)
     */
    public int getTextureTileX(int normalX, int normalY, int normalZ) {
        return properties.tileX;
    }

    /**
     * Get texture tile Y for a face.
     */
    public int getTextureTileY(int normalX, int normalY, int normalZ) {
        return properties.tileY;
    }

    // ==================== REGISTRY INFO ====================

    /**
     * Called by registry after registration - do not call manually
     */
    public void setRegistryInfo(ResourceLocation id, int numericId) {
        if (this.registryId != null) {
            throw new IllegalStateException("Block already registered: " + this.registryId);
        }
        this.registryId = id;
        this.numericId = numericId;
    }

    /**
     * Get the registry ID (e.g., "game:stone")
     */
    public ResourceLocation getRegistryId() {
        return registryId;
    }

    /**
     * Get numeric ID for serialization
     */
    public int getNumericId() {
        return numericId;
    }

    /**
     * Check if this block is registered
     */
    public boolean isRegistered() {
        return registryId != null;
    }

    // ==================== STATIC HELPERS ====================

    /**
     * Get a block from the registry by ID
     */
    public static Optional<Block> get(String id) {
        return Registries.BLOCKS.get(id);
    }

    /**
     * Get a block or the default (air)
     */
    public static Block getOrDefault(String id) {
        return Registries.BLOCKS.getOrDefault(id);
    }

    /**
     * Get a block by numeric ID
     */
    public static Block getByNumericId(int id) {
        return Registries.BLOCKS.getByNumericIdOrDefault(id);
    }

    /**
     * Get the default block (air)
     */
    public static Block getDefault() {
        return Registries.BLOCKS.getDefault().orElseThrow(
                () -> new IllegalStateException("No default block registered"));
    }

    @Override
    public String toString() {
        return "Block{" + (registryId != null ? registryId : "unregistered") + "}";
    }

    // ==================== BLOCK ENTITY ====================

    /**
     * Check if this block type has an associated block entity.
     * Override in subclasses that need block entities.
     */
    public boolean hasBlockEntity() {
        return false;
    }

    /**
     * Create a new block entity for this block.
     * Override in subclasses that have block entities.
     * 
     * @param pos The position where the block entity will be placed
     * @return The new block entity, or null if this block doesn't have one
     */
    public BlockEntity createBlockEntity(BlockPos pos) {
        return null;
    }

    /**
     * Get the block entity type for this block.
     * Override in subclasses.
     */
    public BlockEntityType<?> getBlockEntityType() {
        return null;
    }

    /**
     * Called when the block is placed in the world.
     */
    public void onPlace(engine.world.World world, int x, int y, int z) {
    }

    /**
     * Called when the block is removed from the world.
     */
    public void onRemove(engine.world.World world, int x, int y, int z, engine.world.block.state.BlockState state) {
    }

    /**
     * Called when the block is interacted with (right-click).
     * 
     * @return true if the interaction was handled, false otherwise.
     */
    public boolean onInteract(engine.world.World world, int x, int y, int z, engine.entity.Player player) {
        return false;
    }

    /**
     * Get the state for placement.
     * Override this to implementing rotation/facing logic.
     */
    public engine.world.block.state.BlockState getStateForPlacement(engine.world.World world, engine.world.BlockPos pos,
            engine.entity.Player player, engine.world.Direction face) {
        return defaultState;
    }

    /**
     * Get the model path for this block state.
     * By default returns the static property model path.
     */
    public String getModelPath(engine.world.block.state.BlockState state) {
        return properties.getModelPath();
    }
}
