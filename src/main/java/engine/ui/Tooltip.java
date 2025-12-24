package engine.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltip component that displays item information on hover.
 * 
 * Features:
 * - Multi-line text support
 * - Auto-sizing based on content
 * - Smart positioning (avoids screen edges)
 * - Dark background with border
 */
public class Tooltip {

    private List<String> lines;
    private int x, y;
    private int width, height;
    private int padding = 4;
    private int lineHeight = 10;
    private float textSize = 6f;

    public Tooltip() {
        this.lines = new ArrayList<>();
    }

    /**
     * Set the tooltip lines (content)
     */
    public void setLines(List<String> lines) {
        this.lines = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
        calculateDimensions();
    }

    /**
     * Set tooltip position (top-left corner)
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Calculate tooltip dimensions based on content
     */
    private void calculateDimensions() {
        if (lines.isEmpty()) {
            width = 0;
            height = 0;
            return;
        }

        // Use the same formula as GuiRenderer.renderText()
        // textSize = 6f, so scale = 6 / 8.0 = 0.75, but clamped to min 1.0
        float scale = textSize / 8.0f;
        if (scale < 1.0f) {
            scale = 1.0f;
        }

        // Calculate width based on longest line
        // Formula from GuiRenderer: (6 * N + 2) * scale
        // where N is text length, 6 is spacing (5px glyph + 1px gap), +2 is margin
        int maxWidth = 0;
        for (String line : lines) {
            int lineWidth = (int) ((6 * line.length() + 2) * scale);
            if (lineWidth > maxWidth) {
                maxWidth = lineWidth;
            }
        }

        // Add padding on both sides
        width = maxWidth + padding * 2;

        // Calculate height
        // Each character is 8 * scale tall, one line per lineHeight
        height = (lines.size() * lineHeight) + padding * 2;
    }

    /**
     * Adjust position to avoid screen edges
     * 
     * @param mouseX       Current mouse X
     * @param mouseY       Current mouse Y
     * @param screenWidth  Window width
     * @param screenHeight Window height
     */
    public void adjustPosition(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        // Start at mouse position with small offset
        int offsetX = 12;
        int offsetY = 12;

        x = mouseX + offsetX;
        y = mouseY + offsetY;

        // Check right edge
        if (x + width > screenWidth) {
            x = mouseX - width - offsetX;
        }

        // Check bottom edge
        if (y + height > screenHeight) {
            y = mouseY - height - offsetY;
        }

        // Ensure minimum position (don't go off left/top edges)
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
    }

    /**
     * Render the tooltip
     */
    public void render(GuiRenderer renderer) {
        if (lines.isEmpty()) {
            return;
        }

        // Render background (dark with slight transparency)
        renderer.renderRect(x, y, width, height, 0.1f, 0.1f, 0.15f, 0.95f);

        // Render border (lighter color)
        int borderThickness = 1;
        renderer.renderRect(x, y, width, borderThickness, 0.4f, 0.4f, 0.6f, 1.0f); // Top
        renderer.renderRect(x, y, borderThickness, height, 0.4f, 0.4f, 0.6f, 1.0f); // Left
        renderer.renderRect(x + width - borderThickness, y, borderThickness, height, 0.6f, 0.6f, 0.8f, 1.0f); // Right
        renderer.renderRect(x, y + height - borderThickness, width, borderThickness, 0.6f, 0.6f, 0.8f, 1.0f); // Bottom

        // Render text lines
        int currentY = y + padding;
        for (String line : lines) {
            renderer.renderText(line, x + padding, currentY, textSize, 1.0f, 1.0f, 1.0f, 1.0f);
            currentY += lineHeight;
        }
    }

    /**
     * Check if tooltip has content
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Clear tooltip content
     */
    public void clear() {
        lines.clear();
        width = 0;
        height = 0;
    }

    // Getters
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
