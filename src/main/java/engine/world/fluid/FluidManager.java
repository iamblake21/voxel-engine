package engine.world.fluid;

import engine.world.World;
import engine.world.block.Block;
import engine.world.block.Blocks;

import java.util.*;

/**
 * Manages fluid expansion and simulation.
 * Uses a priority queue for timed updates (ticks).
 */
public class FluidManager {

    private final World world;
    private final PriorityQueue<FluidUpdate> updateQueue = new PriorityQueue<>();
    private final Set<Long> pendingUpdates = new HashSet<>();

    private long currentTick = 0;

    // Max updates per tick to prevent lag
    private static final int MAX_UPDATES_PER_TICK = 2000;

    private static class FluidUpdate implements Comparable<FluidUpdate> {
        long posKey;
        int x, y, z;
        long targetTick;

        public FluidUpdate(long posKey, int x, int y, int z, long targetTick) {
            this.posKey = posKey;
            this.x = x;
            this.y = y;
            this.z = z;
            this.targetTick = targetTick;
        }

        @Override
        public int compareTo(FluidUpdate o) {
            return Long.compare(this.targetTick, o.targetTick);
        }
    }

    public FluidManager(World world) {
        this.world = world;
    }

    /**
     * Called by World.setBlock when a block changes.
     */
    public void scheduleUpdate(int x, int y, int z) {
        // Only schedule if it's a fluid
        Block block = world.getBlockType(x, y, z);
        int delay = 0;
        if (block.isLiquid()) {
            delay = block.getFluidTickRate();
        } else {
            // Non-fluid updates (neighbor triggers) usually handled by the caller
            // directing flow INTO this block.
            return;
        }

        scheduleUpdate(x, y, z, delay);
    }

    public void scheduleUpdate(int x, int y, int z, int delay) {
        long key = packPos(x, y, z);

        if (pendingUpdates.contains(key))
            return;

        long targetTick = currentTick + delay;
        updateQueue.add(new FluidUpdate(key, x, y, z, targetTick));
        pendingUpdates.add(key);
    }

    private long packPos(int x, int y, int z) {
        return ((long) x & 0x7FFFFFF) | (((long) z & 0x7FFFFFF) << 27) | (((long) y & 0x3FF) << 54);
    }

    /**
     * Main simulation tick.
     */
    public void tick() {
        currentTick++;
        int processed = 0;

        while (!updateQueue.isEmpty() && processed < MAX_UPDATES_PER_TICK) {
            // Peek first
            FluidUpdate next = updateQueue.peek();
            if (next.targetTick > currentTick) {
                break; // Not ready yet
            }

            updateQueue.poll();
            pendingUpdates.remove(next.posKey);

            updateFluid(next.x, next.y, next.z);
            processed++;
        }
    }

