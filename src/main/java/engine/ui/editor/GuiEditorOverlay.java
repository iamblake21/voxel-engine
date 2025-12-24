package engine.ui.editor;

import engine.ui.GuiComponent;
import engine.ui.GuiRenderer;
import engine.ui.GuiTexture;
import engine.ui.definition.*;
import engine.window.InputManager;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

/**
 * In-game GUI Editor tool.
 * Allows visual placement of slots on a GUI texture.
 * 
 * Controls:
 * - F7: Toggle editor
 * - Left Click: Place/select slot
 * - Right Click: Delete slot
 * - Arrow Keys: Move selected slot
 * - G: Toggle grid snap
 * - S: Save to JSON
 * - L: Load texture
 * - 1-9: Set slot type
 * - Tab: Cycle through slots
 * - Enter: Edit slot properties
 */
public class GuiEditorOverlay extends GuiComponent {

    // Editor state
    private boolean active = false;
    private GuiDefinition editingDefinition;
    private GuiTexture backgroundTexture;

    // Grid settings
    private boolean snapToGrid = true;
    private int gridSize = 18; // Standard slot size

    // Selection - single and multi
    private GuiSlotDefinition selectedSlot = null;
    private int selectedIndex = -1;
    private List<GuiSlotDefinition> multiSelectedSlots = new ArrayList<>();

    // View settings
    private boolean backgroundVisible = true;

    // Current tool settings
    private String currentSlotType = "hotbar";

    // Mouse state
    private int mouseX, mouseY;
    private int guiMouseX, guiMouseY; // Mouse relative to GUI origin

    // GUI positioning
    private int guiX, guiY;
    private int guiScale = 2;

    // Editing state
    private boolean isDragging = false;
    private int dragOffsetX, dragOffsetY;
    private boolean leftClickLatch = false; // For single-click detection

    // Status message
    private String statusMessage = "";
    private long statusTime = 0;

    // Output path
    private String outputPath = "gui_output";

    // Key state tracking for single press
    private boolean[] keyWasDown = new boolean[512];

    public GuiEditorOverlay(int windowWidth, int windowHeight) {
        super(0, 0, windowWidth, windowHeight);

        // Create empty definition to start
        editingDefinition = new GuiDefinition(null, 176, 166);
        editingDefinition.setId(engine.registry.ResourceLocation.of("editor_gui"));

        updateGuiPosition(windowWidth, windowHeight);
    }

    /**
     * Toggle editor visibility
     */
    public void toggle() {
        active = !active;
        if (active) {
            setStatus("GUI Editor activated. Press H for help.");
        }
    }

    /**
     * Check if editor is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Set GUI scale
     */
    public void setGuiScale(int scale) {
        this.guiScale = Math.max(1, scale);
        updateGuiPosition(width, height);
    }

    /**
     * Load a texture to edit
     */
    public void loadTexture(String texturePath) {
        if (backgroundTexture != null) {
            backgroundTexture.cleanup();
        }

        try {
            backgroundTexture = new GuiTexture(texturePath);
            editingDefinition.setTexture(texturePath);
            editingDefinition.setWidth(backgroundTexture.getWidth());
            editingDefinition.setHeight(backgroundTexture.getHeight());
            updateGuiPosition(width, height);
            setStatus("Loaded: " + texturePath);
        } catch (Exception e) {
            setStatus("Failed to load: " + texturePath);
        }
    }

    /**
     * Load an existing GUI definition for editing
     */
    public void loadDefinition(GuiDefinition definition) {
        this.editingDefinition = definition;

        if (definition.getTexture() != null) {
            loadTexture(definition.getTexture());
        }

        updateGuiPosition(width, height);
        setStatus("Loaded definition: " + definition.getId());
    }

    private void updateGuiPosition(int windowWidth, int windowHeight) {
        this.width = windowWidth;
        this.height = windowHeight;

        float logicalW = windowWidth / (float) guiScale;
        float logicalH = windowHeight / (float) guiScale;

        guiX = (int) ((logicalW - editingDefinition.getWidth()) / 2);
        guiY = (int) ((logicalH - editingDefinition.getHeight()) / 2);
    }

