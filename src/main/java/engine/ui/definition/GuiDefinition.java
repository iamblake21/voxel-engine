package engine.ui.definition;

import engine.registry.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Complete definition of a GUI screen.
 * Loaded from JSON metadata file.
 * 
 * JSON Format:
 * {
 *   "texture": "gui/inventory.png",
 *   "width": 176,
 *   "height": 166,
 *   "slots": [ ... ],
 *   "labels": [ ... ]
 * }
 */
public class GuiDefinition {
    
    // Texture path relative to resources (e.g., "textures/gui/inventory.png")
    private String texture;
    
    // GUI dimensions (in texture pixels, before scaling)
    private int width;
    private int height;
    
    // Background color (used if no texture, or as fallback)
    private float bgR = 0.0f;
    private float bgG = 0.0f;
    private float bgB = 0.0f;
    private float bgA = 0.95f;
    
    // Slot definitions
    private List<GuiSlotDefinition> slots = new ArrayList<>();
    
    // Label definitions
    private List<GuiLabelDefinition> labels = new ArrayList<>();
    
    // Progress bars, buttons, etc. can be added here
    private List<GuiProgressBarDefinition> progressBars = new ArrayList<>();
    
    // Registry ID (set after loading)
    private transient ResourceLocation id;
    
    public GuiDefinition() {
        // Default constructor for JSON deserialization
    }
    
    public GuiDefinition(String texture, int width, int height) {
        this.texture = texture;
        this.width = width;
        this.height = height;
    }
    
    // ==================== SLOT HELPERS ====================
    
    /**
     * Get all slots of a specific type
     */
    public List<GuiSlotDefinition> getSlotsByType(String type) {
        List<GuiSlotDefinition> result = new ArrayList<>();
        for (GuiSlotDefinition slot : slots) {
            if (type.equals(slot.getType())) {
                result.add(slot);
            }
        }
        return result;
    }
    
    /**
     * Get a slot by ID
     */
    public Optional<GuiSlotDefinition> getSlotById(String id) {
        for (GuiSlotDefinition slot : slots) {
            if (id.equals(slot.getId())) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Get slot at a specific position (for hit testing)
     */
    public Optional<GuiSlotDefinition> getSlotAt(int x, int y) {
        for (GuiSlotDefinition slot : slots) {
            if (x >= slot.getX() && x < slot.getX() + slot.getWidth() &&
                y >= slot.getY() && y < slot.getY() + slot.getHeight()) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Add a slot definition (for editor tool)
     */
    public void addSlot(GuiSlotDefinition slot) {
        slots.add(slot);
    }
    
    /**
     * Remove a slot by ID
     */
    public boolean removeSlot(String id) {
        return slots.removeIf(s -> id.equals(s.getId()));
    }
    
    // ==================== LABEL HELPERS ====================
    
    /**
     * Get a label by ID
     */
    public Optional<GuiLabelDefinition> getLabelById(String id) {
        for (GuiLabelDefinition label : labels) {
            if (id.equals(label.getId())) {
                return Optional.of(label);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Add a label definition
     */
    public void addLabel(GuiLabelDefinition label) {
        labels.add(label);
    }
    
    // ==================== GETTERS ====================
    
    public String getTexture() {
        return texture;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public float getBgR() {
        return bgR;
    }
    
    public float getBgG() {
        return bgG;
    }
    
    public float getBgB() {
        return bgB;
    }
    
    public float getBgA() {
        return bgA;
    }
    
    public List<GuiSlotDefinition> getSlots() {
        return Collections.unmodifiableList(slots);
    }
    
    public List<GuiLabelDefinition> getLabels() {
        return Collections.unmodifiableList(labels);
    }
    
    public List<GuiProgressBarDefinition> getProgressBars() {
        return Collections.unmodifiableList(progressBars);
    }
    
    public ResourceLocation getId() {
        return id;
    }
    
    // ==================== SETTERS ====================
    
    public void setTexture(String texture) {
        this.texture = texture;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public void setBackgroundColor(float r, float g, float b, float a) {
        this.bgR = r;
        this.bgG = g;
        this.bgB = b;
        this.bgA = a;
    }
    
    public void setId(ResourceLocation id) {
        this.id = id;
    }
    
    // ==================== VALIDATION ====================
    
    /**
     * Validate the definition for common errors
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        
        if (width <= 0) {
            errors.add("Width must be positive");
        }
        if (height <= 0) {
            errors.add("Height must be positive");
        }
        
        // Check for duplicate slot IDs
        List<String> seenIds = new ArrayList<>();
        for (GuiSlotDefinition slot : slots) {
            if (slot.getId() != null) {
                if (seenIds.contains(slot.getId())) {
                    errors.add("Duplicate slot ID: " + slot.getId());
                }
                seenIds.add(slot.getId());
            }
            
            // Check slot bounds
            if (slot.getX() < 0 || slot.getY() < 0) {
                errors.add("Slot " + slot.getId() + " has negative position");
            }
            if (slot.getX() + slot.getWidth() > width || slot.getY() + slot.getHeight() > height) {
                errors.add("Slot " + slot.getId() + " extends outside GUI bounds");
            }
        }
        
        return errors;
    }
    
    @Override
    public String toString() {
        return "GuiDefinition{" +
                "id=" + id +
                ", texture='" + texture + '\'' +
                ", size=" + width + "x" + height +
                ", slots=" + slots.size() +
                ", labels=" + labels.size() +
                '}';
    }
}
