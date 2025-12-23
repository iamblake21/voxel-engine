package engine.ui;

import engine.entity.Player;
import engine.entity.inventory.PlayerInventory;
import engine.ui.definition.GuiDefinition;

import engine.world.item.ItemStack;
import engine.window.InputManager;

import java.util.Optional;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Base GUI for containers (chests, furnaces, etc.).
 * 
 * Handles both the container's inventory and the player's inventory,
 * including item transfer between them.
 */
public abstract class ContainerGui extends TexturedGui {

    protected final Player player;
    protected final ContainerAccess container;
    protected final PlayerInventory playerInventory;

    // Cursor item (item being dragged)
    protected ItemStack cursorStack = ItemStack.EMPTY;

    // Click tracking
    private boolean leftClickLatch = false;
    private boolean rightClickLatch = false;

    public ContainerGui(GuiDefinition definition, Player player, ContainerAccess container,
            int windowWidth, int windowHeight) {
        super(definition, windowWidth, windowHeight);
        this.player = player;
        this.container = container;
        this.playerInventory = player.getInventory();

        // Set up stack provider for all slots
        setStackProvider(this::getStackForSlot);
    }

    /**
     * Get the stack for a slot index.
     * Subclasses define how slot indices map to inventories.
     */
    protected abstract ItemStack getStackForSlot(int slotIndex);

    /**
     * Set the stack in a slot.
     * Subclasses define how slot indices map to inventories.
     */
    protected abstract void setStackInSlot(int slotIndex, ItemStack stack);

    /**
     * Get the number of container slots (before player inventory).
     */
    protected abstract int getContainerSlotCount();

    // ==================== INPUT HANDLING ====================

