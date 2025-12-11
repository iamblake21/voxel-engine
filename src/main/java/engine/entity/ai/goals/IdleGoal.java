package engine.entity.ai.goals;

import engine.entity.Entity;
import engine.entity.LivingEntity;
import engine.entity.ai.Goal;

import java.util.Random;

/**
 * Default idle behavior - occasionally look around.
 */
public class IdleGoal extends Goal {
    
    private final Random random = new Random();
    private int lookCooldown = 0;
    private float targetYaw;
    
    public IdleGoal(LivingEntity entity) {
        super(entity);
        setFlags(Flag.LOOK);
    }
    
    @Override
    public boolean canStart() {
        // Always can idle as fallback
        return true;
    }
    
    @Override
    public void start() {
        lookCooldown = 20 + random.nextInt(40);
        targetYaw = entity.getYaw();
    }
    
    @Override
    public void tick() {
        lookCooldown--;
        
        if (lookCooldown <= 0) {
            // Pick a new random direction to look
            targetYaw = entity.getYaw() + (random.nextFloat() - 0.5f) * 90f;
            lookCooldown = 40 + random.nextInt(80); // 2-6 seconds
        }
        
        // Slowly turn toward target
        float currentYaw = entity.getYaw();
        float newYaw = Entity.lerpAngle(currentYaw, targetYaw, 0.05f);
        entity.setRotation(newYaw, entity.getPitch());
        
        // Also turn body
        entity.setBodyYaw(Entity.lerpAngle(entity.getBodyYaw(), newYaw, 0.03f));
    }
}
