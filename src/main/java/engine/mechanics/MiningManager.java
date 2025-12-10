package engine.mechanics;

import engine.entity.Player;
import engine.world.World;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.item.ItemStack;
import engine.world.item.ToolItem;
import engine.window.InputManager;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Manages block breaking logic.
 * Calculates break speeds based on Tool vs Block.
 * Tracks breaking progress.
 */
public class MiningManager {

    private int currentBlockX, currentBlockY, currentBlockZ;
    private float breakProgress = 0.0f;
    private boolean isBreaking = false;

    // Configurable constant for base break speed
    // 30 ticks = 1.5 seconds base time for hardness 1

    /**
     * Update mining state. Call every frame (or tick).
     */
    public void update(float deltaTime, Player player, World world, InputManager input) {
        boolean breakButtonDown = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_1);

        if (!breakButtonDown) {
            resetBreaking();
            return;
        }

        // Raycast to find target block (re-using player's raycast logic simplified or
        // shared)
        // For now, we will ask the player or assume the player passes the target.
        // Actually, Player.java handles the raycast. We should split that
        // responsibility or have Player call us.
        // Better: Player calls "interact" and we decide if we are mining.

        // But Player.update handles input.
        // Let's rely on Player calling a method here with the target block info.
    }

    /**
     * Called by Player when holding left click on a block.
     */
    public void processMining(Player player, World world, int bx, int by, int bz, float deltaTime) {
        if (!isBreaking || bx != currentBlockX || by != currentBlockY || bz != currentBlockZ) {
            // Started breaking a NEW block
            currentBlockX = bx;
            currentBlockY = by;
            currentBlockZ = bz;
            isBreaking = true;
            breakProgress = 0.0f;
        }

        int blockId = world.getBlock(bx, by, bz);
        Block block = Blocks.get(blockId);

        if (Blocks.isAir(blockId)) {
            resetBreaking();
            return;
        }

        float speed = calculateMiningSpeed(player, block);

        if (block.getHardness() <= 0) {
            breakBlock(world, player, bx, by, bz);
            return;
        }

        // Increment progress
        // Progress is 0..1
        // Speed is "progress per second"
        breakProgress += speed * deltaTime;

        if (breakProgress >= 1.0f) {
            breakBlock(world, player, bx, by, bz);
        }
    }

    private void breakBlock(World world, Player player, int x, int y, int z) {

        world.setBlock(x, y, z, Blocks.AIR().getNumericId());

        // Damage tool
        ItemStack stack = player.getInventory().getSelectedStack();
        if (!stack.isEmpty() && stack.isDamageable()) {
            stack.damageItem(1);
        }

        resetBreaking();
    }

    public void resetBreaking() {
        isBreaking = false;
        breakProgress = 0.0f;
    }

    /**
     * Calculate mining speed (progress per second).
     */
    public float calculateMiningSpeed(Player player, Block block) {
        float hardness = block.getHardness();
        if (hardness < 0)
            return 0.0f; // Unbreakable
        if (hardness == 0)
            return 1000.0f; // Instant

        ItemStack stack = player.getInventory().getSelectedStack();
        float toolSpeed = 1.0f; // Hand speed

        // Hand
        if (stack.isEmpty() || !(stack.getItem() instanceof ToolItem)) {
            // Base speed is 1.0
        } else {
            ToolItem tool = (ToolItem) stack.getItem();
            toolSpeed = tool.getMiningSpeed();

            // Check tool compatibility
            // 1. Tool Type matches?
            // 2. Tool Tier >= Block Tier?

            ToolItem.ToolType requiredType = block.getRequiredToolType();
            if (requiredType != null) {
                if (tool.getType() == requiredType) {
                } else {
                    toolSpeed = 1.0f; // Wrong tool type acts like hand
                }
            } else {
                // No specific tool required, use tool speed? Or specific blocks have specific
                // tools?
                // Usually blocks like wood need Axe for speed, otherwise hand speed.
                // If requiredToolType is null, assume anything is 1.0 or tool speed applies?
                // Let's say if null, tools don't give bonus.

                // Actually, if requiredType is NULL, maybe it's like dirt/leaves where
                // shovel/shears help?
                // For now: if wrong tool, speed = 1.0.
            }
        }

        // Formula: Speed / Hardness / Constant
        // Let's calibrate:
        // Stone (Hardness 1.5). Wood Pick (Speed 2.0).
        // MC: Time = Hardness * 1.5 (if can harvest) or * 5 (if cannot)
        // Speed = 1 / Time

        // Simplified:
        // base = toolSpeed / hardness
        // We want breaking to take 'hardness' seconds if toolSpeed is 1? No.

        return toolSpeed / hardness / 1.5f; // Tweak 1.5f to feel right
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
