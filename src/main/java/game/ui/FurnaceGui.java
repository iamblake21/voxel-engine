package game.ui;

import engine.entity.Player;
import engine.registry.Registries;
import engine.ui.ContainerGui;
import engine.ui.GuiRenderer;
import engine.ui.definition.GuiDefinition;
import engine.ui.definition.GuiSlotDefinition;
import engine.world.item.ItemStack;
import game.blockentity.FurnaceBlockEntity;

import java.util.Optional;

public class FurnaceGui extends ContainerGui {
    
    private static final int FURNACE_SLOTS = 3;
    private final FurnaceBlockEntity furnace;
    
    public FurnaceGui(Player player, FurnaceBlockEntity furnace, int windowWidth, int windowHeight) {
        super(loadDefinition(), player, furnace, windowWidth, windowHeight);
        this.furnace = furnace;
    }
    
    private static GuiDefinition loadDefinition() {
        Optional<GuiDefinition> def = Registries.GUIS.get("game:furnace");
        if (def.isPresent()) {
            return def.get();
        }
        throw new IllegalStateException("[FurnaceGui] No furnace GUI definition found!");
    }
    
    @Override
    protected ItemStack getStackForSlot(int absoluteIndex) {
        GuiSlotDefinition slotDef = findSlotByAbsoluteIndex(absoluteIndex);
        if (slotDef == null) return ItemStack.EMPTY;
        
        String type = slotDef.getType();
        int index = slotDef.getIndex();
        
        return switch (type) {
            case "furnace_input" -> furnace.getItem(FurnaceBlockEntity.SLOT_INPUT);
            case "furnace_fuel" -> furnace.getItem(FurnaceBlockEntity.SLOT_FUEL);
            case "furnace_output" -> furnace.getItem(FurnaceBlockEntity.SLOT_OUTPUT);
            case "hotbar" -> playerInventory.getHotbarStack(index);
            case "main" -> playerInventory.getMainStack(index);
            default -> ItemStack.EMPTY;
        };
    }
    
    @Override
    protected void setStackInSlot(int absoluteIndex, ItemStack stack) {
        GuiSlotDefinition slotDef = findSlotByAbsoluteIndex(absoluteIndex);
        if (slotDef == null) return;
        
        String type = slotDef.getType();
        int index = slotDef.getIndex();
        
        switch (type) {
            case "furnace_input" -> furnace.setItem(FurnaceBlockEntity.SLOT_INPUT, stack);
            case "furnace_fuel" -> furnace.setItem(FurnaceBlockEntity.SLOT_FUEL, stack);
            case "furnace_output" -> furnace.setItem(FurnaceBlockEntity.SLOT_OUTPUT, stack);
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
    
    @Override
    protected int getContainerSlotCount() {
        return FURNACE_SLOTS;
    }
    
    @Override
    protected void renderCustom(GuiRenderer renderer) {
        float burnProgress = furnace.getBurnProgress();
        if (burnProgress > 0) {
            int flameX = x + 56;
            int flameY = y + 36;
            int filledHeight = (int) (14 * burnProgress);
            renderer.renderRect(flameX, flameY + (14 - filledHeight), 14, filledHeight, 1.0f, 0.5f, 0.0f, 1.0f);
        }
        
        float smeltProgress = furnace.getSmeltProgressPercent();
        if (smeltProgress > 0) {
            int arrowX = x + 79;
            int arrowY = y + 34;
            int filledWidth = (int) (24 * smeltProgress);
            renderer.renderRect(arrowX, arrowY, filledWidth, 17, 1.0f, 1.0f, 1.0f, 0.8f);
        }
    }
}