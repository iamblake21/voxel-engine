package engine.core;

import engine.EngineBootstrap;
import engine.window.Window;
import engine.window.InputManager;
import engine.rendering.Renderer;
import engine.entity.Entity;
import engine.entity.render.EntityRenderer;
import engine.world.World;
import engine.entity.EntityManager;
import engine.physics.PhysicsEngine;
import engine.api.IGame;

public class Engine {

    private final Config config;
    private final Window window;
    private final InputManager input;

    private final Renderer renderer;
    private final EntityRenderer entityRenderer;

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
        this.entityRenderer = new EntityRenderer();
        this.physics = new PhysicsEngine(config);
        this.entities = new EntityManager();
        this.gameLoop = new GameLoop(this);
    }

    public void init(IGame game) {
        this.game = game;

        System.out.println("=== Engine Initialization ===");
        EngineBootstrap.init();

        System.out.println("=== Content Registration Phase ===");
        game.registerContent();
        EngineBootstrap.freeze();

        window.create();
        renderer.init();
        entityRenderer.init();
        input.init();

        System.out.println("=== Game Initialization ===");
        game.init(this);

        System.out.println("Engine initialized successfully");
    }

    public void start() {
        if (running)
            throw new IllegalStateException("Engine already running");
        running = true;
        gameLoop.run();
    }

    public void update(float deltaTime) {
        if (!running)
            return;

        input.update();

        if (world != null) {
            world.update(deltaTime);
        }

        if (game != null) {
            // Qui dentro viene chiamato player.update(input), che APPLICA la fisica al
            // player.
            game.update(deltaTime);
        }
        entities.update(deltaTime);
        if (world != null) {
            for (Entity e : entities.getEntities()) {
                if (e instanceof engine.entity.Player)
                    continue;
                physics.processEntity(e, world, deltaTime);
            }
        }
    }

    public void render() {
        if (!running)
            return;

        // 1. Render World
        renderer.beginFrame(world);
        if (world != null) {
            renderer.renderWorld(world);
        }

        // 2. Render Entities
        if (entities.getPlayer() != null) {
            engine.utils.Math3D.Vec3 sunDir = (world != null) ? world.getSunDirection() : null;
            entityRenderer.begin(entities.getPlayer().getCamera(), sunDir);

            for (Entity e : entities.getEntities()) {
                // Non renderizzare il player stesso per evitare glitch visivi
                if (e == entities.getPlayer())
                    continue;

                entityRenderer.renderEntity(e, entities.getPartialTick());
            }
            entityRenderer.end();
        }

        // 3. Render Game GUI / Overlay
        if (game != null) {
            game.render(renderer);
        }

        renderer.endFrame();
        window.swapBuffers();
    }

    public void shutdown() {
        running = false;
        if (game != null)
            game.cleanup();
        entities.cleanup();
        entityRenderer.cleanup();
        if (world != null)
            world.cleanup();
        renderer.cleanup();
        window.destroy();
        System.out.println("Engine shutdown complete");
    }

    // Getters
    public Config getConfig() {
        return config;
    }

    public Window getWindow() {
        return window;
    }

    public InputManager getInput() {
        return input;
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public EntityRenderer getEntityRenderer() {
        return entityRenderer;
    }

    public PhysicsEngine getPhysics() {
        return physics;
    }

    public EntityManager getEntities() {
        return entities;
    }

    public World getWorld() {
        return world;
    }

    public GameLoop getGameLoop() {
        return gameLoop;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public boolean isRunning() {
        return running && !window.shouldClose();
    }

    public EntityManager getEntityManager() {
        return entities;
    }
}