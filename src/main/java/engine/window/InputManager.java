package engine.window;

import static org.lwjgl.glfw.GLFW.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Input management - keyboard, mouse, and callbacks
 */
public class InputManager {

    private final Window window;

    // Keyboard state
    private final Map<Integer, Boolean> keys = new HashMap<>();
    private final Set<Integer> keysJustPressed = new HashSet<>();
    private final Set<Integer> keysToConsume = new HashSet<>();

    // Mouse state - using latch system like original MinecraftOneFile
    private final Map<Integer, Boolean> mouseButtons = new HashMap<>();
    private final Map<Integer, Boolean> mouseLatch = new HashMap<>();

    private double mouseX, mouseY;
    private double lastMouseX, lastMouseY;
    private double mouseDeltaX, mouseDeltaY;
    private boolean firstMouse = true;

    // Mouse scroll
    private double scrollX, scrollY;

    public InputManager(Window window) {
        this.window = window;
    }

    /**
     * Initialize input callbacks
     */
    public void init() {
        long handle = window.getHandle();

        // Keyboard callback
        glfwSetKeyCallback(handle, (w, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_UNKNOWN)
                return;

            if (action == GLFW_PRESS) {
                keys.put(key, true);
                keysJustPressed.add(key);
            } else if (action == GLFW_RELEASE) {
                keys.put(key, false);
            }
        });

        // Mouse button callback
        glfwSetMouseButtonCallback(handle, (w, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                mouseButtons.put(button, true);
            } else if (action == GLFW_RELEASE) {
                mouseButtons.put(button, false);
                // Reset latch on release so next press triggers
                mouseLatch.put(button, false);
            }
        });

        // Mouse position callback
        glfwSetCursorPosCallback(handle, (w, x, y) -> {
            if (firstMouse) {
                lastMouseX = x;
                lastMouseY = y;
                firstMouse = false;
            }

            mouseX = x;
            mouseY = y;
        });

        // Mouse scroll callback
        glfwSetScrollCallback(handle, (w, xoffset, yoffset) -> {
            scrollX = xoffset;
            scrollY = yoffset;
        });

        // Set cursor to center
        glfwSetCursorPos(handle, window.getWidth() / 2.0, window.getHeight() / 2.0);
    }

    /**
     * Backwards-compat alias per il movimento del mouse (X)
     */
    public double getMouseDX() {
        return getMouseDeltaX();
    }

    /**
     * Backwards-compat alias per il movimento del mouse (Y)
     */
    public double getMouseDY() {
        return getMouseDeltaY();
    }

    public void update() {
        // Calcola il delta del mouse tra questo frame e il precedente
        mouseDeltaX = mouseX - lastMouseX;
        mouseDeltaY = lastMouseY - mouseY; // spesso invertito sull'asse Y

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // Se gestisci tasti "just pressed" o scroll, puoi azzerarli/aggiornarli qui
    }

    /**
     * c
     * // Clear scroll at end of frame
     * scrollX = 0;
     * scrollY = 0;
     * }
     * 
     * /**
     * Call at END of frame to clear single-press states
     */
    public void endFrame() {
        // Clear single-press states
        keysJustPressed.clear();

        // Clear scroll values so they don't accumulate
        clearScroll();
    }

    // Keyboard methods

    /**
     * Check if key is currently held down
     */
    public boolean isKeyDown(int key) {
        return keys.getOrDefault(key, false);
    }

    /**
     * Check if key was pressed this frame (only true for one frame)
     */
    public boolean isKeyPressed(int key) {
        return keysJustPressed.contains(key);
    }

    // Mouse methods

    /**
     * Check if mouse button is currently held down
     */
    public boolean isMouseButtonDown(int button) {
        return mouseButtons.getOrDefault(button, false);
    }

    /**
     * Check if mouse button was pressed this frame (latch system)
     * Returns true only ONCE per click, not while held
     */
    public boolean isMouseButtonPressed(int button) {
        boolean isDown = mouseButtons.getOrDefault(button, false);
        boolean wasLatched = mouseLatch.getOrDefault(button, false);

        if (isDown && !wasLatched) {
            // First frame of press - latch it and return true
            mouseLatch.put(button, true);
            return true;
        }

        // Either not pressed, or already latched
        return false;
    }

    /**
     * Get mouse X position
     */
    public double getMouseX() {
        return mouseX;
    }

    /**
     * Get mouse Y position
     */
    public double getMouseY() {
        return mouseY;
    }

    /**
     * Get mouse X movement since last frame
     */
    public double getMouseDeltaX() {
        return mouseDeltaX;
    }

    /**
     * Get mouse Y movement since last frame
     */
    public double getMouseDeltaY() {
        return mouseDeltaY;
    }

    /**
     * Get mouse scroll X offset
     */
    public double getScrollX() {
        return scrollX;
    }

    /**
     * Get mouse scroll Y offset
     */
    public double getScrollY() {
        return scrollY;
    }

    /**
     * Clear scroll values (call at end of frame)
     */
    public void clearScroll() {
        scrollX = 0;
        scrollY = 0;
    }

    /**
     * Reset mouse delta (useful after camera jumps)
     */
    public void resetMouseDelta() {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }
}