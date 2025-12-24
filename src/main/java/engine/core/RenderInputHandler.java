package engine.core;

import engine.rendering.RenderSettings;
import engine.world.World;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Handles keyboard input for render settings and debug toggles.
 * 
 * Key bindings:
 * - F3: Toggle debug screen
 * - F4: Toggle frustum culling
 * - F5: Toggle fog
 * - F6: Toggle chunk borders
 * - F7: Toggle wireframe
 * - +/=: Increase view distance
 * - -: Decrease view distance
 * - 1-4: Preset quality levels
 */
public class RenderInputHandler {

    private final RenderSettings settings;
    private final World world;

    // Key state tracking to detect press (not hold)
    private boolean[] keyWasPressed = new boolean[512];

    public RenderInputHandler(RenderSettings settings, World world) {
        this.settings = settings;
        this.world = world;
    }

    /**
     * Process input. Call every frame.
     * 
     * @param window GLFW window handle
     */
    public void processInput(long window) {
        // F3 - Toggle debug screen
        if (isKeyJustPressed(window, GLFW_KEY_F3)) {
            settings.toggleDebugInfo();
            System.out.println("Debug screen: " + (settings.isShowDebugInfo() ? "ON" : "OFF"));
        }

        // F4 - Toggle frustum culling
        if (isKeyJustPressed(window, GLFW_KEY_F4)) {
            settings.toggleFrustumCulling();
            System.out.println("Frustum culling: " + (settings.isFrustumCullingEnabled() ? "ON" : "OFF"));
        }

        // F5 - Toggle fog
        if (isKeyJustPressed(window, GLFW_KEY_F5)) {
            settings.toggleFog();
            System.out.println("Fog: " + (settings.isFogEnabled() ? "ON" : "OFF"));
        }

        // F6 - Toggle chunk borders
        if (isKeyJustPressed(window, GLFW_KEY_F6)) {
            settings.toggleChunkBorders();
            System.out.println("Chunk borders: " + (settings.isShowChunkBorders() ? "ON" : "OFF"));
        }

        // F7 - Toggle wireframe (moved from F3)
        if (isKeyJustPressed(window, GLFW_KEY_F7)) {
            settings.toggleWireframe();
            System.out.println("Wireframe: " + (settings.isWireframeMode() ? "ON" : "OFF"));
        }

        // + / = - Increase view distance
        if (isKeyJustPressed(window, GLFW_KEY_EQUAL) || isKeyJustPressed(window, GLFW_KEY_KP_ADD)) {
            settings.increaseViewDistance();
            world.setViewDistance(settings.getViewDistance());
            System.out.println("View distance: " + settings.getViewDistance());
        }

        // - - Decrease view distance
        if (isKeyJustPressed(window, GLFW_KEY_MINUS) || isKeyJustPressed(window, GLFW_KEY_KP_SUBTRACT)) {
            settings.decreaseViewDistance();
            world.setViewDistance(settings.getViewDistance());
            System.out.println("View distance: " + settings.getViewDistance());
        }

        // 1 - Fast preset
        if (isKeyJustPressed(window, GLFW_KEY_1) && isCtrlPressed(window)) {
            settings.applyFastPreset();
            world.setViewDistance(settings.getViewDistance());
            System.out.println("Applied FAST preset (view: " + settings.getViewDistance() + ")");
        }

        // 2 - Balanced preset
        if (isKeyJustPressed(window, GLFW_KEY_2) && isCtrlPressed(window)) {
            settings.applyBalancedPreset();
            world.setViewDistance(settings.getViewDistance());
            System.out.println("Applied BALANCED preset (view: " + settings.getViewDistance() + ")");
        }

        // 3 - Fancy preset
        if (isKeyJustPressed(window, GLFW_KEY_3) && isCtrlPressed(window)) {
            settings.applyFancyPreset();
            world.setViewDistance(settings.getViewDistance());
            System.out.println("Applied FANCY preset (view: " + settings.getViewDistance() + ")");
        }

        // 4 - Extreme preset
        if (isKeyJustPressed(window, GLFW_KEY_4) && isCtrlPressed(window)) {
            settings.applyExtremePreset();
            world.setViewDistance(settings.getViewDistance());
            System.out.println("Applied EXTREME preset (view: " + settings.getViewDistance() + ")");
        }
    }

    /**
     * Check if a key was just pressed (not held).
     */
    private boolean isKeyJustPressed(long window, int key) {
        boolean pressed = glfwGetKey(window, key) == GLFW_PRESS;
        boolean wasPressed = keyWasPressed[key];
        keyWasPressed[key] = pressed;
        return pressed && !wasPressed;
    }

    /**
     * Check if Ctrl is pressed.
     */
    private boolean isCtrlPressed(long window) {
        return glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS ||
                glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS;
    }
}
