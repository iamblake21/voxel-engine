package engine.interaction;

import engine.entity.Player;

/**
 * Interface for anything that can be interacted with via right-click.
 * 
 * Implemented by:
 * - Entities (NPCs, animals, etc.)
 * - Block Entities (chests, furnaces, etc.)
 * 
 * This provides a unified interaction system where the InteractionManager
 * can handle both entity and block interactions consistently.
 */
public interface IInteractable {
    
    /**
     * Called when a player right-clicks on this object.
     * 
     * @param player The player interacting
     * @return The result of the interaction
     */
    InteractionResult onInteract(Player player);
    
    /**
     * Check if this object can currently be interacted with.
     * 
     * @param player The player attempting to interact
     * @return true if interaction is allowed
     */
    default boolean canInteract(Player player) {
        return true;
    }
    
    /**
     * Get the maximum distance from which this can be interacted with.
     * Default is 6 blocks (same as block reach).
     */
    default float getInteractionRange() {
        return 6.0f;
    }
}
