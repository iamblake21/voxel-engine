package engine.ui.definition;

import engine.entity.inventory.PlayerInventory;

/**
 * Definition of a single slot in a GUI.
 * Loaded from JSON metadata.
 */
public class GuiSlotDefinition {
    
    private String id;
    private int x;
    private int y;
    private int width = 18;
    private int height = 18;
    private String type;
    private int index;
    
    // Absolute index assigned by builder (sequential order in GUI)
    private int absoluteIndex = -1;
    
    // Optional properties
    private boolean canInsert = true;
    private boolean canExtract = true;
    private String filter;
    
    public GuiSlotDefinition() {
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
    
    // ==================== SETTERS ====================
    
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
    
    /** NUOVO METODO: Per impostare l'indice assoluto esplicitamente. */
    public void setAbsoluteIndex(int absoluteIndex) {
        this.absoluteIndex = absoluteIndex;
    }
    
    /**
     * Get absolute slot index.
     * Se esplicitamente impostato (container e slot player in GUI composte), usalo.
     * Altrimenti calcola dal tipo (fallback, non dovrebbe essere usato in GUIs composte).
     */
    public int getAbsoluteIndex() {
        if (absoluteIndex >= 0) {
            return absoluteIndex;
        }
        // Fallback (solo se absoluteIndex non è stato impostato)
        return switch (type) {
            case "hotbar" -> index;
            // Assumiamo che PlayerInventory.HOTBAR_SIZE sia 9
            case "main" -> PlayerInventory.HOTBAR_SIZE + index; 
            // Gestione di altri tipi se necessario (armor, crafting, ecc.)
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
                ", absoluteIndex=" + getAbsoluteIndex() +
                '}';
    }
}