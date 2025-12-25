package game.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import engine.command.CommandManager;
import engine.command.CommandSender;
import engine.ui.GuiRenderer;
import engine.window.InputManager;
import static org.lwjgl.glfw.GLFW.*;

public class ChatGui {

    private static final int MAX_HISTORY = 100;

    // Message container with timestamp for fading
    private static class ChatMessage {
        String text;
        long time;

        public ChatMessage(String text) {
            this.text = text;
            this.time = System.currentTimeMillis();
        }
    }

    private static final List<ChatMessage> history = new CopyOnWriteArrayList<>();

    // Exposed statically so Player.sendMessage can reach it easily
    public static void addMessage(String msg) {
        history.add(new ChatMessage(msg));
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    private final CommandManager commandManager;
    private final CommandSender sender; // Usually the player

    private boolean isOpen = false;
    private boolean ignoreNextChar = false; // Latch for 'T' key
    private boolean shouldClearInput = false; // Latch for clearing buffer on open
    private StringBuilder inputBuffer = new StringBuilder();

    // Command History
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1; // -1 means typing new command

    // Layout constants
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 20;
    private static final long FADE_DELAY = 8000; // 8 seconds fully visible
    private static final long FADE_TIME = 2000; // 2 seconds fade out

    public ChatGui(CommandManager commandManager, CommandSender sender) {
        this.commandManager = commandManager;
        this.sender = sender;
    }

    public void setOpen(boolean open) {
        this.isOpen = open;
        if (open) {
            inputBuffer.setLength(0);
            ignoreNextChar = true; // Consume the keypress that opened the chat
            historyIndex = -1;
            shouldClearInput = true; // Signal handleInput to clear buffer
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void handleInput(InputManager input) {
        if (!isOpen)
            return;

        // Clear buffer if just opened (prevents WASD leak)
        if (shouldClearInput) {
            input.clearCharBuffer();
            shouldClearInput = false;
        }

        // Character Input
        Character C = input.pollChar();
        while (C != null) {
            char c = C;
            // Prevent 't' from opening key from leaking into chat
            if (ignoreNextChar) {
                // Only ignore if it matches the opening key (often 't' or '/')
                // Simple heuristic: ignore first char if processed in same frame
            }
            // Logic: we set ignoreNextChar = true on setOpen.
            // We should consume one valid char if it arrives instantly?
            // Actually, pollChar returns chars from the queue. 'T' press generates 't'.
            // Checks:
            if (!ignoreNextChar || (c != 't' && c != 'T')) {
                if (c >= 32 && c != 127) {
                    inputBuffer.append(c);
                }
            }
            ignoreNextChar = false;
            C = input.pollChar();
        }

        // If we processed input, disable latch
        ignoreNextChar = false;

        // History Navigation
        if (input.isKeyPressed(GLFW_KEY_UP)) {
            if (historyIndex < commandHistory.size() - 1) {
                historyIndex++;
                updateInputFromHistory();
            }
        }
        if (input.isKeyPressed(GLFW_KEY_DOWN)) {
            if (historyIndex > -1) {
                historyIndex--;
                updateInputFromHistory();
            }
        }

        // Special Keys Handling
        if (input.isKeyPressed(GLFW_KEY_BACKSPACE)) {
            if (inputBuffer.length() > 0) {
                inputBuffer.setLength(inputBuffer.length() - 1);
            }
        }

        if (input.isKeyPressed(GLFW_KEY_ENTER)) {
            String text = inputBuffer.toString().trim();
            if (!text.isEmpty()) {
                // Determine if command or chat
                if (text.startsWith("/")) {
                    commandHistory.add(0, text); // Add to history (newest first)
                    boolean found = commandManager.dispatch(sender, text);
                    if (!found) {
                        // Optional: Feedback if command not found handled by Manager?
                        // Usually Manager calls sender.sendMessage("Unknown command")
                    }
                } else {
                    addMessage("<" + sender.getName() + "> " + text);
                }
            }
            setOpen(false);
        }

        if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            setOpen(false);
        }
    }

    private void updateInputFromHistory() {
        inputBuffer.setLength(0);
        if (historyIndex >= 0 && historyIndex < commandHistory.size()) {
            inputBuffer.append(commandHistory.get(historyIndex));
        } else {
            // -1 case, empty
        }
    }

    public void render(GuiRenderer renderer, int screenWidth, int screenHeight) {
        float scale = renderer.getGuiScale();
        float logicalW = screenWidth / scale;
        float logicalH = screenHeight / scale;

        int drawY = (int) logicalH - 40; // Start above input box
        if (isOpen)
            drawY -= 20; // Move up for input box

        long currentTime = System.currentTimeMillis();

        // Render History
        int count = 0;
        // We iterate backwards to draw bottom-up
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage msg = history.get(i);

            float alpha = 1.0f;
            if (!isOpen) {
                long age = currentTime - msg.time;
                if (age > FADE_DELAY + FADE_TIME) {
                    continue; // Too old, don't draw
                } else if (age > FADE_DELAY) {
                    alpha = 1.0f - (float) (age - FADE_DELAY) / FADE_TIME;
                }
            }

            // Draw background for readability?
            if (alpha > 0.1f) {
                float textW = msg.text.length() * 8.0f; // Approx width
                renderer.renderRect(PADDING - 2, drawY - 2, textW + 4, LINE_HEIGHT, 0, 0, 0, 0.4f * alpha);
                renderer.renderText(msg.text, PADDING, drawY, 2.0f, 1, 1, 1, alpha);
            }

            drawY -= LINE_HEIGHT;
            count++;
            if (count >= 10 && !isOpen)
                break;
            if (count >= 20 && isOpen)
                break;
        }

        // Render Input Box if Open
        if (isOpen) {
            int inputY = (int) logicalH - 35;
            // Background strip
            renderer.renderRect(0, inputY - 5, (int) logicalW, 35, 0, 0, 0, 0.7f);

            // Cursor
            String renderText = inputBuffer.toString();
            if (System.currentTimeMillis() % 1000 > 500) {
                renderText += "_";
            }

            // Allow scrolling prompt if too long? For now just clip
            renderer.renderText(renderText, PADDING, inputY, 2.0f, 1, 1, 1, 1);
        }
    }
}
