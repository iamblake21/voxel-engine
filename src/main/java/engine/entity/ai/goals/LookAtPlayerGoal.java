package engine.entity.ai.goals;

import engine.entity.Entity;
import engine.entity.LivingEntity;
import engine.entity.ai.Goal;

/**
 * Look at nearby players.
 */
public class LookAtPlayerGoal extends Goal {
    
    private final float range;
    private final float chance;
    
    private Entity target;
    private int lookTime;
    
    /**
     * @param entity Entity to control
     * @param range Maximum range to detect players
     * @param chance Probability (0-1) to look at player each check
     */
    public LookAtPlayerGoal(LivingEntity entity, float range, float chance) {
        super(entity);
        this.range = range;
        this.chance = chance;
        setFlags(Flag.LOOK);
    }
    
    @Override
    public boolean canStart() {
        if (Math.random() > chance) {
            return false;
        }
        
        // Find nearest player from brain memory
        target = findNearestPlayer();
        
        return target != null;
    }
    
    private Entity findNearestPlayer() {
        // Check brain's memory for player reference
        Entity remembered = entity.getBrain().recall("nearestPlayer");
        if (remembered != null && !remembered.isRemoved()) {
            float distSq = entity.distanceToSq(remembered);
            if (distSq < range * range) {
                return remembered;
            }
        }
        return null;
    }
    
    @Override
    public boolean canContinue() {
        if (target == null || target.isRemoved()) {
            return false;
        }
        
        float distSq = entity.distanceToSq(target);
        if (distSq > range * range) {
            return false;
        }
        
        return lookTime > 0;
    }
    
    @Override
    public void start() {
        lookTime = 40 + (int)(Math.random() * 40); // 2-4 seconds
    }
    
    @Override
    public void tick() {
        lookTime--;
        
        if (target != null) {
            // Look at target's eyes
            entity.lookAt(target.getX(), target.getY() + target.getEyeHeight(), target.getZ());
            
            // Slowly turn body to face target too
            float targetBodyYaw = (float) Math.toDegrees(
                Math.atan2(target.getZ() - entity.getZ(), 
                          target.getX() - entity.getX())
            ) - 90f;
            
            entity.setBodyYaw(
                Entity.lerpAngle(entity.getBodyYaw(), targetBodyYaw, 0.1f)
            );
        }
    }
    
    @Override
    public void stop() {
        target = null;
    }
}
