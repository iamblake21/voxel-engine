package engine.core;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Game loop with fixed timestep for physics and variable rendering
 */
public class GameLoop {

    private final Engine engine;
    private final Time time;

    // Fixed timestep for physics (60 fps)
    private static final float FIXED_TIMESTEP = 1f / 60f;
    private static final float MAX_FRAME_TIME = 0.25f; // Prevent spiral of death

    private long lastFrameTime;
    private float accumulator = 0f;

    // FPS counter
    private int frames = 0;
    private long lastFPSTime = 0;
    private int currentFPS = 0;

    public GameLoop(Engine engine) {
        this.engine = engine;
        this.time = new Time();
    }

    /**
     * Main game loop - runs until engine stops
     */
    public void run() {
        lastFrameTime = System.nanoTime();
        lastFPSTime = System.nanoTime();

        while (engine.isRunning()) {
            long currentTime = System.nanoTime();
            float frameTime = (currentTime - lastFrameTime) / 1_000_000_000f;
            lastFrameTime = currentTime;

            // Cap frame time to prevent spiral of death
            if (frameTime > MAX_FRAME_TIME) {
                frameTime = MAX_FRAME_TIME;
            }

            accumulator += frameTime;

            // Fixed timestep updates (physics)
            while (accumulator >= FIXED_TIMESTEP) {
                time.update(FIXED_TIMESTEP);
                engine.update(FIXED_TIMESTEP);
                accumulator -= FIXED_TIMESTEP;
            }

            // Render with interpolation factor
            float alpha = accumulator / FIXED_TIMESTEP;
            time.setAlpha(alpha);
            engine.render();

            // Update FPS counter
            updateFPS(currentTime);

            // Clear input state (scroll, justPressed) from this frame
            engine.getInput().endFrame();

            // Poll events
            glfwPollEvents();
        }

        engine.shutdown();
    }

    private void updateFPS(long currentTime) {
        frames++;

        if (currentTime - lastFPSTime >= 1_000_000_000L) {
            currentFPS = frames;

            if (engine.getConfig().showDebugInfo) {
                String title = String.format("%s - FPS: %d",
                        engine.getConfig().windowTitle, currentFPS);
                glfwSetWindowTitle(engine.getWindow().getHandle(), title);
            }

            frames = 0;
            lastFPSTime = currentTime;
        }
    }

    public Time getTime() {
        return time;
    }

    public int getCurrentFPS() {
        return currentFPS;
    }

    public Engine getEngine() {
        return engine;
    }
}