package engine.input;

import engine.window.InputManager;

import java.io.*;
import java.util.*;

/**
 * Central registry for all keybinds.
 * 
 * This singleton manages all configurable keybinds in the game.
 * It provides:
 * - Registration of new keybinds
 * - Query methods (isDown, isPressed) by action name
 * - Rebinding functionality
 * - JSON persistence (save/load)
 * - Conflict detection
 */
public class KeyBindings {

    private static KeyBindings INSTANCE;

    private final Map<String, KeyBind> bindings = new LinkedHashMap<>();
    private final Map<String, List<KeyBind>> categories = new LinkedHashMap<>();
    private InputManager input;

    private KeyBindings() {
    }

    public static KeyBindings getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new KeyBindings();
        }
        return INSTANCE;
    }

    // ==================== Initialization ====================

    /**
     * Set the InputManager instance (called by Engine during init).
     */
    public void setInput(InputManager input) {
        this.input = input;
    }

    public InputManager getInput() {
        return input;
    }

    // ==================== Registration ====================

    /**
     * Register a new keybind.
     * 
     * @param id          Unique identifier (e.g., "forward", "jump")
     * @param displayName Human-readable name for UI (e.g., "Move Forward")
     * @param category    Category for grouping (e.g., "movement", "gameplay")
     * @param defaultKey  Default GLFW key code
     * @return The created KeyBind for storing in GameKeyBinds
     */
    public KeyBind register(String id, String displayName, String category, int defaultKey) {
        KeyBind bind = new KeyBind(id, displayName, category, defaultKey);
        bindings.put(id, bind);

        // Add to category list
        categories.computeIfAbsent(category, k -> new ArrayList<>()).add(bind);

        return bind;
    }

    // ==================== Query Methods ====================

    /**
     * Check if a keybind is currently held down.
     */
    public boolean isDown(String action) {
        KeyBind bind = bindings.get(action);
        if (bind == null || input == null) {
            return false;
        }
        return input.isKeyDown(bind.getKeyCode());
    }

    /**
     * Check if a keybind was just pressed this frame.
     */
    public boolean isPressed(String action) {
        KeyBind bind = bindings.get(action);
        if (bind == null || input == null) {
            return false;
        }
        return input.isKeyPressed(bind.getKeyCode());
    }

    /**
     * Get a keybind by ID.
     */
    public KeyBind get(String id) {
        return bindings.get(id);
    }

    // ==================== Configuration ====================

    /**
     * Rebind an action to a new key.
     */
    public void rebind(String action, int newKey) {
        KeyBind bind = bindings.get(action);
        if (bind != null) {
            bind.setKeyCode(newKey);
        }
    }

    /**
     * Reset a single keybind to its default.
     */
    public void resetToDefault(String action) {
        KeyBind bind = bindings.get(action);
        if (bind != null) {
            bind.resetToDefault();
        }
    }

    /**
     * Reset all keybinds to their defaults.
     */
    public void resetAllDefaults() {
        for (KeyBind bind : bindings.values()) {
            bind.resetToDefault();
        }
    }

    /**
     * Find any keybind that conflicts with the given key.
     * 
     * @param key       The key code to check
     * @param excluding The action ID to exclude from conflict check (the one being
     *                  rebound)
     * @return The conflicting KeyBind, or null if no conflict
     */
    public KeyBind findConflict(int key, String excluding) {
        for (KeyBind bind : bindings.values()) {
            if (!bind.getId().equals(excluding) && bind.getKeyCode() == key) {
                return bind;
            }
        }
        return null;
    }

    // ==================== UI Support ====================

    /**
     * Get all registered keybinds.
     */
    public Collection<KeyBind> getAllBindings() {
        return Collections.unmodifiableCollection(bindings.values());
    }

    /**
     * Get all category names in registration order.
     */
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(categories.keySet());
    }

    /**
     * Get all keybinds in a category.
     */
    public List<KeyBind> getBindingsByCategory(String category) {
        List<KeyBind> list = categories.get(category);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    // ==================== Persistence ====================

    /**
     * Save keybinds to a JSON file.
     */
    public void save(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("{");
            writer.println("  \"version\": 1,");
            writer.println("  \"bindings\": {");

            Iterator<KeyBind> it = bindings.values().iterator();
            while (it.hasNext()) {
                KeyBind bind = it.next();
                writer.print("    \"" + bind.getId() + "\": " + bind.getKeyCode());
                if (it.hasNext()) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }

            writer.println("  }");
            writer.println("}");

            System.out.println("[KeyBindings] Saved " + bindings.size() + " keybinds to " + file.getName());
        } catch (IOException e) {
            System.err.println("[KeyBindings] Failed to save keybinds: " + e.getMessage());
        }
    }

    /**
     * Load keybinds from a JSON file.
     * Only updates bindings that exist in the registry.
     */
    public void load(File file) {
        if (!file.exists()) {
            System.out.println("[KeyBindings] No keybinds file found, using defaults.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            String json = sb.toString();

            // Simple JSON parsing (no external dependencies)
            // Format: { "bindings": { "forward": 87, "back": 83, ... } }
            int bindingsStart = json.indexOf("\"bindings\"");
            if (bindingsStart == -1) {
                return;
            }

            int braceStart = json.indexOf('{', bindingsStart);
            int braceEnd = json.indexOf('}', braceStart);
            if (braceStart == -1 || braceEnd == -1) {
                return;
            }

            String bindingsJson = json.substring(braceStart + 1, braceEnd);
            String[] pairs = bindingsJson.split(",");

            int loadedCount = 0;
            for (String pair : pairs) {
                pair = pair.trim();
                if (pair.isEmpty())
                    continue;

                // Parse "key": value
                int colonIndex = pair.indexOf(':');
                if (colonIndex == -1)
                    continue;

                String key = pair.substring(0, colonIndex).trim();
                // Remove quotes from key
                key = key.replace("\"", "");

                String value = pair.substring(colonIndex + 1).trim();
                try {
                    int keyCode = Integer.parseInt(value);
                    KeyBind bind = bindings.get(key);
                    if (bind != null) {
                        bind.setKeyCode(keyCode);
                        loadedCount++;
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            System.out.println("[KeyBindings] Loaded " + loadedCount + " keybinds from " + file.getName());

        } catch (IOException e) {
            System.err.println("[KeyBindings] Failed to load keybinds: " + e.getMessage());
        }
    }
}
