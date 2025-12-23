package engine.ui;

import engine.entity.Player;
import engine.world.item.ItemStack;

/**
 * Interface for accessing a container backing a GUI.
 * Used to decouple ContainerGui from ContainerBlockEntity.
 */
public interface ContainerAccess {

    int getContainerSize();

    ItemStack getItem(int slot);

    void setItem(int slot, ItemStack stack);

    default void onClose(Player player) {
    }
}
