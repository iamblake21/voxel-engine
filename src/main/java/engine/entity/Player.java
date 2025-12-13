package engine.entity;

import engine.core.Config;
import engine.core.Engine;
import engine.entity.inventory.PlayerInventory;
import engine.rendering.Camera;
import engine.world.World;
import engine.world.block.Blocks;
import engine.world.item.IUsableItem;
import engine.world.item.ItemStack;
import engine.physics.PhysicsEngine;
import engine.window.InputManager;
import engine.utils.Math3D.Vec3;
import static org.lwjgl.glfw.GLFW.*;
import engine.interaction.InteractionManager;

public class Player extends Entity {
    private final Config config;
    private final Camera camera;
    private final PlayerInventory inventory;
    private final engine.mechanics.MiningManager miningManager = new engine.mechanics.MiningManager();

    private World world;
    private PhysicsEngine physics;

    private boolean flying = false;
    private boolean flyKeyLatch = false;

    private InteractionManager interactionManager;
    private EntityManager entityManager;

    // Click cooldowns per evitare spam
    private boolean rightClickLatch = false;

    public Player(EntityType<?> type, Config config) {
        super(type);
        this.config = config;
        this.camera = new Camera(config);
        this.inventory = new PlayerInventory();
        this.setSize(0.6f, 1.8f);
    }

    @Override
    public void init(Engine engine) {
        this.world = engine.getWorld();
        this.physics = engine.getPhysics();

        engine.getRenderer().setCamera(camera);

        if (world != null) {
            Vec3 spawn = world.findSpawnPosition();
            setPosition(spawn.x, spawn.y + 1, spawn.z);
        }
        updateCamera();

        this.entityManager = engine.getEntityManager();
        this.interactionManager = new InteractionManager();

        // Setup GUI handler (collega al tuo sistema GUI)
        interactionManager.setGuiHandler((player, blockEntity) -> {
            // Questo sarà gestito dal game layer
            // Per ora logga
            System.out.println("[Player] Opening GUI for: " + blockEntity);
        });

    }

    @Override
    public void update(float deltaTime) {
    }

    public void update(float deltaTime, InputManager input, boolean inputEnabled) {
        if (world == null)
            return;

        // 1. INPUT: Definisce VX e VZ
        if (inputEnabled) {
            handleInput(input, deltaTime);
        } else {
            this.vx = 0;
            this.vz = 0;
            if (flying)
                this.vy = 0;
        }

        // 2. STATO: Definisce gravità
        if (flying) {
            this.hasGravity = false;
            this.onGround = false;
        } else {
            this.hasGravity = true;
        }

        // 3. FISICA: Delega all'engine universale
        if (physics != null) {
            physics.processEntity(this, world, deltaTime);
        }

        updateCamera();
        world.maintainChunks(x, z);
    }

