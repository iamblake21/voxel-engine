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
import game.init.GameEntities;
import game.init.GameInit;
import static org.lwjgl.glfw.GLFW.*;
import engine.world.blockentity.ContainerBlockEntity;
import engine.ui.ContainerGui;
import engine.world.blockentity.ContainerBlockEntity;
import engine.world.gen.StructureLoader;

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

    public ExampleGame(Config config) {
        this.config = config;
    }

    public static void main(String[] args) {
        Config config = Config.builder()
                .windowSize(1280, 720)
                .viewDistance(24)
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
                        .properties(EntityProperties.create()
                                .humanoid()
                                .texture("textures/entity/villager.png"))
                        .build());

        // Load structures
        StructureLoader.loadStructures();

        System.out.println("[Game] Entity types registered");
    }

    private RenderSettings renderSettings;
    private engine.rendering.WireframeRenderer wireframeRenderer;

    @Override
    public void init(Engine engine) {
        this.engine = engine;

        engine.getWindow().setCursorMode(GLFW_CURSOR_DISABLED);

        this.world = new World(config);
        engine.setWorld(world);

        // 1. Setup Player
        this.player = playerType.create();
        this.player.init(engine);

        player.getInteractionManager().setGuiHandler((p, blockEntity) -> {
            if (blockEntity instanceof ContainerBlockEntity container) {
                ContainerGui gui = (ContainerGui) container.createGui(p, config.windowWidth, config.windowHeight);
                gui.setGuiScale(guiRenderer.getGuiScale());
                this.currentContainerGui = gui;
                this.currentContainer = container;
                this.inventoryOpen = true;
                engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            }
        });

        // CRUCIALE: Diciamo all'Engine che questo è il player principale
        // Così l'EntityRenderer dell'engine saprà quale camera usare!
        engine.getEntities().setPlayer(player);
        engine.getEntities().addEntity(player);

        NpcEntity villager = GameEntities.VILLAGER.create();
        villager.setPosition(player.getX() + 3, player.getY() + 10, player.getZ() + 3);
        engine.getEntities().addEntity(villager);
        // Setup Inventario Player (Items di test)
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.DIRT, 64));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.STONE, 64));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.WOODEN_PICKAXE, 1));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.CHEST, 2));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.TORCH, 34));
        player.getInventory().addItem(new engine.world.item.ItemStack(game.init.GameItems.DOOR, 1));

        // Setup render input handler (Using Field)
        this.renderSettings = new RenderSettings();
        this.renderSettings.setViewDistance(config.viewDistance);
        this.renderInputHandler = new RenderInputHandler(this.renderSettings, world);

        // Setup Wireframe Renderer
        this.wireframeRenderer = new engine.rendering.WireframeRenderer();

        // Setup GUI
        this.guiRenderer = new GuiRenderer(config.windowWidth, config.windowHeight);
        guiRenderer.setAtlasTexture(engine.getRenderer().getAtlasTexture());
        int scale = guiRenderer.getGuiScale();

        this.hotbarGui = new HotbarGui(player.getInventory(), config.windowWidth / scale, config.windowHeight / scale);
        this.inventoryGui = new InventoryGui(player.getInventory(), config.windowWidth, config.windowHeight);
        inventoryGui.setGuiScale(scale);
        this.guiEditor = new GuiEditorIntegration(config.windowWidth, config.windowHeight, guiRenderer);
        this.guiEditor.setOnActivate(() -> {
            engine.ui.definition.GuiDefinition syncDef = null;
            if (currentContainerGui != null) {
                syncDef = currentContainerGui.getDefinition();
            } else if (inventoryOpen && inventoryGui != null) {
                syncDef = inventoryGui.getDefinition();
            }

            if (syncDef != null) {
                guiEditor.getEditor().loadDefinition(syncDef);
                // Auto-hide background if overlaying a live GUI
                // guiEditor.getEditor().setBackgroundVisible(false); // Method not added yet,
                // manual toggle is fine
            } else {
                // No active GUI to sync with, create a new blank one or keep existing
                // If the current one is just the default placeholder, maybe reset it?
                // For now, let's reset to a "New GUI" if we are opening in the void.
                // guiEditor.getEditor().newDefinition("new_gui", 176, 166);
                // Actually, user might want to continue editing what they had.
                // Let's print a message.
                System.out.println("[Game] Editor opened without active GUI to sync.");
            }
        });

        this.inventoryInteraction = new InventoryInteractionManager(player.getInventory());
        this.breakProgressRenderer = new engine.rendering.BreakProgressRenderer();

        System.out.println("[Game] Init complete");
    }

    @Override
    public void update(float deltaTime) {
        if (world == null || player == null)
            return;

        renderInputHandler.processInput(engine.getWindow().getHandle());

        // NOTA: Non chiamiamo più entityManager.update(deltaTime) qui!
        // Ci pensa Engine.java a chiamare engine.getEntities().update()

        // GUI Editor Update
        guiEditor.update(engine.getInput(), engine.getInput().getMouseX(), engine.getInput().getMouseY());
        if (guiEditor.isEditorActive()) {
            engine.getWindow().setCursorMode(GLFW_CURSOR_NORMAL);
            return;
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

        // Update player logic (movimento etc)
        player.update(deltaTime, engine.getInput(), !inventoryOpen);
    }

    @Override
    public void render3D(engine.rendering.Renderer renderer, float partialTick) {
        if (player == null || world == null)
            return;

        // Render 3D overlays before Hand Pass
        breakProgressRenderer.render(player.getMiningManager(), player.getCamera(), world);

        // Also render selection box here so it's behind hand?
        // Currently selection box is in render() (lines 232-243), meaning it's AFTER
        // hand too?
        // Usually selection box should BEHIND hand. Let's move it too?
        // The user only complained about Cracks, but I should fix both ideally.
        // Let's stick to Cracks first as requested.
    }

    @Override
    public void render(Renderer renderer) {
        // Draw Selection Box (Wireframe) - Keep here for now unless needed
        // Perform raycast locally to know what to highlight
        engine.interaction.RaycastResult target = player.getInteractionManager().performRaycast(player, world,
                engine.getEntities());
        if (target != null && target.isBlock()) {
            engine.world.BlockPos pos = target.getBlockPos();
            // Draw black outline with slight transparency
            wireframeRenderer.draw(player.getCamera().getProjectionMatrix(),
                    player.getCamera().getViewMatrix(),
                    pos.getX(), pos.getY(), pos.getZ(),
                    0, 0, 0, 0.4f);
        }

        guiRenderer.begin();

        hotbarGui.render(guiRenderer);

        if (inventoryOpen) {
            if (currentContainerGui != null) {
                // Container GUI (chest, furnace)
                currentContainerGui.render(guiRenderer);
                if (currentContainerGui.hasCursorItem()) {
                    currentContainerGui.renderCursorItem(guiRenderer,
                            engine.getInput().getMouseX(),
                            engine.getInput().getMouseY());
                }
            } else {
                // Normal inventory
                inventoryGui.render(guiRenderer);
                if (inventoryInteraction.hasCursorItem()) {
                    inventoryGui.renderCursorItem(guiRenderer, inventoryInteraction.getCursorStack(),
                            engine.getInput().getMouseX(), engine.getInput().getMouseY());
                }
            }
        }

        if (guiEditor != null && guiEditor.isEditorActive()) {
            guiEditor.getEditor().render(guiRenderer);
        }

        guiRenderer.end();
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