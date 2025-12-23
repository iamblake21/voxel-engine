package game.ui;

import engine.entity.Player;
import engine.ui.ContainerGui;
import engine.ui.definition.GuiDefinition;
import engine.ui.definition.GuiSlotDefinition;
import engine.world.item.ItemStack;
import engine.registry.Registries;
import java.util.Optional;

/**
 * GUI logic for the Crafting Table.
 */
public class CraftingGui extends ContainerGui {

    // Output slot index in the GUI definition is 0 (relative to output region)
    // But in our flat slot list, let's map it.
    // The definition has:
    // 9 crafting slots (indices 0-8)
    // 1 output slot (index 0 of "crafting_output" group?)
    // Let's check GameGuis definition.

    // craft_0...8 -> "crafting_input", indices 0-8
    // craft_output -> "crafting_output", index 0

    // We map these to our internal state.
    // Slots 0-8: Crafting Input
    private ItemStack outputStack = ItemStack.EMPTY;

    public CraftingGui(Player player, CraftingInventory inventory, int width, int height) {
        super(loadDefinition(), player, inventory, width, height);
    }

    private static GuiDefinition loadDefinition() {
        Optional<GuiDefinition> def = Registries.GUIS.get("game:crafting_table");
        if (def.isPresent()) {
            return def.get();
        }
        throw new IllegalStateException("[CraftingGui] No crafting GUI definition found!");
    }

    @Override
    protected int getContainerSlotCount() {
        return 10; // 9 input + 1 output
    }

    @Override
    protected ItemStack getStackForSlot(int absoluteIndex) {
        GuiSlotDefinition slotDef = findSlotByAbsoluteIndex(absoluteIndex);
        if (slotDef == null) {
            return ItemStack.EMPTY;
        }

        String type = slotDef.getType();
        int index = slotDef.getIndex();

        if (type.startsWith("craft_input")) {
            return container.getItem(index);
        } else if (type.startsWith("craft_output")) {
            return outputStack;
        }

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

        if (type.startsWith("craft_input")) {
            container.setItem(index, stack);
            updateCraftingResult();
            return;
        } else if (type.startsWith("craft_output")) {
            if (stack.isEmpty() && !outputStack.isEmpty()) {
                onTakeOutput();
            }
            outputStack = stack;
            return;
        }

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

    private void updateCraftingResult() {
        outputStack = Registries.RECIPES.getCraftingResult((CraftingInventory) container);
    }

    private void onTakeOutput() {
        for (int i = 0; i < 9; i++) {
            ItemStack input = container.getItem(i);
            if (!input.isEmpty()) {
                input.shrink(1);
                if (input.getCount() <= 0) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
        updateCraftingResult();
    }
}
