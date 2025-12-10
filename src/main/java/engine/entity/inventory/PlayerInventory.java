package engine.entity.inventory;

import engine.world.item.ItemStack;

/**
 * Player-specific inventory with hotbar support.
 * 
 * Layout:
 * - Slots 0-8: Hotbar (quick access)
 * - Slots 9-35: Main inventory
 * Total: 36 slots
 */
public class PlayerInventory extends Inventory {

    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_SIZE = 27;
    public static final int TOTAL_SIZE = HOTBAR_SIZE + MAIN_SIZE;

    private int selectedHotbarSlot = 0; // 0-8

    public PlayerInventory() {
        super(TOTAL_SIZE);
    }

    // ==================== HOTBAR ====================

    /**
     * Get the currently selected hotbar slot index (0-8)
     */
    public int getSelectedSlot() {
        return selectedHotbarSlot;
    }

    /**
     * Set the selected hotbar slot (0-8)
     */
    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            this.selectedHotbarSlot = slot;
        }
    }

    /**
     * Get the item stack in the currently selected hotbar slot
     */
    public ItemStack getSelectedStack() {
        return getStack(selectedHotbarSlot);
    }

    /**
     * Get a specific hotbar slot (0-8)
     */
    public ItemStack getHotbarStack(int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE) {
            return ItemStack.EMPTY;
        }
        return getStack(slot);
    }

    /**
     * Set a specific hotbar slot (0-8)
     */
    public void setHotbarStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            setStack(slot, stack);
        }
    }

    // ==================== MAIN INVENTORY ====================

    /**
     * Get a slot from the main inventory (0-26 maps to slots 9-35)
     */
    public ItemStack getMainStack(int slot) {
        if (slot < 0 || slot >= MAIN_SIZE) {
            return ItemStack.EMPTY;
        }
        return getStack(HOTBAR_SIZE + slot);
    }

    /**
     * Set a slot in the main inventory (0-26 maps to slots 9-35)
     */
    public void setMainStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < MAIN_SIZE) {
            setStack(HOTBAR_SIZE + slot, stack);
        }
    }

    // ==================== UTILITY ====================

    /**
     * Select next hotbar slot (wraps around)
     */
    public void selectNext() {
        selectedHotbarSlot = (selectedHotbarSlot + 1) % HOTBAR_SIZE;
    }

    /**
     * Select previous hotbar slot (wraps around)
     */
    public void selectPrevious() {
        selectedHotbarSlot--;
        if (selectedHotbarSlot < 0) {
            selectedHotbarSlot = HOTBAR_SIZE - 1;
        }
    }

    @Override
    public String toString() {
        return "PlayerInventory{" +
                "selected=" + selectedHotbarSlot +
                ", hotbar=" + countNonEmptySlots(0, HOTBAR_SIZE) + "/" + HOTBAR_SIZE +
                ", main=" + countNonEmptySlots(HOTBAR_SIZE, TOTAL_SIZE) + "/" + MAIN_SIZE +
                "}";
    }

    private int countNonEmptySlots(int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (!slots[i].isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
