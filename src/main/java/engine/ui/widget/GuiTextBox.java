package engine.ui.widget;

import engine.ui.GuiComponent;
import engine.ui.GuiRenderer;
import engine.window.InputManager;

import static org.lwjgl.glfw.GLFW.*;

public class GuiTextBox extends GuiComponent {

    private StringBuilder text;
    private StringBuilder placeholder;
    private boolean isFocused = false;
    private int maxLength = 32;
    private boolean isNumericOnly = false;

    // Blinking cursor
    private long lastBlinkTime = 0;
    private boolean showCursor = true;

    private float textScale = 2.0f; // Default scale

    public GuiTextBox(int x, int y, int width, int height, String initialText) {
        super(x, y, width, height);
        this.text = new StringBuilder(initialText);
        this.placeholder = new StringBuilder();
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder.setLength(0);
        this.placeholder.append(placeholder);
    }

    public void setNumericOnly(boolean numeric) {
        this.isNumericOnly = numeric;
    }

    public void setTextScale(float scale) {
        this.textScale = scale;
    }

    public String getText() {
        return text.toString();
    }

    public void setText(String text) {
        this.text.setLength(0);
        this.text.append(text);
    }

    public void input(InputManager input, double mx, double my, boolean mousePressed) {
        if (!visible)
            return;

        // Focus handling
        if (mousePressed) {
            isFocused = isMouseOver((int) mx, (int) my);
        }

        if (!isFocused)
            return;

        // Character Input
        Character c = input.pollChar();
        while (c != null) {
            if (text.length() < maxLength) {
                if (isNumericOnly) {
                    if (Character.isDigit(c) || (c == '-' && text.length() == 0)) {
                        text.append(c);
                    }
                } else {
                    // Allow alphanumeric + space + simple punctuation
                    if (Character.isLetterOrDigit(c) || " _-".indexOf(c) != -1) {
                        text.append(c);
                    }
                }
            }
            c = input.pollChar();
        }

        // Key Input (Backspace)
        // Note: polling keys like this in update loop is tricky if keysJustPressed is
        // cleared.
        // We should instead pass key events or rely on repeated key presses if input
        // manager supports it.
        // InputManager 'isKeyPressed' only returns true for the frame it was pressed.
        // For backspace hold, we'd need repeats. For now, single press backspace.
        if (input.isKeyPressed(GLFW_KEY_BACKSPACE)) {
            if (text.length() > 0) {
                text.setLength(text.length() - 1);
            }
        }
    }

    public void update(float deltaTime) {
        // Cursor Blink
        long now = System.currentTimeMillis();
        if (now - lastBlinkTime > 500) {
            showCursor = !showCursor;
            lastBlinkTime = now;
        }
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!visible)
            return;

        // Background
        float r = 0.1f, g = 0.1f, b = 0.1f;
        if (isFocused) {
            r = 0.2f;
            g = 0.2f;
            b = 0.2f;
        }
        renderer.renderRect(x, y, width, height, r, g, b, 1.0f);

        // Border
        renderer.renderRect(x, y, width, 2, 0.5f, 0.5f, 0.5f, 1f); // Top
        renderer.renderRect(x, y + height - 2, width, 2, 0.5f, 0.5f, 0.5f, 1f); // Bottom
        renderer.renderRect(x, y, 2, height, 0.5f, 0.5f, 0.5f, 1f); // Left
        renderer.renderRect(x + width - 2, y, 2, height, 0.5f, 0.5f, 0.5f, 1f); // Right

        // Text
        String display = text.length() == 0 && !isFocused ? placeholder.toString() : text.toString();
        float textR = 1, textG = 1, textB = 1;

        if (text.length() == 0 && !isFocused) {
            textR = 0.5f;
            textG = 0.5f;
            textB = 0.5f;
        }

        float charHeight = 8 * textScale;
        float textY = y + (height - charHeight) / 2;

        renderer.renderText(display, x + 10, textY, textScale * 8.0f, textR, textG, textB, 1);

        // Cursor
        if (isFocused && showCursor) {
            float spacing = 6 * textScale;
            float cursorX = x + 10 + (display.length() * spacing);
            // Draw cursor slightly taller than text
            renderer.renderRect(cursorX, textY, 2 * textScale, charHeight, 1, 1, 1, 1);
        }
    }

    public boolean isFocused() {
        return isFocused;
    }
}
