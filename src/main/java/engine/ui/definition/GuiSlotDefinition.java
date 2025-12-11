package engine.ui.definition;

/**
 * Definition of a single slot in a GUI.
 * Loaded from JSON metadata.
 */
public class GuiSlotDefinition {
    
    private String id;
    private int x;
    private int y;
    private int width = 18;  // Default slot size
    private int height = 18;
    private String type;     // "hotbar", "main", "crafting_input", "crafting_output", "armor", etc.
    private int index;       // Index within the slot type (e.g., hotbar slot 0-8)
    
    // Optional properties
    private boolean canInsert = true;
    private boolean canExtract = true;
    private String filter;   // Item filter (e.g., "armor_helmet", "fuel")
    
    public GuiSlotDefinition() {
        // Default constructor for JSON deserialization
    }
    
    public GuiSlotDefinition(String id, int x, int y, String type, int index) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.type = type;
        this.index = index;
    }
    
    // ==================== GETTERS ====================
    
    public String getId() {
        return id;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public String getType() {
        return type;
    }
    
    public int getIndex() {
        return index;
    }
    
    public boolean canInsert() {
        return canInsert;
    }
    
    public boolean canExtract() {
        return canExtract;
    }
    
    public String getFilter() {
        return filter;
    }
    
    // ==================== SETTERS (for editor tool) ====================
    
    public void setId(String id) {
        this.id = id;
    }
    
    public void setX(int x) {
        this.x = x;
    }
    
    public void setY(int y) {
        this.y = y;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public void setIndex(int index) {
        this.index = index;
    }
    
    public void setCanInsert(boolean canInsert) {
        this.canInsert = canInsert;
    }
    
    public void setCanExtract(boolean canExtract) {
        this.canExtract = canExtract;
    }
    
    public void setFilter(String filter) {
        this.filter = filter;
    }
    
    /**
     * Calculate absolute slot index for inventory access.
     * Hotbar: 0-8, Main: 9-35, etc.
     */
    public int getAbsoluteIndex() {
        return switch (type) {
            case "hotbar" -> index;
            case "main" -> 9 + index;  // Main inventory starts at 9
            case "armor" -> 36 + index;
            case "offhand" -> 40;
            case "crafting_input" -> 41 + index;
            case "crafting_output" -> 45;
            default -> index;
        };
    }
    
    @Override
    public String toString() {
        return "GuiSlotDefinition{" +
                "id='" + id + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", type='" + type + '\'' +
                ", index=" + index +
                '}';
    }
}
