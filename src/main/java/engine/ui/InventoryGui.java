package engine.ui;

import engine.entity.inventory.PlayerInventory;
import engine.registry.Registries;
import engine.ui.definition.GuiDefinition;
import engine.ui.definition.GuiDefinitionLoader;
import engine.ui.definition.Guis;
import engine.window.InputManager;
import engine.world.item.ItemStack;
import engine.ui.editor.*;

import java.util.Optional;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Full inventory GUI screen - opened with E key.
 * Uses texture + JSON metadata for layout.
 * 
 * This is a refactored version that extends TexturedGui
 * and loads slot positions from the registry.
 * 
 * Usage:
 * // In GameGuis.register():
 * INVENTORY = Guis.builder("game:inventory", ...)...register();
 * 
 * // In game init:
 * InventoryGui gui = new InventoryGui(playerInventory, width, height);
 */
public class InventoryGui extends TexturedGui {

    private final PlayerInventory inventory;

    /**
     * Create inventory GUI from registry.
     * Requires "game:inventory" to be registered in GameGuis.
     */
    public InventoryGui(PlayerInventory inventory, int windowWidth, int windowHeight) {
        super(loaDefinition(), windowWidth, windowHeight);

        this.inventory = inventory;

        // Set up the stack provider to get items from the player inventory
        setStackProvider(this::getStackForSlot);

        System.out.println("[InventoryGui] Created with " + definition.getSlots().size() + " slots");
    }

    /**
     * Create inventory GUI with a custom definition.
     * Useful for testing or modded inventories.
     */
    public InventoryGui(PlayerInventory inventory, GuiDefinition customDefinition,
            int windowWidth, int windowHeight) {
        super(customDefinition, windowWidth, windowHeight);

        this.inventory = inventory;
        setStackProvider(this::getStackForSlot);
    }

    /**
     * Get the GUI definition from registry.
     * Falls back to "inventory" if "game:inventory" not found.
     */
    private static GuiDefinition loaDefinition() {
        // Try game:inventory first (standard game GUI)
        Optional<GuiDefinition> gameDef = Registries.GUIS.get("game:inventory");
        if (gameDef.isPresent()) {
            return gameDef.get();
        }

        // Try engine:inventory (engine default)
        Optional<GuiDefinition> engineDef = Registries.GUIS.get("engine:inventory");
        if (engineDef.isPresent()) {
            return engineDef.get();
        }

        // Try just "inventory" (default namespace)
        Optional<GuiDefinition> defaultDef = Registries.GUIS.get("inventory");
        if (defaultDef.isPresent()) {
            return defaultDef.get();
        }

        // No definition found - this is an error in setup
        throw new IllegalStateException(
                "[InventoryGui] No inventory GUI definition found! " +
                        "Make sure GameGuis.register() is called before creating InventoryGui. " +
                        "Expected: game:inventory, engine:inventory, or inventory");
    }

    /**
     * Get ItemStack for a slot index (used by stack provider)
     */
    private ItemStack getStackForSlot(int absoluteIndex) {
        if (absoluteIndex < 0) {
            return ItemStack.EMPTY;
        }

        // Hotbar: 0-8
        if (absoluteIndex < PlayerInventory.HOTBAR_SIZE) {
            return inventory.getHotbarStack(absoluteIndex);
        }

        // Main inventory: 9-35
        int mainIndex = absoluteIndex - PlayerInventory.HOTBAR_SIZE;
        if (mainIndex < PlayerInventory.MAIN_SIZE) {
            return inventory.getMainStack(mainIndex);
        }

        return ItemStack.EMPTY;
    }

    @Override
    protected void renderCustom(GuiRenderer renderer) {
        // Update selected slot highlighting
        setSelectedSlot(inventory.getSelectedSlot());
    }

    /**
     * Handle input for inventory interaction.
     * 
     * @param input        The input manager
     * @param manager      The interaction manager for handling clicks
     * @param rawMouseX    Raw window mouse X position
     * @param rawMouseY    Raw window mouse Y position
     * @param windowHeight Window height (unused, kept for API compat)
     */
    public void handleInput(InputManager input, InventoryInteractionManager manager,
            double rawMouseX, double rawMouseY, int windowHeight) {
        // Convert raw mouse coordinates to GUI coordinates
        int[] coords = convertMouseCoords(rawMouseX, rawMouseY);
        int mx = coords[0];
        int my = coords[1];

        // Update hover state on all slots
        updateHoverStates(mx, my);

        // Process clicks
        boolean[] clicks = manager.processInput(input);
        boolean leftClicked = clicks[0];
        boolean rightClicked = clicks[1];

        if (!leftClicked && !rightClicked) {
            return;
        }

        boolean shiftHeld = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);

        // Check which slot was clicked
        Optional<InventorySlot> clickedSlot = getSlotAt(mx, my);

        if (clickedSlot.isPresent() && clickedSlot.get().getSlotIndex() >= 0) {
            manager.handleSlotClick(clickedSlot.get().getSlotIndex(), leftClicked, shiftHeld, input);
        } else {
            // Clicked outside any slot
            manager.handleClickOutside(leftClicked);
        }
    }

    /**
     * Render the cursor item (should be called last, after all other UI)
     */
    public void renderCursorItem(GuiRenderer renderer, ItemStack cursorStack,
            double rawMouseX, double rawMouseY) {
        if (cursorStack == null || cursorStack.isEmpty()) {
            return;
        }

        int[] coords = convertMouseCoords(rawMouseX, rawMouseY);
        super.renderCursorItem(renderer, cursorStack, coords[0], coords[1]);
    }

    /**
     * Get current mouse position in GUI coordinates
     */
    public int[] getMousePosition(double rawMouseX, double rawMouseY, int windowHeight) {
        return convertMouseCoords(rawMouseX, rawMouseY);
    }

    /**
     * Get the player inventory
     */
    public PlayerInventory getInventory() {
        return inventory;
    }
}