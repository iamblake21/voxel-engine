package engine.entity.inventory;

import engine.world.item.Item;
import engine.world.item.ItemStack;

/**
 * Generic inventory container that can be used by any entity.
 * Stores ItemStacks in slots and provides methods for adding/removing items.
 */
public class Inventory {

    protected final ItemStack[] slots;
    protected final int size;

    /**
     * Create an inventory with specified number of slots
     */
    public Inventory(int size) {
        this.size = size;
        this.slots = new ItemStack[size];

        // Initialize all slots as empty
        for (int i = 0; i < size; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    // ==================== BASIC OPERATIONS ====================

    /**
     * Get the stack in a specific slot
     */
    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= size) {
            return ItemStack.EMPTY;
        }
        return slots[slot];
    }

    /**
     * Set the stack in a specific slot
     */
    public void setStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= size) {
            return;
        }
        slots[slot] = stack == null ? ItemStack.EMPTY : stack;
    }

    /**
     * Get the size of this inventory
     */
    public int getSize() {
        return size;
    }

    /**
     * Check if inventory is completely empty
     */
    public boolean isEmpty() {
        for (int i = 0; i < size; i++) {
            if (!slots[i].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clear all slots
     */
    public void clear() {
        for (int i = 0; i < size; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    // ==================== ITEM OPERATIONS ====================

    /**
     * Add an item stack to the inventory.
     * First tries to merge with existing stacks, then finds empty slot.
     * 
     * @param stack The stack to add
     * @return Remainder (what couldn't fit), or EMPTY if all was added
     */
    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        // First pass: try to merge with existing stacks
        for (int i = 0; i < size; i++) {
            if (slots[i].isEmpty()) {
                continue;
            }

            if (slots[i].canMerge(remaining)) {
                slots[i].merge(remaining);
                if (remaining.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        // Second pass: put in empty slots
        for (int i = 0; i < size; i++) {
            if (slots[i].isEmpty()) {
                int toAdd = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                slots[i] = remaining.split(toAdd);

                if (remaining.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        // Return whatever couldn't fit
        return remaining;
    }

    /**
     * Remove a certain amount from a slot
     * 
     * @param slot  Slot index
     * @param count Amount to remove
     * @return The removed stack
     */
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= size) {
            return ItemStack.EMPTY;
        }

        if (slots[slot].isEmpty()) {
            return ItemStack.EMPTY;
        }

        return slots[slot].split(count);
    }

    /**
     * Remove entire stack from a slot
     */
    public ItemStack removeStack(int slot) {
        if (slot < 0 || slot >= size) {
            return ItemStack.EMPTY;
        }

        ItemStack result = slots[slot];
        slots[slot] = ItemStack.EMPTY;
        return result;
    }

    // ==================== UTILITY ====================

    /**
     * Find the first slot containing a specific item
     * 
     * @return Slot index, or -1 if not found
     */
    public int findStack(Item item) {
        for (int i = 0; i < size; i++) {
            if (!slots[i].isEmpty() && slots[i].getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Check if inventory has space for a stack
     */
    public boolean hasSpace(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        ItemStack test = stack.copy();

        // Check if can merge with existing stacks
        for (int i = 0; i < size; i++) {
            if (!slots[i].isEmpty() && slots[i].canMerge(test)) {
                int spaceLeft = slots[i].getMaxStackSize() - slots[i].getCount();
                if (spaceLeft >= test.getCount()) {
                    return true;
                }
                test.shrink(spaceLeft);
            }
        }

        // Check for empty slots
        for (int i = 0; i < size; i++) {
            if (slots[i].isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Count total amount of a specific item
     */
    public int countItem(Item item) {
        int total = 0;
        for (int i = 0; i < size; i++) {
            if (!slots[i].isEmpty() && slots[i].getItem() == item) {
                total += slots[i].getCount();
            }
        }
        return total;
    }

    @Override
    public String toString() {
        int nonEmpty = 0;
        for (int i = 0; i < size; i++) {
            if (!slots[i].isEmpty()) {
                nonEmpty++;
            }
        }
        return "Inventory{size=" + size + ", filled=" + nonEmpty + "/" + size + "}";
    }
}
