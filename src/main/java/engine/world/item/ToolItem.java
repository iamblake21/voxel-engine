package engine.world.item;

import engine.entity.Player;
import engine.world.World;
import engine.world.block.Block;

/**
 * A tool item with mining level, speed, and durability.
 * Used for pickaxes, axes, shovels, swords, etc.
 */
public class ToolItem extends Item implements IUsableItem {

    private final ToolTier tier;
    private final ToolType type;
    private final float miningSpeed;

    /**
     * Tool tier determines durability and mining level
     */
    public enum ToolTier {
        WOOD(2, 60),
        STONE(4, 132),
        IRON(6, 251),
        GOLD(8, 33), // Fast but fragile
        DIAMOND(8, 1562);

        private final int miningLevel;
        private final int durability;

        ToolTier(int miningLevel, int durability) {
            this.miningLevel = miningLevel;
            this.durability = durability;
        }

        public int getMiningLevel() {
            return miningLevel;
        }

        public int getDurability() {
            return durability;
        }
    }

    /**
     * Tool type determines what blocks it's effective against
     */
    public enum ToolType {
        PICKAXE, // Stone, ores
        AXE, // Wood
        SHOVEL, // Dirt, sand, gravel
        SWORD, // Combat, cobwebs
        HOE // Farming
    }

    /**
     * Create a tool item
     * 
     * @param type        Tool type
     * @param tier        Tool tier (determines durability and mining level)
     * @param miningSpeed Mining speed multiplier
     */
    public ToolItem(ToolType type, ToolTier tier, float miningSpeed) {
        super(ItemProperties.create()
                .toolItem()
                .durability(tier.getDurability()));
        this.tier = tier;
        this.type = type;
        this.miningSpeed = miningSpeed;
    }

    /**
     * Create a tool with default mining speed based on tier
     */
    public ToolItem(ToolType type, ToolTier tier) {
        this(type, tier, getDefaultSpeed(tier));
    }

    /**
     * Create a tool with custom properties (for setting icon, etc.)
     */
    public ToolItem(ToolType type, ToolTier tier, ItemProperties properties) {
        super(properties.durability(tier.getDurability()));
        this.tier = tier;
        this.type = type;
        this.miningSpeed = getDefaultSpeed(tier);
    }

    private static float getDefaultSpeed(ToolTier tier) {
        switch (tier) {
            case WOOD:
                return 2.0f;
            case STONE:
                return 4.0f;
            case IRON:
                return 6.0f;
            case GOLD:
                return 12.0f;
            case DIAMOND:
                return 8.0f;
            default:
                return 1.0f;
        }
    }

    // ==================== GETTERS ====================

    public ToolTier getTier() {
        return tier;
    }

    public ToolType getType() {
        return type;
    }

    public float getMiningSpeed() {
        return miningSpeed;
    }

    public int getMiningLevel() {
        return tier.getMiningLevel();
    }

    /**
     * Check if this tool can harvest a block
     * (In a full implementation, this would check block hardness vs tool level)
     */
    public boolean canHarvest(Block block) {
        // For now, just check if it's a hard block
        // In a full game, you'd check block.getHarvestLevel() vs getMiningLevel()
        return block.isHard();
    }

    /**
     * Tools don't have a "use" action (right-click), they're used for breaking
     * blocks
     */
    @Override
    public boolean use(World world, Player player, ItemStack stack,
            float hitX, float hitY, float hitZ,
            int lastAirX, int lastAirY, int lastAirZ) {
        // Tools don't have a right-click action
        return false;
    }

    @Override
    public String toString() {
        return "ToolItem{" + getRegistryId() + ", " + type + ", " + tier + "}";
    }
}
