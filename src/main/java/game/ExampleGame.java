package game;

import engine.api.IGame;
import engine.core.Config;
import engine.core.Engine;
import engine.core.RenderInputHandler; // <-- Aggiungi import
import engine.rendering.RenderSettings; // <-- Aggiungi import
import engine.entity.EntityType;
import engine.entity.EntityTypes;
import engine.entity.Player;
import engine.rendering.Renderer;
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

        System.out.println("[Game] Init complete");
    }

    @Override
    public void update(float deltaTime) {
        if (world == null || player == null)
            return;

        // Process render settings input (view distance, frustum toggle, etc.)
        renderInputHandler.processInput(engine.getWindow().getHandle()); // <-- Aggiungi

        player.update(deltaTime, engine.getInput());
        player.handleBlockInteraction(engine.getInput());
    }

    @Override
    public void render(Renderer renderer) {
        // HUD, crosshair, debug info...
    }

    @Override
    public void cleanup() {
    }
}