    private void handleInput(InputManager input, float deltaTime) {
        // Mouse Look
        float sensitivity = 0.1f;
        yaw += input.getMouseDX() * sensitivity;
        pitch += input.getMouseDY() * sensitivity;
        pitch = Math.max(-89f, Math.min(89f, pitch));

        // Speed settings (Originali)
        float speed = onGround ? config.playerSpeed : 4.0f;
        if (flying)
            speed = config.playerFlySpeed; // Velocità fissa in volo

        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT) && !flying) {
            speed *= config.playerSprintMultiplier;
        }

        // Direzione
        float yawRad = (float) Math.toRadians(yaw);
        float fx = (float) Math.cos(yawRad);
        float fz = (float) Math.sin(yawRad);
        float rx = -fz;
        float rz = fx;

        float mx = 0, mz = 0;
        if (input.isKeyDown(GLFW_KEY_W)) {
            mx += fx;
            mz += fz;
        }
        if (input.isKeyDown(GLFW_KEY_S)) {
            mx -= fx;
            mz -= fz;
        }
        if (input.isKeyDown(GLFW_KEY_D)) {
            mx += rx;
            mz += rz;
        }
        if (input.isKeyDown(GLFW_KEY_A)) {
            mx -= rx;
            mz -= rz;
        }

        float len = (float) Math.sqrt(mx * mx + mz * mz);
        if (len > 0) {
            mx /= len;
            mz /= len;
        }

        // ASSEGNAZIONE DIRETTA (Restituisce il feeling "Snappy")
        // Se non premo tasti, mx/mz sono 0, quindi vx/vz diventano 0 immediatamente.
        this.vx = mx * speed;
        this.vz = mz * speed;

        // Salto e Volo
        if (flying) {
            float vyInput = 0;
            if (input.isKeyDown(GLFW_KEY_SPACE))
                vyInput += speed;
            if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT))
                vyInput -= speed;
            this.vy = vyInput;
        } else {
            if (onGround && input.isKeyDown(GLFW_KEY_SPACE)) {
                this.vy = config.jumpForce;
                this.onGround = false;
            }
        }

        // Toggle Volo
        boolean fDown = input.isKeyDown(GLFW_KEY_F);
        if (fDown && !flyKeyLatch) {
            flying = !flying;
            flyKeyLatch = true;
            if (flying)
                this.vy = 0;
        }
        if (!fDown)
            flyKeyLatch = false;

        // Hotbar
        for (int i = 0; i < 9; i++) {
            if (input.isKeyDown(GLFW_KEY_1 + i))
                inventory.setSelectedSlot(i);
        }
    }

    private void updateCamera() {
        camera.setPosition(x, y + config.playerEyeHeight, z);
        camera.setRotation(pitch, yaw);
        camera.update(0f);
        if (world != null) {
            world.updateCamera(
                    camera.getProjectionMatrix().toArray(),
                    camera.getViewMatrix().toArray(),
                    camera.getForward());
        }
    }

    public void handleInteraction(InputManager input, float deltaTime) {
        if (world == null)
            return;

        boolean breakBlock = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_1);
        boolean useItem = input.isMouseButtonDown(GLFW_MOUSE_BUTTON_2);
        boolean useItemPressed = input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_2);

        // Left Click: Mining/Attack
        if (breakBlock) {
            handleLeftClick(input, deltaTime);
        } else {
            miningManager.resetBreaking();
        }

        // Right Click: Use/Interact
        if (useItemPressed && !rightClickLatch) {
            rightClickLatch = true;
            handleRightClick();
        }
        if (!useItem) {
            rightClickLatch = false;
        }
    }

    private void handleLeftClick(InputManager input, float deltaTime) {
        // First check for entity attack
        if (interactionManager != null && entityManager != null) {
            // For now, just do block mining
            // Entity attack can be added later
        }

        // Block mining (existing raycast logic)
        ItemStack selectedStack = inventory.getSelectedStack();
        float eyeX = x, eyeY = y + config.playerEyeHeight, eyeZ = z;
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float dirX = (float) (Math.cos(pitchRad) * Math.cos(yawRad));
        float dirY = (float) (Math.sin(pitchRad));
        float dirZ = (float) (Math.cos(pitchRad) * Math.sin(yawRad));
        float maxDist = 6.0f, step = 0.05f, dist = 0f;

        while (dist <= maxDist) {
            float cx = eyeX + dirX * dist;
            float cy = eyeY + dirY * dist;
            float cz = eyeZ + dirZ * dist;
            int bx = (int) Math.floor(cx);
            int by = (int) Math.floor(cy);
            int bz = (int) Math.floor(cz);
            int blockId = world.getBlock(bx, by, bz);

            if (!Blocks.isAir(blockId) && !Blocks.isLiquid(blockId)) {
                miningManager.processMining(this, world, bx, by, bz, deltaTime);
                return;
            }
            dist += step;
        }
        miningManager.resetBreaking();
    }

    private void handleRightClick() {
        if (interactionManager == null || entityManager == null) {
            // Fallback to old system
            return;
        }

        // Use the new interaction manager
        boolean handled = interactionManager.handleRightClick(this, world, entityManager);

        if (!handled) {
            // Nothing was interacted with
        }
    }

    // ==================== GETTER ====================

    public InteractionManager getInteractionManager() {
        return interactionManager;
    }

    public Camera getCamera() {
        return camera;
    }

    public boolean isFlying() {
        return flying;
    }

    public PlayerInventory getInventory() {
        return inventory;
    }

    public engine.mechanics.MiningManager getMiningManager() {
        return miningManager;
    }
}