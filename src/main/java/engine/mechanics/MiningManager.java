package engine.mechanics;

import engine.entity.Player;
import engine.loot.LootTable;
import engine.world.World;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.item.ItemStack;
import engine.world.item.ToolItem;

/**
 * Manages block breaking logic.
 */
public class MiningManager {

    private int currentBlockX, currentBlockY, currentBlockZ;
    private float breakProgress = 0.0f;
    private boolean isBreaking = false;

    public void processMining(Player player, World world, int bx, int by, int bz, float deltaTime) {
        if (!isBreaking || bx != currentBlockX || by != currentBlockY || bz != currentBlockZ) {
            currentBlockX = bx;
            currentBlockY = by;
            currentBlockZ = bz;
            isBreaking = true;
            breakProgress = 0.0f;
            System.out.println("Start Mining: " + bx + "," + by + "," + bz);
        }

        int blockId = world.getBlock(bx, by, bz);
        Block block = Blocks.get(blockId);

        if (block.isAir() || block.isUnbreakable()) {
            resetBreaking();
            return;
        }

        float speed = calculateMiningSpeed(player, block);

        if (block.getHardness() <= 0) {
            breakBlock(world, player, bx, by, bz, block);
            return;
        }

        breakProgress += speed * deltaTime;

        if (breakProgress >= 1.0f) {
            System.out.println("Breaking Block!");
            breakBlock(world, player, bx, by, bz, block);
        }
    }

    private void breakBlock(World world, Player player, int x, int y, int z, Block block) {
        // Calculate fortune level from held tool
        int fortuneLevel = getFortuneLevel(player);

        // Drop loot BEFORE removing the block
        if (canHarvest(player, block)) {
            world.dropBlockLoot(x, y, z, block, fortuneLevel);
        }

        // Remove the block
        world.setBlock(x, y, z, Blocks.AIR().getNumericId());

        // Damage tool
        ItemStack stack = player.getInventory().getSelectedStack();
        if (!stack.isEmpty() && stack.isDamageable()) {
            stack.damageItem(1);
        }

        resetBreaking();
    }

    /**
     * Check if player can harvest this block (gets drops).
     * Returns false if wrong tool tier.
     */
    private boolean canHarvest(Player player, Block block) {
        // If no tool required, always can harvest
        if (block.getRequiredToolType() == null && block.getMinToolTier() == 0) {
            return true;
        }

        ItemStack stack = player.getInventory().getSelectedStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ToolItem)) {
            // Hand can only harvest tier 0 blocks
            return block.getMinToolTier() == 0;
        }

        ToolItem tool = (ToolItem) stack.getItem();

        // Check tool type matches if required
        if (block.getRequiredToolType() != null && tool.getType() != block.getRequiredToolType()) {
            return false;
        }

        // Check tier
        return tool.getTier().ordinal() >= block.getMinToolTier();
    }

    /**
     * Get fortune level from held item.
     */
    private int getFortuneLevel(Player player) {
        ItemStack stack = player.getInventory().getSelectedStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ToolItem)) {
            return 0;
        }
        // TODO: Add enchantment system later
        // For now, return 0
        return 0;
    }

    public void resetBreaking() {
        isBreaking = false;
        breakProgress = 0.0f;
    }

    public float calculateMiningSpeed(Player player, Block block) {
        float hardness = block.getHardness();
        if (hardness < 0)
            return 0.0f;
        if (hardness == 0)
            return 1000.0f;

        ItemStack stack = player.getInventory().getSelectedStack();
        float toolSpeed = 1.0f;

        if (!stack.isEmpty() && stack.getItem() instanceof ToolItem) {
            ToolItem tool = (ToolItem) stack.getItem();
            toolSpeed = tool.getMiningSpeed();

            ToolItem.ToolType requiredType = block.getRequiredToolType();
            if (requiredType != null && tool.getType() != requiredType) {
                toolSpeed = 1.0f;
            }
        }

        return toolSpeed / hardness / 1.5f;
    }

    public float getBreakProgress() {
        return isBreaking ? breakProgress : 0.0f;
    }

    public boolean isBreaking() {
        return isBreaking;
    }

    public int getCurrentBlockX() {
        return currentBlockX;
    }

    public int getCurrentBlockY() {
        return currentBlockY;
    }

    public int getCurrentBlockZ() {
        return currentBlockZ;
    }
}