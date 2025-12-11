package game.ui;

import engine.entity.Player;
import engine.entity.inventory.PlayerInventory;
import engine.registry.Registries;
import engine.ui.ContainerGui;
import engine.ui.definition.GuiDefinition;
import engine.world.item.ItemStack;
import game.blockentity.ChestBlockEntity;

import java.util.Optional;

/**
 * GUI for chest block entity.
 * 
 * Layout (27 chest slots + 36 player slots = 63 total):
 * - Slots 0-26: Chest inventory (3 rows of 9)
 * - Slots 27-35: Player hotbar
 * - Slots 36-62: Player main inventory
 */
public class ChestGui extends ContainerGui {
    
    private static final int CHEST_SLOTS = 27;
    
    private final ChestBlockEntity chest;
    
    public ChestGui(Player player, ChestBlockEntity chest, int windowWidth, int windowHeight) {
        super(loadDefinition(), player, chest, windowWidth, windowHeight);
        this.chest = chest;
    }
    
    private static GuiDefinition loadDefinition() {
        // Try to load from registry
        Optional<GuiDefinition> def = Registries.GUIS.get("game:chest");
        if (def.isPresent()) {
            return def.get();
        }
        
        // Fallback: create programmatically
        // In real implementation, you'd have a chest.json GUI definition
        throw new IllegalStateException(
            "[ChestGui] No chest GUI definition found! " +
            "Register 'game:chest' in GameGuis.register()");
    }
    
    @Override
    protected ItemStack getStackForSlot(int slotIndex) {
        if (slotIndex < 0) return ItemStack.EMPTY;
        
        // Chest slots
        if (slotIndex < CHEST_SLOTS) {
            return chest.getItem(slotIndex);
        }
        
        // Player hotbar (slots 27-35)
        int playerIndex = slotIndex - CHEST_SLOTS;
        if (playerIndex < PlayerInventory.HOTBAR_SIZE) {
            return playerInventory.getHotbarStack(playerIndex);
        }
        
        // Player main inventory (slots 36-62)
        int mainIndex = playerIndex - PlayerInventory.HOTBAR_SIZE;
        if (mainIndex < PlayerInventory.MAIN_SIZE) {
            return playerInventory.getMainStack(mainIndex);
        }
        
        return ItemStack.EMPTY;
    }
    
    @Override
    protected void setStackInSlot(int slotIndex, ItemStack stack) {
        if (slotIndex < 0) return;
        
        // Chest slots
        if (slotIndex < CHEST_SLOTS) {
            chest.setItem(slotIndex, stack);
            return;
        }
        
        // Player hotbar
        int playerIndex = slotIndex - CHEST_SLOTS;
        if (playerIndex < PlayerInventory.HOTBAR_SIZE) {
            playerInventory.setHotbarStack(playerIndex, stack);
            return;
        }
        
        // Player main inventory
        int mainIndex = playerIndex - PlayerInventory.HOTBAR_SIZE;
        if (mainIndex < PlayerInventory.MAIN_SIZE) {
            playerInventory.setMainStack(mainIndex, stack);
        }
    }
    
    @Override
    protected int getContainerSlotCount() {
        return CHEST_SLOTS;
    }
    
    public ChestBlockEntity getChest() {
        return chest;
    }
}