    /**
     * Handle input
     */
    public void handleInput(InputManager input, double rawMouseX, double rawMouseY) {
        if (!active)
            return;

        // Convert mouse coordinates
        mouseX = (int) (rawMouseX / guiScale);
        mouseY = (int) (rawMouseY / guiScale);
        guiMouseX = mouseX - guiX;
        guiMouseY = mouseY - guiY;

        // Key handling (single press detection)
        handleKeyInput(input);

        // Mouse handling
        handleMouseInput(input);
    }

    private void handleKeyInput(InputManager input) {
        // Toggle grid snap - G
        if (keyPressed(input, GLFW_KEY_G)) {
            snapToGrid = !snapToGrid;
            setStatus("Grid snap: " + (snapToGrid ? "ON" : "OFF"));
        }

        // Toggle background - B
        if (keyPressed(input, GLFW_KEY_B)) {
            backgroundVisible = !backgroundVisible;
            setStatus("Background: " + (backgroundVisible ? "ON" : "OFF"));
        }

        // Save - S (with Ctrl)
        if (input.isKeyDown(GLFW_KEY_LEFT_CONTROL) && keyPressed(input, GLFW_KEY_S)) {
            saveDefinition();
        }

        // Help - H
        if (keyPressed(input, GLFW_KEY_H)) {
            printHelp();
        }

        // Slot type selection - 1-5
        if (keyPressed(input, GLFW_KEY_1)) {
            currentSlotType = "hotbar";
            setStatus("Slot type: hotbar");
        }
        if (keyPressed(input, GLFW_KEY_2)) {
            currentSlotType = "main";
            setStatus("Slot type: main");
        }
        if (keyPressed(input, GLFW_KEY_3)) {
            currentSlotType = "crafting_input";
            setStatus("Slot type: crafting_input");
        }
        if (keyPressed(input, GLFW_KEY_4)) {
            currentSlotType = "crafting_output";
            setStatus("Slot type: crafting_output");
        }
        if (keyPressed(input, GLFW_KEY_5)) {
            currentSlotType = "armor";
            setStatus("Slot type: armor");
        }

        // Move selected slot(s) with arrow keys
        if (selectedSlot != null || !multiSelectedSlots.isEmpty()) {
            int moveAmount = snapToGrid ? gridSize : 1;
            List<GuiSlotDefinition> toMove = multiSelectedSlots.isEmpty() ? List.of(selectedSlot) : multiSelectedSlots;

            if (keyPressed(input, GLFW_KEY_UP)) {
                for (GuiSlotDefinition s : toMove)
                    s.setY(s.getY() - moveAmount);
            }
            if (keyPressed(input, GLFW_KEY_DOWN)) {
                for (GuiSlotDefinition s : toMove)
                    s.setY(s.getY() + moveAmount);
            }
            if (keyPressed(input, GLFW_KEY_LEFT)) {
                for (GuiSlotDefinition s : toMove)
                    s.setX(s.getX() - moveAmount);
            }
            if (keyPressed(input, GLFW_KEY_RIGHT)) {
                for (GuiSlotDefinition s : toMove)
                    s.setX(s.getX() + moveAmount);
            }

            // Delete selected slot(s)
            if (keyPressed(input, GLFW_KEY_DELETE) || keyPressed(input, GLFW_KEY_BACKSPACE)) {
                if (!multiSelectedSlots.isEmpty()) {
                    for (GuiSlotDefinition s : multiSelectedSlots) {
                        editingDefinition.removeSlot(s.getId());
                    }
                    setStatus("Deleted " + multiSelectedSlots.size() + " slots");
                    multiSelectedSlots.clear();
                } else if (selectedSlot != null) {
                    editingDefinition.removeSlot(selectedSlot.getId());
                    setStatus("Slot deleted");
                }
                selectedSlot = null;
                selectedIndex = -1;
            }
        }

        // Tab to cycle selection
        if (keyPressed(input, GLFW_KEY_TAB)) {
            cycleSelection();
        }

        // Resize selected slot with +/- (support both main keyboard and numpad)
        boolean plusPressed = keyPressed(input, GLFW_KEY_EQUAL) || keyPressed(input, GLFW_KEY_KP_ADD);
        boolean minusPressed = keyPressed(input, GLFW_KEY_MINUS) || keyPressed(input, GLFW_KEY_KP_SUBTRACT);

        if (plusPressed) {
            if (selectedSlot != null) {
                int newSize = selectedSlot.getWidth() + 1;
                selectedSlot.setWidth(newSize);
                selectedSlot.setHeight(newSize);
                setStatus("Slot size: " + newSize + "x" + newSize);
            } else {
                gridSize = Math.min(36, gridSize + 1);
                setStatus("Grid size: " + gridSize);
            }
        }
        if (minusPressed) {
            if (selectedSlot != null) {
                int newSize = Math.max(8, selectedSlot.getWidth() - 1);
                selectedSlot.setWidth(newSize);
                selectedSlot.setHeight(newSize);
                setStatus("Slot size: " + newSize + "x" + newSize);
            } else {
                gridSize = Math.max(1, gridSize - 1);
                setStatus("Grid size: " + gridSize);
            }
        }

        // Generate Java code - J
        if (input.isKeyDown(GLFW_KEY_LEFT_CONTROL) && keyPressed(input, GLFW_KEY_J)) {
            generateJavaCode();
        }
    }

