package game;

import engine.api.IGame;
import engine.core.Config;
import engine.core.Engine;
import engine.core.RenderInputHandler;
import engine.rendering.RenderSettings;
import engine.entity.EntityType;
import engine.entity.EntityTypes;
import engine.entity.EntityProperties;
import engine.entity.NpcEntity;
import engine.entity.Player;
import engine.rendering.Renderer;
import engine.ui.ContainerGui;
import engine.ui.GuiRenderer;
import engine.ui.HotbarGui;
import engine.ui.InventoryGui;
import engine.ui.InventoryInteractionManager;
import engine.ui.editor.GuiEditorIntegration;
import engine.world.World;
import engine.world.Chunk;
import game.init.GameBiomes;
import game.init.GameEntities;
import game.init.GameInit;
import game.init.GameItems;
import game.init.GameRecipes;
import static org.lwjgl.glfw.GLFW.*;
import engine.world.blockentity.ContainerBlockEntity;
import engine.world.gen.StructureLoader;
import engine.utils.Math3D.Vec3; // Added Import

public class ExampleGame implements IGame {

    private final Config config;

    private Engine engine;
    private World world;
    private Player player;
    private EntityType<Player> playerType;

    private RenderInputHandler renderInputHandler;

    // GUI
    private GuiRenderer guiRenderer;
    private HotbarGui hotbarGui;
    private InventoryGui inventoryGui;
    private InventoryInteractionManager inventoryInteraction;
    private boolean inventoryOpen = false;
    private boolean eKeyLatch = false;
    private GuiEditorIntegration guiEditor;

    private ContainerGui currentContainerGui = null;
    private ContainerBlockEntity currentContainer = null;

    // Rendering specifico del gioco (Overlay rottura blocchi)
    private engine.rendering.BreakProgressRenderer breakProgressRenderer;

    private RenderSettings renderSettings;
    private engine.rendering.WireframeRenderer wireframeRenderer;

    private enum GameState {
        MENU,
        WORLD_SELECT,
        PLAYING,
        PAUSED
    }

    private GameState state = GameState.MENU;
    private java.util.List<String> worldList = new java.util.ArrayList<>();
    private int selectedWorldIndex = -1;

    // UI Constants
    private static final int BTN_WIDTH = 200;
    private static final int BTN_HEIGHT = 40;
    private static final int BTN_PADDING = 10;

    public ExampleGame(Config config) {
        this.config = config;
    }

    public static void main(String[] args) {
        Config config = Config.builder()
                .windowSize(1280, 720)
                .viewDistance(12)
                .worldSeed(System.currentTimeMillis())
                .debug(true)
                .vsync(true)
                .build();

        Engine engine = new Engine(config);
        ExampleGame game = new ExampleGame(config);

        engine.init(game);
        engine.start();
        engine.shutdown();
    }

    @Override
    public void registerContent() {
        GameInit.registerContent();
        playerType = EntityTypes.register("game:player",
                EntityType.<Player>builder(type -> new Player(type, config))
                        .size(config.playerWidth, config.playerHeight)
                        .persistent(false)
                        .summonable(true)
                        .properties(EntityProperties.create()
                                .humanoid()
                                .texture("textures/entity/villager.png"))
                        .build());

        // Load structures
        StructureLoader.loadStructures();

        System.out.println("[Game] Entity types registered");
    }

    @Override
    public void init(Engine engine) {
        this.engine = engine;

        // Init basic systems
        engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL); // Start with cursor visible

        // Setup Render System
        this.renderSettings = new RenderSettings();
        this.renderSettings.setViewDistance(config.viewDistance);

        // Setup GUI Renderer
        this.guiRenderer = new GuiRenderer(config.windowWidth, config.windowHeight);
        guiRenderer.setAtlasTexture(engine.getRenderer().getAtlasTexture());

        // Wireframe debug
        this.wireframeRenderer = new engine.rendering.WireframeRenderer();

