package engine.ui;

import engine.entity.inventory.PlayerInventory;
import engine.world.item.ItemStack;

/**
 * Full inventory GUI screen - opened with E key.
 * Shows hotbar (9 slots) + main inventory (27 slots).
 */
public class InventoryGui extends GuiComponent {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    private final PlayerInventory inventory;
    private final InventorySlot[] hotbarSlots;
    private final InventorySlot[] mainSlots;

    public InventoryGui(PlayerInventory inventory, int windowWidth, int windowHeight) {
        super(windowWidth / 2 - GUI_WIDTH / 2,
                windowHeight / 2 - GUI_HEIGHT / 2,
                GUI_WIDTH,
                GUI_HEIGHT);

        this.inventory = inventory;
        this.hotbarSlots = new InventorySlot[9];
        this.mainSlots = new InventorySlot[27];

        // Create hotbar slots (bottom row)
        int hotbarY = y + GUI_HEIGHT - 26;
        for (int i = 0; i < 9; i++) {
            int slotX = x + 8 + i * 18;
            hotbarSlots[i] = new InventorySlot(slotX, hotbarY);
        }

        // Create main inventory slots (3 rows above hotbar)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                int slotX = x + 8 + col * 18;
                int slotY = y + 40 + row * 18;
                mainSlots[slotIndex] = new InventorySlot(slotX, slotY);
            }
        }

        System.out.println("[InventoryGui] Created (36 slots)");
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
