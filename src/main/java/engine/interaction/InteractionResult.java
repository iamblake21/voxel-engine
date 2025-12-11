package engine.interaction;

/**
 * Result of an interaction attempt.
 * 
 * Used by items, entities, and block entities to communicate
 * the outcome of a right-click interaction.
 */
public enum InteractionResult {
    
    /**
     * Interaction succeeded and should stop processing.
     * Example: Opened a chest GUI.
     */
    SUCCESS,
    
    /**
     * Interaction succeeded and the item should be consumed/damaged.
     * Example: Ate food, placed a block.
     */
    CONSUME,
    
    /**
     * Interaction didn't apply, try the next handler.
     * Example: Right-clicked with wrong item type.
     */
    PASS,
    
    /**
     * Interaction explicitly failed, stop processing.
     * Example: Can't place block here (collision).
     */
    FAIL;
    
    /**
     * Check if this result indicates success (SUCCESS or CONSUME).
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == CONSUME;
    }
    
    /**
     * Check if this result should consume the item.
     */
    public boolean shouldConsume() {
        return this == CONSUME;
    }
    
    /**
     * Check if processing should continue to next handler.
     */
    public boolean shouldContinue() {
        return this == PASS;
    }
    
    /**
     * Helper to create result based on boolean success.
     */
    public static InteractionResult of(boolean success) {
        return success ? SUCCESS : FAIL;
    }
    
    /**
     * Helper to create consumable result based on boolean success.
     */
    public static InteractionResult consumeIf(boolean success) {
        return success ? CONSUME : FAIL;
    }
}
