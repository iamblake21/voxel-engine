package game.ui;

import engine.entity.Player;
import engine.entity.inventory.PlayerInventory;
import engine.registry.Registries;
import engine.ui.ContainerGui;
import engine.ui.GuiRenderer;
import engine.ui.definition.GuiDefinition;
import engine.world.item.ItemStack;
import game.blockentity.FurnaceBlockEntity;

import java.util.Optional;

/**
 * GUI for furnace block entity.
 * 
 * Layout (3 furnace slots + 36 player slots = 39 total):
 * - Slot 0: Input
 * - Slot 1: Fuel
 * - Slot 2: Output
 * - Slots 3-11: Player hotbar
 * - Slots 12-38: Player main inventory
 */
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
        
        throw new IllegalStateException(
            "[FurnaceGui] No furnace GUI definition found! " +
            "Register 'game:furnace' in GameGuis.register()");
    }
    
    @Override
    protected ItemStack getStackForSlot(int slotIndex) {
        if (slotIndex < 0) return ItemStack.EMPTY;
        
        // Furnace slots
        if (slotIndex < FURNACE_SLOTS) {
            return furnace.getItem(slotIndex);
        }
        
        // Player hotbar
        int playerIndex = slotIndex - FURNACE_SLOTS;
        if (playerIndex < PlayerInventory.HOTBAR_SIZE) {
            return playerInventory.getHotbarStack(playerIndex);
        }
        
        // Player main inventory
        int mainIndex = playerIndex - PlayerInventory.HOTBAR_SIZE;
        if (mainIndex < PlayerInventory.MAIN_SIZE) {
            return playerInventory.getMainStack(mainIndex);
        }
        
        return ItemStack.EMPTY;
    }
    
    @Override
    protected void setStackInSlot(int slotIndex, ItemStack stack) {
        if (slotIndex < 0) return;
        
        // Furnace slots
        if (slotIndex < FURNACE_SLOTS) {
            furnace.setItem(slotIndex, stack);
            return;
        }
        
        // Player hotbar
        int playerIndex = slotIndex - FURNACE_SLOTS;
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
        return FURNACE_SLOTS;
    }
    
    // ==================== CUSTOM RENDERING ====================
    
    @Override
    protected void renderCustom(GuiRenderer renderer) {
        // Render burn progress (flame icon)
        float burnProgress = furnace.getBurnProgress();
        if (burnProgress > 0) {
            renderBurnProgress(renderer, burnProgress);
        }
        
        // Render smelt progress (arrow)
        float smeltProgress = furnace.getSmeltProgressPercent();
        if (smeltProgress > 0) {
            renderSmeltProgress(renderer, smeltProgress);
        }
    }
    
    /**
     * Render the burn progress flame.
     * The flame fills from bottom to top.
     */
    private void renderBurnProgress(GuiRenderer renderer, float progress) {
        // These positions should match your furnace GUI texture
        int flameX = x + 56;  // Adjust based on your texture
        int flameY = y + 36;
        int flameWidth = 14;
        int flameHeight = 14;
        
        int filledHeight = (int) (flameHeight * progress);
        int yOffset = flameHeight - filledHeight;
        
        // Draw filled portion of flame
        // This assumes you have a flame sprite in your texture
        // renderer.renderSubTexture(flameX, flameY + yOffset, flameWidth, filledHeight,
        //     backgroundTexture, 176, 0 + yOffset, flameWidth, filledHeight);
        
        // Simple colored rectangle fallback
        if (progress > 0) {
            renderer.renderRect(flameX, flameY + yOffset, flameWidth, filledHeight, 
                1.0f, 0.5f, 0.0f, 1.0f); // Orange
        }
    }
    
    /**
     * Render the smelt progress arrow.
     * The arrow fills from left to right.
     */
    private void renderSmeltProgress(GuiRenderer renderer, float progress) {
        // These positions should match your furnace GUI texture
        int arrowX = x + 79;  // Adjust based on your texture
        int arrowY = y + 34;
        int arrowWidth = 24;
        int arrowHeight = 17;
        
        int filledWidth = (int) (arrowWidth * progress);
        
        // Draw filled portion of arrow
        // renderer.renderSubTexture(arrowX, arrowY, filledWidth, arrowHeight,
        //     backgroundTexture, 176, 14, filledWidth, arrowHeight);
        
        // Simple colored rectangle fallback
        if (progress > 0) {
            renderer.renderRect(arrowX, arrowY, filledWidth, arrowHeight,
                0.0f, 0.8f, 0.0f, 1.0f); // Green
        }
    }
    
    public FurnaceBlockEntity getFurnace() {
        return furnace;
    }
    
    public boolean isBurning() {
        return furnace.isLit();
    }
}
