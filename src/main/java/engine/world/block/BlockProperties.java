package engine.world.block;

/**
 * Properties for a block type.
 * Use the builder pattern for clean configuration.
 */
public class BlockProperties {

    // Physical properties
    boolean solid = true; // Blocks movement
    boolean opaque = true; // Blocks light, hides faces
    boolean hard = true; // Can't be replaced by features (trees, etc.)
    boolean transparent = false; // Renders with alpha blending
    boolean liquid = false; // Is a liquid (water, lava)
    boolean air = false; // Is air (special case)
    boolean replaceable = false; // Can be replaced when placing blocks

    // Rendering
    int tileX = 0; // Texture atlas tile X
    int tileY = 0; // Texture atlas tile Y
    boolean tintGrass = false; // Apply grass tint
    boolean tintFoliage = false; // Apply foliage tint

    // Physics
    float friction = 0.6f; // Surface friction
    float slipperiness = 0.6f; // Ice-like sliding
    int lightLevel = 0;

    // Fluid Properties
    int viscosity = 1; // Fluid level drop per block
    int fluidTickRate = 5; // Ticks between fluid updates
    int maxFluidLevel = 7; // Max fluid level (0-15 usually)

    String modelPath = null;

    // Mining Properties
    float hardness = 1.0f; // Seconds to break with correct tool
    engine.world.item.ToolItem.ToolType requiredToolType = null; // Null means any tool (or hand)
    int minToolTier = 0; // 0=Hand/Wood, 1=Stone, 2=Iron, 3=Diamond

    public BlockProperties model(String path) {
        this.modelPath = path;
        return this;
    }

    private BlockProperties() {
    }

    /**
     * Create a new properties builder
     */
    public static BlockProperties create() {
        return new BlockProperties();
    }

    // ==================== BUILDER METHODS ====================

    public BlockProperties lightLevel(int level) {
        this.lightLevel = Math.max(0, Math.min(15, level));
        return this;
    }

    public BlockProperties solid(boolean solid) {
        this.solid = solid;
        return this;
    }

    public BlockProperties opaque(boolean opaque) {
        this.opaque = opaque;
        return this;
    }

    public BlockProperties hard(boolean hard) {
        this.hard = hard;
        return this;
    }

    public BlockProperties transparent(boolean transparent) {
        this.transparent = transparent;
        return this;
    }

    public String getModelPath() {
        return modelPath;
    }

    public boolean hasCustomModel() {
        return modelPath != null;
    }

    public BlockProperties liquid(boolean liquid) {
        this.liquid = liquid;
        return this;
    }

    public BlockProperties air(boolean air) {
        this.air = air;
        return this;
    }

    public BlockProperties replaceable(boolean replaceable) {
        this.replaceable = replaceable;
        return this;
    }

    public BlockProperties tile(int x, int y) {
        this.tileX = x;
        this.tileY = y;
        return this;
    }

    public BlockProperties tintGrass() {
        this.tintGrass = true;
        return this;
    }

    public BlockProperties tintFoliage() {
        this.tintFoliage = true;
        return this;
    }

    public BlockProperties friction(float friction) {
        this.friction = friction;
        return this;
    }

    public BlockProperties slipperiness(float slipperiness) {
        this.slipperiness = slipperiness;
        return this;
    }

    public BlockProperties viscosity(int viscosity) {
        this.viscosity = viscosity;
        return this;
    }

    public BlockProperties fluidTickRate(int fluidTickRate) {
        this.fluidTickRate = fluidTickRate;
        return this;
    }

    public BlockProperties maxFluidLevel(int maxFluidLevel) {
        this.maxFluidLevel = maxFluidLevel;
        return this;
    }

    public BlockProperties hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    public BlockProperties requiredTool(engine.world.item.ToolItem.ToolType type) {
        this.requiredToolType = type;
        return this;
    }

    public BlockProperties minTier(int tier) {
        this.minToolTier = tier;
        return this;
    }

    // ==================== PRESETS ====================

    /**
     * Preset for air-like blocks
     */
    public BlockProperties airLike() {
        this.solid = false;
        this.opaque = false;
        this.hard = false;
        this.air = true;
        this.replaceable = true;
        return this;
    }

    /**
     * Preset for liquid blocks (water, lava)
     */
    public BlockProperties liquidLike() {
        this.solid = false;
        this.opaque = false;
        this.hard = false;
        this.transparent = true;
        this.liquid = true;
        this.replaceable = true;
        return this;
    }

    /**
     * Preset for transparent solid blocks (leaves, glass)
     */
    public BlockProperties transparentSolid() {
        this.solid = true;
        this.opaque = false;
        this.hard = false;
        this.transparent = true;
        return this;
    }

    /**
     * Preset for standard solid blocks (stone, dirt)
     */
    public BlockProperties standardSolid() {
        this.solid = true;
        this.opaque = true;
        this.hard = true;
        this.transparent = false;
        return this;
    }

    // ==================== GETTERS ====================

    public boolean isSolid() {
        return solid;
    }

    public boolean isOpaque() {
        return opaque;
    }

    public boolean isHard() {
        return hard;
    }

    public boolean isTransparent() {
        return transparent;
    }

    public boolean isLiquid() {
        return liquid;
    }

    public boolean isAir() {
        return air;
    }

    public boolean isReplaceable() {
        return replaceable;
    }

    public int getTileX() {
        return tileX;
    }

    public int getTileY() {
        return tileY;
    }

    public boolean hasTintGrass() {
        return tintGrass;
    }

    public boolean hasTintFoliage() {
        return tintFoliage;
    }

    public float getFriction() {
        return friction;
    }

    public float getSlipperiness() {
        return slipperiness;
    }

    public int getLightLevel() {
        return lightLevel;
    }

    public boolean isLightSource() {
        return lightLevel > 0;
    }

    public int getViscosity() {
        return viscosity;
    }

    public int getFluidTickRate() {
        return fluidTickRate;
    }

    public int getMaxFluidLevel() {
        return maxFluidLevel;
    }

    /**
     * Copy properties from another BlockProperties
     */
    public BlockProperties copyFrom(BlockProperties other) {
        this.solid = other.solid;
        this.opaque = other.opaque;
        this.hard = other.hard;
        this.transparent = other.transparent;
        this.liquid = other.liquid;
        this.air = other.air;
        this.replaceable = other.replaceable;
        this.tileX = other.tileX;
        this.tileY = other.tileY;
        this.tintGrass = other.tintGrass;
        this.tintFoliage = other.tintFoliage;
        this.friction = other.friction;
        this.slipperiness = other.slipperiness;
        this.lightLevel = other.lightLevel;
        this.viscosity = other.viscosity;
        this.fluidTickRate = other.fluidTickRate;
        this.maxFluidLevel = other.maxFluidLevel;
        this.hardness = other.hardness;
        this.requiredToolType = other.requiredToolType;
        this.minToolTier = other.minToolTier;
        return this;
    }
}
