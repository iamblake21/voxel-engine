package game;

import engine.api.IGame;
import engine.core.Config;
import engine.core.Engine;
import engine.core.RenderInputHandler;
import engine.input.KeyBindings;
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
import game.ui.PlayerInventoryGui;
import engine.ui.editor.GuiEditorIntegration;
import engine.world.World;
import engine.world.Chunk;
import game.init.GameInit;
import game.input.GameKeyBinds;
import static org.lwjgl.glfw.GLFW.*;
import engine.world.blockentity.ContainerBlockEntity;
import engine.world.gen.StructureLoader;
import engine.utils.Math3D.Vec3;
import engine.utils.GameStorage; // Fix shadowing block

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
    private PlayerInventoryGui inventoryGui;
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
        CREATE_WORLD,
        RENAME_WORLD,
        OPTIONS,
        KEYBINDS,
        PLAYING,
        PAUSED,
        LOADING
    }

    private GameState state = GameState.MENU;
    private GameState previousState = GameState.MENU; // For Back button
    private java.util.List<String> worldList = new java.util.ArrayList<>();
    private int selectedWorldIndex = -1;

    // Loading State
    private float loadingProgress = 0.0f;
    private String loadingMessage = "";
    private java.util.concurrent.CompletableFuture<Void> currentTask = null;
    private java.util.Queue<Runnable> mainThreadTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    // Config/Input buffers
    private StringBuilder nameBuffer = new StringBuilder();
    private StringBuilder seedBuffer = new StringBuilder();
    private boolean isTypingSeed = false; // Toggle between Name/Seed
    private String renameTarget = null;

    // Keybinds UI State
    private engine.input.KeyBind editingKeybind = null; // Currently being rebound
    private int keybindScrollOffset = 0;

    // UI Constants

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

    // Resize Callback
    public void resize(int width, int height) {
        config.windowWidth = width;
        config.windowHeight = height;

        // Update Grid Renderer
        if (guiRenderer != null) {
            guiRenderer.updateDimensions(width, height);
        }

        // Re-setup UI if player exists
        if (player != null) {
            setupPlayerUI();
        }
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

        // Hook Resize
        engine.getWindow().addResizeCallback(this::resize);
        guiRenderer.setAtlasTexture(engine.getRenderer().getAtlasTexture());

        // Wireframe debug
        this.wireframeRenderer = new engine.rendering.WireframeRenderer();

        // Initialize KeyBindings System
        KeyBindings.getInstance().setInput(this.engine.getInput());
        GameKeyBinds.register();
        KeyBindings.getInstance().load(GameStorage.getKeybindsFile());

        System.out.println("[Game] Engine Init complete. Waiting in Main Menu.");
    }

    private void startWorld(String worldName) {
        config.worldName = worldName;

        // Cleanup old world if exists
        if (this.world != null) {
            this.world.cleanup();
        }

        engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);

        // 1. Pre-load Level Data to get the Seed!
        engine.world.storage.WorldStorage storage = new engine.world.storage.WorldStorage(new java.io.File("."));
        storage.prepareWorld(worldName);
        engine.world.item.nbt.NBTTagCompound levelDat = storage.loadLevelData();

        if (levelDat != null && levelDat.hasKey("Seed")) {
            long savedSeed = levelDat.getLong("Seed");
            System.out.println("[Game] Restored Seed from level.dat: " + savedSeed);
            config.worldSeed = savedSeed;
        }

        // 2. NOW we can create the World with the correct Seed
        this.world = new World(config);
        engine.setWorld(world);

        // 3. Setup Player
        this.player = playerType.create();
        this.player.init(engine);

        // CRITICAL: Inject EntityManager into World so it can save the player!
        this.world.setEntityManager(engine.getEntities());

        // 4. Restore Player State
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
        this.inventoryGui = new PlayerInventoryGui(player, config.windowWidth, config.windowHeight);
        inventoryGui.setGuiScale(scale);

        this.renderInputHandler = new RenderInputHandler(this.renderSettings, world);
        this.breakProgressRenderer = new engine.rendering.BreakProgressRenderer();

        // GUI Editor
        this.guiEditor = new GuiEditorIntegration(config.windowWidth, config.windowHeight, guiRenderer);
        // When activated, load the player inventory for editing
        this.guiEditor.setOnActivate(() -> {
            guiEditor.editGui("game:player_inventory");
        });

        state = GameState.PLAYING;
    }

    @Override
    public void update(float deltaTime) {
        // Process Main Thread Tasks
        while (!mainThreadTasks.isEmpty()) {
            mainThreadTasks.poll().run();
        }

        if (state == GameState.PAUSED) {
            handlePauseInput();
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            return;
        }

        if (state == GameState.MENU || state == GameState.WORLD_SELECT || state == GameState.CREATE_WORLD
                || state == GameState.RENAME_WORLD || state == GameState.OPTIONS || state == GameState.KEYBINDS) {
            handleMenuInput();
            // Only set cursor to NORMAL if we are still in a menu state
            if (state != GameState.PLAYING) {
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            }
            return;
        }

        if (world == null || player == null)
            return;

        // PLAYING STATE

        // Check Pause

        // Check Pause
        if (GameKeyBinds.MENU.isPressed()) {
            state = GameState.PAUSED;
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            return;
        }
        if (guiEditor != null) {
            guiEditor.update(engine.getInput(), engine.getInput().getMouseX(), engine.getInput().getMouseY());
            if (guiEditor.isEditorActive()) {
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
                return;
            }
        }

        // Gestione apertura inventario (Logica invariata)
        boolean eDown = GameKeyBinds.INVENTORY.isDown();
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
                inventoryGui.onClose();
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
            if (scrollY != 0) {
                // Hotbar scroll
                player.getInventory()
                        .setSelectedSlot((player.getInventory().getSelectedSlot() - (int) scrollY + 9) % 9);
            }
            player.handleInteraction(engine.getInput(), deltaTime);
        } else {
            // GUI input
            if (currentContainerGui != null) {
                currentContainerGui.handleInput(engine.getInput(),
                        engine.getInput().getMouseX(),
                        engine.getInput().getMouseY());
            } else {
                inventoryGui.handleInput(engine.getInput(),
                        engine.getInput().getMouseX(),
                        engine.getInput().getMouseY());
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
        if (state == GameState.CREATE_WORLD || state == GameState.RENAME_WORLD) {
            handleTextInput();
        }

        if (state == GameState.OPTIONS) {
            handleOptionsInput();
            return; // Options has its own full input handler
        }

        if (state == GameState.KEYBINDS) {
            handleKeybindsInput();
            return; // Keybinds has its own full input handler
        }

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
                if (isHover(mx, my, cx - 150, cy - 25, 300, 50)) {
                    refreshWorldList();
                    state = GameState.WORLD_SELECT;
                    selectedWorldIndex = -1;
                }
                // Quit Button
                if (isHover(mx, my, cx - 150, cy + 50, 300, 50)) {
                    engine.shutdown();
                }
                // Options Button
                if (isHover(mx, my, cx - 150, cy + 125, 300, 50)) {
                    previousState = GameState.MENU;
                    state = GameState.OPTIONS;
                }
            } else if (state == GameState.WORLD_SELECT) {
                // Back Button
                if (isHover(mx, my, 20, 20, 100, 40)) {
                    state = GameState.MENU;
                }

                // World List Click
                int listY = 120;
                for (int i = 0; i < worldList.size(); i++) {
                    if (isHover(mx, my, cx - 200, listY + i * 50, 400, 40)) {
                        selectedWorldIndex = i; // Select
                    }
                }

                int startX = (int) cx - 210;
                int btnY = (int) logicalH - 60;
                // Create New Button
                if (isHover(mx, my, startX, btnY, 140, 40)) {
                    state = GameState.CREATE_WORLD;
                    nameBuffer.setLength(0);
                    nameBuffer.append("New World");
                    seedBuffer.setLength(0);
                    isTypingSeed = false;
                }

                // Action Buttons
                if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
                    String selectedName = worldList.get(selectedWorldIndex);
                    if (isHover(mx, my, cx - 60, btnY, 80, 40)) {
                        startWorldAsync(selectedName);
                    }
                    // Delete
                    if (isHover(mx, my, cx + 30, btnY, 80, 40)) {
                        deleteWorld(selectedName);
                        refreshWorldList();
                        selectedWorldIndex = -1;
                    }
                    // Rename
                    if (isHover(mx, my, cx + 120, btnY, 80, 40)) {
                        state = GameState.RENAME_WORLD;
                        renameTarget = selectedName;
                        nameBuffer.setLength(0);
                        nameBuffer.append(selectedName);
                        isTypingSeed = false;
                    }
                }

            } else if (state == GameState.CREATE_WORLD) {
                int btnY = (int) logicalH - 80;
                // Create
                if (isHover(mx, my, (int) cx - 210, btnY, 200, 40)) {
                    String name = nameBuffer.toString().trim();
                    if (!name.isEmpty()) {
                        // Parse Seed
                        String seedStr = seedBuffer.toString().trim();
                        long seed;
                        if (seedStr.isEmpty()) {
                            seed = new java.util.Random().nextLong();
                        } else {
                            try {
                                seed = Long.parseLong(seedStr);
                            } catch (NumberFormatException e) {
                                seed = seedStr.hashCode();
                            }
                        }

                        config.worldSeed = seed;
                        startWorldAsync(name);
                    } else {

                    }
                }
                // Cancel
                if (isHover(mx, my, (int) cx + 10, btnY, 200, 40)) {

                    state = GameState.WORLD_SELECT;
                }

                // Focus Click
                // Name Box
                if (isHover(mx, my, cx - 200, cy - 90, 400, 40))
                    isTypingSeed = false;
                // Seed Box
                if (isHover(mx, my, cx - 200, cy, 400, 40))
                    isTypingSeed = true;

            } else if (state == GameState.RENAME_WORLD) {
                // Rename
                if (isHover(mx, my, cx - 210, cy + 60, 200, 40)) {
                    String newName = nameBuffer.toString().trim();
                    if (!newName.isEmpty() && renameTarget != null) {
                        renameWorld(renameTarget, newName);
                        refreshWorldList();
                        state = GameState.WORLD_SELECT;
                        selectedWorldIndex = -1;
                    }
                }
                // Cancel
                if (isHover(mx, my, cx + 10, cy + 60, 200, 40)) {
                    state = GameState.WORLD_SELECT;
                }
            }
        }
    }

    // ==================== OPTIONS MENU ====================

    private void handleOptionsInput() {
        if (engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
            float scale = guiRenderer.getGuiScale();
            double mx = engine.getInput().getMouseX() / scale;
            double my = engine.getInput().getMouseY() / scale;

            float logicalW = config.windowWidth / scale;
            float logicalH = config.windowHeight / scale;
            float cx = logicalW / 2;
            float cy = logicalH / 2;

            // Back Button
            if (isHover(mx, my, 20, 20, 100, 40)) {
                state = previousState;
                return;
            }

            float startY = cy - 100;
            float x = cx - 200;
            float w = 400;
            float h = 30;
            float gap = 40;

            // Sliders Interaction
            // 1. FOV (30 to 120)
            if (handleSlider(mx, my, x, startY, w, h, 30, 120, config.fov)) {
                float newVal = calculateSliderValue(mx, x, w, 30, 120);
                config.fov = newVal;
                player.getCamera().setFov(newVal);
            }

            // 2. View Distance (2 to 32)
            if (handleSlider(mx, my, x, startY + gap, w, h, 2, 32, config.viewDistance)) {
                int newVal = (int) calculateSliderValue(mx, x, w, 2, 32);
                if (newVal != config.viewDistance) {
                    config.viewDistance = newVal;
                    if (world != null)
                        world.setViewDistance(newVal);
                    if (renderSettings != null)
                        renderSettings.setViewDistance(newVal);
                    // Update Fog
                    engine.getRenderer().setFogStart(0.4f); // Reset defaults or calc based on dist
                    engine.getRenderer().setFogEnd(0.85f);
                }
            }

            // 3. GUI Scale (1 to 4)
            if (handleSlider(mx, my, x, startY + gap * 2, w, h, 1, 4, guiRenderer.getGuiScale())) {
                int newVal = (int) calculateSliderValue(mx, x, w, 1, 4);
                if (newVal != guiRenderer.getGuiScale()) {
                    guiRenderer.setGuiScale(newVal);
                    // Re-setup UI to apply new scale
                    if (player != null) {
                        setupPlayerUI();
                    }
                }
            }

            // Toggles Interaction
            float toggleY = startY + gap * 3.5f;
            float col1 = cx - 200;
            float col2 = cx + 20;

            // VSync
            if (isHover(mx, my, col1, toggleY, 180, 40)) {
                engine.getWindow().setVSync(!config.vsync);
            }
            // Fog
            if (isHover(mx, my, col2, toggleY, 180, 40)) {
                engine.getRenderer().toggleFog();
            }

            toggleY += 50;
            // Debug Info
            if (isHover(mx, my, col1, toggleY, 180, 40)) {
                config.showDebugInfo = !config.showDebugInfo;
                renderSettings.toggleDebugInfo(); // Sync
            }
            // Chunk Borders
            if (isHover(mx, my, col2, toggleY, 180, 40)) {
                config.showChunkBorders = !config.showChunkBorders;
                renderSettings.toggleChunkBorders(); // Sync
            }

            toggleY += 50;
            // Wireframe
            if (isHover(mx, my, col1, toggleY, 180, 40)) {
                renderSettings.toggleWireframe();
            }

            // Controls button
            if (isHover(mx, my, col2, toggleY, 180, 40)) {
                // Don't change previousState - KEYBINDS always returns to OPTIONS
                state = GameState.KEYBINDS;
            }

        }
    }

    private boolean handleSlider(double mx, double my, float x, float y, float w, float h, float min, float max,
            float current) {
        return isHover(mx, my, x, y, w, h);
    }

    private float calculateSliderValue(double mx, float x, float w, float min, float max) {
        double percent = (mx - x) / w;
        percent = Math.max(0, Math.min(1, percent));
        return min + (float) (percent * (max - min));
    }

    // ==================== KEYBINDS MENU ====================

    private void handleKeybindsInput() {
        float scale = guiRenderer.getGuiScale();
        double mx = engine.getInput().getMouseX() / scale;
        double my = engine.getInput().getMouseY() / scale;

        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;

        // If editing a keybind, capture the next key press
        if (editingKeybind != null) {
            // Check all keys for press
            for (int key = 32; key <= 348; key++) { // GLFW key range
                if (engine.getInput().isKeyPressed(key)) {
                    // Check for ESC to cancel
                    if (key == GLFW_KEY_ESCAPE) {
                        editingKeybind = null;
                        return;
                    }
                    // Set the new key
                    editingKeybind.setKeyCode(key);
                    KeyBindings.getInstance().save(GameStorage.getKeybindsFile());
                    editingKeybind = null;
                    return;
                }
            }
            return; // Don't process other input while editing
        }

        // Handle mouse clicks
        if (engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
            // Back Button - always return to OPTIONS
            if (isHover(mx, my, 20, 20, 100, 40)) {
                state = GameState.OPTIONS;
                return;
            }

            // Reset All button
            if (isHover(mx, my, cx + 100, 20, 140, 40)) {
                KeyBindings.getInstance().resetAllDefaults();
                KeyBindings.getInstance().save(GameStorage.getKeybindsFile());
                return;
            }

            // Check keybind buttons
            float startY = 80;
            float rowHeight = 35;
            int rowIndex = 0;

            for (String category : KeyBindings.getInstance().getCategories()) {
                rowIndex++; // Category header

                for (engine.input.KeyBind bind : KeyBindings.getInstance().getBindingsByCategory(category)) {
                    float y = startY + (rowIndex - keybindScrollOffset) * rowHeight;

                    // Only process visible rows
                    if (y >= 60 && y < logicalH - 60) {
                        // Key button (right side)
                        float btnX = cx + 20;
                        float btnW = 100;
                        float btnH = 28;

                        if (isHover(mx, my, btnX, y, btnW, btnH)) {
                            editingKeybind = bind;
                            return;
                        }
                    }
                    rowIndex++;
                }
            }
        }

        // Handle scroll
        double scroll = engine.getInput().getScrollY();
        if (scroll != 0) {
            keybindScrollOffset -= (int) scroll;
            keybindScrollOffset = Math.max(0, keybindScrollOffset);
        }
    }

    private void drawKeybinds() {
        drawBackground();

        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;

        guiRenderer.renderText("CONTROLS", cx - 100, 20, 6.0f, 1, 1, 1, 1);

        // Back Button
        drawButton(20, 20, 100, 40, "Back");

        // Reset All button
        drawButton((int) (cx + 100), 20, 140, 40, "Reset All");

        float startY = 80;
        float rowHeight = 35;
        int rowIndex = 0;

        for (String category : KeyBindings.getInstance().getCategories()) {
            float y = startY + (rowIndex - keybindScrollOffset) * rowHeight;

            // Draw category header
            if (y >= 60 && y < logicalH - 20) {
                guiRenderer.renderText("--- " + category + " ---", cx - 120, y, 3.0f, 0.7f, 0.7f, 1.0f, 1.0f);
            }
            rowIndex++;

            for (engine.input.KeyBind bind : KeyBindings.getInstance().getBindingsByCategory(category)) {
                y = startY + (rowIndex - keybindScrollOffset) * rowHeight;

                // Only draw visible rows
                if (y >= 60 && y < logicalH - 20) {
                    // Bind name (left side)
                    guiRenderer.renderText(bind.getDisplayName(), cx - 180, y + 5, 2.5f, 1, 1, 1, 1);

                    // Key button (right side)
                    float btnX = cx + 20;
                    float btnW = 100;
                    float btnH = 28;

                    String keyText;
                    if (editingKeybind == bind) {
                        keyText = "> ... <";
                    } else {
                        keyText = "[ " + bind.getKeyName() + " ]";
                    }

                    // Draw button background
                    boolean isEditing = (editingKeybind == bind);
                    if (isEditing) {
                        guiRenderer.renderRect(btnX, y, btnW, btnH, 0.5f, 0.5f, 0.2f, 1f);
                    } else {
                        guiRenderer.renderRect(btnX, y, btnW, btnH, 0.3f, 0.3f, 0.3f, 1f);
                    }

                    // Center text in button
                    guiRenderer.renderText(keyText, btnX + 8, y + 6, 2.0f, 1, 1, 1, 1);
                }
                rowIndex++;
            }
        }
    }

    private void drawOptions() {
        drawBackground();

        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;
        float cy = logicalH / 2;

        guiRenderer.renderText("OPTIONS", cx - 100, 20, 6.0f, 1, 1, 1, 1);

        // Back Button
        drawButton(20, 20, 100, 40, "Back");

        float startY = cy - 100;
        float x = cx - 200;
        float w = 400;
        float h = 30;
        float gap = 40;

        // Sliders
        drawSlider(x, startY, w, h, 30, 120, config.fov, "FOV: " + (int) config.fov);
        drawSlider(x, startY + gap, w, h, 2, 32, config.viewDistance, "View Dist: " + config.viewDistance);
        drawSlider(x, startY + gap * 2, w, h, 1, 4, guiRenderer.getGuiScale(),
                "GUI Scale: " + guiRenderer.getGuiScale());

        // Toggles
        float toggleY = startY + gap * 3.5f;
        float col1 = cx - 200;
        float col2 = cx + 20;

        drawButton((int) col1, (int) toggleY, 180, 40, "VSync: " + (config.vsync ? "ON" : "OFF"));
        drawButton((int) col2, (int) toggleY, 180, 40, "Fog: " + (engine.getRenderer().isFogEnabled() ? "ON" : "OFF"));

        drawButton((int) col1, (int) (toggleY + 50), 180, 40, "Debug: " + (config.showDebugInfo ? "ON" : "OFF"));
        drawButton((int) col2, (int) (toggleY + 50), 180, 40, "Borders: " + (config.showChunkBorders ? "ON" : "OFF"));

        drawButton((int) col1, (int) (toggleY + 100), 180, 40,
                "Wireframe: " + (renderSettings.isWireframeMode() ? "ON" : "OFF"));
        drawButton((int) col2, (int) (toggleY + 100), 180, 40, "Controls");
    }

    private void drawSlider(float x, float y, float w, float h, float min, float max, float current, String label) {
        // Background
        guiRenderer.renderRect(x, y, w, h, 0.2f, 0.2f, 0.2f, 1f);

        // Fill
        float percent = (current - min) / (max - min);
        guiRenderer.renderRect(x, y, w * percent, h, 0.4f, 0.8f, 0.4f, 1f);

        // Border
        // guiRenderer.renderRect(x, y, w, h, 1, 1, 1, 0.2f); // Optional outline logic

        // Label (Centered)
        // Label (Centered)
        float textScale = 2.0f;
        float textW = getTextWidth(label, textScale);
        guiRenderer.renderText(label, x + w / 2 - textW / 2, y + 8, textScale, 1, 1, 1, 1);
    } // End drawOptions helpers

    private void drawLoadingScreen() {
        // Dark dim
        float scale = guiRenderer.getGuiScale();
        float w = config.windowWidth / scale;
        float h = config.windowHeight / scale;
        guiRenderer.renderRect(0, 0, w, h, 0.1f, 0.1f, 0.1f, 1f);

        float cx = w / 2;
        float cy = h / 2;

        String loadingText = loadingMessage != null ? loadingMessage : "Loading...";
        float textScale = 4.0f;
        float textW = getTextWidth(loadingText, textScale);
        guiRenderer.renderText(loadingText, cx - textW / 2, cy - 50, textScale, 1, 1, 1, 1);

        // Progress Bar
        float barW = 400;
        float barH = 20;
        float barX = cx - barW / 2;
        float barY = cy + 10;

        // Background
        guiRenderer.renderRect(barX, barY, barW, barH, 0.3f, 0.3f, 0.3f, 1);
        // Fill
        guiRenderer.renderRect(barX, barY, barW * loadingProgress, barH, 0.2f, 0.8f, 0.2f, 1);
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
            if (isHover(mx, my, cx - 150, cy - 60, 300, 50)) {
                state = GameState.PLAYING;
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
            }

            // Options
            if (isHover(mx, my, cx - 150, cy + 10, 300, 50)) {
                previousState = GameState.PAUSED;
                state = GameState.OPTIONS; // Switch to options
            }

            // Save & Quit
            if (isHover(mx, my, cx - 150, cy + 80, 300, 50)) {
                saveWorldAsync();
            }
        }

        if (engine.getInput().isKeyPressed(GLFW_KEY_ESCAPE)) {
            state = GameState.PLAYING;
            engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
        }
    }

    private void saveWorldAsync() {
        if (world == null) {
            state = GameState.MENU;
            return;
        }

        state = GameState.LOADING;
        loadingProgress = 0.0f;
        loadingMessage = "Saving World...";

        // We must copy references needed for background thread
        World worldRef = this.world;
        String nameRef = config.worldName;
        // WorldStorage needs to be created or we use a persistent one?
        // startWorld creates a new local one.. let's make a new one for now or use the
        // one from startWorld if it was a field.
        // It's not a field. Safe to create new one since it just finds directories.

        currentTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            engine.world.storage.WorldStorage storage = new engine.world.storage.WorldStorage(new java.io.File("."));

            // NOTE: WorldStorage.saveWorld hits world entities.
            // Accessing world entities from background thread while game loop is paused
            // (LOADING state) is SAFE
            // because update() is skipping world updates.

            storage.saveWorld(worldRef, nameRef, (progress) -> {
                this.loadingProgress = progress;
            });

        }).thenRunAsync(() -> {
            // Main Thread callback
            System.out.println("Save Complete. Cleaning up...");
            worldRef.cleanup();
            if (currentContainerGui != null) {
                currentContainerGui.cleanup();
                currentContainerGui = null;
            }
            state = GameState.MENU;
            world = null; // Detach
            engine.setWorld(null);
        }, this::runOnMainThread); // Ensure we switch states on main thread if needed
    }

    private void runOnMainThread(Runnable task) {
        mainThreadTasks.add(task);
    }

    private void startWorldAsync(String worldName) {
        state = GameState.LOADING;
        loadingProgress = 0.0f;
        loadingMessage = "Loading World...";

        currentTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            // 1. Load Data (Background)
            loadingMessage = "Reading Data...";
            loadingProgress = 0.1f;

            engine.world.storage.WorldStorage storage = new engine.world.storage.WorldStorage(new java.io.File("."));
            storage.prepareWorld(worldName);
            engine.world.item.nbt.NBTTagCompound levelDat = storage.loadLevelData();

            loadingProgress = 0.3f;

            // Pass data to Main Thread for World Creation
            runOnMainThread(() -> {
                config.worldName = worldName;

                // Cleanup old world if exists
                if (this.world != null)
                    this.world.cleanup();

                // Mouse is kept NORMAL during loading. Disabled ONLY when playing.

                long savedSeed = new java.util.Random().nextLong(); // Default seed
                if (levelDat != null && levelDat.hasKey("Seed")) {
                    savedSeed = levelDat.getLong("Seed");
                    System.out.println("[Game] Restored Seed from level.dat: " + savedSeed);
                }
                config.worldSeed = savedSeed;

                loadingMessage = "Generating Terrain...";
                this.world = new World(config);
                engine.setWorld(world);

                this.player = playerType.create();
                this.player.init(engine);
                this.world.setEntityManager(engine.getEntities());

                if (levelDat != null && levelDat.hasKey("Player")) {
                    System.out.println("[Game] Restoring player state from level.dat");
                    player.load(levelDat.getTag("Player"));
                    player.setPosition(player.getX(), player.getY() + 0.1f, player.getZ());
                    player.setVelocity(0, 0, 0);
                } else {
                    Vec3 spawn = world.findSpawnPosition();
                    player.setPosition(spawn.x, spawn.y + 2.0f, spawn.z);
                }

                setupPlayerUI(); // Extract UI setup to helper

                // Pre-load chunks around player
                loadingMessage = "Loading Chunks...";
                // We can't easily wait for chunks here without blocking Main Thread.
                // But World.update() will load them.
                // We could just finish here for now.
                state = GameState.PLAYING;
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
            });
        });
    }

    private void setupPlayerUI() {
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
        // HotbarGui expects logical dimensions
        this.hotbarGui = new HotbarGui(player.getInventory(), config.windowWidth / scale, config.windowHeight / scale);
        // PlayerInventoryGui handles scaling internally via setGuiScale
        this.inventoryGui = new PlayerInventoryGui(player, config.windowWidth, config.windowHeight);
        inventoryGui.setGuiScale(scale);

        this.renderInputHandler = new RenderInputHandler(this.renderSettings, world);
        this.breakProgressRenderer = new engine.rendering.BreakProgressRenderer();

        // GUI Editor
        this.guiEditor = new GuiEditorIntegration(config.windowWidth, config.windowHeight, guiRenderer);
        // When activated, load the player inventory for editing
        this.guiEditor.setOnActivate(() -> {
            guiEditor.editGui("game:player_inventory");
        });
    }

    private void refreshWorldList() {
        worldList.clear();
        java.io.File saves = GameStorage.getSavesDir();
        if (saves.exists() && saves.isDirectory()) {
            for (java.io.File f : saves.listFiles()) {
                if (f.isDirectory()) {
                    worldList.add(f.getName());
                }
            }
        }
    }

    private void deleteWorld(String name) {
        java.io.File worldDir = new java.io.File(GameStorage.getSavesDir(), name);
        deleteRecursive(worldDir);
    }

    private void deleteRecursive(java.io.File file) {
        if (file.isDirectory()) {
            for (java.io.File c : file.listFiles())
                deleteRecursive(c);
        }
        file.delete();
    }

    private void renameWorld(String oldName, String newName) {
        java.io.File oldDir = new java.io.File(GameStorage.getSavesDir(), oldName);
        java.io.File newDir = new java.io.File(GameStorage.getSavesDir(), newName);
        if (oldDir.exists() && !newDir.exists()) {
            oldDir.renameTo(newDir);
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

        // 1. Render 3D Debug (Wireframes) BEFORE GUI
        if (state == GameState.PLAYING) {
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

        // 2. Render GUI (Overlay)
        guiRenderer.begin();

        if (state == GameState.LOADING) {
            drawLoadingScreen();
            guiRenderer.end(); // IMPORTANT: End gui renderer for this frame
            return;
        }

        if (state == GameState.MENU) {
            drawMainMenu();
        } else if (state == GameState.WORLD_SELECT) {
            drawWorldSelect();
        } else if (state == GameState.CREATE_WORLD) {
            drawCreateWorld();
        } else if (state == GameState.RENAME_WORLD) {
            drawRenameWorld();
        } else if (state == GameState.OPTIONS) {
            drawOptions();
        } else if (state == GameState.KEYBINDS) {
            drawKeybinds();
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
                    if (inventoryGui.hasCursorItem()) {
                        inventoryGui.renderCursorItem(guiRenderer,
                                engine.getInput().getMouseX(),
                                engine.getInput().getMouseY());
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

        // 3. Debug Overlays (Text on top)
        if (state == GameState.PLAYING) {
            guiRenderer.begin();

            // Position Debug
            String posStr = String.format("Pos: %.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ());
            guiRenderer.renderText(posStr, 10, 10, 2.0f, 1, 1, 1, 1);

            // Chunk Debug
            int cx = (int) Math.floor(player.getX()) >> 4;
            int cz = (int) Math.floor(player.getZ()) >> 4;
            engine.world.Chunk c = world.getChunkIfLoaded(cx, cz);
            String chunkStr = "Chunk: " + (c == null ? "NULL" : c.getPhase().toString());
            guiRenderer.renderText(chunkStr, 10, 30, 2.0f, 1, 1, 1, 1);

            guiRenderer.end();
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
        float titleScale = 12.0f; // BIGGER
        float titleW = title.length() * (titleScale * 3.5f);
        guiRenderer.renderText(title, cx - titleW / 2, cy - 150, titleScale, 1, 1, 1, 1);

        drawButton((int) cx - 150, (int) cy - 25, 300, 50, "Singleplayer");
        drawButton((int) cx - 150, (int) cy + 50, 300, 50, "Quit Game");
        drawButton((int) cx - 150, (int) cy + 125, 300, 50, "Options");
    }

    private void drawWorldSelect() {
        drawBackground();

        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;

        guiRenderer.renderText("SELECT WORLD", cx - 180, 20, 6.0f, 1, 1, 1, 1);

        // Show Path
        String pathInfo = "Saves in: " + GameStorage.getSavesDir().getAbsolutePath();
        guiRenderer.renderText(pathInfo, cx - 300, 80, 2.0f, 0.7f, 0.7f, 0.7f, 1);

        // Back Button
        drawButton(20, 20, 100, 40, "Back");

        // World List
        int listY = 120;
        for (int i = 0; i < worldList.size(); i++) {
            boolean selected = (i == selectedWorldIndex);
            float y = listY + i * 50;

            // Background for item
            if (selected) {
                guiRenderer.renderRect(cx - 150, y, 300, 30, 0.3f, 0.3f, 0.3f, 1); // Highlight
            } else {
                guiRenderer.renderRect(cx - 150, y, 300, 30, 0.1f, 0.1f, 0.1f, 0.5f);
            }

            guiRenderer.renderText(worldList.get(i), cx - 140, y + 5, 2.0f, 1, 1, 1, 1);
        }

        // Bottom Controls
        int startX = (int) cx - 210;
        int btnY = (int) logicalH - 60;
        drawButton(startX, btnY, 140, 40, "Create New");

        if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
            drawButton((int) cx - 60, btnY, 80, 40, "Play");
            drawButton((int) cx + 30, btnY, 80, 40, "Delete");
            drawButton((int) cx + 120, btnY, 80, 40, "Rename");
        }

    }

    private long backspaceTimer = 0;
    private boolean backspaceAuth = false; // logic for initial press vs hold

    // Generic Text Input Handler
    private void handleTextInput() {
        StringBuilder target = isTypingSeed ? seedBuffer : nameBuffer;

        // Char input
        Character c;
        while ((c = engine.getInput().pollChar()) != null) {
            if (isTypingSeed && !Character.isDigit(c) && c != '-') {
                // Allow but maybe warn? For now allow all for hashing.
            }
            target.append(c);
        }

        // Backspace Logic (Repeat)
        boolean isBackspaceDown = engine.getInput().isKeyDown(GLFW_KEY_BACKSPACE);
        long now = System.currentTimeMillis();

        if (isBackspaceDown) {
            if (!backspaceAuth) {
                // First press
                if (target.length() > 0)
                    target.deleteCharAt(target.length() - 1);
                backspaceAuth = true;
                backspaceTimer = now + 400; // Wait 400ms before repeating
            } else if (now > backspaceTimer) {
                // Repeating
                if (target.length() > 0)
                    target.deleteCharAt(target.length() - 1);
                backspaceTimer = now + 50; // Repeat every 50ms
            }
        } else {
            backspaceAuth = false;
        }

        // Tab to toggle
        if (engine.getInput().isKeyPressed(GLFW_KEY_TAB)) {
            isTypingSeed = !isTypingSeed;
        }
    }

    private void drawCreateWorld() {
        drawBackground();

        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;
        float cy = logicalH / 2;

        String title = "CREATE NEW WORLD";
        float tScale = 6.0f;
        float tW = getTextWidth(title, tScale);
        guiRenderer.renderText(title, cx - tW / 2, 20, tScale, 1, 1, 1, 1);

        // Name Field
        guiRenderer.renderText("World Name:", cx - 200, cy - 120, 3.0f, 0.9f, 0.9f, 0.9f, 1);
        boolean nameFocus = !isTypingSeed;
        guiRenderer.renderRect(cx - 200, cy - 90, 400, 40, nameFocus ? 0.2f : 0.1f, nameFocus ? 0.2f : 0.1f,
                nameFocus ? 0.2f : 0.1f, 1);
        guiRenderer.renderText(nameBuffer.toString() + (nameFocus ? "_" : ""), cx - 190, cy - 80, 3.0f, 1, 1, 1, 1);

        // Seed Field
        guiRenderer.renderText("Seed (Optional):", cx - 200, cy - 30, 3.0f, 0.9f, 0.9f, 0.9f, 1);
        boolean seedFocus = isTypingSeed;
        guiRenderer.renderRect(cx - 200, cy, 400, 40, seedFocus ? 0.2f : 0.1f, seedFocus ? 0.2f : 0.1f,
                seedFocus ? 0.2f : 0.1f, 1);
        guiRenderer.renderText(seedBuffer.toString() + (seedFocus ? "_" : ""), cx - 190, cy + 10, 3.0f, 1, 1, 1, 1);

        // Confirm / Cancel
        // Note: Coordinates must match handleMenuInput
        int btnY = (int) cy + 120;
        drawButton((int) cx - 210, btnY, 200, 40, "Create");
        drawButton((int) cx + 10, btnY, 200, 40, "Cancel");
    }

    private void drawRenameWorld() {
        drawBackground();

        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;
        float cy = logicalH / 2;

        String title = "RENAME WORLD";
        float tScale = 6.0f;
        float tW = getTextWidth(title, tScale);
        guiRenderer.renderText(title, cx - tW / 2, 20, tScale, 1, 1, 1, 1);

        guiRenderer.renderText("New Name:", cx - 200, cy - 80, 3.0f, 0.9f, 0.9f, 0.9f, 1);
        guiRenderer.renderRect(cx - 200, cy - 50, 400, 40, 0.1f, 0.1f, 0.1f, 1);
        guiRenderer.renderText(nameBuffer.toString() + "_", cx - 190, cy - 40, 3.0f, 1, 1, 1, 1);

        drawButton((int) cx - 210, (int) cy + 60, 200, 40, "Rename");
        drawButton((int) cx + 10, (int) cy + 60, 200, 40, "Cancel");
    }

    private void drawPauseMenu() {
        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;

        guiRenderer.renderRect(0, 0, logicalW, logicalH, 0, 0, 0, 0.5f);

        float cx = logicalW / 2;
        float cy = logicalH / 2;

        String title = "PAUSED";
        float tScale = 9.0f;
        float tW = getTextWidth(title, tScale);
        guiRenderer.renderText(title, cx - tW / 2, cy - 150, tScale, 1, 1, 1, 1);

        drawButton((int) (cx - 150), (int) (cy - 60), 300, 50, "Resume Game");
        drawButton((int) (cx - 150), (int) cy + 10, 300, 50, "Options");
        drawButton((int) (cx - 150), (int) cy + 80, 300, 50, "Save and Quit");
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
        float size = 2.0f; // Scale 2.0 for buttons (approx 16px high)
        float textW = getTextWidth(text, size);
        float textX = x + (w - textW) / 2;
        float textY = y + (h - (8 * size / 8.0f)) / 2; // Approximation logic for Y centering: (h - height) / 2
        // Actually, just use h/2 - size*4?
        // Font height is 8 * (size/8) = size.
        // Wait, size parameter passed to renderText is treated as "approx height" in
        // current logical.
        // My renderText logic: scale = size / 8.0f.
        // So Height = 8 * scale = size.
        textY = y + (h - size) / 2;

        guiRenderer.renderText(text, textX, textY, size, 1, 1, 1, 1);
    }

    private float getTextWidth(String text, float size) {
        if (text == null || text.isEmpty())
            return 0;
        float scale = size / 8.0f;
        if (scale < 1.0f)
            scale = 1.0f;
        // Formula: (6 * N + 2) * scale
        return (6 * text.length() + 2) * scale;
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