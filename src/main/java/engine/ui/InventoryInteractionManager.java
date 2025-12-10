package engine.ui;

import engine.entity.inventory.PlayerInventory;
import engine.window.InputManager;
import engine.world.item.ItemStack;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Manages inventory GUI interactions: drag-drop, shift-click, mouse wheel
 * selection.
 * Centralized interaction logic for consistent behavior across inventory
 * components.
 */
public class InventoryInteractionManager {

    private final PlayerInventory inventory;

    // Item held on cursor during drag operations
    private ItemStack cursorStack = ItemStack.EMPTY;

    // Track mouse button latch to prevent repeated actions while held
    private boolean leftClickLatch = false;
    private boolean rightClickLatch = false;

    public InventoryInteractionManager(PlayerInventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Handle mouse wheel for hotbar slot selection (when inventory is closed).
     * Scroll up = next slot, scroll down = previous slot.
     */
    public void handleMouseWheel(double scrollY) {
        // Handle scroll magnitude
        int scrollAmount = (int) Math.abs(scrollY);
        if (scrollAmount == 0 && scrollY != 0)
            scrollAmount = 1; // At least one if non-zero

        for (int i = 0; i < scrollAmount; i++) {
            if (scrollY > 0) {
                inventory.selectPrevious();
            } else if (scrollY < 0) {
                inventory.selectNext();
            }
        }
    }

    // ==================== SLOT CLICK HANDLING ====================

    /**
     * Handle a click on an inventory slot.
     * 
     * @param slotIndex The absolute slot index in PlayerInventory (0-35)
     * @param leftClick true for left-click, false for right-click
     * @param shiftHeld true if shift key is held
     * @param input     InputManager for checking current input state
     */
    public void handleSlotClick(int slotIndex, boolean leftClick, boolean shiftHeld, InputManager input) {
        if (slotIndex < 0 || slotIndex >= PlayerInventory.TOTAL_SIZE) {
            return;
        }

        ItemStack slotStack = inventory.getStack(slotIndex);
        boolean isHotbarSlot = slotIndex < PlayerInventory.HOTBAR_SIZE;

        // Shift-click: quick transfer
        if (shiftHeld && !slotStack.isEmpty()) {
            quickTransfer(slotIndex, isHotbarSlot);
            return;
        }

        if (leftClick) {
            handleLeftClick(slotIndex, slotStack);
        } else {
            handleRightClick(slotIndex, slotStack);
        }
    }

    /**
     * Left-click logic:
     * - Empty hand + item slot = pick up full stack
     * - Item in hand + empty slot = place full stack
     * - Item in hand + same item = merge (as much as fits)
     * - Item in hand + different item = swap
     */
    private void handleLeftClick(int slotIndex, ItemStack slotStack) {
        if (cursorStack.isEmpty()) {
            // Pick up full stack from slot
            if (!slotStack.isEmpty()) {
                cursorStack = slotStack.copy();
                inventory.setStack(slotIndex, ItemStack.EMPTY);
            }
        } else {
            // Have item on cursor
            if (slotStack.isEmpty()) {
                // Place full stack in empty slot
                inventory.setStack(slotIndex, cursorStack);
                cursorStack = ItemStack.EMPTY;
            } else if (cursorStack.canMerge(slotStack)) {
                // Same item type - try to merge
                int remainder = slotStack.merge(cursorStack);
                if (cursorStack.isEmpty() || cursorStack.getCount() <= 0) {
                    cursorStack = ItemStack.EMPTY;
                }
                // Update slot with merged stack
                inventory.setStack(slotIndex, slotStack);
            } else {
                // Different items - swap
                ItemStack temp = slotStack.copy();
                inventory.setStack(slotIndex, cursorStack);
                cursorStack = temp;
            }
        }
    }

    /**
     * Right-click logic:
     * - Empty hand + item slot = pick up half stack
     * - Item in hand + empty slot = place single item
     * - Item in hand + same item = place single item (if room)
     * - Item in hand + different item = swap (same as left-click)
     */
    private void handleRightClick(int slotIndex, ItemStack slotStack) {
        if (cursorStack.isEmpty()) {
            // Pick up half the stack
            if (!slotStack.isEmpty()) {
                int halfCount = (slotStack.getCount() + 1) / 2;
                cursorStack = slotStack.split(halfCount);
                if (slotStack.isEmpty() || slotStack.getCount() <= 0) {
                    inventory.setStack(slotIndex, ItemStack.EMPTY);
                } else {
                    inventory.setStack(slotIndex, slotStack);
                }
            }
        } else {
            // Have item on cursor
            if (slotStack.isEmpty()) {
                // Place single item
                ItemStack single = cursorStack.split(1);
                inventory.setStack(slotIndex, single);
                if (cursorStack.getCount() <= 0) {
                    cursorStack = ItemStack.EMPTY;
                }
            } else if (cursorStack.canMerge(slotStack)) {
                // Same item - place one if there's room
                if (slotStack.getCount() < slotStack.getMaxStackSize()) {
                    slotStack.grow(1);
                    cursorStack.shrink(1);
                    inventory.setStack(slotIndex, slotStack);
                    if (cursorStack.getCount() <= 0) {
                        cursorStack = ItemStack.EMPTY;
                    }
                }
            } else {
                // Different items - swap
                ItemStack temp = slotStack.copy();
                inventory.setStack(slotIndex, cursorStack);
                cursorStack = temp;
            }
        }
    }

    // ==================== QUICK TRANSFER ====================

    /**
     * Quick transfer (shift-click): move item between hotbar and main inventory
     */
    private void quickTransfer(int slotIndex, boolean fromHotbar) {
        ItemStack stack = inventory.getStack(slotIndex);
        if (stack.isEmpty()) {
            return;
        }

        if (fromHotbar) {
            // From hotbar (0-8) -> main inventory (9-35)
            ItemStack remaining = tryAddToRange(stack, PlayerInventory.HOTBAR_SIZE, PlayerInventory.TOTAL_SIZE);
            inventory.setStack(slotIndex, remaining);
        } else {
            // From main inventory (9-35) -> hotbar (0-8)
            ItemStack remaining = tryAddToRange(stack, 0, PlayerInventory.HOTBAR_SIZE);
            inventory.setStack(slotIndex, remaining);
        }
    }

    /**
     * Try to add a stack to a range of slots.
     * First tries to merge with existing stacks, then uses empty slots.
     * 
     * @return Remaining items that couldn't fit
     */
    private ItemStack tryAddToRange(ItemStack stack, int startSlot, int endSlot) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        // First pass: merge with existing compatible stacks
        for (int i = startSlot; i < endSlot && !remaining.isEmpty(); i++) {
            ItemStack slotStack = inventory.getStack(i);
            if (!slotStack.isEmpty() && slotStack.canMerge(remaining)) {
                slotStack.merge(remaining);
                inventory.setStack(i, slotStack);
            }
        }

        // Second pass: use empty slots
        for (int i = startSlot; i < endSlot && !remaining.isEmpty(); i++) {
            ItemStack slotStack = inventory.getStack(i);
            if (slotStack.isEmpty()) {
                inventory.setStack(i, remaining.copy());
                remaining = ItemStack.EMPTY;
                break;
            }
        }

        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    // ==================== CLICK OUTSIDE INVENTORY ====================

    /**
     * Handle click outside any slot (drop item to world - for future
     * implementation)
     */
    public void handleClickOutside(boolean leftClick) {
        // TODO: Drop cursor item into world
        // For now, just leave it on cursor
    }

    // ==================== INPUT PROCESSING ====================

    /**
     * Process raw input and update latch states.
     * Call this each frame when inventory is open.
     * 
     * @return Array: [leftClickPressed, rightClickPressed] - true only on first
     *         frame of click
     */
    public boolean[] processInput(InputManager input) {
        boolean leftDown = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_1);
        boolean rightDown = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_2);

        boolean leftPressed = leftDown && !leftClickLatch;
        boolean rightPressed = rightDown && !rightClickLatch;

        leftClickLatch = leftDown;
        rightClickLatch = rightDown;

        return new boolean[] { leftPressed, rightPressed };
    }

    // ==================== GETTERS ====================

    public ItemStack getCursorStack() {
        return cursorStack;
    }

    public boolean hasCursorItem() {
        return !cursorStack.isEmpty();
    }

    /**
     * Drop cursor item back to inventory (used when closing inventory)
     */
    public void dropCursorToInventory() {
        if (!cursorStack.isEmpty()) {
            ItemStack remaining = inventory.addItem(cursorStack);
            // If couldn't fit all, just lose it (or drop to world in future)
            cursorStack = ItemStack.EMPTY;
        }
    }
}
