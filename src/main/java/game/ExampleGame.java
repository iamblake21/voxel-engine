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
    private boolean inventoryOpen = false;
    private boolean eKeyLatch = false; // Latch for E key to prevent flickering

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
        System.out.println("[Game] Player inventory initialized with starting items");

        // Setup render input handler
        RenderSettings settings = new RenderSettings();
        settings.setViewDistance(config.viewDistance); // Sync con config
        this.renderInputHandler = new RenderInputHandler(settings, world);

        // Setup GUI renderer
        this.guiRenderer = new GuiRenderer(config.windowWidth, config.windowHeight);

        // Pass atlas texture to GUI renderer so we can use it
        guiRenderer.setAtlasTexture(engine.getRenderer().getAtlasTexture());

        // Use GUI scale factor from renderer to setup layout
        // This ensures the GUI Layout Logic matches the Scaled Projection Matrix
        int scale = guiRenderer.getGuiScale();

        this.hotbarGui = new HotbarGui(player.getInventory(), config.windowWidth / scale, config.windowHeight / scale);
        this.inventoryGui = new InventoryGui(player.getInventory(), config.windowWidth / scale,
                config.windowHeight / scale);

        System.out.println("[Game] GUI initialized:");
        System.out.println("  - GuiRenderer created (Scale=" + scale + ")");
        System.out.println("  - HotbarGui created using scaled sizes: " + (config.windowWidth / scale) + "x"
                + (config.windowHeight / scale));
        System.out.println("  - InventoryGui created using scaled sizes: " + (config.windowWidth / scale) + "x"
                + (config.windowHeight / scale));
        System.out.println("  - Atlas texture: " + (engine.getRenderer().getAtlasTexture() != null ? "OK" : "NULL"));

        System.out.println("[Game] Init complete");
    }

    private int countNonEmptySlots(engine.entity.inventory.PlayerInventory inv, int start, int count) {
        int nonEmpty = 0;
        for (int i = start; i < start + count; i++) {
            if (!inv.getStack(i).isEmpty())
                nonEmpty++;
        }
        return nonEmpty;
    }

    @Override
    public void update(float deltaTime) {
        if (world == null || player == null)
            return;

        // Process render settings input (view distance, frustum toggle, etc.)
        renderInputHandler.processInput(engine.getWindow().getHandle());

        // Toggle inventory with E key (with latch to prevent flickering)
        boolean eDown = engine.getInput().isKeyDown(GLFW_KEY_E);
        if (eDown && !eKeyLatch) {
            inventoryOpen = !inventoryOpen;
            eKeyLatch = true;
            // System.out.println("[Game] Inventory " + (inventoryOpen ? "OPENED" :
            // "CLOSED"));
            if (inventoryOpen) {
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            } else {
                engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);
            }
        }
        if (!eDown) {
            eKeyLatch = false;
        }

        player.update(deltaTime, engine.getInput());

        // Don't handle block interaction if inventory is open
        if (!inventoryOpen) {
            player.handleBlockInteraction(engine.getInput());
        }
    }

    @Override
    public void render(Renderer renderer) {
        // HUD, crosshair, debug info...
        guiRenderer.begin();

        // Always render hotbar
        hotbarGui.render(guiRenderer);

        // Render full inventory if open
        if (inventoryOpen) {
            inventoryGui.render(guiRenderer);
        }

        // TODO: Render crosshair if inventory not open

        guiRenderer.end();
    }

    @Override
    public void cleanup() {
        if (guiRenderer != null) {
            guiRenderer.cleanup();
        }
    }
}