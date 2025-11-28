package game.init;

/**
 * Game initialization - registers all game content.
 * 
 * Called by Engine during initialization.
 */
public final class GameInit {
    
    private static boolean registered = false;
    
    private GameInit() {}
    
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
        // (blocks first, then biomes which reference blocks, then entities)
        GameBlocks.register();
        GameBiomes.register();
        // GameEntities.register();  // When entities are implemented
        
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