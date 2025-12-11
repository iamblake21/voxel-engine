package engine.ui;

import engine.ui.definition.GuiDefinition;
import engine.ui.definition.GuiLabelDefinition;
import engine.ui.definition.GuiSlotDefinition;
import engine.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base class for GUI screens that use texture + JSON metadata.
 * * Subclasses provide:
 * - Inventory/container binding
 * - Custom rendering logic
 * - Input handling
 * * The GUI definition provides:
 * - Background texture
 * - Slot positions
 * - Label positions
 */
public abstract class TexturedGui extends GuiComponent {

    protected final GuiDefinition definition;
    protected final Map<String, InventorySlot> slotComponents;

    // Background texture (loaded from definition)
    protected GuiTexture backgroundTexture;

    // GUI scale
    protected int guiScale = 2;

    // Window dimensions (for centering)
    protected int windowWidth;
    protected int windowHeight;

    // Callback for getting item stacks (provided by subclass)
    protected Function<Integer, ItemStack> stackProvider;

    // Selected slot (for highlighting)
    protected int selectedSlotIndex = -1;

    /**
     * Create a textured GUI from a definition
     * * @param definition The GUI definition (from JSON)
     * 
     * @param windowWidth  Window width for centering
     * @param windowHeight Window height for centering
     */
    public TexturedGui(GuiDefinition definition, int windowWidth, int windowHeight) {
        // Usa le dimensioni definite nel JSON (es. 176x166) per il componente
        super(0, 0, definition.getWidth(), definition.getHeight());

        this.definition = definition;
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.slotComponents = new HashMap<>();

        // Center the GUI
        updatePosition(windowWidth, windowHeight);

        // Load background texture
        if (definition.getTexture() != null && !definition.getTexture().isEmpty()) {
            try {
                this.backgroundTexture = new GuiTexture(definition.getTexture());
            } catch (Exception e) {
                System.err.println("[TexturedGui] Failed to load texture: " + definition.getTexture());
                this.backgroundTexture = null;
            }
        }

        // Create slot components from definition
        createSlotComponents();

        System.out.println("[TexturedGui] Created from definition: " + definition.getId() +
                " Size: " + width + "x" + height);
    }

    /**
     * Create InventorySlot components from the definition
     */
    protected void createSlotComponents() {
        for (GuiSlotDefinition slotDef : definition.getSlots()) {
            // Calculate absolute position (definition position + GUI offset)
            int slotX = x + slotDef.getX();
            int slotY = y + slotDef.getY();

            InventorySlot slot = new InventorySlot(slotX, slotY, slotDef.getAbsoluteIndex());
            slotComponents.put(slotDef.getId(), slot);
        }
    }

    /**
     * Update slot positions after GUI repositioning
     */
    protected void updateSlotPositions() {
        for (GuiSlotDefinition slotDef : definition.getSlots()) {
            InventorySlot slot = slotComponents.get(slotDef.getId());
            if (slot != null) {
                slot.setPosition(x + slotDef.getX(), y + slotDef.getY());
            }
        }
    }

    /**
     * Set the function that provides ItemStacks for slots.
     * Called with absolute slot index, should return the ItemStack.
     */
    public void setStackProvider(Function<Integer, ItemStack> provider) {
        this.stackProvider = provider;
    }

    /**
     * Set GUI scale
     */
    public void setGuiScale(int scale) {
        this.guiScale = Math.max(1, scale);
    }

    /**
     * Get GUI scale
     */
    public int getGuiScale() {
        return guiScale;
    }

    /**
     * Set selected slot index (for hotbar highlighting)
     */
    public void setSelectedSlot(int index) {
        this.selectedSlotIndex = index;
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!visible)
            return;

        // 1. Render darkened background overlay
        renderOverlay(renderer);

        // 2. Render GUI background (CORRETTO con UV)
        renderBackground(renderer);

        // 3. Render labels
        renderLabels(renderer);

        // 4. Render all slots
        renderSlots(renderer);

