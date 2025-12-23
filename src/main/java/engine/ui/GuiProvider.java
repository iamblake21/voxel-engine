package engine.ui;

import engine.entity.Player;

/**
 * Interface for any object that can provide a Container GUI.
 * Implemented by ContainerBlockEntity, and also by transient logic like
 * Crafting Tables.
 */
public interface GuiProvider {

    /**
     * Create the GUI for this provider.
     * 
     * @param player The player opening the GUI
     * @param width  Window width
     * @param height Window height
     * @return The created GUI instance
     */
    ContainerGui createGui(Player player, int width, int height);

}
