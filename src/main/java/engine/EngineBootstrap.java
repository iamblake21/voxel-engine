package engine;

import engine.registry.Registries;
import engine.world.block.Blocks;
import engine.world.biome.Biomes;
import engine.entity.EntityTypes;

/**
 * Engine bootstrap - initializes all engine systems.
 * 
 * Call order:
 * 1. EngineBootstrap.init() - registers engine defaults
 * 2. Game.registerContent() - game registers its content
 * 3. EngineBootstrap.freeze() - locks registries
 * 4. Game.start() - game runs
 */
public final class EngineBootstrap {
    
    private static boolean initialized = false;
    private static boolean frozen = false;
    
    private EngineBootstrap() {}
    
    /**
     * Initialize the engine.
     * Registers engine-provided defaults (AIR block, default biome, etc.)
     * Call this BEFORE game registers its content.
     */
    public static void init() {
        if (initialized) {
            throw new IllegalStateException("Engine already initialized");
        }
        
        System.out.println("========================================");
        System.out.println("  Voxel Engine - Initializing");
        System.out.println("========================================");
        
        // Register engine defaults
        Blocks.registerEngineBlocks();
        Biomes.registerEngineBiomes();
        EntityTypes.registerEngineEntityTypes();
        
        
        initialized = true;
        
        System.out.println("[Engine] Initialization complete");
        System.out.println("[Engine] Ready for game content registration");
        System.out.println("----------------------------------------");
    }
    
    /**
     * Freeze all registries.
     * Call this AFTER game has registered all its content.
     * No more registrations allowed after this.
     */
    public static void freeze() {
        if (!initialized) {
            throw new IllegalStateException("Engine not initialized");
        }
        if (frozen) {
            throw new IllegalStateException("Registries already frozen");
        }
        
        System.out.println("----------------------------------------");
        System.out.println("[Engine] Freezing registries...");
        
        Registries.freezeAll();
        
        frozen = true;
        
        System.out.println("[Engine] All registries frozen");
        Registries.dumpInfo();
        System.out.println("========================================");
    }
    
    /**
     * Check if engine is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Check if registries are frozen
     */
    public static boolean isFrozen() {
        return frozen;
    }
    
    /**
     * Reset for testing (not for production use)
     */
    public static void resetForTesting() {
        initialized = false;
        frozen = false;
        // Note: This doesn't actually clear registries - 
        // they would need their own reset methods for that
    }
}