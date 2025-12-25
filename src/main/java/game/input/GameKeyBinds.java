package game.input;

import engine.input.KeyBind;
import engine.input.KeyBindings;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Game-specific keybind definitions.
 * 
 * This class registers all keybinds used by the game and provides
 * static references for easy access throughout the codebase.
 * 
 * Usage:
 * if (GameKeyBinds.FORWARD.isDown()) { ... }
 * if (GameKeyBinds.INVENTORY.isPressed()) { ... }
 */
public class GameKeyBinds {

    // ==================== Movement ====================
    public static KeyBind FORWARD;
    public static KeyBind BACK;
    public static KeyBind LEFT;
    public static KeyBind RIGHT;
    public static KeyBind JUMP;
    public static KeyBind SPRINT;

    // ==================== Gameplay ====================
    public static KeyBind INVENTORY;
    public static KeyBind DROP;
    public static KeyBind FLY_TOGGLE;
    public static KeyBind CAMERA_MODE;

    // ==================== Hotbar ====================
    public static KeyBind HOTBAR_1;
    public static KeyBind HOTBAR_2;
    public static KeyBind HOTBAR_3;
    public static KeyBind HOTBAR_4;
    public static KeyBind HOTBAR_5;
    public static KeyBind HOTBAR_6;
    public static KeyBind HOTBAR_7;
    public static KeyBind HOTBAR_8;
    public static KeyBind HOTBAR_9;

    // ==================== UI ====================
    // ==================== UI ====================
    public static KeyBind CHAT;
    public static KeyBind DEBUG_INFO;
    public static KeyBind MENU;

    // Array for easy hotbar access
    public static KeyBind[] HOTBAR_SLOTS;

    /**
     * Register all game keybinds.
     * Must be called during game initialization, before the main loop.
     */
    public static void register() {
        KeyBindings kb = KeyBindings.getInstance();

        // Movement
        FORWARD = kb.register("forward", "Move Forward", "Movement", GLFW_KEY_W);
        BACK = kb.register("back", "Move Backward", "Movement", GLFW_KEY_S);
        LEFT = kb.register("left", "Strafe Left", "Movement", GLFW_KEY_A);
        RIGHT = kb.register("right", "Strafe Right", "Movement", GLFW_KEY_D);
        JUMP = kb.register("jump", "Jump", "Movement", GLFW_KEY_SPACE);
        SPRINT = kb.register("sprint", "Sprint", "Movement", GLFW_KEY_LEFT_SHIFT);

        // Gameplay
        INVENTORY = kb.register("inventory", "Open Inventory", "Gameplay", GLFW_KEY_E);
        DROP = kb.register("drop", "Drop Item", "Gameplay", GLFW_KEY_Q);
        FLY_TOGGLE = kb.register("fly_toggle", "Toggle Fly Mode", "Gameplay", GLFW_KEY_F);
        CAMERA_MODE = kb.register("camera_mode", "Toggle Camera", "Gameplay", GLFW_KEY_F5);

        // Hotbar
        HOTBAR_1 = kb.register("hotbar_1", "Hotbar Slot 1", "Hotbar", GLFW_KEY_1);
        HOTBAR_2 = kb.register("hotbar_2", "Hotbar Slot 2", "Hotbar", GLFW_KEY_2);
        HOTBAR_3 = kb.register("hotbar_3", "Hotbar Slot 3", "Hotbar", GLFW_KEY_3);
        HOTBAR_4 = kb.register("hotbar_4", "Hotbar Slot 4", "Hotbar", GLFW_KEY_4);
        HOTBAR_5 = kb.register("hotbar_5", "Hotbar Slot 5", "Hotbar", GLFW_KEY_5);
        HOTBAR_6 = kb.register("hotbar_6", "Hotbar Slot 6", "Hotbar", GLFW_KEY_6);
        HOTBAR_7 = kb.register("hotbar_7", "Hotbar Slot 7", "Hotbar", GLFW_KEY_7);
        HOTBAR_8 = kb.register("hotbar_8", "Hotbar Slot 8", "Hotbar", GLFW_KEY_8);
        HOTBAR_9 = kb.register("hotbar_9", "Hotbar Slot 9", "Hotbar", GLFW_KEY_9);

        // UI
        // UI
        CHAT = kb.register("chat", "Open Chat", "UI", GLFW_KEY_T);
        DEBUG_INFO = kb.register("debug_info", "Toggle Debug Info", "UI", GLFW_KEY_F3);
        MENU = kb.register("menu", "Open Menu / Back", "UI", GLFW_KEY_ESCAPE);

        // Build hotbar array for easy iteration
        HOTBAR_SLOTS = new KeyBind[] {
                HOTBAR_1, HOTBAR_2, HOTBAR_3, HOTBAR_4, HOTBAR_5,
                HOTBAR_6, HOTBAR_7, HOTBAR_8, HOTBAR_9
        };

        System.out.println("[GameKeyBinds] Registered " + kb.getAllBindings().size() + " keybinds");
    }

    /**
     * Check which hotbar slot key is pressed (if any).
     * 
     * @return Slot index (0-8), or -1 if no hotbar key pressed
     */
    public static int getPressedHotbarSlot() {
        for (int i = 0; i < HOTBAR_SLOTS.length; i++) {
            if (HOTBAR_SLOTS[i].isDown()) {
                return i;
            }
        }
        return -1;
    }
}