        System.out.println("[Game] Engine Init complete. Waiting in Main Menu.");
    }

    private void startWorld(String worldName) {
        config.worldName = worldName;

        // Cleanup old world if exists
        if (this.world != null) {
            this.world.cleanup();
        }

        engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);

        this.world = new World(config);
        engine.setWorld(world);

        // 1. Setup Player
        this.player = playerType.create();
        this.player.init(engine);

        // CRITICAL: Inject EntityManager into World so it can save the player!
        this.world.setEntityManager(engine.getEntities());

        // Check if player was loaded from level.dat (position set)

        // We can access WorldStorage via reflection or just create a new instance to
        // read level.dat (safe).
        engine.world.storage.WorldStorage storage = new engine.world.storage.WorldStorage(new java.io.File("."));
        storage.prepareWorld(worldName);
        engine.world.item.nbt.NBTTagCompound levelDat = storage.loadLevelData();
        if (levelDat != null && levelDat.hasKey("Player")) {
            System.out.println("[Game] Restoring player state from level.dat");
            player.load(levelDat.getTag("Player"));
            // Force a small offset to prevent getting stuck in blocks if rounding errors
            // occurred
            player.setPosition(player.getX(), player.getY() + 0.1f, player.getZ());
            // Reset velocity to prevent accumulating fall damage/speed from previous
            // session
            player.setVelocity(0, 0, 0);
        } else {

            // New world spawn logic
            Vec3 spawn = world.findSpawnPosition();
            player.setPosition(spawn.x, spawn.y + 2.0f, spawn.z); // Spawn higher
        }

        player.getInteractionManager().setGuiHandler((p, provider) -> {
            ContainerGui gui = provider.createGui(p, config.windowWidth, config.windowHeight);
            if (gui != null) {
                gui.setGuiScale(guiRenderer.getGuiScale());
                this.currentContainerGui = gui;

                // Track container for block updates if it is a block entity
                if (provider instanceof ContainerBlockEntity container) {
                    this.currentContainer = container;
                } else {
                    this.currentContainer = null;
                }

                this.inventoryOpen = true;
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            }
        });

        engine.getEntities().setPlayer(player);
        engine.getEntities().addEntity(player);

        // Setup HUD / Inventories
        int scale = guiRenderer.getGuiScale();
        this.hotbarGui = new HotbarGui(player.getInventory(), config.windowWidth / scale, config.windowHeight / scale);
        this.inventoryGui = new InventoryGui(player.getInventory(), config.windowWidth, config.windowHeight);
        inventoryGui.setGuiScale(scale);

        this.renderInputHandler = new RenderInputHandler(this.renderSettings, world);
        this.inventoryInteraction = new InventoryInteractionManager(player.getInventory());
        this.breakProgressRenderer = new engine.rendering.BreakProgressRenderer();

        state = GameState.PLAYING;
    }

    @Override
    public void update(float deltaTime) {
        if (state == GameState.MENU || state == GameState.WORLD_SELECT) {
            handleMenuInput();
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            return;
        }

        if (state == GameState.PAUSED) {
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            // Handle pause menu input
            handlePauseInput();
            return;
        }

        if (world == null || player == null)
            return;

        // PLAYING STATE

        // Check Pause
        if (engine.getInput().isKeyPressed(GLFW_KEY_ESCAPE)) { // Fixed method name
            state = GameState.PAUSED;
            return;
        }

        renderInputHandler.processInput(engine.getWindow().getHandle());

        // GUI Editor Update (if active)
        if (guiEditor != null) {
            guiEditor.update(engine.getInput(), engine.getInput().getMouseX(), engine.getInput().getMouseY());
            if (guiEditor.isEditorActive()) {
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
                return;
            }
        }

        // Gestione apertura inventario (Logica invariata)
        boolean eDown = engine.getInput().isKeyDown(GLFW_KEY_E);
        if (eDown && !eKeyLatch) {
            eKeyLatch = true;
            if (inventoryOpen) {
                // Close current GUI
                if (currentContainerGui != null) {
                    currentContainerGui.onClose();
                    currentContainerGui.cleanup();
                    currentContainerGui = null;
                    currentContainer = null;
                }
                inventoryOpen = false;
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
                engine.getInput().resetMouseDelta();
                inventoryInteraction.dropCursorToInventory();
            } else {
                // Open normal inventory
                inventoryOpen = true;
                currentContainerGui = null;
                currentContainer = null;
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            }
        }
        if (!eDown)
            eKeyLatch = false;

        // Gestione Input Inventario vs Gioco
        if (!inventoryOpen) {
            double scrollY = engine.getInput().getScrollY();
            if (scrollY != 0)
                inventoryInteraction.handleMouseWheel(scrollY);
            player.handleInteraction(engine.getInput(), deltaTime);
        } else {
            // GUI input
            if (currentContainerGui != null) {
                currentContainerGui.handleInput(engine.getInput(),
                        engine.getInput().getMouseX(),
                        engine.getInput().getMouseY());
            } else {
                inventoryGui.handleInput(engine.getInput(), inventoryInteraction,
                        engine.getInput().getMouseX(),
                        engine.getInput().getMouseY(),
                        config.windowHeight);
            }
        }

        // VOID FALL PROTECTION
        // Ensure the chunk under the player is loaded before applying physics/gravity
        int cx = (int) Math.floor(player.getX()) >> 4;
        int cz = (int) Math.floor(player.getZ()) >> 4;
        Chunk chunk = world.getChunkIfLoaded(cx, cz);

        // If chunk is missing or empty (not generated yet), freeze player Y
        if (chunk == null || chunk.getPhase() == Chunk.Phase.EMPTY) {
            player.setVelocity(0, 0, 0); // Stop movement
            // Maybe allow horizontal but not vertical? For now freeze to be safe.
        } else {
            // Normal update
            player.update(deltaTime, engine.getInput(), !inventoryOpen);
        }
    }

    // ==================== MENU LOGIC ====================

    private void handleMenuInput() {
        if (engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
            // Convert to Logical Coordinates
            float scale = guiRenderer.getGuiScale();
            double mx = engine.getInput().getMouseX() / scale;
            double my = engine.getInput().getMouseY() / scale;

            float logicalW = config.windowWidth / scale;
            float logicalH = config.windowHeight / scale;

            float cx = logicalW / 2;
            float cy = logicalH / 2;

            if (state == GameState.MENU) {
                // Singleplayer Button
                if (isHover(mx, my, cx - BTN_WIDTH / 2, cy - BTN_HEIGHT / 2, BTN_WIDTH, BTN_HEIGHT)) {
                    refreshWorldList();
                    state = GameState.WORLD_SELECT;
                }
                // Quit Button
                if (isHover(mx, my, cx - BTN_WIDTH / 2, cy + BTN_HEIGHT + BTN_PADDING, BTN_WIDTH, BTN_HEIGHT)) {
                    engine.shutdown();
                }
            } else if (state == GameState.WORLD_SELECT) {
                // Back Button
                if (isHover(mx, my, 10, 10, 80, 30)) {
                    state = GameState.MENU;
                }

                // Create New Button
                if (isHover(mx, my, cx - BTN_WIDTH / 2, logicalH - 60, BTN_WIDTH, BTN_HEIGHT)) {
                    startWorld("New World " + System.currentTimeMillis());
                }

                // World List Click
                int listY = 80;
                for (int i = 0; i < worldList.size(); i++) {
                    if (isHover(mx, my, cx - 150, listY + i * 35, 300, 30)) {
                        startWorld(worldList.get(i));
                    }
                }
            }
        }
    }

    private void handlePauseInput() {
        if (engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
            float scale = guiRenderer.getGuiScale();
            double mx = engine.getInput().getMouseX() / scale;
            double my = engine.getInput().getMouseY() / scale;

            float logicalW = config.windowWidth / scale;
            float logicalH = config.windowHeight / scale;
            float cx = logicalW / 2;
            float cy = logicalH / 2;

            // Resume
            if (isHover(mx, my, cx - BTN_WIDTH / 2, cy - BTN_HEIGHT - BTN_PADDING, BTN_WIDTH, BTN_HEIGHT)) {
                state = GameState.PLAYING;
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
            }

            // Save & Quit
            if (isHover(mx, my, cx - BTN_WIDTH / 2, cy, BTN_WIDTH, BTN_HEIGHT)) {
                if (world != null) {
                    world.saveWorld();
                }
                // Return to Main Menu
                if (world != null)
                    world.cleanup();
                world = null;
                player = null;
                state = GameState.MENU;
            }
        }

        if (engine.getInput().isKeyPressed(GLFW_KEY_ESCAPE)) {
            state = GameState.PLAYING;
            engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
        }
    }

    private void refreshWorldList() {
        worldList.clear();
        java.io.File saves = new java.io.File("saves");
        if (saves.exists() && saves.isDirectory()) {
            for (java.io.File f : saves.listFiles()) {
                if (f.isDirectory()) {
                    worldList.add(f.getName());
                }
            }
        }
    }

    private boolean isHover(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render3D(engine.rendering.Renderer renderer, float partialTick) {
        if (state != GameState.PLAYING && state != GameState.PAUSED)
            return;
        if (player == null || world == null)
            return;

        // Render 3D overlays before Hand Pass
        breakProgressRenderer.render(player.getMiningManager(), player.getCamera(), world);
    }

    @Override
    public void render(Renderer renderer) {
        guiRenderer.begin();

        if (state == GameState.MENU) {
            drawMainMenu();
        } else if (state == GameState.WORLD_SELECT) {
            drawWorldSelect();
        } else if (state == GameState.PLAYING || state == GameState.PAUSED) {
            // HUD
            hotbarGui.render(guiRenderer);

            if (inventoryOpen) {
                if (currentContainerGui != null) {
                    currentContainerGui.render(guiRenderer);
                    if (currentContainerGui.hasCursorItem()) {
                        currentContainerGui.renderCursorItem(guiRenderer,
                                engine.getInput().getMouseX(),
                                engine.getInput().getMouseY());
                    }
                } else {
                    inventoryGui.render(guiRenderer);
                    if (inventoryInteraction.hasCursorItem()) {
                        inventoryGui.renderCursorItem(guiRenderer, inventoryInteraction.getCursorStack(),
                                engine.getInput().getMouseX(), engine.getInput().getMouseY());
                    }
                }
            }

            if (state == GameState.PAUSED) {
                drawPauseMenu();
            }
        }

        if (guiEditor != null && guiEditor.isEditorActive()) {
            guiEditor.getEditor().render(guiRenderer);
        }

        guiRenderer.end();

        // Debug
        if (state == GameState.PLAYING) {
            // Position Debug
            String posStr = String.format("Pos: %.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ());
            guiRenderer.renderText(posStr, 10, 10, 2.0f, 1, 1, 1, 1);

            // Chunk Debug
            int cx = (int) Math.floor(player.getX()) >> 4;
            int cz = (int) Math.floor(player.getZ()) >> 4;
            engine.world.Chunk c = world.getChunkIfLoaded(cx, cz);
            String chunkStr = "Chunk: " + (c == null ? "NULL" : c.getPhase().toString());
            guiRenderer.renderText(chunkStr, 10, 30, 2.0f, 1, 1, 1, 1);

            engine.interaction.RaycastResult target = player.getInteractionManager().performRaycast(player, world,
                    engine.getEntities());
            if (target != null && target.isBlock()) {
                engine.world.BlockPos pos = target.getBlockPos();
                wireframeRenderer.draw(player.getCamera().getProjectionMatrix(),
                        player.getCamera().getViewMatrix(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        0, 0, 0, 0.4f);
            }
        }
    }

    private void drawBackground() {
        // Draw darker dimmed dirt background
        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;

        // Tile Dirt
        float size = 32;
        int cols = (int) (logicalW / size) + 1;
        int rows = (int) (logicalH / size) + 1;

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                // Dimmed (0.5 brightness)
                guiRenderer.renderBlockFlat(x * size, y * size, size, game.init.GameBlocks.DIRT); // How to dim?
                                                                                                  // renderBlockFlat
                                                                                                  // doesn't take color.
                // We can draw a black overlay on top.
            }
        }

        // Dark overlay
        guiRenderer.renderRect(0, 0, logicalW, logicalH, 0, 0, 0, 0.6f);
    }

    private void drawMainMenu() {
        drawBackground();

        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;

        float cx = logicalW / 2;
        float cy = logicalH / 2;

        // Title
        String title = "VOXEL ENGINE";
        float titleScale = 8.0f; // Scale 8 = 40px height
        // Approximation of text width: char count * (size*0.7)
        float titleW = title.length() * (titleScale * 3.5f); // 3x5 font -> 3.5 width per scale 1
        guiRenderer.renderText(title, cx - titleW / 2, cy - 100, titleScale, 1, 1, 1, 1);

        drawButton((int) (cx - BTN_WIDTH / 2), (int) (cy - BTN_HEIGHT / 2), "Singleplayer");
        drawButton((int) (cx - BTN_WIDTH / 2), (int) (cy + BTN_HEIGHT + BTN_PADDING), "Quit Game");
    }

    private void drawWorldSelect() {
        drawBackground();

        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;

        guiRenderer.renderText("SELECT WORLD", cx - 100, 20, 4.0f, 1, 1, 1, 1);

        // Back
        drawButton(10, 10, 80, 30, "Back");

        // List Background
        guiRenderer.renderRect(cx - 160, 60, 320, logicalH - 120, 0, 0, 0, 0.5f);

        // List
        int listY = 80;
        for (String world : worldList) {
            drawButton((int) (cx - 150), listY, 300, 30, world);
            listY += 35;
        }

        // Create New
        drawButton((int) (cx - BTN_WIDTH / 2), (int) (logicalH - 60), "Create New World");
    }

    private void drawPauseMenu() {
        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;

        guiRenderer.renderRect(0, 0, logicalW, logicalH, 0, 0, 0, 0.5f);

        float cx = logicalW / 2;
        float cy = logicalH / 2;

        guiRenderer.renderText("PAUSED", cx - 60, cy - 120, 6.0f, 1, 1, 1, 1);

        drawButton((int) (cx - BTN_WIDTH / 2), (int) (cy - BTN_HEIGHT - BTN_PADDING), "Resume Game");
        drawButton((int) (cx - BTN_WIDTH / 2), (int) cy, "Save and Quit");
    }

    private void drawButton(int x, int y, int w, int h, String text) {
        float scale = guiRenderer.getGuiScale();
        double mx = engine.getInput().getMouseX() / scale;
        double my = engine.getInput().getMouseY() / scale;
        boolean hover = isHover(mx, my, x, y, w, h);

        // Background
        float r = hover ? 0.4f : 0.2f;
        float g = hover ? 0.4f : 0.2f;
        float b = hover ? 0.4f : 0.2f;
        guiRenderer.renderRect(x, y, w, h, r, g, b, 1.0f);

        // Border
        guiRenderer.renderRect(x, y, w, 2, 0.8f, 0.8f, 0.8f, 1); // Top
        guiRenderer.renderRect(x, y + h - 2, w, 2, 0.1f, 0.1f, 0.1f, 1); // Bottom
        guiRenderer.renderRect(x, y, 2, h, 0.8f, 0.8f, 0.8f, 1); // Left
        guiRenderer.renderRect(x + w - 2, y, 2, h, 0.1f, 0.1f, 0.1f, 1); // Right

        // Text Centering
        float size = 4.0f; // Scale 4 = 20px height
        float charW = size * 0.7f;
        float textW = text.length() * charW;
        float textX = x + (w - textW) / 2;
        float textY = y + (h - size) / 2;

        guiRenderer.renderText(text, textX, textY, size, 1, 1, 1, 1);
    }

    private void drawButton(int x, int y, String text) {
        drawButton(x, y, BTN_WIDTH, BTN_HEIGHT, text);
    }

    @Override
    public void cleanup() {
        if (guiRenderer != null)
            guiRenderer.cleanup();
        if (guiEditor != null)
            guiEditor.cleanup();
        if (wireframeRenderer != null)
            wireframeRenderer.cleanup();
    }
}