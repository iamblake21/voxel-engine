package game.ui;

import engine.entity.Player;
import engine.entity.inventory.PlayerInventory;
import engine.registry.Registries;
import engine.ui.ContainerGui;
import engine.ui.GuiRenderer;
import engine.ui.definition.GuiDefinition;
import engine.ui.definition.GuiSlotDefinition;
import engine.world.item.ItemStack;

import java.util.Optional;

/**
 * Player inventory GUI with 2x2 crafting grid and armor slots.
 * 
 * This replaces the old InventoryGui and aligns with the ContainerGui
 * architecture
 * used by CraftingGui and ChestGui.
 * 
 * Slot types:
 * - main: Main inventory slots (0-44)
 * - hotbar: Hotbar slots (0-8)
 * - craft_input: 2x2 crafting grid (0-3)
 * - craft_output: Crafting result slot (0)
 * - armor_helmet, armor_chestplate, armor_leggings, armor_boots: Armor slots
 */
public class PlayerInventoryGui extends ContainerGui {

    private final PlayerCraftingInventory craftingInventory;
    private ItemStack craftingOutputStack = ItemStack.EMPTY;

    // Armor slots stored in player inventory (indices 45-48)
    // We handle them via special slot types

    public PlayerInventoryGui(Player player, int windowWidth, int windowHeight) {
        super(loadDefinition(), player, new PlayerCraftingInventory(), windowWidth, windowHeight);
        this.craftingInventory = (PlayerCraftingInventory) this.container;
    }

    private static GuiDefinition loadDefinition() {
        Optional<GuiDefinition> def = Registries.GUIS.get("game:player_inventory");
        if (def.isPresent()) {
            return def.get();
        }
        // Fallback to old inventory definition during transition
        Optional<GuiDefinition> fallback = Registries.GUIS.get("game:inventory");
        if (fallback.isPresent()) {
            System.out.println("[PlayerInventoryGui] Warning: Using fallback inventory definition");
            return fallback.get();
        }
        throw new IllegalStateException("[PlayerInventoryGui] No player inventory GUI definition found!");
    }

    @Override
    protected int getContainerSlotCount() {
        // 4 crafting inputs + 1 output + 4 armor = 9 container slots
        return 9;
    }

    @Override
    protected ItemStack getStackForSlot(int absoluteIndex) {
        GuiSlotDefinition slotDef = findSlotByAbsoluteIndex(absoluteIndex);
        if (slotDef == null) {
            return ItemStack.EMPTY;
        }

        String type = slotDef.getType();
        int index = slotDef.getIndex();

        // Crafting slots
        if (type.startsWith("craft_input")) {
            return craftingInventory.getItem(index);
        } else if (type.startsWith("craft_output")) {
            return craftingOutputStack;
        }

        // Armor slots
        if (type.equals("armor_helmet")) {
            return playerInventory.getArmorStack(0);
        } else if (type.equals("armor_chestplate")) {
            return playerInventory.getArmorStack(1);
        } else if (type.equals("armor_leggings")) {
            return playerInventory.getArmorStack(2);
        } else if (type.equals("armor_boots")) {
            return playerInventory.getArmorStack(3);
        }

        // Player inventory slots
        return switch (type) {
            case "hotbar" -> playerInventory.getHotbarStack(index);
            case "main" -> playerInventory.getMainStack(index);
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    protected void setStackInSlot(int absoluteIndex, ItemStack stack) {
        GuiSlotDefinition slotDef = findSlotByAbsoluteIndex(absoluteIndex);
        if (slotDef == null) {
            return;
        }

        String type = slotDef.getType();
        int index = slotDef.getIndex();

        // Crafting slots
        if (type.startsWith("craft_input")) {
            craftingInventory.setItem(index, stack);
            updateCraftingResult();
            return;
        } else if (type.startsWith("craft_output")) {
            if (stack.isEmpty() && !craftingOutputStack.isEmpty()) {
                onTakeCraftingOutput();
                updateCraftingResult();
                return;
            }
            craftingOutputStack = stack;
            return;
        }

        // Armor slots
        if (type.equals("armor_helmet")) {
            playerInventory.setArmorStack(0, stack);
            return;
        } else if (type.equals("armor_chestplate")) {
            playerInventory.setArmorStack(1, stack);
            return;
        } else if (type.equals("armor_leggings")) {
            playerInventory.setArmorStack(2, stack);
            return;
        } else if (type.equals("armor_boots")) {
            playerInventory.setArmorStack(3, stack);
            return;
        }

        // Player inventory slots
        switch (type) {
            case "hotbar" -> playerInventory.setHotbarStack(index, stack);
            case "main" -> playerInventory.setMainStack(index, stack);
        }
    }

    private GuiSlotDefinition findSlotByAbsoluteIndex(int absoluteIndex) {
        for (GuiSlotDefinition slot : definition.getSlots()) {
            if (slot.getAbsoluteIndex() == absoluteIndex) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Update the crafting result based on current grid contents.
     */
    private void updateCraftingResult() {
        craftingOutputStack = Registries.RECIPES.getCraftingResult(craftingInventory);
    }

    /**
     * Called when the player takes the crafting output.
     * Consumes one of each ingredient.
     */
    private void onTakeCraftingOutput() {
        for (int i = 0; i < 4; i++) {
            ItemStack input = craftingInventory.getItem(i);
            if (!input.isEmpty()) {
                input.shrink(1);
                if (input.getCount() <= 0) {
                    craftingInventory.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        updateCraftingResult();
    }

    @Override
    protected void renderCustom(GuiRenderer renderer) {
        // Update selected hotbar slot highlighting
        setSelectedSlot(playerInventory.getSelectedSlot());
    }

    @Override
    public void onClose() {
        // Return crafting grid items to player inventory
        craftingInventory.onClose(player);

        // Handle cursor item
        super.onClose();
    }

    /**
     * Get the player inventory.
     */
    public PlayerInventory getInventory() {
        return playerInventory;
    }
}
