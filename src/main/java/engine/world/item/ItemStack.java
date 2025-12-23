package engine.world.item;

import engine.world.item.nbt.NBTTagCompound;
import engine.registry.ResourceLocation;
import java.util.Optional;

/**
 * Represents a stack of items with count, damage, and NBT data.
 * 
 * This is the in-game representation of items (inventory, etc.).
 * ItemStack is mutable - operations modify the stack.
 */
public class ItemStack {

    public static final ItemStack EMPTY = new ItemStack((Item) null, 0);

    private Item item;
    private int count;
    private int damage;
    private NBTTagCompound nbt;

    // ==================== CONSTRUCTORS ====================

    public ItemStack(Item item) {
        this(item, 1);
    }

    public ItemStack(Item item, int count) {
        this(item, count, 0);
    }

    public ItemStack(Item item, int count, int damage) {
        this.item = item;
        this.count = count;
        this.damage = damage;
        this.nbt = null;
    }

    // ==================== BASIC PROPERTIES ====================

    public Item getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(0, count);
    }

    public boolean isEmpty() {
        return item == null || count <= 0;
    }

    public int getMaxStackSize() {
        return isEmpty() ? 0 : item.getMaxStackSize();
    }

    // ==================== STACK OPERATIONS ====================

    /**
     * Increase stack size
     */
    public void grow(int amount) {
        setCount(count + amount);
    }

    /**
     * Decrease stack size
     */
    public void shrink(int amount) {
        setCount(count - amount);
    }

    /**
     * Split this stack, removing count from this and returning a new stack
     */
    public ItemStack split(int amount) {
        if (isEmpty()) {
            return EMPTY;
        }

        int toSplit = Math.min(amount, count);
        ItemStack result = copy();
        result.setCount(toSplit);
        shrink(toSplit);
        return result;
    }

    /**
     * Check if this stack can be merged with another.
     */
    public boolean canStackWith(ItemStack other) {
        if (this.isEmpty() || other.isEmpty())
            return false;
        if (this.item != other.item)
            return false;
        // Add NBT/damage check here if needed
        return true;
    }

    /**
     * Check if two stacks can be merged (same item, damage, NBT)
     */
    public boolean canMerge(ItemStack other) {
        if (isEmpty() || other.isEmpty()) {
            return false;
        }
        if (this.item != other.item) {
            return false;
        }
        if (this.damage != other.damage) {
            return false;
        }
        // Check NBT equality
        if (this.nbt == null && other.nbt == null) {
            return true;
        }
        if (this.nbt == null || other.nbt == null) {
            return false;
        }
        return this.nbt.equals(other.nbt);
    }

    /**
     * Merge another stack into this one (modifies both stacks)
     * 
     * @return Amount that couldn't be merged
     */
    public int merge(ItemStack other) {
        if (!canMerge(other)) {
            return other.getCount();
        }

        int maxSize = getMaxStackSize();
        int spaceLeft = maxSize - this.count;
        int toMerge = Math.min(spaceLeft, other.count);

        this.grow(toMerge);
        other.shrink(toMerge);

        return other.count;
    }

    /**
     * Create a deep copy of this stack
     */
    public ItemStack copy() {
        if (isEmpty()) {
            return EMPTY;
        }

        ItemStack copy = new ItemStack(item, count, damage);
        if (nbt != null) {
            copy.nbt = nbt.copy();
        }
        return copy;
    }

    // ==================== DURABILITY ====================

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = Math.max(0, damage);
    }

    public int getMaxDamage() {
        return isEmpty() ? 0 : item.getMaxDurability();
    }

    /**
     * Damage the item by a certain amount
     * 
     * @return true if item broke (damage >= max durability)
     */
    public boolean damageItem(int amount) {
        if (!isDamageable()) {
            return false;
        }

        damage += amount;

        if (damage >= getMaxDamage()) {
            // Item broke
            setCount(0);
            return true;
        }
        return false;
    }

    public boolean isDamaged() {
        return isDamageable() && damage > 0;
    }

    public boolean isDamageable() {
        return !isEmpty() && item.isDamageable();
    }

    // ==================== NBT DATA ====================

    public NBTTagCompound save(NBTTagCompound tag) {
        ResourceLocation id = item.getRegistryId();
        tag.setString("id", id.toString());
        tag.setInt("Count", count);
        tag.setInt("Damage", damage);

        if (nbt != null) {
            tag.setTag("tag", nbt.copy());
        }
        return tag;
    }

    public static ItemStack load(NBTTagCompound tag) {
        if (!tag.hasKey("id")) {
            return EMPTY;
        }

        String idStr = tag.getString("id");
        Optional<Item> item = Item.get(idStr);
        if (!item.isPresent()) {
            return EMPTY; // Item no longer exists
        }

        int count = tag.getInt("Count");
        int damage = tag.getInt("Damage");

        ItemStack stack = new ItemStack(item.get(), count, damage);

        if (tag.hasKey("tag")) {
            stack.setNBT(tag.getTag("tag").copy());
        }

        return stack;
    }

    public boolean hasNBT() {
        return nbt != null && !nbt.isEmpty();
    }

    public NBTTagCompound getNBT() {
        return nbt;
    }

    public void setNBT(NBTTagCompound nbt) {
        this.nbt = nbt;
    }

    /**
     * Get NBT or create if doesn't exist
     */
    public NBTTagCompound getOrCreateNBT() {
        if (nbt == null) {
            nbt = new NBTTagCompound();
        }
        return nbt;
    }

    /**
     * Remove NBT data
     */
    public void clearNBT() {
        this.nbt = null;
    }

    // ==================== UTILITY ====================

    @Override
    public String toString() {
        if (isEmpty()) {
            return "ItemStack{EMPTY}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ItemStack{");
        sb.append(item.getRegistryId());
        sb.append(" x").append(count);
        if (damage > 0) {
            sb.append(", damage=").append(damage);
        }
        if (hasNBT()) {
            sb.append(", nbt=").append(nbt);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof ItemStack))
            return false;
        ItemStack other = (ItemStack) obj;

        if (this.isEmpty() && other.isEmpty()) {
            return true;
        }

        return this.item == other.item &&
                this.count == other.count &&
                this.damage == other.damage &&
                java.util.Objects.equals(this.nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        if (isEmpty()) {
            return 0;
        }
        int result = item.hashCode();
        result = 31 * result + count;
        result = 31 * result + damage;
        result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
        return result;
    }
}
