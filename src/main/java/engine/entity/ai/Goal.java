package engine.entity.ai;

import engine.entity.LivingEntity;

import java.util.EnumSet;

/**
 * Base class for AI goals.
 * 
 * Goals control entity behavior with priority-based selection.
 * Lower priority number = higher importance.
 */
public abstract class Goal {
    
    /**
     * Flags indicate what systems a goal uses.
     * Goals with conflicting flags cannot run simultaneously.
     */
    public enum Flag {
        MOVE,   // Controls movement
        LOOK,   // Controls head rotation
        JUMP,   // Controls jumping
        TARGET  // Controls target selection
    }
    
    protected final LivingEntity entity;
    private EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
    
    public Goal(LivingEntity entity) {
        this.entity = entity;
    }
    
    /**
     * Set which flags this goal uses.
     */
    protected void setFlags(Flag... flags) {
        this.flags = EnumSet.noneOf(Flag.class);
        for (Flag f : flags) {
            this.flags.add(f);
        }
    }
    
    public EnumSet<Flag> getFlags() {
        return flags;
    }
    
    /**
     * Check if this goal can start.
     * Called every tick when goal is not running.
     */
    public abstract boolean canStart();
    
    /**
     * Check if this goal should continue running.
     * Called every tick when goal is running.
     * Default: same as canStart().
     */
    public boolean canContinue() {
        return canStart();
    }
    
    /**
     * Check if this goal can be interrupted by higher priority goals.
     * Default: true.
     */
    public boolean isInterruptable() {
        return true;
    }
    
    /**
     * Called when goal starts.
     */
    public void start() {}
    
    /**
     * Called every tick while goal is running.
     */
    public abstract void tick();
    
    /**
     * Called when goal stops (interrupted or finished).
     */
    public void stop() {}
    
    /**
     * Require a flag for this goal to work.
     */
    public boolean requiresFlag(Flag flag) {
        return flags.contains(flag);
    }
}