    public void handleInput(InputManager input, double rawMouseX, double rawMouseY) {
        int[] coords = convertMouseCoords(rawMouseX, rawMouseY);
        int mx = coords[0];
        int my = coords[1];

        updateHoverStates(mx, my);

        // Process clicks
        boolean leftDown = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_1);
        boolean rightDown = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_2);
        boolean leftClick = leftDown && !leftClickLatch;
        boolean rightClick = rightDown && !rightClickLatch;
        leftClickLatch = leftDown;
        rightClickLatch = rightDown;

        if (!leftClick && !rightClick)
            return;

        boolean shiftHeld = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);

        Optional<InventorySlot> clickedSlot = getSlotAt(mx, my);

        if (clickedSlot.isPresent()) {
            int slotIndex = clickedSlot.get().getSlotIndex();
            if (slotIndex >= 0) {
                handleSlotClick(slotIndex, leftClick, rightClick, shiftHeld);
            }
        } else {
            // Clicked outside - drop cursor item
            if (leftClick && !cursorStack.isEmpty()) {
                dropCursorStack(false);
            } else if (rightClick && !cursorStack.isEmpty()) {
                dropCursorStack(true); // Drop single item
            }
        }
    }

    public boolean hasCursorItem() {
        return !cursorStack.isEmpty();
    }

    /**
     * Handle a click on a slot.
     */
    protected void handleSlotClick(int slotIndex, boolean leftClick, boolean rightClick, boolean shiftHeld) {
        ItemStack slotStack = getStackForSlot(slotIndex);

        if (shiftHeld && leftClick) {
            // Quick transfer
            quickTransfer(slotIndex);
            return;
        }

        if (leftClick) {
            if (cursorStack.isEmpty()) {
                // Pick up stack
                if (!slotStack.isEmpty()) {
                    cursorStack = slotStack.copy();
                    setStackInSlot(slotIndex, ItemStack.EMPTY);
                }
            } else {
                // Place or swap
                if (slotStack.isEmpty()) {
                    setStackInSlot(slotIndex, cursorStack);
                    cursorStack = ItemStack.EMPTY;
                } else if (cursorStack.canMerge(slotStack)) {
                    // Merge stacks
                    int remaining = slotStack.merge(cursorStack);
                    setStackInSlot(slotIndex, slotStack);
                    if (remaining == 0) {
                        cursorStack = ItemStack.EMPTY;
                    }
                } else {
                    // Swap
                    ItemStack temp = slotStack.copy();
                    setStackInSlot(slotIndex, cursorStack);
                    cursorStack = temp;
                }
            }
        } else if (rightClick) {
            if (cursorStack.isEmpty()) {
                // Pick up half
                if (!slotStack.isEmpty()) {
                    int half = (slotStack.getCount() + 1) / 2;
                    cursorStack = slotStack.split(half);
                    setStackInSlot(slotIndex, slotStack);
                }
            } else {
                // Place single item
                if (slotStack.isEmpty()) {
                    ItemStack single = cursorStack.split(1);
                    setStackInSlot(slotIndex, single);
                } else if (cursorStack.canMerge(slotStack) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                    slotStack.grow(1);
                    cursorStack.shrink(1);
                    setStackInSlot(slotIndex, slotStack);
                }
            }
        }
    }

    /**
     * Quick transfer a stack to the other inventory.
     */
    protected void quickTransfer(int slotIndex) {
        ItemStack stack = getStackForSlot(slotIndex);
        if (stack.isEmpty())
            return;

        int containerSlots = getContainerSlotCount();

        if (slotIndex < containerSlots) {
            // Transfer from container to player inventory
            if (tryMergeIntoPlayerInventory(stack)) {
                setStackInSlot(slotIndex, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        } else {
            // Transfer from player to container
            if (tryMergeIntoContainer(stack)) {
                setStackInSlot(slotIndex, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
    }

    /**
     * Try to merge a stack into the player inventory.
     */
    protected boolean tryMergeIntoPlayerInventory(ItemStack stack) {
        // First try hotbar
        for (int i = 0; i < PlayerInventory.HOTBAR_SIZE && !stack.isEmpty(); i++) {
            ItemStack target = playerInventory.getHotbarStack(i);
            if (target.isEmpty()) {
                playerInventory.setHotbarStack(i, stack.copy());
                stack.setCount(0);
                return true;
            } else if (target.canMerge(stack)) {
                target.merge(stack);
            }
        }

        // Then try main inventory
        for (int i = 0; i < PlayerInventory.MAIN_SIZE && !stack.isEmpty(); i++) {
            ItemStack target = playerInventory.getMainStack(i);
            if (target.isEmpty()) {
                playerInventory.setMainStack(i, stack.copy());
                stack.setCount(0);
                return true;
            } else if (target.canMerge(stack)) {
                target.merge(stack);
            }
        }

        return stack.isEmpty();
    }

    /**
     * Try to merge a stack into the container.
     */
    protected boolean tryMergeIntoContainer(ItemStack stack) {
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack target = container.getItem(i);
            if (target.isEmpty()) {
                container.setItem(i, stack.copy());
                stack.setCount(0);
                return true;
            } else if (target.canMerge(stack)) {
                target.merge(stack);
            }
        }
        return stack.isEmpty();
    }

    /**
     * Drop the cursor stack (throw on ground).
     */
    protected void dropCursorStack(boolean singleItem) {
        if (cursorStack.isEmpty())
            return;

        if (singleItem) {
            // Drop single item
            // TODO: Spawn item entity
            cursorStack.shrink(1);
        } else {
            // Drop whole stack
            // TODO: Spawn item entity
            cursorStack = ItemStack.EMPTY;
        }
    }

    // ==================== RENDERING ====================

    @Override
    public void render(GuiRenderer renderer) {
        super.render(renderer);
    }

    /**
     * Render the cursor item after everything else.
     */
    public void renderCursorItem(GuiRenderer renderer, double rawMouseX, double rawMouseY) {
        if (cursorStack.isEmpty())
            return;

        int[] coords = convertMouseCoords(rawMouseX, rawMouseY);
        super.renderCursorItem(renderer, cursorStack, coords[0], coords[1]);
    }

    // ==================== LIFECYCLE ====================

    /**
     * Called when the GUI is closed.
     */
    public void onClose() {
        // Return cursor item to player
        if (!cursorStack.isEmpty()) {
            // Try to put it back in player inventory
            if (!tryMergeIntoPlayerInventory(cursorStack)) {
                // If failed, drop it
                dropCursorStack(false);
            }
        }

        // Notify container
        container.onClose(player);
    }

    public ContainerAccess getContainer() {
        return container;
    }

    public ItemStack getCursorStack() {
        return cursorStack;
    }
}
