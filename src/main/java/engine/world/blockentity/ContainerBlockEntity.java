package engine.world.blockentity;

import engine.entity.Player;
import engine.entity.inventory.Inventory;
import engine.interaction.IInteractable;
import engine.interaction.InteractionResult;
import engine.ui.TexturedGui;
import engine.ui.GuiProvider;
import engine.ui.ContainerGui;
import engine.world.BlockPos;
import engine.world.item.ItemStack;
import engine.world.item.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for block entities that have an inventory (chest, furnace, etc.).
 * 
 * Provides:
 * - Inventory storage
 * - NBT serialization of items
 * - IInteractable implementation
 * - GUI creation (each subclass defines its own GUI)
 */
public abstract class ContainerBlockEntity extends BlockEntity
        implements IInteractable, GuiProvider, engine.ui.ContainerAccess {

    protected Inventory inventory;
    protected String customName;

    public ContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, int inventorySize) {
        super(type, pos);
        this.inventory = new Inventory(inventorySize);
    }

    // ==================== INVENTORY ACCESS ====================

    public Inventory getInventory() {
        return inventory;
    }

    public int getContainerSize() {
        return inventory.getSize();
    }

    public ItemStack getItem(int slot) {
        return inventory.getStack(slot);
    }

    public void setItem(int slot, ItemStack stack) {
        inventory.setStack(slot, stack);
        setChanged();
    }

    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = inventory.getStack(slot).split(amount);
        setChanged();
        return result;
    }

    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!inventory.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ==================== CUSTOM NAME ====================

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    public boolean hasCustomName() {
        return customName != null && !customName.isEmpty();
    }

    /**
     * Get display name (custom name or default).
     */
    public abstract String getDefaultName();

    public String getDisplayName() {
        return hasCustomName() ? customName : getDefaultName();
    }

    // ==================== GUI CREATION ====================

    /**
     * Create the GUI for this container.
     * Each subclass creates its own specific GUI type.
     * 
     * @param player       The player opening the container
     * @param windowWidth  Window width for GUI positioning
     * @param windowHeight Window height for GUI positioning
     * @return The GUI instance ready to be displayed
     */
    public abstract ContainerGui createGui(Player player, int windowWidth, int windowHeight);

    // ==================== INTERACTION ====================

    @Override
    public InteractionResult onInteract(Player player) {
        if (world == null)
            return InteractionResult.FAIL;

        // Check distance
        if (!isInRange(player.getX(), player.getY(), player.getZ(), getInteractionRange())) {
            return InteractionResult.FAIL;
        }

        // Open GUI - this will be handled by the game layer
        onOpen(player);
        return InteractionResult.SUCCESS;
    }

    /**
     * Called when player opens this container.
     * Override to provide custom behavior or trigger GUI opening.
     */
    protected void onOpen(Player player) {
        System.out.println("[" + getDisplayName() + "] Opened by " + player);
        // GUI opening is handled by InteractionManager.GuiOpenHandler
    }

    /**
     * Called when player closes this container.
     */
    public void onClose(Player player) {
        System.out.println("[" + getDisplayName() + "] Closed by " + player);
    }

    // ==================== SERIALIZATION ====================

    @Override
    protected void saveAdditional(NBTTagCompound nbt) {
        super.saveAdditional(nbt);

        // Save custom name
        if (hasCustomName()) {
            nbt.setString("CustomName", customName);
        }

        // Save inventory
        NBTTagCompound itemsNbt = new NBTTagCompound();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                NBTTagCompound itemNbt = new NBTTagCompound();
                itemNbt.setString("id", stack.getItem().getRegistryId().toString());
                itemNbt.setInt("count", stack.getCount());
                itemNbt.setInt("damage", stack.getDamage());
                if (stack.hasNBT()) {
                    itemNbt.setTag("tag", stack.getNBT().copy());
                }
                itemsNbt.setTag("slot" + i, itemNbt);
            }
        }
        nbt.setTag("Items", itemsNbt);
    }

    @Override
    protected void loadAdditional(NBTTagCompound nbt) {
        super.loadAdditional(nbt);

        // Load custom name
        if (nbt.hasKey("CustomName")) {
            customName = nbt.getString("CustomName");
        }

        // Load inventory
        if (nbt.hasKey("Items")) {
            NBTTagCompound itemsNbt = nbt.getTag("Items");
            for (int i = 0; i < inventory.getSize(); i++) {
                String key = "slot" + i;
                if (itemsNbt.hasKey(key)) {
                    NBTTagCompound itemNbt = itemsNbt.getTag(key);
                    String itemId = itemNbt.getString("id");
                    int count = itemNbt.getInt("count");
                    int damage = itemNbt.getInt("damage");
                    final int slot = i; // For lambda capture

                    // Look up item from registry
                    engine.registry.Registries.ITEMS.get(itemId).ifPresent(item -> {
                        ItemStack stack = new ItemStack(item, count);
                        stack.setDamage(damage);
                        if (itemNbt.hasKey("tag")) {
                            stack.setNBT(itemNbt.getTag("tag").copy());
                        }
                        inventory.setStack(slot, stack);
                    });
                }
            }
        }
    }

    // ==================== DROP ITEMS ====================

    /**
     * Get all items to drop when block is broken.
     */
    public List<ItemStack> getDrops() {
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        return drops;
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        // Items should be dropped by the block's break logic
    }
}