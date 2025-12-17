package engine.api;

import engine.core.Engine;
import engine.rendering.Renderer;

/**
 * Interface for game implementations
 * The engine calls these methods at appropriate times
 */
public interface IGame {

    /**
     * Called FIRST - register all blocks, entities, etc.
     * This is called BEFORE init() so registries are ready
     */
    default void registerContent() {
        // default: nothing, game can override
    }

    /**
     * Called to initialize the game (create world, player, etc.)
     */
    void init(Engine engine);

    /**
     * Called every tick to update game logic
     */
    void update(float deltaTime);

    /**
     * Called every frame to render game-specific content
     */
    void render(Renderer renderer);

    /**
     * Called after world/entities rendering but BEFORE Hand rendering.
     * Use this for 3D overlays like Block Selection, Breaking Progress, etc.
     */
    default void render3D(Renderer renderer, float partialTick) {
        // default: nothing
    }

    /**
     * Called on shutdown to clean up resources
     */
    void cleanup();
}
