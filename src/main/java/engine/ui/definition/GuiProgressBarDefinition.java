package engine.ui.definition;

/**
 * Definition of a progress bar in a GUI.
 * Used for furnace cooking progress, crafting progress, etc.
 */
public class GuiProgressBarDefinition {
    
    private String id;
    
    // Position and size
    private int x;
    private int y;
    private int width;
    private int height;
    
    // Direction the bar fills
    private Direction direction = Direction.RIGHT;
    
    // Texture coordinates for the empty and filled states
    // (u, v coordinates in the GUI texture)
    private int emptyU;
    private int emptyV;
    private int filledU;
    private int filledV;
    
    // Alternative: separate texture for the bar
    private String barTexture;
    
    public enum Direction {
        RIGHT,  // Fills left to right
        LEFT,   // Fills right to left
        UP,     // Fills bottom to top
        DOWN    // Fills top to bottom
    }
    
    public GuiProgressBarDefinition() {
        // Default constructor for JSON
    }
    
    public GuiProgressBarDefinition(String id, int x, int y, int width, int height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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
    
    public Direction getDirection() {
        return direction;
    }
    
    public int getEmptyU() {
        return emptyU;
    }
    
    public int getEmptyV() {
        return emptyV;
    }
    
    public int getFilledU() {
        return filledU;
    }
    
    public int getFilledV() {
        return filledV;
    }
    
    public String getBarTexture() {
        return barTexture;
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
    
    public void setDirection(Direction direction) {
        this.direction = direction;
    }
    
    public void setEmptyUV(int u, int v) {
        this.emptyU = u;
        this.emptyV = v;
    }
    
    public void setFilledUV(int u, int v) {
        this.filledU = u;
        this.filledV = v;
    }
    
    public void setBarTexture(String barTexture) {
        this.barTexture = barTexture;
    }
    
    @Override
    public String toString() {
        return "GuiProgressBarDefinition{" +
                "id='" + id + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", size=" + width + "x" + height +
                ", direction=" + direction +
                '}';
    }
}
