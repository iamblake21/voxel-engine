package engine.entity.ai.goals;

import engine.entity.LivingEntity;
import engine.entity.ai.Goal;

import java.util.Random;

/**
 * Randomly wander around.
 */
public class WanderGoal extends Goal {
    
    private final Random random = new Random();
    private final float speed;
    private final int minWaitTicks;
    private final int maxWaitTicks;
    
    private float targetX, targetZ;
    private int waitTicks;
    private int stuckTicks;
    private float lastX, lastZ;
    
    /**
     * @param entity Entity to control
     * @param speed Movement speed multiplier
     * @param minWaitTicks Minimum ticks to wait between wanders
     * @param maxWaitTicks Maximum ticks to wait between wanders
     */
    public WanderGoal(LivingEntity entity, float speed, int minWaitTicks, int maxWaitTicks) {
        super(entity);
        this.speed = speed;
        this.minWaitTicks = minWaitTicks;
        this.maxWaitTicks = maxWaitTicks;
        setFlags(Flag.MOVE, Flag.LOOK);
    }
    
    @Override
    public boolean canStart() {
        waitTicks--;
        if (waitTicks > 0) {
            return false;
        }
        
        // Pick random nearby position
        float range = 8f;
        targetX = entity.getX() + (random.nextFloat() - 0.5f) * 2 * range;
        targetZ = entity.getZ() + (random.nextFloat() - 0.5f) * 2 * range;
        
        return true;
    }
    
    @Override
    public boolean canContinue() {
        // Stop if reached target or stuck
        float dx = targetX - entity.getX();
        float dz = targetZ - entity.getZ();
        float distSq = dx * dx + dz * dz;
        
        return distSq > 1f && stuckTicks < 40;
    }
    
    @Override
    public void start() {
        stuckTicks = 0;
        lastX = entity.getX();
        lastZ = entity.getZ();
    }
    
    @Override
    public void tick() {
        // Check if stuck
        float movedX = entity.getX() - lastX;
        float movedZ = entity.getZ() - lastZ;
        if (movedX * movedX + movedZ * movedZ < 0.01f) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
        lastX = entity.getX();
        lastZ = entity.getZ();
        
        // Move toward target
        entity.moveToward(targetX, targetZ, speed);
    }
    
    @Override
    public void stop() {
        // Reset wait time
        waitTicks = minWaitTicks + random.nextInt(maxWaitTicks - minWaitTicks + 1);
    }
}
