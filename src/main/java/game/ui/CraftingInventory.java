package game.ui;

import engine.entity.inventory.Inventory;
import engine.ui.ContainerAccess;
import engine.world.item.ItemStack;
import engine.entity.Player;

public class CraftingInventory extends Inventory implements ContainerAccess {

    public CraftingInventory() {
        super(9); // 3x3 grid
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
        // Drop any remaining items in the crafting grid when closed
        for (int i = 0; i < getSize(); i++) {
            ItemStack stack = getStack(i);
            if (!stack.isEmpty()) {
                // Return to player inventory or drop
                player.getInventory().addItem(stack);
                // If inventory full, drop on ground (handled by player logic or we should do
                // it)
                // For now, if inventory full, it vanishes (MVP limitation or simple fix)
                // TODO: Drop entity if inventory full
            }
        }
    }
}
