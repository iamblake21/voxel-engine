package engine.ui;

/**
 * Base class for all GUI components
 */
public abstract class GuiComponent {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;

    public GuiComponent(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Render this component
     */
    public abstract void render(GuiRenderer renderer);

    /**
     * Check if mouse is over this component
     */
    public boolean isMouseOver(int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    /**
     * Handle mouse click
     */
    public void onClick(int mx, int my) {
        // Override in subclasses
    }

    // Getters and Setters
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

    public boolean isVisible() {
        return visible;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
