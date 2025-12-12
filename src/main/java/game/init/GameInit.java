package game.init;

/**
 * Game initialization - registers all game content.
 * 
 * Called by Engine during initialization.
 */
public final class GameInit {

    private static boolean registered = false;

    private GameInit() {
    }

    /**
     * Register all game content.
     * Called by Engine before registries are frozen.
     */
    public static void registerContent() {
        if (registered) {
            System.out.println("[Game] Content already registered, skipping");
            return;
        }

        System.out.println("[Game] Registering game content...");

        // Register in dependency order
        // 1. Blocks first (no dependencies)
        // 2. Items (reference blocks for BlockItems)
        // 3. GUIs (can reference items/blocks for icons)
        // 4. Biomes (reference blocks)
        // 5. Entities (reference items, etc.)

        GameBlocks.register();
        GameItems.register();
        GameGuis.register(); // <-- NEW: Register GUIs
        GameBiomes.register();
        GameEntities.register(); // When entities are implemented
        GameBlockEntities.register(); // When block entities are implemented
        GameLootTables.register(); // <-- NEW: Register loot tables

        registered = true;

        System.out.println("[Game] Content registration complete");
    }

    /**
     * Check if content is registered
     */
    public static boolean isRegistered() {
        return registered;
    }
}