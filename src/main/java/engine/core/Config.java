package engine.core;

/**
 * Engine configuration - all settings in one place
 */
public class Config {

    // Window settings
    public int windowWidth = 1280;
    public int windowHeight = 720;
    public String windowTitle = "Voxel Engine";
    public boolean vsync = false;
    public boolean fullscreen = false;

    // Rendering settings
    public float fov = 70f;
    public float nearPlane = 0.1f;
    public float farPlane = 1000f;
    public boolean wireframe = false;

    // World settings
    public int chunkSize = 16;
    public int worldHeight = 256;
    public int viewDistance = 1;
    public float waterLevel = 62f;

    // Physics settings
    public float gravity = 22f;
    public float playerSpeed = 8f;
    public float playerSprintMultiplier = 1.8f;
    public float playerFlySpeed = 42f;
    public float playerHeight = 1.80f;
    public float playerEyeHeight = 1.62f;
    public float playerWidth = 0.6f; // diameter
    public float jumpForce = 7.8f;

    // Water physics
    public float waterBuoyancy = 11f;
    public float waterDrag = 3.5f;
    public float waterSwimForce = 18f;

    // Generation settings
    public long worldSeed = 55555;
    public String worldName = "New World";
    public boolean generateTrees = true;
    public float treeDensity = 0.28f;
    public boolean generateCaves = true;

    // Performance settings
    public int maxChunkUpdatesPerFrame = 4;
    public boolean multiThreadedGeneration = false;
    public int generationThreads = 4;

    // Debug settings
    public boolean showDebugInfo = false;
    public boolean showChunkBorders = false;
    public boolean showCollisionBoxes = false;

    private Config() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Config defaultConfig() {
        return new Config();
    }

    /**
     * Builder pattern for easy configuration
     */
    public static class Builder {
        private final Config config = new Config();

        public Builder windowSize(int width, int height) {
            config.windowWidth = width;
            config.windowHeight = height;
            return this;
        }

        public Builder windowTitle(String title) {
            config.windowTitle = title;
            return this;
        }

        public Builder vsync(boolean enabled) {
            config.vsync = enabled;
            return this;
        }

        public Builder fullscreen(boolean enabled) {
            config.fullscreen = enabled;
            return this;
        }

        public Builder fov(float fov) {
            config.fov = fov;
            return this;
        }

        public Builder viewDistance(int chunks) {
            config.viewDistance = chunks;
            return this;
        }

        public Builder worldSeed(long seed) {
            config.worldSeed = seed;
            return this;
        }

        public Builder multiThreadedGeneration(boolean enabled, int threads) {
            config.multiThreadedGeneration = enabled;
            config.generationThreads = threads;
            return this;
        }

        public Builder debug(boolean enabled) {
            config.showDebugInfo = enabled;
            return this;
        }

        public Config build() {
            validate();
            return config;
        }

        private void validate() {
            if (config.windowWidth <= 0 || config.windowHeight <= 0) {
                throw new IllegalStateException("Invalid window size");
            }
            if (config.chunkSize <= 0 || config.worldHeight <= 0) {
                throw new IllegalStateException("Invalid world dimensions");
            }
            if (config.viewDistance < 1) {
                throw new IllegalStateException("View distance must be at least 1");
            }
        }
    }
}