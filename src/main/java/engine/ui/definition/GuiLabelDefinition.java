package engine.ui.definition;

/**
 * Definition of a text label in a GUI.
 * Loaded from JSON metadata.
 */
public class GuiLabelDefinition {
    
    private String id;
    private String text;
    private int x;
    private int y;
    private float size = 8;
    private boolean centered = false;
    
    // Color (default white)
    private float r = 1.0f;
    private float g = 1.0f;
    private float b = 1.0f;
    private float a = 1.0f;
    
    // Optional: dynamic text key for localization or runtime text
    private String textKey;
    
    public GuiLabelDefinition() {
        // Default constructor for JSON deserialization
    }
    
    public GuiLabelDefinition(String text, int x, int y) {
        this.text = text;
        this.x = x;
        this.y = y;
    }
    
    // ==================== GETTERS ====================
    
    public String getId() {
        return id;
    }
    
    public String getText() {
        return text;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public float getSize() {
        return size;
    }
    
    public boolean isCentered() {
        return centered;
    }
    
    public float getR() {
        return r;
    }
    
    public float getG() {
        return g;
    }
    
    public float getB() {
        return b;
    }
    
    public float getA() {
        return a;
    }
    
    public String getTextKey() {
        return textKey;
    }
    
    // ==================== SETTERS ====================
    
    public void setId(String id) {
        this.id = id;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public void setX(int x) {
        this.x = x;
    }
    
    public void setY(int y) {
        this.y = y;
    }
    
    public void setSize(float size) {
        this.size = size;
    }
    
    public void setCentered(boolean centered) {
        this.centered = centered;
    }
    
    public void setColor(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }
    
    public void setTextKey(String textKey) {
        this.textKey = textKey;
    }
    
    @Override
    public String toString() {
        return "GuiLabelDefinition{" +
                "text='" + text + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", centered=" + centered +
                '}';
    }
}
