// ============================================================
// NOTA: Questa classe dovrebbe già esistere nel tuo progetto
// Se non esiste, eccola:
// ============================================================

package engine.entity.inventory;

import engine.world.item.ItemStack;

/**
 * Base inventory class.
 */
public class Inventory {

    protected final ItemStack[] slots;

    // Serialization
    public void writeToNBT(engine.world.item.nbt.NBTTagCompound tag) {
        java.util.List<engine.world.item.nbt.NBTTagCompound> list = new java.util.ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isEmpty()) {
                engine.world.item.nbt.NBTTagCompound itemTag = new engine.world.item.nbt.NBTTagCompound();
                itemTag.setInt("Slot", i);
                slots[i].save(itemTag);
                list.add(itemTag);
            }
        }
        tag.setList("Items", list);
    }

    public void readFromNBT(engine.world.item.nbt.NBTTagCompound tag) {
        clear();
        if (tag.hasKey("Items")) {
            @SuppressWarnings("unchecked")
            java.util.List<engine.world.item.nbt.NBTTagCompound> list = (java.util.List<engine.world.item.nbt.NBTTagCompound>) tag
                    .getList("Items");
            for (engine.world.item.nbt.NBTTagCompound itemTag : list) {
                int slot = itemTag.getInt("Slot");
                ItemStack stack = ItemStack.load(itemTag);
                setStack(slot, stack);
            }
        }
    }

    public Inventory(int size) {
        this.slots = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    public int getSize() {
        return slots.length;
    }

    public ItemStack getStack(int slot) {
        if (slot < 0 || slot >= slots.length) {
            return ItemStack.EMPTY;
        }
        return slots[slot];
    }

    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < slots.length) {
            slots[slot] = stack != null ? stack : ItemStack.EMPTY;
        }
    }

    public ItemStack removeStack(int slot) {
        if (slot < 0 || slot >= slots.length) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots[slot];
        slots[slot] = ItemStack.EMPTY;
        return stack;
    }

    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void clear() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    /**
     * Try to add an item stack to the inventory.
     * 
     * @return The remaining items that couldn't be added
     */
    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // First pass: try to merge with existing stacks
        for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
            if (slots[i].canMerge(stack)) {
                slots[i].merge(stack);
            }
        }

        // Second pass: find empty slots
        for (int i = 0; i < slots.length && !stack.isEmpty(); i++) {
            if (slots[i].isEmpty()) {
                slots[i] = stack.copy();
                stack.setCount(0);
            }
        }

        return stack;
    }
}
