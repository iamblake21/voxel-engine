package engine.entity.ai;

import engine.entity.Entity;
import engine.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * AI controller for living entities.
 * Contains goal selectors and memory.
 */
public class EntityBrain {
    
    private final LivingEntity entity;
    
    // Goal selectors
    private final GoalSelector goalSelector;
    private final GoalSelector targetSelector;
    
    // Memory - stores information about the world
    private final Map<String, Object> memory = new HashMap<>();
    
    // Current target entity
    private Entity target;
    private int targetLostTicks = 0;
    
    public EntityBrain(LivingEntity entity) {
        this.entity = entity;
        this.goalSelector = new GoalSelector(entity);
        this.targetSelector = new GoalSelector(entity);
    }
    
    /**
     * Update AI each tick.
     */
    public void tick() {
        // Update target tracking
        updateTarget();
        
        // Run goal selectors
        targetSelector.tick();
        goalSelector.tick();
    }
    
    private void updateTarget() {
        if (target != null) {
            if (target.isRemoved()) {
                target = null;
                targetLostTicks = 0;
            } else {
                float distSq = entity.distanceToSq(target);
                if (distSq > 32 * 32) {
                    // Target too far
                    targetLostTicks++;
                    if (targetLostTicks > 100) {
                        target = null;
                        targetLostTicks = 0;
                    }
                } else {
                    targetLostTicks = 0;
                }
            }
        }
    }
    
    // ==================== GOAL SELECTORS ====================
    
    public GoalSelector getGoalSelector() {
        return goalSelector;
    }
    
    public GoalSelector getTargetSelector() {
        return targetSelector;
    }
    
    // ==================== TARGET ====================
    
    public Entity getTarget() {
        return target;
    }
    
    public void setTarget(Entity target) {
        this.target = target;
        this.targetLostTicks = 0;
    }
    
    public boolean hasTarget() {
        return target != null && !target.isRemoved();
    }
    
    // ==================== MEMORY ====================
    
    public void remember(String key, Object value) {
        memory.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T recall(String key) {
        return (T) memory.get(key);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T recall(String key, T defaultValue) {
        Object value = memory.get(key);
        if (value == null) return defaultValue;
        return (T) value;
    }
    
    public boolean remembers(String key) {
        return memory.containsKey(key);
    }
    
    public void forget(String key) {
        memory.remove(key);
    }
    
    public void forgetAll() {
        memory.clear();
    }
    
    // Common memory keys
    public static final String MEMORY_LAST_PLAYER_POS = "lastPlayerPos";
    public static final String MEMORY_HOME_POS = "homePos";
    public static final String MEMORY_DANGER_POS = "dangerPos";
    public static final String MEMORY_TICKS_SINCE_PLAYER = "ticksSincePlayer";
}
