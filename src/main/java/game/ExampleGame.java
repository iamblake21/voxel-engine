package game;

import engine.api.IGame;
import engine.core.Config;
import engine.core.Engine;
import engine.core.RenderInputHandler;
import engine.rendering.RenderSettings;
import engine.entity.EntityType;
import engine.entity.EntityTypes;
import engine.entity.Player;
import engine.rendering.Renderer;
import engine.ui.GuiRenderer;
import engine.ui.HotbarGui;
import engine.ui.InventoryGui;
import engine.ui.InventoryInteractionManager;
import engine.ui.editor.GuiEditorIntegration; // <-- NUOVO IMPORT
import engine.world.World;
import game.init.GameInit;
import static org.lwjgl.glfw.GLFW.*;

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

    // GUI Editor <-- NUOVO
    private GuiEditorIntegration guiEditor;

    private engine.rendering.BreakProgressRenderer breakProgressRenderer;

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
                        .persistent(true)
                        .summonable(true)
                        .build());

        System.out.println("[Game] Entity types registered (player)");
    }

    @Override
    public void init(Engine engine) {
        this.engine = engine;

        engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);

        this.world = new World(config);
        engine.setWorld(world);

        this.player = playerType.create();
        this.player.init(engine);
        engine.getEntities().addEntity(player);

        // Add starting items to player inventory for testing
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.DIRT, 64));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.STONE, 64));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.WOOD, 64));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.TORCH, 16));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.WOODEN_PICKAXE, 1));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.WOODEN_SHOVEL, 1));
        System.out.println("[Game] Player inventory initialized with starting items");

        // Setup render input handler
        RenderSettings settings = new RenderSettings();
        settings.setViewDistance(config.viewDistance);
        this.renderInputHandler = new RenderInputHandler(settings, world);

        // ============================================================
        // SETUP GUI - ORDINE CORRETTO
        // ============================================================

        // 1. Crea GuiRenderer
        this.guiRenderer = new GuiRenderer(config.windowWidth, config.windowHeight);
        guiRenderer.setAtlasTexture(engine.getRenderer().getAtlasTexture());

        int scale = guiRenderer.getGuiScale();

        // 2. Crea HotbarGui (usa dimensioni LOGICHE perché non estende TexturedGui)
        this.hotbarGui = new HotbarGui(player.getInventory(),
                config.windowWidth / scale,
                config.windowHeight / scale);

        // 3. Crea InventoryGui con dimensioni RAW (TexturedGui fa la divisione)
        this.inventoryGui = new InventoryGui(player.getInventory(),
                config.windowWidth, // <-- RAW, non diviso!
                config.windowHeight); // <-- RAW, non diviso!
        inventoryGui.setGuiScale(scale);

        // 4. Crea GUI Editor
        this.guiEditor = new GuiEditorIntegration(config.windowWidth, config.windowHeight, guiRenderer);

        // 5. Altri componenti
        this.inventoryInteraction = new InventoryInteractionManager(player.getInventory());
        this.breakProgressRenderer = new engine.rendering.BreakProgressRenderer();

        System.out.println("[Game] GUI initialized with scale=" + scale);
        System.out.println("[Game] Init complete");
    }

    @Override
    public void update(float deltaTime) {
        if (world == null || player == null)
            return;

        // Process render settings input (view distance, frustum toggle, etc.)
        renderInputHandler.processInput(engine.getWindow().getHandle());

        // ============================================================
        // GUI EDITOR (F7) - DEVE ESSERE PRIMA DELL'INVENTARIO!
        // ============================================================
        guiEditor.update(engine.getInput(),
                engine.getInput().getMouseX(),
                engine.getInput().getMouseY());

        // Se l'editor è attivo, blocca tutto il resto
        if (guiEditor.isEditorActive()) {
            // L'editor gestisce il cursore internamente
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            return; // Skip tutto il resto
        }

        // ============================================================
        // INVENTARIO (E key)
        // ============================================================
        boolean eDown = engine.getInput().isKeyDown(GLFW_KEY_E);
        if (eDown && !eKeyLatch) {
            boolean wasOpen = inventoryOpen;
            inventoryOpen = !inventoryOpen;
            eKeyLatch = true;

            if (inventoryOpen) {
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            } else {
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
                engine.getInput().resetMouseDelta();

                if (wasOpen) {
                    inventoryInteraction.dropCursorToInventory();
                }
            }
        }
        if (!eDown) {
            eKeyLatch = false;
        }

        // Handle mouse wheel for hotbar selection (only when inventory closed)
        if (!inventoryOpen) {
            double scrollY = engine.getInput().getScrollY();
            if (scrollY != 0) {
                inventoryInteraction.handleMouseWheel(scrollY);
            }
        }

        // Handle inventory interaction when open
        if (inventoryOpen) {
            inventoryGui.handleInput(
                    engine.getInput(),
                    inventoryInteraction,
                    engine.getInput().getMouseX(),
                    engine.getInput().getMouseY(),
                    config.windowHeight);
        }

        // Pass false to disable player input if inventory is open
        player.update(deltaTime, engine.getInput(), !inventoryOpen);

        // Don't handle block interaction if inventory is open
        if (!inventoryOpen) {
            player.handleBlockInteraction(engine.getInput(), deltaTime);
        }
    }

    @Override
    public void render(Renderer renderer) {
        guiRenderer.begin();

        // ============================================================
        // SE EDITOR ATTIVO, RENDERIZZA SOLO QUELLO
        // ============================================================
        if (guiEditor != null && guiEditor.isEditorActive()) {
            guiEditor.getEditor().render(guiRenderer);
            guiRenderer.end();
            return; // Non renderizzare altro
        }

        // ============================================================
        // RENDER NORMALE
        // ============================================================

        // Always render hotbar
        hotbarGui.render(guiRenderer);

        // Render full inventory if open
        if (inventoryOpen) {
            inventoryGui.render(guiRenderer);

            // Render cursor item if holding one
            if (inventoryInteraction.hasCursorItem()) {
                inventoryGui.renderCursorItem(guiRenderer,
                        inventoryInteraction.getCursorStack(),
                        engine.getInput().getMouseX(),
                        engine.getInput().getMouseY());
            }
        }

        guiRenderer.end();

        // Render break progress (3D overlay)
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        engine.rendering.Renderer ren = engine.getRenderer();
        ren.setCamera(player.getCamera());
        breakProgressRenderer.render(player.getMiningManager(), player.getCamera(), world);
    }

    @Override
    public void cleanup() {
        if (guiRenderer != null)
            guiRenderer.cleanup();
        if (guiEditor != null)
            guiEditor.cleanup();
    }
}