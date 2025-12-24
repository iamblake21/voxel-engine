package engine.input;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Represents a single configurable keybind.
 * 
 * Each keybind has:
 * - A unique ID (e.g., "forward", "jump")
 * - A display name for the UI (e.g., "Move Forward", "Jump")
 * - A category for grouping in the UI (e.g., "movement", "gameplay")
 * - A current key code (GLFW key constant)
 * - A default key code for reset functionality
 */
public class KeyBind {

    private final String id;
    private final String displayName;
    private final String category;
    private int keyCode;
    private final int defaultKey;

    public KeyBind(String id, String displayName, String category, int defaultKey) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.keyCode = defaultKey;
        this.defaultKey = defaultKey;
    }

    // ==================== Getters ====================

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getDefaultKey() {
        return defaultKey;
    }

    // ==================== Setters ====================

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public void resetToDefault() {
        this.keyCode = defaultKey;
    }

    // ==================== Query Methods ====================

    /**
     * Check if this keybind's key is currently held down.
     */
    public boolean isDown() {
        return KeyBindings.getInstance().getInput().isKeyDown(keyCode);
    }

    /**
     * Check if this keybind's key was just pressed this frame.
     */
    public boolean isPressed() {
        return KeyBindings.getInstance().getInput().isKeyPressed(keyCode);
    }

    // ==================== Display Helpers ====================

    /**
     * Get a human-readable name for the current key.
     */
    public String getKeyName() {
        return getKeyName(keyCode);
    }

    /**
     * Get a human-readable name for any GLFW key code.
     */
    public static String getKeyName(int key) {
        // Try GLFW's built-in name first
        String name = glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) {
            return name.toUpperCase();
        }

        // Handle special keys that don't have printable names
        return switch (key) {
            case GLFW_KEY_SPACE -> "SPACE";
            case GLFW_KEY_LEFT_SHIFT -> "L SHIFT";
            case GLFW_KEY_RIGHT_SHIFT -> "R SHIFT";
            case GLFW_KEY_LEFT_CONTROL -> "L CTRL";
            case GLFW_KEY_RIGHT_CONTROL -> "R CTRL";
            case GLFW_KEY_LEFT_ALT -> "L ALT";
            case GLFW_KEY_RIGHT_ALT -> "R ALT";
            case GLFW_KEY_TAB -> "TAB";
            case GLFW_KEY_ENTER -> "ENTER";
            case GLFW_KEY_ESCAPE -> "ESC";
            case GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW_KEY_DELETE -> "DELETE";
            case GLFW_KEY_INSERT -> "INSERT";
            case GLFW_KEY_HOME -> "HOME";
            case GLFW_KEY_END -> "END";
            case GLFW_KEY_PAGE_UP -> "PAGE UP";
            case GLFW_KEY_PAGE_DOWN -> "PAGE DOWN";
            case GLFW_KEY_UP -> "UP";
            case GLFW_KEY_DOWN -> "DOWN";
            case GLFW_KEY_LEFT -> "LEFT";
            case GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW_KEY_F1 -> "F1";
            case GLFW_KEY_F2 -> "F2";
            case GLFW_KEY_F3 -> "F3";
            case GLFW_KEY_F4 -> "F4";
            case GLFW_KEY_F5 -> "F5";
            case GLFW_KEY_F6 -> "F6";
            case GLFW_KEY_F7 -> "F7";
            case GLFW_KEY_F8 -> "F8";
            case GLFW_KEY_F9 -> "F9";
            case GLFW_KEY_F10 -> "F10";
            case GLFW_KEY_F11 -> "F11";
            case GLFW_KEY_F12 -> "F12";
            case GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW_KEY_NUM_LOCK -> "NUM LOCK";
            case GLFW_KEY_SCROLL_LOCK -> "SCROLL LOCK";
            case GLFW_KEY_PRINT_SCREEN -> "PRINT";
            case GLFW_KEY_PAUSE -> "PAUSE";
            default -> "KEY " + key;
        };
    }

    @Override
    public String toString() {
        return "KeyBind{" + id + "=" + getKeyName() + "}";
    }
}
