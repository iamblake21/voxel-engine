package engine.entity.ai;

import engine.entity.LivingEntity;

import java.util.*;

/**
 * Manages and runs AI goals for an entity.
 * 
 * Goals are prioritized - lower number = higher priority.
 * Only one goal per flag can run at a time.
 */
public class GoalSelector {
    
    private final LivingEntity entity;
    private final List<PrioritizedGoal> goals = new ArrayList<>();
    private final Set<PrioritizedGoal> runningGoals = new HashSet<>();
    private final EnumSet<Goal.Flag> disabledFlags = EnumSet.noneOf(Goal.Flag.class);
    
    public GoalSelector(LivingEntity entity) {
        this.entity = entity;
    }
    
    /**
     * Add a goal with priority.
     * Lower priority number = more important.
     */
    public void addGoal(int priority, Goal goal) {
        goals.add(new PrioritizedGoal(priority, goal));
        goals.sort(Comparator.comparingInt(g -> g.priority));
    }
    
    /**
     * Remove a goal.
     */
    public void removeGoal(Goal goal) {
        goals.removeIf(pg -> pg.goal == goal);
        runningGoals.removeIf(pg -> pg.goal == goal);
    }
    
    /**
     * Remove all goals.
     */
    public void removeAllGoals() {
        for (PrioritizedGoal pg : runningGoals) {
            pg.goal.stop();
        }
        goals.clear();
        runningGoals.clear();
    }
    
    /**
     * Disable a flag - goals requiring this flag cannot run.
     */
    public void disableFlag(Goal.Flag flag) {
        disabledFlags.add(flag);
    }
    
    /**
     * Enable a flag.
     */
    public void enableFlag(Goal.Flag flag) {
        disabledFlags.remove(flag);
    }
    
    /**
     * Update goals each tick.
     */
    public void tick() {
        // Stop goals that can't continue
        Iterator<PrioritizedGoal> runningIter = runningGoals.iterator();
        while (runningIter.hasNext()) {
            PrioritizedGoal pg = runningIter.next();
            
            // Check if flags are disabled
            boolean flagDisabled = false;
            for (Goal.Flag flag : pg.goal.getFlags()) {
                if (disabledFlags.contains(flag)) {
                    flagDisabled = true;
                    break;
                }
            }
            
            if (flagDisabled || !pg.goal.canContinue()) {
                pg.goal.stop();
                runningIter.remove();
            }
        }
        
        // Try to start new goals
        for (PrioritizedGoal pg : goals) {
            if (runningGoals.contains(pg)) {
                continue;
            }
            
            // Check if any flags are disabled
            boolean canRun = true;
            for (Goal.Flag flag : pg.goal.getFlags()) {
                if (disabledFlags.contains(flag)) {
                    canRun = false;
                    break;
                }
            }
            
            if (!canRun) continue;
            
            // Check if conflicts with higher priority running goals
            if (hasConflict(pg)) {
                // Can we interrupt the conflicting goals?
                if (!canInterruptConflicts(pg)) {
                    continue;
                }
                // Interrupt them
                interruptConflicts(pg);
            }
            
            // Try to start
            if (pg.goal.canStart()) {
                pg.goal.start();
                runningGoals.add(pg);
            }
        }
        
        // Tick running goals
        for (PrioritizedGoal pg : runningGoals) {
            pg.goal.tick();
        }
    }
    
    private boolean hasConflict(PrioritizedGoal candidate) {
        for (PrioritizedGoal running : runningGoals) {
            if (flagsOverlap(candidate.goal.getFlags(), running.goal.getFlags())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean canInterruptConflicts(PrioritizedGoal candidate) {
        for (PrioritizedGoal running : runningGoals) {
            if (flagsOverlap(candidate.goal.getFlags(), running.goal.getFlags())) {
                // Higher priority (lower number) can interrupt
                if (candidate.priority >= running.priority) {
                    return false;
                }
                if (!running.goal.isInterruptable()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private void interruptConflicts(PrioritizedGoal candidate) {
        Iterator<PrioritizedGoal> iter = runningGoals.iterator();
        while (iter.hasNext()) {
            PrioritizedGoal running = iter.next();
            if (flagsOverlap(candidate.goal.getFlags(), running.goal.getFlags())) {
                running.goal.stop();
                iter.remove();
            }
        }
    }
    
    private boolean flagsOverlap(EnumSet<Goal.Flag> a, EnumSet<Goal.Flag> b) {
        for (Goal.Flag flag : a) {
            if (b.contains(flag)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if any goal is running.
     */
    public boolean hasRunningGoals() {
        return !runningGoals.isEmpty();
    }
    
    /**
     * Check if a specific goal is running.
     */
    public boolean isRunning(Goal goal) {
        for (PrioritizedGoal pg : runningGoals) {
            if (pg.goal == goal) return true;
        }
        return false;
    }
    
    /**
     * Get running goals (for debug).
     */
    public Set<Goal> getRunningGoals() {
        Set<Goal> result = new HashSet<>();
        for (PrioritizedGoal pg : runningGoals) {
            result.add(pg.goal);
        }
        return result;
    }
    
    // ==================== INNER CLASS ====================
    
    private static class PrioritizedGoal {
        final int priority;
        final Goal goal;
        
        PrioritizedGoal(int priority, Goal goal) {
            this.priority = priority;
            this.goal = goal;
        }
    }
}