    private void updateFluid(int x, int y, int z) {
        Block block = world.getBlockType(x, y, z);
        if (!block.isLiquid()) {
            return;
        }

        int currentLevel = world.getFluidLevel(x, y, z);
        int viscosity = block.getViscosity();
        int maxLevel = block.getMaxFluidLevel();
        int tickRate = block.getFluidTickRate();

        // 1. Calculate the MAX theoretical level this block should have based on
        // neighbors
        int maxNeighborLevel = 0;

        // Check UP (waterfall source)
        Block blockAbove = world.getBlockType(x, y + 1, z);
        if (blockAbove.getNumericId() == block.getNumericId()) {
            // If liquid above, we inherit its level (waterfall)
            maxNeighborLevel = world.getFluidLevel(x, y + 1, z);
        }

        // Check Neighbors (Horizontal spread)
        int[] dx = { 1, -1, 0, 0 };
        int[] dz = { 0, 0, 1, -1 };

        for (int i = 0; i < 4; i++) {
            Block nBlock = world.getBlockType(x + dx[i], y, z + dz[i]);
            int nLevel = world.getFluidLevel(x + dx[i], y, z + dz[i]);

            if (nBlock.getNumericId() == block.getNumericId()) {
                int flowRef = nLevel - viscosity;
                if (flowRef > maxNeighborLevel) {
                    maxNeighborLevel = flowRef;
                }
            }
        }

        // 2. Depropagation / State Correction
        // If we are NOT a source block (created by generation or manual placement
        // assumed source if maxLevel??
        // Wait, manual placement needs to be distinguished?
        // For now, assume if currentLevel is maxLevel AND we are not falling, we MIGHT
        // be a source.
        // But how to tell a static source from a filled hole?
        // Simplification: We only decay if calculated < currentLevel.
        // But sources shouldn't decay.
        // Constraint: Sources are only maxLevel. But falling water is also maxLevel.
        // We need a stable source logic.
        // PROPOSAL: If it's maxLevel, we assume it's a source UNLESS we explicitly
        // track "Falling" state differently.
        // But falling water spreads horizontally too.

        // Let's rely on level:
        // If currentLevel == maxLevel, we assume it's a Source OR directly under a
        // source.
        // If directly under a source/liquid, it's sustained.
        // If it's a "placed" source, it stays.
        // How to simulate removal?
        // If I remove a source, its neighbors (level < max) should decay.
        // A maxLevel block should NEVER decay by itself in this simple logic unless we
        // add metadata 'isSource'.
        // For this task, let's assume maxLevel blocks don't decay (they are sources).
        // Only flowing blocks (level < max) decay.

        if (currentLevel < maxLevel) {
            if (maxNeighborLevel < currentLevel) {
                // Decay
                int newLevel = maxNeighborLevel; // Drop to what neighbors support
                if (newLevel <= 0) {
                    world.setBlock(x, y, z, Blocks.AIR().getNumericId());
                    world.setFluidLevel(x, y, z, 0);
                } else {
                    world.setFluidLevel(x, y, z, newLevel);
                    scheduleUpdate(x, y, z, tickRate);
                }

                // Alert neighbors to check themselves
                scheduleUpdate(x, y - 1, z, tickRate);
                scheduleUpdate(x + 1, y, z, tickRate);
                scheduleUpdate(x - 1, y, z, tickRate);
                scheduleUpdate(x, y, z + 1, tickRate);
                scheduleUpdate(x, y, z - 1, tickRate);
                return;
            }
        } else {
            // It is max level.
            // If it was falling water (fed from above), and source above is gone, it should
            // probably decay?
            // But we can't distinguish "Placed Source" from "Falling Water" just by
            // ID/Level without metadata.
            // Assumption: Falling water is always treated as Source for downstream, but if
            // we want it to vanish...
            // Check if it's "Falling". A full block is falling if block above is same
            // liquid.
            // If block above becomes AIR, this block turns into a Source? That's Minecraft
            // behavior (water source creation).
            // But we want it to disappear.

            // To properly fix "Falling water stays when source removed":
            // We need to know if it's a "Source" or "Flow".
            // Since we lack metadata, let's assume:
            // MANUAL PLACEMENT sets it as Source (Max Level).
            // GENERATION sets it as Source.
            // FALLING updates set it as Max Level.

            // If we remove the top one, the one below is Max Level. It thinks it's a
            // source.
            // This is the classic "infinite water column" issue if we don't have 'falling'
            // bit.
            // Let's keep it simple: MaxLevel blocks rarely decay unless we add complex
            // logic.
            // Fixing "spreading" water failing to dry up is the main goal.
            // Spreading water has level < maxLevel.
        }

        // 3. Flow Logic (Spreading)

        // Flow Down
        if (canFlowInto(x, y - 1, z)) {
            Block blockBelow = world.getBlockType(x, y - 1, z);
            boolean isLiquidBelow = blockBelow.isLiquid();
            int levelBelow = world.getFluidLevel(x, y - 1, z);

            if (!isLiquidBelow || levelBelow < currentLevel) {
                world.setBlock(x, y - 1, z, Blocks.getId(block));
                world.setFluidLevel(x, y - 1, z, currentLevel); // Falling = Current Level (Propagate distance)
                scheduleUpdate(x, y - 1, z, tickRate);
            }
        }

        // Flow Horizontally
        if (world.getBlockType(x, y - 1, z).isReplaceable() && !world.getBlockType(x, y - 1, z).isLiquid()) {
            return; // Fast path: falling water doesn't spread sideways (optimization)
        }

        int nextLevel = currentLevel - viscosity;
        if (nextLevel > 0) {
            flow(x + 1, y, z, block, nextLevel, tickRate);
            flow(x - 1, y, z, block, nextLevel, tickRate);
            flow(x, y, z + 1, block, nextLevel, tickRate);
            flow(x, y, z - 1, block, nextLevel, tickRate);
        }
    }

    private void flow(int x, int y, int z, Block fluidBlock, int level, int delay) {
        if (canFlowInto(x, y, z)) {
            Block target = world.getBlockType(x, y, z);
            int currentTargetLevel = world.getFluidLevel(x, y, z);

            // Don't flow if target is already higher/equal
            if (target.isLiquid()) {
                if (target.getNumericId() != fluidBlock.getNumericId()) {
                    // Evaluate mixing if needed (e.g. water vs lava)
                    return;
                }
                if (currentTargetLevel >= level) {
                    return;
                }
            }

            // Update the block
            world.setBlock(x, y, z, Blocks.getId(fluidBlock));
            world.setFluidLevel(x, y, z, level);
            scheduleUpdate(x, y, z, delay);
        }
    }

    private boolean canFlowInto(int x, int y, int z) {
        if (y < 0)
            return false; // Void
        Block block = world.getBlockType(x, y, z);
        // Can flow into Air, Replaceable (Grass), or Liquid
        // Check solid
        if (block.isSolid() && !block.isReplaceable())
            return false;

        return true;
    }
}
