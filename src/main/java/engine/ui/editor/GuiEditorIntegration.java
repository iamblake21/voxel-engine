package engine.ui.editor;

import engine.ui.GuiRenderer;
import engine.ui.definition.GuiDefinition;
import engine.ui.definition.Guis;
import engine.window.InputManager;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Example integration of the GUI Editor into a game.
 * 
 * This class demonstrates how to add the editor to your game loop.
 * Copy and adapt this code to your specific architecture.
 * 
 * Usage:
 * 
 * // In your Game class constructor:
 * editorIntegration = new GuiEditorIntegration(windowWidth, windowHeight,
 * guiRenderer);
 * 
 * // In your update loop:
 * editorIntegration.update(input, mouseX, mouseY);
 * 
 * // In your render loop (after world, before final UI):
 * editorIntegration.render();
 * 
 * // In your cleanup:
 * editorIntegration.cleanup();
 */
public class GuiEditorIntegration {

    private final GuiEditorOverlay editor;
    private final GuiRenderer guiRenderer;

    // Track F7 key state for toggle
    private boolean f7WasDown = false;

    // Callback for when editor is activated
    private Runnable onActivate;

    public GuiEditorIntegration(int windowWidth, int windowHeight, GuiRenderer guiRenderer) {
        this.guiRenderer = guiRenderer;
        this.editor = new GuiEditorOverlay(windowWidth, windowHeight);
        this.editor.setGuiScale(guiRenderer.getGuiScale());

        // Set output path for saved files
        this.editor.setOutputPath("src/main/resources/gui");

        System.out.println("[GuiEditorIntegration] Ready. Press F7 to toggle editor.");
    }

    public void setOnActivate(Runnable onActivate) {
        this.onActivate = onActivate;
    }

    /**
     * Update editor state - call every frame
     */
    public void update(InputManager input, double mouseX, double mouseY) {
        // Toggle with F7
        boolean f7Down = input.isKeyDown(GLFW_KEY_F7);
        if (f7Down && !f7WasDown) {
            System.out.println("[GuiEditorIntegration] F7 Pressed. Toggling...");
            editor.toggle();
            System.out.println("[GuiEditorIntegration] Active: " + editor.isActive());

            // Notify callback if needed or just let the main game handle state sync
            if (onActivate != null && editor.isActive()) {
                onActivate.run();
            }
        }
        f7WasDown = f7Down;

        // Handle editor input if active
        if (editor.isActive()) {
            editor.handleInput(input, mouseX, mouseY);
        }
    }

    /**
     * Render editor - call during 2D rendering phase
     */
    public void render() {
        if (editor.isActive()) {
            guiRenderer.begin();
            editor.render(guiRenderer);
            guiRenderer.end();
        }
    }

    /**
     * Check if editor is currently active
     * Use this to block other input handlers while editing
     */
    public boolean isEditorActive() {
        return editor.isActive();
    }

    /**
     * Load a specific GUI for editing
     */
    public void editGui(String guiName) {
        if (Guis.exists(guiName)) {
            editor.loadDefinition(Guis.getOrThrow(guiName));
        } else {
            System.err.println("[GuiEditorIntegration] GUI not found: " + guiName);
        }
    }

    /**
     * Create a new GUI for editing
     */
    public void newGui(String name, int width, int height) {
        editor.newDefinition(name, width, height);
    }

    /**
     * Load a texture for the current GUI being edited
     */
    public void loadTexture(String texturePath) {
        editor.loadTexture(texturePath);
    }

    /**
     * Update window size (call on resize)
     */
    public void onResize(int width, int height) {
        editor.setSize(width, height);
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        editor.cleanup();
    }

    /**
     * Get the editor overlay (for advanced usage)
     */
    public GuiEditorOverlay getEditor() {
        return editor;
    }
}
