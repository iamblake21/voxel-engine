package engine.entity.ai.goals;

import engine.entity.LivingEntity;
import engine.entity.ai.Goal;

/**
 * Return to spawn position when too far away.
 */
public class ReturnHomeGoal extends Goal {
    
    private final float maxDistance;
    private final float speed;
    
    private boolean returning = false;
    
    /**
     * @param entity Entity to control
     * @param maxDistance Maximum distance from spawn before returning
     * @param speed Movement speed multiplier
     */
    public ReturnHomeGoal(LivingEntity entity, float maxDistance, float speed) {
        super(entity);
        this.maxDistance = maxDistance;
        this.speed = speed;
        setFlags(Flag.MOVE, Flag.LOOK);
    }
    
    @Override
    public boolean canStart() {
        float distSq = entity.getDistanceFromSpawnSq();
        return distSq > maxDistance * maxDistance;
    }
    
    @Override
    public boolean canContinue() {
        float distSq = entity.getDistanceFromSpawnSq();
        // Continue until close to home
        return distSq > 4f;
    }
    
    @Override
    public void start() {
        returning = true;
    }
    
    @Override
    public void tick() {
        entity.moveToward(entity.getSpawnX(), entity.getSpawnZ(), speed);
    }
    
    @Override
    public void stop() {
        returning = false;
    }
    
    @Override
    public boolean isInterruptable() {
        // Don't interrupt when returning home
        return false;
    }
}
