package game;

import static engine.world.block.Blocks.get;

import engine.api.IGame;
import engine.core.Config;
import engine.core.Engine;
import engine.core.RenderInputHandler;
import engine.input.KeyBindings;
import engine.rendering.RenderSettings;
import engine.entity.EntityType;
import engine.entity.EntityTypes;
import engine.entity.EntityProperties;

import engine.entity.Player;
import engine.rendering.Renderer;
import engine.ui.ContainerGui;
import engine.ui.GuiRenderer;
import engine.ui.HotbarGui;
import game.ui.PlayerInventoryGui;
import engine.ui.editor.GuiEditorIntegration;
import engine.world.World;
import engine.world.WorldMemoryStats;
import engine.world.Chunk;
import engine.world.block.Block;
import engine.world.biome.Biome;
import engine.world.BlockPos;
import engine.interaction.RaycastResult;
import game.init.GameInit;
import game.input.GameKeyBinds;
import static org.lwjgl.glfw.GLFW.*;
import engine.world.blockentity.ContainerBlockEntity;
import engine.world.gen.StructureLoader;
import engine.utils.Math3D.Vec3;
import engine.utils.GameStorage;

// COMMANDS
import engine.command.CommandManager;
import game.command.GiveCommand;
import game.ui.ChatGui;
import engine.ui.widget.GuiSlider;
import engine.ui.widget.GuiTextBox;
import engine.ui.widget.GuiScrollableList;

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

    // UI Widgets
    private GuiSlider fovSlider;
    private GuiSlider viewDistanceSlider;
    private GuiSlider guiScaleSlider;
    private GuiTextBox worldNameInput;
    private GuiTextBox worldSeedInput;
    private GuiScrollableList worldListWidget;

    // CHAT & COMMANDS
    private CommandManager commandManager;
    private ChatGui chatGui;
    private boolean tKeyLatch = false;

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
        CHAT,
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

    // FPS tracking for debug screen
    private long lastFpsTime = System.nanoTime();
    private int fpsFrameCount = 0;
    private int lastFps = 0;

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
            guiRenderer.setGuiScale(config.guiScale); // Ensure config value persists
        }

        // Re-init menu widgets for new size
        initMenuWidgets();

        // Re-setup UI if player exists
        if (player != null) {
            // Re-creating UI components to respect new scale/size
            setupPlayerUI();

            // Re-create ChatGUI to respect scale (handled in render usually, but good to
            // refresh)
            if (chatGui == null) {
                chatGui = new ChatGui(commandManager, player);
            }
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

        StructureLoader.loadStructures();

        System.out.println("[Game] Entity types registered");
    }

    @Override
    public void init(Engine engine) {
        this.engine = engine;

        // Init basic systems
        engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);

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

        // Initialize Command System
        this.commandManager = new CommandManager();
        // Commands are registered when world loads (so we have 'world' instance)
        System.out.println("[Game] Command System Initialized");

        initMenuWidgets();

        System.out.println("[Game] Engine Init complete. Waiting in Main Menu.");
    }

    private void startWorld(String worldName) {
        startWorldAsync(worldName);
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
            if (state != GameState.PLAYING && state != GameState.CHAT) {
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            }
            return;
        }

        // CHAT STATE
        if (state == GameState.CHAT) {
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            if (chatGui != null) {
                chatGui.handleInput(engine.getInput());
                if (!chatGui.isOpen()) {
                    state = GameState.PLAYING;
                    engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
                    engine.getInput().resetMouseDelta();
                }
            }
            return;
        }

        if (world == null || player == null)
            return;

        // PLAYING STATE

        if (GameKeyBinds.MENU.isPressed()) {
            state = GameState.PAUSED;
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            return;
        }

        // Check Chat Open
        boolean tDown = GameKeyBinds.CHAT.isPressed();
        if (tDown && !inventoryOpen) {
            state = GameState.CHAT;
            if (chatGui != null) {
                chatGui.setOpen(true);
            }
            return;
        }

        // Toggle Debug
        if (GameKeyBinds.DEBUG_INFO.isPressed()) {
            config.showDebugInfo = !config.showDebugInfo;
            renderSettings.toggleDebugInfo();
        }

        if (guiEditor != null) {
            guiEditor.update(engine.getInput(), engine.getInput().getMouseX(), engine.getInput().getMouseY());
            if (guiEditor.isEditorActive()) {
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
                return;
            }
        }

        if (renderInputHandler != null) {
            renderInputHandler.processInput(engine.getWindow().getHandle());
        }

        boolean eDown = GameKeyBinds.INVENTORY.isDown();
        if (eDown && !eKeyLatch) {
            eKeyLatch = true;
            if (inventoryOpen) {
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
                inventoryOpen = true;
                currentContainerGui = null;
                currentContainer = null;
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            }
        }
        if (!eDown)
            eKeyLatch = false;

        if (!inventoryOpen) {
            double scrollY = engine.getInput().getScrollY();
            if (scrollY != 0) {
                player.getInventory()
                        .setSelectedSlot((player.getInventory().getSelectedSlot() - (int) scrollY + 9) % 9);
            }
            player.handleInteraction(engine.getInput(), deltaTime);
        } else {
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

        int cx = (int) Math.floor(player.getX()) >> 4;
        int cz = (int) Math.floor(player.getZ()) >> 4;
        Chunk chunk = world.getChunkIfLoaded(cx, cz);
        if (chunk == null || chunk.getPhase() == Chunk.Phase.EMPTY) {
            player.setVelocity(0, 0, 0);
        } else {
            player.update(deltaTime, engine.getInput(), !inventoryOpen);
        }
    }

    private void handleMenuInput() {
        if (state == GameState.CREATE_WORLD) {
            float scale = guiRenderer.getGuiScale();
            double mx = engine.getInput().getMouseX() / scale;
            double my = engine.getInput().getMouseY() / scale;
            boolean mousePressed = engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1);

            worldNameInput.input(engine.getInput(), mx, my, mousePressed);
            worldSeedInput.input(engine.getInput(), mx, my, mousePressed);

            // Buttons
            if (mousePressed) {
                float w = config.windowWidth / scale;
                float h = config.windowHeight / scale;
                float cx = w / 2;
                float cy = h / 2;
                int btnY = (int) cy + 120;

                if (isHover(mx, my, cx - 210, btnY, 200, 40)) {
                    // Create
                    String name = worldNameInput.getText().trim();
                    if (!name.isEmpty()) {
                        String seedStr = worldSeedInput.getText().trim();
                        long seed = seedStr.isEmpty() ? new java.util.Random().nextLong() : seedStr.hashCode();
                        try {
                            seed = Long.parseLong(seedStr);
                        } catch (NumberFormatException ignored) {
                        }

                        config.worldSeed = seed;
                        startWorldAsync(name);
                    }
                }
                if (isHover(mx, my, cx + 10, btnY, 200, 40)) {
                    state = GameState.WORLD_SELECT;
                    engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
                }
            }
            return;
        }

        if (state == GameState.RENAME_WORLD) {
            handleTextInput(); // Legacy/Simple fallback for now or update later
            if (engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
                float scale = guiRenderer.getGuiScale();
                double mx = engine.getInput().getMouseX() / scale;
                double my = engine.getInput().getMouseY() / scale;
                float cx = (config.windowWidth / scale) / 2;
                float cy = (config.windowHeight / scale) / 2;
                if (isHover(mx, my, cx - 210, cy + 60, 200, 40)) {
                    String newName = nameBuffer.toString().trim(); // Use legacy nameBuffer for rename currently
                    if (!newName.isEmpty() && renameTarget != null) {
                        renameWorld(renameTarget, newName);
                        refreshWorldList();
                        worldListWidget.setItems(worldList);
                        state = GameState.WORLD_SELECT;
                        selectedWorldIndex = -1;
                    }
                }
                if (isHover(mx, my, cx + 10, cy + 60, 200, 40)) {
                    state = GameState.WORLD_SELECT;
                }
            }
            return;
        }

        if (state == GameState.OPTIONS) {
            handleOptionsInput();
            return;
        }

        if (state == GameState.KEYBINDS) {
            handleKeybindsInput();
            return;
        }

        boolean mousePressed = engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1);
        if (mousePressed) {
            float scale = guiRenderer.getGuiScale();
            double mx = engine.getInput().getMouseX() / scale;
            double my = engine.getInput().getMouseY() / scale;
            float logicalW = config.windowWidth / scale;
            float logicalH = config.windowHeight / scale;
            float cx = logicalW / 2;
            float cy = logicalH / 2;

            if (state == GameState.MENU) {
                if (isHover(mx, my, cx - 150, cy - 25, 300, 50)) {
                    refreshWorldList();
                    worldListWidget.setItems(worldList);
                    state = GameState.WORLD_SELECT;
                    selectedWorldIndex = -1;
                }
                if (isHover(mx, my, cx - 150, cy + 50, 300, 50)) {
                    engine.shutdown();
                }
                if (isHover(mx, my, cx - 150, cy + 125, 300, 50)) {
                    previousState = GameState.MENU;
                    state = GameState.OPTIONS;
                }
            } else if (state == GameState.WORLD_SELECT) {
                if (isHover(mx, my, 20, 20, 100, 40)) {
                    state = GameState.MENU;
                }

                int startX = (int) cx - 210;
                int btnY = (int) logicalH - 60;
                if (isHover(mx, my, startX, btnY, 140, 40)) {
                    state = GameState.CREATE_WORLD;
                    worldNameInput.setText("New World");
                    worldSeedInput.setText("");
                }

                if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
                    String selectedName = worldList.get(selectedWorldIndex);
                    if (isHover(mx, my, cx - 60, btnY, 80, 40)) {
                        startWorldAsync(selectedName);
                    }
                    if (isHover(mx, my, cx + 30, btnY, 80, 40)) {
                        deleteWorld(selectedName);
                        refreshWorldList();
                        worldListWidget.setItems(worldList);
                        selectedWorldIndex = -1;
                    }
                    if (isHover(mx, my, cx + 120, btnY, 80, 40)) {
                        state = GameState.RENAME_WORLD;
                        renameTarget = selectedName;
                        nameBuffer.setLength(0);
                        nameBuffer.append(selectedName);
                        isTypingSeed = false;
                    }
                }
            }
        }

        // Widget Input (Scrollable List)
        if (state == GameState.WORLD_SELECT) {
            float scale = guiRenderer.getGuiScale();
            worldListWidget.input(engine.getInput(), engine.getInput().getMouseX() / scale,
                    engine.getInput().getMouseY() / scale, mousePressed);
        }
    }

    private void handleOptionsInput() {
        float scale = guiRenderer.getGuiScale();
        double mx = engine.getInput().getMouseX() / scale;
        double my = engine.getInput().getMouseY() / scale;
        boolean mouseDown = engine.getInput().isMouseButtonDown(GLFW_MOUSE_BUTTON_1);
        boolean mousePressed = engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1);

        // Sliders
        fovSlider.input(mx, my, mouseDown);
        viewDistanceSlider.input(mx, my, mouseDown);
        guiScaleSlider.input(mx, my, mouseDown);

        // Buttons
        if (mousePressed) {
            float cx = (config.windowWidth / scale) / 2;
            float cy = (config.windowHeight / scale) / 2;

            if (isHover(mx, my, 20, 20, 100, 40)) {
                state = previousState;
                return;
            }

            float toggleY = cy - 100 + (40 * 3.5f);
            float col1 = cx - 200;
            float col2 = cx + 20;

            if (isHover(mx, my, col1, toggleY, 180, 40)) {
                engine.getWindow().setVSync(!config.vsync);
            }
            if (isHover(mx, my, col2, toggleY, 180, 40)) {
                engine.getRenderer().toggleFog();
            }

            toggleY += 50;
            if (isHover(mx, my, col1, toggleY, 180, 40)) {
                config.showDebugInfo = !config.showDebugInfo;
                renderSettings.toggleDebugInfo();
            }
            if (isHover(mx, my, col2, toggleY, 180, 40)) {
                config.showChunkBorders = !config.showChunkBorders;
                renderSettings.toggleChunkBorders();
            }

            toggleY += 50;
            if (isHover(mx, my, col1, toggleY, 180, 40)) {
                renderSettings.toggleWireframe();
            }
            if (isHover(mx, my, col2, toggleY, 180, 40)) {
                state = GameState.KEYBINDS;
            }
        }
    }

    private void handleKeybindsInput() {
        float scale = guiRenderer.getGuiScale();
        double mx = engine.getInput().getMouseX() / scale;
        double my = engine.getInput().getMouseY() / scale;
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;

        if (editingKeybind != null) {
            for (int key = 32; key <= 348; key++) {
                if (engine.getInput().isKeyPressed(key)) {
                    if (key == GLFW_KEY_ESCAPE) {
                        editingKeybind = null;
                        return;
                    }
                    editingKeybind.setKeyCode(key);
                    KeyBindings.getInstance().save(GameStorage.getKeybindsFile());
                    editingKeybind = null;
                    return;
                }
            }
            return;
        }

        if (engine.getInput().isMouseButtonPressed(GLFW_MOUSE_BUTTON_1)) {
            if (isHover(mx, my, 20, 20, 100, 40)) {
                state = GameState.OPTIONS;
                return;
            }
            if (isHover(mx, my, cx + 100, 20, 140, 40)) {
                KeyBindings.getInstance().resetAllDefaults();
                KeyBindings.getInstance().save(GameStorage.getKeybindsFile());
                return;
            }

            float startY = 80;
            float rowHeight = 35;
            int rowIndex = 0;
            for (String category : KeyBindings.getInstance().getCategories()) {
                rowIndex++;
                for (engine.input.KeyBind bind : KeyBindings.getInstance().getBindingsByCategory(category)) {
                    float y = startY + (rowIndex - keybindScrollOffset) * rowHeight;
                    if (y >= 60 && y < logicalH - 60) {
                        float btnX = cx + 20;
                        if (isHover(mx, my, btnX, y, 100, 28)) {
                            editingKeybind = bind;
                            return;
                        }
                    }
                    rowIndex++;
                }
            }
        }
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
        drawButton(20, 20, 100, 40, "Back");
        drawButton((int) (cx + 100), 20, 140, 40, "Reset All");
        float startY = 80;
        float rowHeight = 35;
        int rowIndex = 0;
        for (String category : KeyBindings.getInstance().getCategories()) {
            float y = startY + (rowIndex - keybindScrollOffset) * rowHeight;
            if (y >= 60 && y < logicalH - 20) {
                guiRenderer.renderText("--- " + category + " ---", cx - 120, y, 3.0f, 0.7f, 0.7f, 1.0f, 1.0f);
            }
            rowIndex++;
            for (engine.input.KeyBind bind : KeyBindings.getInstance().getBindingsByCategory(category)) {
                y = startY + (rowIndex - keybindScrollOffset) * rowHeight;
                if (y >= 60 && y < logicalH - 20) {
                    guiRenderer.renderText(bind.getDisplayName(), cx - 180, y + 5, 2.5f, 1, 1, 1, 1);
                    float btnX = cx + 20;
                    float btnW = 100;
                    float btnH = 28;
                    String keyText = (editingKeybind == bind) ? "> ... <" : "[ " + bind.getKeyName() + " ]";
                    boolean isEditing = (editingKeybind == bind);
                    guiRenderer.renderRect(btnX, y, btnW, btnH, isEditing ? 0.5f : 0.3f, isEditing ? 0.5f : 0.3f,
                            isEditing ? 0.2f : 0.3f, 1f);
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
        drawButton(20, 20, 100, 40, "Back");

        // Render Sliders
        fovSlider.render(guiRenderer);
        viewDistanceSlider.render(guiRenderer);
        guiScaleSlider.render(guiRenderer);

        float startY = cy - 100;
        float gap = 40;
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

    private void drawLoadingScreen() {
        float scale = guiRenderer.getGuiScale();
        float w = config.windowWidth / scale;
        float h = config.windowHeight / scale;
        guiRenderer.renderRect(0, 0, w, h, 0.1f, 0.1f, 0.1f, 1f);
        float cx = w / 2;
        float cy = h / 2;
        String loadingText = loadingMessage != null ? loadingMessage : "Loading...";
        float textW = getTextWidth(loadingText, 4.0f);
        guiRenderer.renderText(loadingText, cx - textW / 2, cy - 50, 4.0f, 1, 1, 1, 1);
        float barW = 400;
        float barH = 20;
        float barX = cx - barW / 2;
        float barY = cy + 10;
        guiRenderer.renderRect(barX, barY, barW, barH, 0.3f, 0.3f, 0.3f, 1);
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
            if (isHover(mx, my, cx - 150, cy - 60, 300, 50)) {
                state = GameState.PLAYING;
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
            }
            if (isHover(mx, my, cx - 150, cy + 10, 300, 50)) {
                previousState = GameState.PAUSED;
                state = GameState.OPTIONS;
            }
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
        World worldRef = this.world;
        String nameRef = config.worldName;
        currentTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            engine.world.storage.WorldStorage storage = new engine.world.storage.WorldStorage(new java.io.File("."));
            storage.saveWorld(worldRef, nameRef, (progress) -> {
                this.loadingProgress = progress;
            });
        }).thenRunAsync(() -> {
            System.out.println("Save Complete. Cleaning up...");
            worldRef.cleanup();
            if (currentContainerGui != null) {
                currentContainerGui.cleanup();
                currentContainerGui = null;
            }
            state = GameState.MENU;
            world = null;
            engine.setWorld(null);
        }, this::runOnMainThread);
    }

    private void runOnMainThread(Runnable task) {
        mainThreadTasks.add(task);
    }

    private void startWorldAsync(String worldName) {
        state = GameState.LOADING;
        loadingProgress = 0.0f;
        loadingMessage = "Loading World...";
        currentTask = java.util.concurrent.CompletableFuture.runAsync(() -> {
            loadingMessage = "Reading Data...";
            loadingProgress = 0.1f;
            engine.world.storage.WorldStorage storage = new engine.world.storage.WorldStorage(new java.io.File("."));
            storage.prepareWorld(worldName);
            engine.world.item.nbt.NBTTagCompound levelDat = storage.loadLevelData();
            loadingProgress = 0.3f;
            runOnMainThread(() -> {
                config.worldName = worldName;
                if (this.world != null)
                    this.world.cleanup();
                long savedSeed = new java.util.Random().nextLong();
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

                // Register Commands with World instance
                game.init.GameCommands.register(commandManager, world);

                if (levelDat != null && levelDat.hasKey("Player")) {
                    System.out.println("[Game] Restoring player state from level.dat");
                    player.load(levelDat.getTag("Player"));
                    player.setPosition(player.getX(), player.getY() + 0.1f, player.getZ());
                    player.setVelocity(0, 0, 0);
                } else {
                    Vec3 spawn = world.findSpawnPosition();
                    player.setPosition(spawn.x, spawn.y + 2.0f, spawn.z);
                }
                setupPlayerUI();
                chatGui = new ChatGui(commandManager, player);
                loadingMessage = "Loading Chunks...";
                state = GameState.PLAYING;
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
            });
        });
    }

    private void initMenuWidgets() {
        if (guiRenderer == null)
            return;
        float scale = guiRenderer.getGuiScale();
        float w = config.windowWidth / scale;
        float h = config.windowHeight / scale;
        float cx = w / 2;
        float cy = h / 2;

        // Options Sliders
        float slWidth = 400;
        float slHeight = 30;
        float slX = cx - 200;
        float slY = cy - 100;
        float gap = 40;

        fovSlider = new GuiSlider((int) slX, (int) slY, (int) slWidth, (int) slHeight, 30, 120, config.fov,
                "FOV: " + (int) config.fov);
        fovSlider.setOnValueChange(val -> {
            config.fov = val;
            fovSlider.setLabel("FOV: " + (int) val.floatValue());
            if (player != null)
                player.getCamera().setFov(val);
        });

        viewDistanceSlider = new GuiSlider((int) slX, (int) (slY + gap), (int) slWidth, (int) slHeight, 2, 32,
                config.viewDistance, "View Dist: " + config.viewDistance);
        viewDistanceSlider.setOnValueChange(val -> {
            int iVal = (int) val.floatValue();
            viewDistanceSlider.setValue(iVal);
            viewDistanceSlider.setLabel("View Dist: " + iVal);
            if (iVal != config.viewDistance) {
                config.viewDistance = iVal;
                if (world != null)
                    world.setViewDistance(iVal);
                if (renderSettings != null)
                    renderSettings.setViewDistance(iVal);
                engine.getRenderer().setFogStart(0.4f);
                engine.getRenderer().setFogEnd(0.85f);
            }
        });

        guiScaleSlider = new GuiSlider((int) slX, (int) (slY + gap * 2), (int) slWidth, (int) slHeight, 1, 4,
                guiRenderer.getGuiScale(), "GUI Scale: " + guiRenderer.getGuiScale());
        guiScaleSlider.setOnValueChange(val -> {
            int iVal = (int) val.floatValue();
            guiScaleSlider.setValue(iVal);
            guiScaleSlider.setLabel("GUI Scale: " + iVal);
            if (iVal != guiRenderer.getGuiScale()) {
                guiRenderer.setGuiScale(iVal);
                config.guiScale = iVal; // Store in config if needed
                initMenuWidgets();

                if (player != null) {
                    setupPlayerUI();
                    chatGui = new ChatGui(commandManager, player);
                }
            }
        });

        // Create World Inputs
        this.worldNameInput = new GuiTextBox((int) (cx - 200), (int) (cy - 90), 400, 40, "New World");
        this.worldNameInput.setPlaceholder("World Name");
        this.worldNameInput.setTextScale(3.5f);

        this.worldSeedInput = new GuiTextBox((int) (cx - 200), (int) cy, 400, 40, "");
        this.worldSeedInput.setPlaceholder("Seed (Optional)");
        this.worldSeedInput.setNumericOnly(true);
        this.worldSeedInput.setTextScale(3.5f);

        // World List
        this.worldListWidget = new GuiScrollableList((int) (cx - 200), 120, 400, (int) (h - 220));
        refreshWorldList();
        System.out.println("Initialized World List with " + worldList.size() + " items.");
        this.worldListWidget.setItems(worldList);
        this.worldListWidget.setOnSelect(index -> {
            selectedWorldIndex = index;
        });
    }

    // UI Helpers match original methods (condensed logic, identical functionality)
    private void setupPlayerUI() {
        player.getInteractionManager().setGuiHandler((p, provider) -> {
            ContainerGui gui = provider.createGui(p, config.windowWidth, config.windowHeight);
            if (gui != null) {
                gui.setGuiScale(guiRenderer.getGuiScale());
                this.currentContainerGui = gui;
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
        int scale = guiRenderer.getGuiScale();
        this.hotbarGui = new HotbarGui(player.getInventory(), config.windowWidth / scale, config.windowHeight / scale);
        // PlayerInventoryGui handles scaling internally via setGuiScale
        this.inventoryGui = new PlayerInventoryGui(player, config.windowWidth, config.windowHeight);
        inventoryGui.setGuiScale(scale);
        this.renderInputHandler = new RenderInputHandler(this.renderSettings, world);
        this.breakProgressRenderer = new engine.rendering.BreakProgressRenderer();
        this.guiEditor = new GuiEditorIntegration(config.windowWidth, config.windowHeight, guiRenderer);
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
        if (oldDir.exists() && !newDir.exists())
            oldDir.renameTo(newDir);
    }

    private boolean isHover(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void handleTextInput() { // Simplified
        StringBuilder target = isTypingSeed ? seedBuffer : nameBuffer;
        Character c;
        while ((c = engine.getInput().pollChar()) != null) {
            target.append(c);
        }
        if (engine.getInput().isKeyPressed(GLFW_KEY_BACKSPACE)) {
            if (target.length() > 0)
                target.deleteCharAt(target.length() - 1);
        }
        if (engine.getInput().isKeyPressed(GLFW_KEY_TAB)) {
            isTypingSeed = !isTypingSeed;
        }
    }

    private void drawButton(int x, int y, int w, int h, String text) {
        float textScale = 2.0f;
        float textW = getTextWidth(text, textScale);
        float scale = guiRenderer.getGuiScale();
        double mx = engine.getInput().getMouseX() / scale;
        double my = engine.getInput().getMouseY() / scale;
        boolean hover = isHover(mx, my, x, y, w, h);
        if (hover) {
            guiRenderer.renderRect(x, y, w, h, 0.4f, 0.4f, 0.8f, 1f);
        } else {
            guiRenderer.renderRect(x, y, w, h, 0.3f, 0.3f, 0.3f, 1f);
        }
        guiRenderer.renderText(text, x + w / 2 - textW / 2, y + h / 2 - 8, textScale, 1, 1, 1, 1);
    }

    private float getTextWidth(String text, float scale) {
        return text.length() * (scale * 3.5f);
    }

    @Override
    public void render3D(engine.rendering.Renderer renderer, float partialTick) {
        if (state != GameState.PLAYING && state != GameState.PAUSED && state != GameState.CHAT)
            return;
        if (player == null || world == null)
            return;
        breakProgressRenderer.render(player.getMiningManager(), player.getCamera(), world);
    }

    @Override
    public void render(Renderer renderer) {

        if (state == GameState.PLAYING || state == GameState.CHAT) {
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

        guiRenderer.begin();

        if (state == GameState.LOADING) {
            drawLoadingScreen();
            guiRenderer.end();
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
        } else if (state == GameState.PLAYING || state == GameState.PAUSED || state == GameState.CHAT) {
            hotbarGui.render(guiRenderer);

            if (inventoryOpen) {
                if (currentContainerGui != null) {
                    currentContainerGui.render(guiRenderer);
                    currentContainerGui.renderTooltipAndCursor(guiRenderer,
                            engine.getInput().getMouseX(),
                            engine.getInput().getMouseY());
                } else {
                    inventoryGui.render(guiRenderer);
                    inventoryGui.renderTooltipAndCursor(guiRenderer,
                            engine.getInput().getMouseX(),
                            engine.getInput().getMouseY());
                }
            }

            if (state == GameState.PAUSED) {
                drawPauseMenu();
            }

            if (chatGui != null) {
                chatGui.render(guiRenderer, config.windowWidth, config.windowHeight);
            }
        }

        if (guiEditor != null && guiEditor.isEditorActive()) {
            guiEditor.getEditor().render(guiRenderer);
        }

        guiRenderer.end();

        if ((state == GameState.PLAYING || state == GameState.CHAT) && renderSettings.isShowDebugInfo()) {
            guiRenderer.begin();
            renderDebugScreen();
            guiRenderer.end();
        }
    }

    // ---------------- HELPER RESTORATION ----------------

    private void renderDebugScreen() {
        updateFPS();
        float lineHeight = 12f;
        float y = 10f;
        float textSize = 2.5f;

        // Header: Title + FPS
        guiRenderer.renderText("Voxel Engine (FPS: " + lastFps + ")", 10, y, textSize, 1, 1, 1, 1);
        y += lineHeight * 1.5f;

        // Player Position
        String posStr = String.format("XYZ: %.1f / %.1f / %.1f",
                player.getX(), player.getY(), player.getZ());
        guiRenderer.renderText(posStr, 10, y, textSize, 1, 1, 1, 1);
        y += lineHeight;

        // Chunk Info
        int cx = (int) Math.floor(player.getX()) >> 4;
        int cz = (int) Math.floor(player.getZ()) >> 4;
        engine.world.Chunk chunk = world.getChunkIfLoaded(cx, cz);
        String chunkPhase = (chunk == null) ? "Unloaded" : chunk.getPhase().toString();
        String chunkStr = String.format("Chunk: %d, %d (%s)", cx, cz, chunkPhase);
        guiRenderer.renderText(chunkStr, 10, y, textSize, 1, 1, 1, 1);
        y += lineHeight;

        // Biome
        int bx = (int) Math.floor(player.getX());
        int bz = (int) Math.floor(player.getZ());
        try {
            Biome biome = world.getBiome(bx, bz);
            String biomeName = "Unknown";
            if (biome != null && biome.getRegistryId() != null) {
                String path = biome.getRegistryId().getPath();
                biomeName = Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
            guiRenderer.renderText("Biome: " + biomeName, 10, y, textSize, 1, 1, 1, 1);
        } catch (Exception e) {
            guiRenderer.renderText("Biome: Error", 10, y, textSize, 1, 1, 1, 1);
        }
        y += lineHeight;

        // Facing direction
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        String direction = getCardinalDirection(yaw);
        String facingStr = String.format("Facing: %s (%.1f\u00b0, %.1f\u00b0)", direction, yaw, pitch);
        guiRenderer.renderText(facingStr, 10, y, textSize, 1, 1, 1, 1);
        y += lineHeight * 1.5f;

        // Targeted Block Info
        RaycastResult target = player.getInteractionManager().performRaycast(
                player, world, engine.getEntities());

        if (target != null && target.isBlock()) {
            BlockPos pos = target.getBlockPos();
            int blockId = world.getBlock(pos.getX(), pos.getY(), pos.getZ());
            Block block = get(blockId);

            String posBlockStr = String.format("Looking at: [%d, %d, %d]",
                    pos.getX(), pos.getY(), pos.getZ());
            guiRenderer.renderText(posBlockStr, 10, y, textSize, 0.8f, 0.8f, 1, 1);
            y += lineHeight;

            // Block name
            String blockName = getBlockName(block);
            guiRenderer.renderText("  Block: " + blockName, 10, y, textSize, 0.8f, 0.8f, 1, 1);
            y += lineHeight;

            // Light levels
            try {
                BlockPos lightPos = target.getPlacePos();
                int skyLight = getLightLevel(lightPos, true);
                int blockLight = getLightLevel(lightPos, false);
                int totalLight = Math.max(skyLight, blockLight);

                String lightStr = String.format("  Light: %d (Sky: %d, Block: %d)",
                        totalLight, skyLight, blockLight);
                guiRenderer.renderText(lightStr, 10, y, textSize, 0.8f, 0.8f, 1, 1);
            } catch (Exception e) {
                guiRenderer.renderText("  Light: Error - " + e.getMessage(), 10, y, textSize, 0.8f, 0.8f, 1, 1);
            }
            y += lineHeight * 1.5f;
        }

        // Entity count
        int entityCount = engine.getEntities().getEntityCount();
        guiRenderer.renderText("Entities: " + entityCount, 10, y, textSize, 1, 1, 1, 1);
        y += lineHeight;

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMB = runtime.maxMemory() / 1024 / 1024;
        int percent = (int) ((usedMB * 100) / maxMB);
        String memStr = String.format("Memory: %d MB / %d MB (%d%%)", usedMB, maxMB, percent);
        guiRenderer.renderText(memStr, 10, y, textSize, 1, 1, 1, 1);
        y += lineHeight;

        if (world != null) {
            WorldMemoryStats stats = world.getMemoryStats();
            String chunkMemStr = String.format(
                    "Chunks: %d/%d pending %d | Sections: %d alloc / %d mesh",
                    stats.loadedChunks,
                    stats.maxResidentChunks,
                    stats.pendingChunks,
                    stats.allocatedSections,
                    stats.meshedSections);
            guiRenderer.renderText(chunkMemStr, 10, y, textSize, 1, 1, 1, 1);
            y += lineHeight;

            String budgetStr = String.format(
                    "Budgets: safe %d unload %d sectionMesh %d/%d",
                    stats.safeRadius,
                    stats.unloadRadius,
                    stats.meshedSections,
                    stats.maxResidentSectionMeshes);
            guiRenderer.renderText(budgetStr, 10, y, textSize, 1, 1, 1, 1);
            y += lineHeight;

            String estimateStr = String.format(
                    "Est: sections %d MB | VBO %d MB | queues T/L/M %d/%d/%d W%d",
                    stats.estimatedSectionMB(),
                    stats.estimatedVboMB(),
                    stats.terrainQueueSize,
                    stats.lightQueueSize,
                    stats.meshQueueSize,
                    stats.workerCount);
            guiRenderer.renderText(estimateStr, 10, y, textSize, 1, 1, 1, 1);
        }
    }

    private void updateFPS() {
        fpsFrameCount++;
        long currentTime = System.nanoTime();
        if (currentTime - lastFpsTime >= 1_000_000_000L) {
            lastFps = fpsFrameCount;
            fpsFrameCount = 0;
            lastFpsTime = currentTime;
        }
    }

    private String getCardinalDirection(float yaw) {
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw >= 337.5f || yaw < 22.5f)
            return "North";
        if (yaw >= 22.5f && yaw < 67.5f)
            return "NE";
        if (yaw >= 67.5f && yaw < 112.5f)
            return "East";
        if (yaw >= 112.5f && yaw < 157.5f)
            return "SE";
        if (yaw >= 157.5f && yaw < 202.5f)
            return "South";
        if (yaw >= 202.5f && yaw < 247.5f)
            return "SW";
        if (yaw >= 247.5f && yaw < 292.5f)
            return "West";
        return "NW";
    }

    private String getBlockName(engine.world.block.Block block) {
        if (block == null)
            return "Air";
        engine.registry.ResourceLocation id = block.getRegistryId();
        if (id != null) {
            String name = id.getPath();
            String[] parts = name.split("_");
            StringBuilder formatted = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    formatted.append(Character.toUpperCase(part.charAt(0)));
                    formatted.append(part.substring(1));
                    formatted.append(" ");
                }
            }
            return formatted.toString().trim();
        }
        return block.getClass().getSimpleName();
    }

    private int getLightLevel(engine.world.BlockPos pos, boolean sky) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        engine.world.Chunk chunk = world.getChunkIfLoaded(cx, cz);
        if (chunk == null)
            return 0;
        int lx = pos.getX() & 15;
        int ly = pos.getY();
        int lz = pos.getZ() & 15;
        if (sky) {
            return chunk.getSkyLight(lx, ly, lz);
        } else {
            int packed = chunk.getBlockLight(lx, ly, lz);
            int r = (packed >> 8) & 0xF;
            int g = (packed >> 4) & 0xF;
            int b = packed & 0xF;
            return Math.max(r, Math.max(g, b));
        }
    }

    private void drawBackground() {
        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float size = 32;
        int cols = (int) (logicalW / size) + 1;
        int rows = (int) (logicalH / size) + 1;
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                guiRenderer.renderBlockFlat(x * size, y * size, size, game.init.GameBlocks.DIRT);
            }
        }
        guiRenderer.renderRect(0, 0, logicalW, logicalH, 0, 0, 0, 0.6f);
    }

    private void drawMainMenu() {
        drawBackground();
        float scale = guiRenderer.getGuiScale();
        float logicalW = config.windowWidth / scale;
        float logicalH = config.windowHeight / scale;
        float cx = logicalW / 2;
        float cy = logicalH / 2;
        String title = "VOXEL ENGINE";
        float titleScale = 12.0f;
        float titleW = getTextWidth(title, titleScale);
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
        String pathInfo = "Saves in: " + GameStorage.getSavesDir().getAbsolutePath();
        guiRenderer.renderText(pathInfo, cx - 300, 80, 2.0f, 0.7f, 0.7f, 0.7f, 1);
        drawButton(20, 20, 100, 40, "Back");

        // Render List Widget
        worldListWidget.render(guiRenderer);

        int startX = (int) cx - 210;
        int btnY = (int) logicalH - 60;
        drawButton(startX, btnY, 140, 40, "Create New");

        // Re-checking the previous code: it checked worldList.size().
        // I should just check selectedWorldIndex validity.
        if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
            drawButton((int) cx - 60, btnY, 80, 40, "Play");
            drawButton((int) cx + 30, btnY, 80, 40, "Delete");
            drawButton((int) cx + 120, btnY, 80, 40, "Rename");
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

        guiRenderer.renderText("World Name:", cx - 200, cy - 120, 3.0f, 0.9f, 0.9f, 0.9f, 1);
        worldNameInput.render(guiRenderer);

        guiRenderer.renderText("Seed (Optional):", cx - 200, cy - 30, 3.0f, 0.9f, 0.9f, 0.9f, 1);
        worldSeedInput.render(guiRenderer);

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
        drawButton((int) (cx - 150), (int) (cy + 10), 300, 50, "Options");
        drawButton((int) (cx - 150), (int) (cy + 80), 300, 50, "Save & Quit");
    }

    @Override
    public void cleanup() {
        if (guiRenderer != null)
            guiRenderer.cleanup();
        if (chatGui != null) {
            // ChatGui doesn't strictly need cleanup yet but good practice
        }
        if (world != null)
            world.cleanup();
    }
}
