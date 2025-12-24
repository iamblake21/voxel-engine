package game.ui;

import engine.entity.inventory.Inventory;
import engine.ui.ContainerAccess;
import engine.world.item.ItemStack;
import engine.entity.Player;

/**
 * 2x2 crafting inventory for the player inventory screen.
 * Unlike CraftingInventory (3x3), this is for quick crafting in the player's
 * inventory.
 */
public class PlayerCraftingInventory extends Inventory implements ContainerAccess {

    public PlayerCraftingInventory() {
        super(4); // 2x2 grid
    }

    @Override
    public int getContainerSize() {
        return getSize();
    }

    @Override
    public ItemStack getItem(int slot) {
        return getStack(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        setStack(slot, stack);
    }

    @Override
    public void onClose(Player player) {
        // Return any remaining items in the crafting grid to the player
        for (int i = 0; i < getSize(); i++) {
            ItemStack stack = getStack(i);
            if (!stack.isEmpty()) {
                player.getInventory().addItem(stack);
                setStack(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Get the grid width (for recipe matching).
     */
    public int getWidth() {
        return 2;
    }

    /**
     * Get the grid height (for recipe matching).
     */
    public int getHeight() {
        return 2;
    }
}
