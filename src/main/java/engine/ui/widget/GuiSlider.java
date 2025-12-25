package engine.ui.widget;

import engine.ui.GuiComponent;
import engine.ui.GuiRenderer;
import engine.window.InputManager;

import java.util.function.Consumer;

public class GuiSlider extends GuiComponent {

    private final float min;
    private final float max;
    private float value;
    private String label;
    private boolean isDragging = false;
    private Consumer<Float> onValueChange;

    public GuiSlider(int x, int y, int width, int height, float min, float max, float initialValue, String label) {
        super(x, y, width, height);
        this.min = min;
        this.max = max;
        this.value = Math.max(min, Math.min(max, initialValue));
        this.label = label;
    }

    public void setOnValueChange(Consumer<Float> onValueChange) {
        this.onValueChange = onValueChange;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = Math.max(min, Math.min(max, value));
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void update(InputManager input) {
        if (!visible)
            return;

        double mx = input.getMouseX(); // Assuming these are scaled coordinates or mishandled in game loop
        // If the game loop handles scaling before passing mx/my, we are good.
        // But GuiComponent doesn't know about scale. ExampleGame passes input directly.
        // We will assume simpler interaction for now: update() needs scaled mouse if
        // the game uses scaling.
    }

    // Better interaction method that takes mouse coordinates directly
    public void input(double mx, double my, boolean mouseDown) {
        if (!visible)
            return;

        if (mouseDown) {
            if (isMouseOver((int) mx, (int) my)) {
                isDragging = true;
            }
        } else {
            isDragging = false;
        }

        if (isDragging) {
            double relativeX = mx - x;
            double percentage = relativeX / (double) width;
            percentage = Math.max(0, Math.min(1, percentage));

            float newValue = min + (float) (percentage * (max - min));

            // Snap to integer if range is large? No, keep it float. User can cast.
            if (newValue != value) {
                value = newValue;
                if (onValueChange != null) {
                    onValueChange.accept(value);
                }
            }
        }
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!visible)
            return;

        // Background / Track
        renderer.renderRect(x, y, width, height, 0.2f, 0.2f, 0.2f, 1.0f);

        // Handle / Fill
        float percentage = (value - min) / (max - min);
        float fillWidth = width * percentage;

        // Hover state (visual only) - we'd need mouse pos here to do it right, but for
        // now simple
        float r = 0.4f, g = 0.8f, b = 0.4f;
        if (isDragging) {
            r = 0.5f;
            g = 1.0f;
            b = 0.5f;
        }

        renderer.renderRect(x, y, fillWidth, height, r, g, b, 1.0f);

        // Label
        if (label != null) {
            String text = label; // Simplified, user might want value in label
            float textScale = 2.0f;
            // Center text
            // We unfortunately don't have text width calculation here easily without access
            // to font logic
            // Assuming hardcoded scale roughly.
            renderer.renderText(text, x + 5, y + 8, textScale, 1, 1, 1, 1);
        }
    }
}