    private boolean keyPressed(InputManager input, int key) {
        boolean isDown = input.isKeyDown(key);
        boolean wasDown = keyWasDown[key];
        keyWasDown[key] = isDown;
        return isDown && !wasDown;
    }

    private void handleMouseInput(InputManager input) {
        boolean leftDown = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT);
        boolean rightDown = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_RIGHT);

        // Check if mouse is in GUI bounds
        boolean inBounds = guiMouseX >= 0 && guiMouseX < editingDefinition.getWidth() &&
                guiMouseY >= 0 && guiMouseY < editingDefinition.getHeight();

        if (!inBounds) {
            isDragging = false;
            return;
        }

        // Find slot under mouse
        GuiSlotDefinition slotUnderMouse = findSlotAt(guiMouseX, guiMouseY);

        boolean ctrlHeld = input.isKeyDown(GLFW_KEY_LEFT_CONTROL) || input.isKeyDown(GLFW_KEY_RIGHT_CONTROL);
        boolean leftClick = leftDown && !leftClickLatch; // Single click detection
        leftClickLatch = leftDown;

        // Left click - select or place
        if (leftDown) {
            if (!isDragging) {
                if (slotUnderMouse != null) {
                    if (ctrlHeld && leftClick) {
                        // Ctrl+click - toggle multi-selection (only on click, not hold)
                        if (multiSelectedSlots.contains(slotUnderMouse)) {
                            multiSelectedSlots.remove(slotUnderMouse);
                            setStatus("Deselected: " + slotUnderMouse.getId() + " (" + multiSelectedSlots.size()
                                    + " selected)");
                        } else {
                            multiSelectedSlots.add(slotUnderMouse);
                            setStatus("Multi-select: " + multiSelectedSlots.size() + " slots");
                        }
                    } else if (!ctrlHeld) {
                        // Normal click - select single slot
                        selectedSlot = slotUnderMouse;
                        selectedIndex = editingDefinition.getSlots().indexOf(slotUnderMouse);
                        if (multiSelectedSlots.isEmpty()) {
                            isDragging = true;
                            dragOffsetX = guiMouseX - slotUnderMouse.getX();
                            dragOffsetY = guiMouseY - slotUnderMouse.getY();
                        } else if (multiSelectedSlots.contains(slotUnderMouse)) {
                            // Dragging multi-selection
                            isDragging = true;
                            dragOffsetX = guiMouseX - slotUnderMouse.getX();
                            dragOffsetY = guiMouseY - slotUnderMouse.getY();
                        } else {
                            // Clicked outside multi-selection - clear it
                            multiSelectedSlots.clear();
                            isDragging = true;
                            dragOffsetX = guiMouseX - slotUnderMouse.getX();
                            dragOffsetY = guiMouseY - slotUnderMouse.getY();
                        }
                    }
                } else {
                    // Place new slot (clear multi-selection)
                    multiSelectedSlots.clear();
                    placeSlot(guiMouseX, guiMouseY);
                }
            } else {
                // Dragging - move slot(s)
                int newX = guiMouseX - dragOffsetX;
                int newY = guiMouseY - dragOffsetY;

                if (snapToGrid) {
                    newX = (newX / gridSize) * gridSize;
                    newY = (newY / gridSize) * gridSize;
                }

                if (!multiSelectedSlots.isEmpty() && selectedSlot != null) {
                    // Move all multi-selected slots
                    int deltaX = newX - selectedSlot.getX();
                    int deltaY = newY - selectedSlot.getY();
                    for (GuiSlotDefinition s : multiSelectedSlots) {
                        s.setX(s.getX() + deltaX);
                        s.setY(s.getY() + deltaY);
                    }
                } else if (selectedSlot != null) {
                    selectedSlot.setX(newX);
                    selectedSlot.setY(newY);
                }
            }
        } else {
            isDragging = false;
        }

        // Right click - delete
        if (rightDown && slotUnderMouse != null) {
            editingDefinition.removeSlot(slotUnderMouse.getId());
            if (slotUnderMouse == selectedSlot) {
                selectedSlot = null;
                selectedIndex = -1;
            }
            setStatus("Deleted: " + slotUnderMouse.getId());
        }
    }

    private void placeSlot(int x, int y) {
        if (snapToGrid) {
            x = (x / gridSize) * gridSize;
            y = (y / gridSize) * gridSize;
        }

        // Calculate next available index for this slot type
        int maxIndex = -1;
        for (GuiSlotDefinition s : editingDefinition.getSlots()) {
            if (currentSlotType.equals(s.getType()) || s.getType().startsWith(currentSlotType)) {
                maxIndex = Math.max(maxIndex, s.getIndex());
            }
        }
        int newIndex = maxIndex + 1;

        String id = currentSlotType + "_" + newIndex;
        GuiSlotDefinition slot = new GuiSlotDefinition(id, x, y, currentSlotType, newIndex);
        editingDefinition.addSlot(slot);

        selectedSlot = slot;
        selectedIndex = editingDefinition.getSlots().size() - 1;

        setStatus("Placed: " + id + " at (" + x + ", " + y + ")");
    }

    private GuiSlotDefinition findSlotAt(int x, int y) {
        for (GuiSlotDefinition slot : editingDefinition.getSlots()) {
            if (x >= slot.getX() && x < slot.getX() + slot.getWidth() &&
                    y >= slot.getY() && y < slot.getY() + slot.getHeight()) {
                return slot;
            }
        }
        return null;
    }

    private int countSlotsOfType(String type) {
        int count = 0;
        for (GuiSlotDefinition slot : editingDefinition.getSlots()) {
            if (type.equals(slot.getType())) {
                count++;
            }
        }
        return count;
    }

    private void cycleSelection() {
        List<GuiSlotDefinition> slots = editingDefinition.getSlots();
        if (slots.isEmpty()) {
            selectedSlot = null;
            selectedIndex = -1;
            return;
        }

        selectedIndex = (selectedIndex + 1) % slots.size();
        selectedSlot = slots.get(selectedIndex);
        setStatus("Selected: " + selectedSlot.getId());
    }

    private void saveDefinition() {
        try {
            String fileName = editingDefinition.getId().getPath() + ".json";
            Path path = Paths.get(outputPath, fileName);
            path.getParent().toFile().mkdirs();

            GuiDefinitionLoader.saveToFile(editingDefinition, path);
            setStatus("Saved to: " + path);

            // Also print JSON to console
            System.out.println("\n=== Generated JSON ===");
            System.out.println(GuiDefinitionLoader.toJson(editingDefinition));
            System.out.println("======================\n");

        } catch (IOException e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private void generateJavaCode() {
        String className = toPascalCase(editingDefinition.getId().getPath()) + "Gui";
        String code = GuiDefinitionLoader.generateJavaCode(editingDefinition, className);

        System.out.println("\n=== Generated Java Code ===");
        System.out.println(code);
        System.out.println("===========================\n");

        setStatus("Java code printed to console");
    }

    private String toPascalCase(String input) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private void printHelp() {
        System.out.println("\n=== GUI Editor Help ===");
        System.out.println("F7         - Toggle editor");
        System.out.println("Left Click - Place/Select slot");
        System.out.println("Right Click- Delete slot");
        System.out.println("Drag       - Move selected slot");
        System.out.println("Arrow Keys - Nudge selected slot");
        System.out.println("Delete     - Delete selected slot");
        System.out.println("Tab        - Cycle selection");
        System.out.println("G          - Toggle grid snap");
        System.out.println("+/-        - Adjust grid size");
        System.out.println("1-5        - Select slot type");
        System.out.println("  1=hotbar, 2=main, 3=craft_in, 4=craft_out, 5=armor");
        System.out.println("Ctrl+S     - Save to JSON");
        System.out.println("Ctrl+J     - Generate Java code");
        System.out.println("H          - Show this help");
        System.out.println("========================\n");

        setStatus("Help printed to console");
    }

    private void setStatus(String message) {
        this.statusMessage = message;
        this.statusTime = System.currentTimeMillis();
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!active)
            return;

        // Dark overlay
        float logicalW = width / (float) guiScale;
        float logicalH = height / (float) guiScale;
        renderer.renderRect(0, 0, logicalW, logicalH, 0, 0, 0, 0.7f);

        // GUI background
        int guiW = editingDefinition.getWidth();
        int guiH = editingDefinition.getHeight();

        if (backgroundVisible) {
            if (backgroundTexture != null) {
                renderer.renderQuad(guiX, guiY, guiW, guiH, backgroundTexture);
            } else {
                renderer.renderRect(guiX, guiY, guiW, guiH, 0.2f, 0.2f, 0.2f, 0.95f);
            }
        }

        // Grid
        if (snapToGrid) {
            renderGrid(renderer, guiX, guiY, guiW, guiH);
        }

        // Render all slots
        for (GuiSlotDefinition slot : editingDefinition.getSlots()) {
            boolean isMultiSelected = multiSelectedSlots.contains(slot);
            renderSlotPreview(renderer, slot, slot == selectedSlot, isMultiSelected);
        }

        // Cursor preview (new slot placement)
        if (selectedSlot == null && !isDragging) {
            int previewX = guiMouseX;
            int previewY = guiMouseY;
            if (snapToGrid) {
                previewX = (previewX / gridSize) * gridSize;
                previewY = (previewY / gridSize) * gridSize;
            }

            if (previewX >= 0 && previewY >= 0 &&
                    previewX + 18 <= guiW && previewY + 18 <= guiH) {
                renderer.renderRect(guiX + previewX, guiY + previewY, 18, 18,
                        0.3f, 0.8f, 0.3f, 0.5f);
            }
        }

        // Status bar
        renderStatusBar(renderer, logicalW, logicalH);

        // Tool info
        renderToolInfo(renderer);
    }

    private void renderGrid(GuiRenderer renderer, int gx, int gy, int gw, int gh) {
        // Vertical lines
        for (int x = 0; x <= gw; x += gridSize) {
            renderer.renderRect(gx + x, gy, 1, gh, 1, 1, 1, 0.15f);
        }
        // Horizontal lines
        for (int y = 0; y <= gh; y += gridSize) {
            renderer.renderRect(gx, gy + y, gw, 1, 1, 1, 1, 0.15f);
        }
    }

    private void renderSlotPreview(GuiRenderer renderer, GuiSlotDefinition slot, boolean selected,
            boolean multiSelected) {
        int sx = guiX + slot.getX();
        int sy = guiY + slot.getY();
        int sw = slot.getWidth();
        int sh = slot.getHeight();

        // Slot background
        float[] color = getSlotTypeColor(slot.getType());
        renderer.renderRect(sx, sy, sw, sh, color[0], color[1], color[2], 0.6f);

        // Border - different colors for selected vs multi-selected
        float borderAlpha, borderR, borderG, borderB;
        if (selected) {
            borderAlpha = 1.0f;
            borderR = 1.0f;
            borderG = 1.0f;
            borderB = 0.0f; // Yellow
        } else if (multiSelected) {
            borderAlpha = 1.0f;
            borderR = 0.0f;
            borderG = 1.0f;
            borderB = 1.0f; // Cyan
        } else {
            borderAlpha = 0.5f;
            borderR = 0.8f;
            borderG = 0.8f;
            borderB = 0.8f; // Gray
        }

        renderer.renderRect(sx, sy, sw, 1, borderR, borderG, borderB, borderAlpha);
        renderer.renderRect(sx, sy + sh - 1, sw, 1, borderR, borderG, borderB, borderAlpha);
        renderer.renderRect(sx, sy, 1, sh, borderR, borderG, borderB, borderAlpha);
        renderer.renderRect(sx + sw - 1, sy, 1, sh, borderR, borderG, borderB, borderAlpha);

        // Slot ID text (simplified - just show index)
        renderer.renderText(String.valueOf(slot.getIndex()), sx + 2, sy + 2, 6, 1, 1, 1, 0.8f);
    }

    private float[] getSlotTypeColor(String type) {
        return switch (type) {
            case "hotbar" -> new float[] { 0.2f, 0.4f, 0.8f };
            case "main" -> new float[] { 0.3f, 0.3f, 0.5f };
            case "crafting_input" -> new float[] { 0.6f, 0.4f, 0.2f };
            case "crafting_output" -> new float[] { 0.8f, 0.6f, 0.2f };
            case "armor" -> new float[] { 0.5f, 0.2f, 0.5f };
            default -> new float[] { 0.4f, 0.4f, 0.4f };
        };
    }

    private void renderStatusBar(GuiRenderer renderer, float logicalW, float logicalH) {
        // Background
        renderer.renderRect(0, logicalH - 20, logicalW, 20, 0, 0, 0, 0.8f);

        // Status message (fade after 3 seconds)
        long elapsed = System.currentTimeMillis() - statusTime;
        if (elapsed < 3000 && !statusMessage.isEmpty()) {
            float alpha = elapsed < 2500 ? 1.0f : 1.0f - (elapsed - 2500) / 500f;
            renderer.renderText(statusMessage, 5, logicalH - 15, 8, 1, 1, 0.5f, alpha);
        }

        // Mouse position
        String posText = "(" + guiMouseX + ", " + guiMouseY + ")";
        renderer.renderText(posText, logicalW - 80, logicalH - 15, 8, 0.7f, 0.7f, 0.7f, 1);
    }

    private void renderToolInfo(GuiRenderer renderer) {
        int y = 5;
        int lineH = 10;

        renderer.renderText("GUI Editor", 5, y, 10, 1, 1, 1, 1);
        y += lineH + 5;

        renderer.renderText("Type: " + currentSlotType, 5, y, 8, 0.8f, 0.8f, 0.8f, 1);
        y += lineH;

        renderer.renderText("Grid: " + (snapToGrid ? gridSize + "px" : "OFF"), 5, y, 8, 0.8f, 0.8f, 0.8f, 1);
        y += lineH;

        renderer.renderText("Back: " + (backgroundVisible ? "ON" : "OFF") + " (B)", 5, y, 8, 0.8f, 0.8f, 0.8f, 1);
        y += lineH;

        renderer.renderText("Slots: " + editingDefinition.getSlots().size(), 5, y, 8, 0.8f, 0.8f, 0.8f, 1);
        y += lineH;

        if (selectedSlot != null) {
            renderer.renderText("Sel: " + selectedSlot.getId(), 5, y, 8, 1, 1, 0, 1);
            y += lineH;
        }

        // --- Visual Slot Picker ---
        y += 5;
        String[] types = { "hotbar", "main", "crafting_input", "crafting_output", "armor" };
        int px = 5;
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            float[] c = getSlotTypeColor(type);
            boolean isSel = type.equals(currentSlotType);

            // Draw box
            renderer.renderRect(px, y, 10, 10, c[0], c[1], c[2], 1.0f);
            if (isSel) {
                // Highlight border
                renderer.renderRect(px, y, 10, 1, 1, 1, 1, 1);
                renderer.renderRect(px, y + 9, 10, 1, 1, 1, 1, 1);
                renderer.renderRect(px, y, 1, 10, 1, 1, 1, 1);
                renderer.renderRect(px + 9, y, 1, 10, 1, 1, 1, 1);
            }

            // Draw number
            renderer.renderText(String.valueOf(i + 1), px + 2, y + 2, 6, 1, 1, 1, 1);

            px += 14;
        }
    }

    /**
     * Set output directory for saved files
     */
    public void setOutputPath(String path) {
        this.outputPath = path;
    }

    /**
     * Get the current definition being edited
     */
    public GuiDefinition getEditingDefinition() {
        return editingDefinition;
    }

    /**
     * Set the definition to edit (and optionally set its name)
     */
    public void newDefinition(String name, int width, int height) {
        editingDefinition = new GuiDefinition(null, width, height);
        editingDefinition.setId(engine.registry.ResourceLocation.of(name));
        selectedSlot = null;
        selectedIndex = -1;
        multiSelectedSlots.clear();
        updateGuiPosition(this.width, this.height);
        setStatus("New GUI: " + name + " (" + width + "x" + height + ")");
    }

    public void cleanup() {
        if (backgroundTexture != null) {
            backgroundTexture.cleanup();
        }
    }
}