        // 5. Custom rendering (override in subclass)
        renderCustom(renderer);
    }

    /**
     * Render the darkened overlay behind the GUI
     */
    protected void renderOverlay(GuiRenderer renderer) {
        float logicalW = windowWidth / (float) guiScale;
        float logicalH = windowHeight / (float) guiScale;
        renderer.renderRect(0, 0, logicalW, logicalH, 0, 0, 0, 0.5f);
    }

    /**
     * Render the GUI background using UV mapping based on JSON dimensions
     */
    protected void renderBackground(GuiRenderer renderer) {
        if (backgroundTexture != null) {
            // MODIFICA CRUCIALE:
            // Usa renderSubTexture per disegnare solo la parte definita nel JSON.
            // x, y, width, height -> Posizione schermo e dimensione (176x166)
            // 0, 0 -> Coordinate inizio texture
            // width, height -> Quanto ritagliare dalla texture (176x166)
            renderer.renderSubTexture(
                    x, y, width, height,
                    backgroundTexture,
                    0, 0, width, height);
        } else {
            // Fallback: solid color background
            renderer.renderRect(x, y, width, height,
                    definition.getBgR(), definition.getBgG(),
                    definition.getBgB(), definition.getBgA());
        }
    }

    /**
     * Render labels from definition
     */
    protected void renderLabels(GuiRenderer renderer) {
        for (GuiLabelDefinition labelDef : definition.getLabels()) {
            String text = labelDef.getText();
            float labelX = x + labelDef.getX();
            float labelY = y + labelDef.getY();

            if (labelDef.isCentered()) {
                // Approximate centering (proper font metrics would be better)
                float textWidth = text.length() * labelDef.getSize() * 0.7f;
                labelX -= textWidth / 2;
            }

            renderer.renderText(text, labelX, labelY, labelDef.getSize(),
                    labelDef.getR(), labelDef.getG(), labelDef.getB(), labelDef.getA());
        }
    }

    /**
     * Render all slots with their contents
     */
    protected void renderSlots(GuiRenderer renderer) {
        for (GuiSlotDefinition slotDef : definition.getSlots()) {
            InventorySlot slot = slotComponents.get(slotDef.getId());
            if (slot == null)
                continue;

            // Update slot stack from provider
            if (stackProvider != null) {
                ItemStack stack = stackProvider.apply(slotDef.getAbsoluteIndex());
                slot.setStack(stack != null ? stack : ItemStack.EMPTY);
            }

            // Update selection state
            slot.setSelected(slotDef.getAbsoluteIndex() == selectedSlotIndex);

            // Render the slot
            slot.render(renderer);
        }
    }

    /**
     * Override in subclass for custom rendering
     */
    protected void renderCustom(GuiRenderer renderer) {
        // Default: nothing
    }

    /**
     * Render an item following the cursor
     */
    public void renderCursorItem(GuiRenderer renderer, ItemStack cursorStack, int mouseX, int mouseY) {
        if (cursorStack == null || cursorStack.isEmpty()) {
            return;
        }

        int renderX = mouseX - InventorySlot.SLOT_SIZE / 2;
        int renderY = mouseY - InventorySlot.SLOT_SIZE / 2;

        InventorySlot cursorSlot = new InventorySlot(renderX, renderY, -1);
        cursorSlot.setStack(cursorStack);
        cursorSlot.render(renderer);
    }

    /**
     * Update hover state for all slots
     */
    public void updateHoverStates(int mouseX, int mouseY) {
        for (InventorySlot slot : slotComponents.values()) {
            slot.setHovered(slot.isMouseOver(mouseX, mouseY));
        }
    }

    /**
     * Get the slot at a mouse position
     */
    public Optional<InventorySlot> getSlotAt(int mouseX, int mouseY) {
        for (InventorySlot slot : slotComponents.values()) {
            if (slot.isMouseOver(mouseX, mouseY)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    /**
     * Get slot by ID
     */
    public Optional<InventorySlot> getSlotById(String id) {
        return Optional.ofNullable(slotComponents.get(id));
    }

    /**
     * Convert raw mouse coordinates to GUI coordinates
     */
    public int[] convertMouseCoords(double rawX, double rawY) {
        return new int[] {
                (int) (rawX / guiScale),
                (int) (rawY / guiScale)
        };
    }

    /**
     * Update position (center in window)
     */
    public void updatePosition(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;

        // Calculate centered position in logical (scaled) coordinates
        float logicalW = windowWidth / (float) guiScale;
        float logicalH = windowHeight / (float) guiScale;

        this.x = (int) ((logicalW - width) / 2);
        this.y = (int) ((logicalH - height) / 2);

        // Update slot positions
        updateSlotPositions();
    }

    /**
     * Get the GUI definition
     */
    public GuiDefinition getDefinition() {
        return definition;
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (backgroundTexture != null) {
            backgroundTexture.cleanup();
            backgroundTexture = null;
        }
    }
}