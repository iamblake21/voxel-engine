package engine.ui;

import engine.entity.inventory.PlayerInventory;
import engine.window.InputManager;
import engine.world.item.ItemStack;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Full inventory GUI screen - opened with E key.
 * Shows hotbar (9 slots) + main inventory (27 slots).
 * Supports mouse interaction for item manipulation.
 */
public class InventoryGui extends GuiComponent {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private final PlayerInventory inventory;
    private final InventorySlot[] hotbarSlots;
    private final InventorySlot[] mainSlots;

    // GUI scale factor (needed for mouse coordinate conversion)
    private int guiScale = 2;

    public InventoryGui(PlayerInventory inventory, int windowWidth, int windowHeight) {
        super(windowWidth / 2 - GUI_WIDTH / 2,
                windowHeight / 2 - GUI_HEIGHT / 2,
                GUI_WIDTH,
                GUI_HEIGHT);

        this.inventory = inventory;
        this.hotbarSlots = new InventorySlot[9];
        this.mainSlots = new InventorySlot[27];

        // Create hotbar slots (bottom row) - slots 0-8
        int hotbarY = y + GUI_HEIGHT - 26;
        for (int i = 0; i < 9; i++) {
            int slotX = x + 8 + i * 18;
            hotbarSlots[i] = new InventorySlot(slotX, hotbarY, i); // slot index = i
        }

        // Create main inventory slots (3 rows above hotbar) - slots 9-35
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int localIndex = row * 9 + col;
                int absoluteSlotIndex = PlayerInventory.HOTBAR_SIZE + localIndex; // 9-35
                int slotX = x + 8 + col * 18;
                int slotY = y + 40 + row * 18;
                mainSlots[localIndex] = new InventorySlot(slotX, slotY, absoluteSlotIndex);
            }
        }

        System.out.println("[InventoryGui] Created (36 slots)");
    }

    /**
     * Set the GUI scale factor for mouse coordinate conversion
     */
    public void setGuiScale(int scale) {
        this.guiScale = scale;
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!visible)
            return;

        // Semi-transparent background overlay (full screen)
        renderer.renderRect(0, 0, renderer.getWindowWidth(), renderer.getWindowHeight(),
                0, 0, 0, 0.5f);

        // Main inventory panel background
        renderer.renderRect(x, y, width, height, 0.15f, 0.15f, 0.15f, 0.95f);

        // Title
        renderer.renderText("Inventory", x + width / 2 - 40, y + 8, 12, 1, 1, 1, 1);

        // Main inventory label
        renderer.renderText("Main", x + 8, y + 28, 10, 0.8f, 0.8f, 0.8f, 1);

        // Hotbar label
        renderer.renderText("Hotbar", x + 8, y + GUI_HEIGHT - 40, 10, 0.8f, 0.8f, 0.8f, 1);

        // Render main inventory slots
        for (int i = 0; i < 27; i++) {
            ItemStack stack = inventory.getMainStack(i);
            mainSlots[i].setStack(stack);
            mainSlots[i].render(renderer);
        }

        // Render hotbar slots
        int selectedSlot = inventory.getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getHotbarStack(i);
            hotbarSlots[i].setStack(stack);
            hotbarSlots[i].setSelected(i == selectedSlot);
            hotbarSlots[i].render(renderer);
        }
    }

    /**
     * Render the cursor item (should be called last, after all other UI)
     */
    public void renderCursorItem(GuiRenderer renderer, ItemStack cursorStack, int mouseX, int mouseY) {
        if (cursorStack == null || cursorStack.isEmpty()) {
            return;
        }

        // Create a temporary slot to render the item at cursor position
        // Offset by half slot size so item is centered on cursor
        int renderX = mouseX - InventorySlot.SLOT_SIZE / 2;
        int renderY = mouseY - InventorySlot.SLOT_SIZE / 2;

        InventorySlot cursorSlot = new InventorySlot(renderX, renderY, -1);
        cursorSlot.setStack(cursorStack);
        cursorSlot.render(renderer);
    }

    /**
     * Handle input for inventory interaction.
     * 
     * @param input        The input manager
     * @param manager      The interaction manager for handling clicks
     * @param rawMouseX    Raw window mouse X position
     * @param rawMouseY    Raw window mouse Y position
     * @param windowHeight Window height for Y flip
     */
    public void handleInput(InputManager input, InventoryInteractionManager manager,
            double rawMouseX, double rawMouseY, int windowHeight) {
        // Convert raw mouse coordinates to GUI coordinates
        // GUI uses top-left origin, GLFW uses top-left, so NO Y-flip needed
        int mx = (int) (rawMouseX / guiScale);
        int my = (int) (rawMouseY / guiScale);

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
        InventorySlot clickedSlot = getSlotAt(mx, my);

        if (clickedSlot != null && clickedSlot.getSlotIndex() >= 0) {
            manager.handleSlotClick(clickedSlot.getSlotIndex(), leftClicked, shiftHeld, input);
        } else {
            // Clicked outside any slot
            manager.handleClickOutside(leftClicked);
        }
    }

    /**
     * Update hover states for all slots based on mouse position
     */
    private void updateHoverStates(int mx, int my) {
        // Check hotbar slots
        for (InventorySlot slot : hotbarSlots) {
            slot.setHovered(slot.isMouseOver(mx, my));
        }

        // Check main slots
        for (InventorySlot slot : mainSlots) {
            slot.setHovered(slot.isMouseOver(mx, my));
        }
    }

    /**
     * Find the slot at the given GUI coordinates
     */
    private InventorySlot getSlotAt(int mx, int my) {
        // Check hotbar slots
        for (InventorySlot slot : hotbarSlots) {
            if (slot.isMouseOver(mx, my)) {
                return slot;
            }
        }

        // Check main slots
        for (InventorySlot slot : mainSlots) {
            if (slot.isMouseOver(mx, my)) {
                return slot;
            }
        }

        return null;
    }

    /**
     * Get current mouse position in GUI coordinates
     */
    public int[] getMousePosition(double rawMouseX, double rawMouseY, int windowHeight) {
        int mx = (int) (rawMouseX / guiScale);
        int my = (int) (rawMouseY / guiScale);
        return new int[] { mx, my };
    }

    /**
     * Update position (e.g., on window resize)
     */
    public void updatePosition(int windowWidth, int windowHeight) {
        this.x = windowWidth / 2 - GUI_WIDTH / 2;
        this.y = windowHeight / 2 - GUI_HEIGHT / 2;

        // Update hotbar slot positions
        int hotbarY = y + GUI_HEIGHT - 26;
        for (int i = 0; i < 9; i++) {
            int slotX = x + 8 + i * 18;
            hotbarSlots[i].setPosition(slotX, hotbarY);
        }

        // Update main slot positions
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                int slotX = x + 8 + col * 18;
                int slotY = y + 40 + row * 18;
                mainSlots[slotIndex].setPosition(slotX, slotY);
            }
        }
    }
}
