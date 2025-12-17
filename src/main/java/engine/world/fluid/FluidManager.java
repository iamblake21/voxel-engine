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

        // ====================================================================
        // RULE 3: INFINITE SOURCE
        // A flowing block between 2+ sources becomes a source
        // ====================================================================
        if (currentLevel < maxLevel) {
            int sourceCount = 0;
            if (isSourceAt(x + 1, y, z, block, maxLevel))
                sourceCount++;
            if (isSourceAt(x - 1, y, z, block, maxLevel))
                sourceCount++;
            if (isSourceAt(x, y, z + 1, block, maxLevel))
                sourceCount++;
            if (isSourceAt(x, y, z - 1, block, maxLevel))
                sourceCount++;

            if (sourceCount >= 2) {
                world.setFluidLevel(x, y, z, maxLevel);
                currentLevel = maxLevel;
            }
        }

        // ====================================================================
        // DEPROPAGATION: Flowing water dries if not supported by higher neighbor
        // ====================================================================
        if (currentLevel < maxLevel) {
            int supportLevel = calculateSupportLevel(x, y, z, block, viscosity);

            if (supportLevel < currentLevel) {
                // Decay to supported level
                if (supportLevel <= 0) {
                    world.setBlock(x, y, z, Blocks.AIR().getNumericId());
                    world.setFluidLevel(x, y, z, 0);
                } else {
                    world.setFluidLevel(x, y, z, supportLevel);
                    scheduleUpdate(x, y, z, tickRate);
                }
                scheduleNeighbors(x, y, z, tickRate);
                return;
            }
        }

        // ====================================================================
        // RULE 1: GRAVITY - Can I fall?
        // If YES: fall and STOP. No horizontal spread.
        // ====================================================================

        // CRITICAL: Check if there's "fallable" space below (air or lower-level liquid)
        boolean canFallInto = canFlowInto(x, y - 1, z);

        if (tryFall(x, y, z, block, currentLevel, tickRate)) {
            return; // Fell! Done with this update.
        }

        // ====================================================================
        // RULE 2: VISCOSITY - Can't fall, spread horizontally
        // BUT ONLY if there's SOLID ground below!
        // If canFallInto is true, it means there's air or liquid below -
        // we shouldn't spread horizontally in that case (even if tryFall failed
        // because the liquid below is at same level - it will fall eventually)
        // ====================================================================
        if (canFallInto) {
            // There's air or liquid below. Don't spread horizontally.
            // Either we just fell, or we're waiting for the liquid below to fall.
            return;
        }

        // Only spread if there's truly solid ground below
        int spreadLevel = currentLevel - viscosity;
        if (spreadLevel > 0) {
            trySpread(x + 1, y, z, block, spreadLevel, tickRate);
            trySpread(x - 1, y, z, block, spreadLevel, tickRate);
            trySpread(x, y, z + 1, block, spreadLevel, tickRate);
            trySpread(x, y, z - 1, block, spreadLevel, tickRate);
        }
    }

    /**
     * Check if position has a source block of the given type
     */
    private boolean isSourceAt(int x, int y, int z, Block fluidType, int maxLevel) {
        Block b = world.getBlockType(x, y, z);
        return b.isLiquid() &&
                b.getNumericId() == fluidType.getNumericId() &&
                world.getFluidLevel(x, y, z) == maxLevel;
    }

    /**
     * Calculate what level this block should have based on neighbors
     */
    private int calculateSupportLevel(int x, int y, int z, Block block, int viscosity) {
        int maxSupport = 0;

        // Check above (waterfall feeds us)
        Block above = world.getBlockType(x, y + 1, z);
        if (above.getNumericId() == block.getNumericId()) {
            maxSupport = world.getFluidLevel(x, y + 1, z);
        }

        // Check horizontal neighbors
        int[] dx = { 1, -1, 0, 0 };
        int[] dz = { 0, 0, 1, -1 };
        for (int i = 0; i < 4; i++) {
            Block neighbor = world.getBlockType(x + dx[i], y, z + dz[i]);
            if (neighbor.getNumericId() == block.getNumericId()) {
                int neighborLevel = world.getFluidLevel(x + dx[i], y, z + dz[i]);
                int flowsTo = neighborLevel - viscosity;
                if (flowsTo > maxSupport) {
                    maxSupport = flowsTo;
                }
            }
        }

        return maxSupport;
    }

    /**
     * RULE 1: Try to fall. Returns true if we fell.
     */
    private boolean tryFall(int x, int y, int z, Block block, int level, int tickRate) {
        // Can we flow into the block below?
        if (!canFlowInto(x, y - 1, z)) {
            return false; // Solid below, can't fall
        }

        Block below = world.getBlockType(x, y - 1, z);

        // Is it the same liquid at same or higher level?
        if (below.isLiquid() && below.getNumericId() == block.getNumericId()) {
            int levelBelow = world.getFluidLevel(x, y - 1, z);
            if (levelBelow >= level) {
                return false; // Below is already at same or higher level, can't fall
            }
        }

        // FALL!
        world.setBlock(x, y - 1, z, Blocks.getId(block));
        world.setFluidLevel(x, y - 1, z, level);
        scheduleUpdate(x, y - 1, z, tickRate);
        return true;
    }

    /**
     * RULE 2: Try to spread horizontally to a position
     */
    private void trySpread(int x, int y, int z, Block block, int level, int tickRate) {
        if (!canFlowInto(x, y, z)) {
            return; // Can't flow into solid
        }

        Block target = world.getBlockType(x, y, z);

        // If target already has same liquid at higher/equal level, skip
        if (target.isLiquid() && target.getNumericId() == block.getNumericId()) {
            if (world.getFluidLevel(x, y, z) >= level) {
                return;
            }
        }

        // Spread!
        world.setBlock(x, y, z, Blocks.getId(block));
        world.setFluidLevel(x, y, z, level);
        scheduleUpdate(x, y, z, tickRate);
    }

    /**
     * Schedule updates for all neighbors
     */
    private void scheduleNeighbors(int x, int y, int z, int delay) {
        scheduleUpdate(x, y - 1, z, delay);
        scheduleUpdate(x, y + 1, z, delay);
        scheduleUpdate(x + 1, y, z, delay);
        scheduleUpdate(x - 1, y, z, delay);
        scheduleUpdate(x, y, z + 1, delay);
        scheduleUpdate(x, y, z - 1, delay);
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
