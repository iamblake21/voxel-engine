package engine.ui;

import engine.entity.inventory.PlayerInventory;
import engine.world.item.ItemStack;

/**
 * Hotbar GUI overlay - always visible at bottom of screen.
 * Shows 9 slots with selection highlight.
 */
public class HotbarGui extends GuiComponent {

    private static final int HOTBAR_WIDTH = 182; // 9 slots * 20 + 2 padding
    private static final int HOTBAR_HEIGHT = 22;

    private final PlayerInventory inventory;
    private final InventorySlot[] slots;

    public HotbarGui(PlayerInventory inventory, int windowWidth, int windowHeight) {
        super(windowWidth / 2 - HOTBAR_WIDTH / 2,
                windowHeight - HOTBAR_HEIGHT - 10,
                HOTBAR_WIDTH,
                HOTBAR_HEIGHT);

        this.inventory = inventory;
        this.slots = new InventorySlot[9];

        // Create 9 slots
        for (int i = 0; i < 9; i++) {
            int slotX = x + 2 + i * 20;
            int slotY = y + 2;
            slots[i] = new InventorySlot(slotX, slotY);
        }
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!visible) {
            return;
        }

        // Render background panel
        renderer.renderRect(x, y, width, height, 0.1f, 0.1f, 0.1f, 0.7f);

        // Update and render slots
        int selectedSlot = inventory.getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getHotbarStack(i);
            slots[i].setStack(stack);
            slots[i].setSelected(i == selectedSlot);
            slots[i].render(renderer);
        }
    }

    /**
     * Update position (e.g., on window resize)
     */
    public void updatePosition(int windowWidth, int windowHeight) {
        this.x = windowWidth / 2 - HOTBAR_WIDTH / 2;
        this.y = windowHeight - HOTBAR_HEIGHT - 10;

        // Update slot positions
        for (int i = 0; i < 9; i++) {
            slots[i].setPosition(x + 2 + i * 20, y + 2);
        }
    }
}
