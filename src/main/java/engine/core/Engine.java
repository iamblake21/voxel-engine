package engine.core;

import engine.EngineBootstrap;
import engine.window.Window;
import engine.window.InputManager;
import engine.rendering.Renderer;
import engine.world.World;
import engine.entity.EntityManager;
import engine.physics.PhysicsEngine;
import engine.api.IGame;

/**
 * Main engine class - coordinates all subsystems
 */
public class Engine {
    
    private final Config config;
    private final Window window;
    private final InputManager input;
    private final Renderer renderer;
    private final PhysicsEngine physics;
    private final EntityManager entities;
    private final GameLoop gameLoop;
    
    private World world;
    private IGame game;
    private boolean running;
    
    public Engine(Config config) {
        this.config = config;
        this.window = new Window(config);
        this.input = new InputManager(window);
        this.renderer = new Renderer(config);
        this.physics = new PhysicsEngine(config);
        this.entities = new EntityManager();
        this.gameLoop = new GameLoop(this);
    }
    
    /**
     * Initialize engine with a game instance
     */
    public void init(IGame game) {
        this.game = game;
        
        // Step 1: Initialize engine (registers AIR block, default biome)
        System.out.println("=== Engine Initialization ===");
        EngineBootstrap.init();
        
        // Step 2: Let game register content (blocks, entities, etc.)
        System.out.println("=== Content Registration Phase ===");
        game.registerContent();
        
        // Step 3: Freeze registries - no more registration allowed
        EngineBootstrap.freeze();
        
        // Step 4: Create window and renderer
        window.create();
        renderer.init();
        input.init();
        
        // Step 5: Let game initialize (create world, player, etc.)
        System.out.println("=== Game Initialization ===");
        game.init(this);
        
        System.out.println("Engine initialized successfully");
    }
    
    /**
     * Start the game loop
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("Engine already running");
        }
        
        running = true;
        gameLoop.run();
    }
    
    /**
     * Update all engine systems
     */
    public void update(float deltaTime) {
        if (!running) return;
        
        // Update input first
        input.update();
        
        // Update world (chunk loading, etc.)
        if (world != null) {
            world.update(deltaTime);
        }
        
        // Let game update (handles player input)
        if (game != null) {
            game.update(deltaTime);
        }
        
        // Update entities (includes player physics)
        entities.update(deltaTime);
        
        // Physics is handled per-entity
        physics.update(deltaTime);
    }
    
    /**
     * Render the current frame
     */
    public void render() {
        if (!running) return;
        
        renderer.beginFrame(world);
        
        if (game != null) {
            game.render(renderer);
        }
        
        if (world != null) {
            renderer.renderWorld(world);
        }
        
        entities.render(renderer);
        
        renderer.endFrame();
        window.swapBuffers();
    }
    
    /**
     * Clean shutdown
     */
    public void shutdown() {
        running = false;
        
        if (game != null) {
            game.cleanup();
        }
        
        entities.cleanup();
        
        if (world != null) {
            world.cleanup();
        }
        
        renderer.cleanup();
        window.destroy();
        
        System.out.println("Engine shutdown complete");
    }
    
    // Getters
    public Config getConfig() { return config; }
    public Window getWindow() { return window; }
    public InputManager getInput() { return input; }
    public Renderer getRenderer() { return renderer; }
    public PhysicsEngine getPhysics() { return physics; }
    public EntityManager getEntities() { return entities; }
    public World getWorld() { return world; }
    public GameLoop getGameLoop() { return gameLoop; }
    
    public void setWorld(World world) { 
        this.world = world; 
    }
    
    public boolean isRunning() { 
        return running && !window.shouldClose(); 
    }
}