package engine.entity;

import engine.core.Config;
import engine.core.Engine;
import engine.entity.inventory.PlayerInventory;
import engine.rendering.Camera;
import engine.world.block.Blocks;
import engine.physics.PhysicsEngine;
import engine.window.InputManager;
import engine.utils.Math3D.Vec3;
import game.input.GameKeyBinds;
import engine.interaction.InteractionManager;
import engine.world.item.nbt.NBTTagCompound;
import static org.lwjgl.glfw.GLFW.*;

public class Player extends LivingEntity {
    private final Config config;
    private final Camera camera;
    private final PlayerInventory inventory;
    private final engine.mechanics.MiningManager miningManager = new engine.mechanics.MiningManager();

    private boolean flying = false;
    private boolean flyKeyLatch = false;
    private boolean thirdPerson = false;
    private boolean viewKeyLatch = false;

    private PhysicsEngine physics;

    // Remove redundant props fields (LivingEntity handles them, or use specific
    // ones)
    // Actually keep them if Player needs overrides or specific tracking,
    // but typically LivingEntity reads from properties.
    // However, Player logic uses these fields directly currently.
    // Let's keep them for now to avoid breaking getters/setters, but sync with
    // properties.
    private String modelPath = "models/entity/player.geo.json";
    private String skinPath = "textures/entity/steve.png";

    private InteractionManager interactionManager;
    private EntityManager entityManager;

    private boolean rightClickLatch = false;

    public Player(EntityType<?> type, Config config) {
        super(type);
        this.config = config;
        this.camera = new Camera(config);
        this.inventory = new PlayerInventory();
        // setSize is handled by EntityType usually, but good to ensure
        this.setSize(0.6f, 1.8f);

        // Load specific Player properties (Model/Skin)
        // LivingEntity loads MaxHealth/Speed etc.
        if (type.getProperties() != null) {
            if (type.getProperties().getModelPath() != null)
                this.modelPath = type.getProperties().getModelPath();
            if (type.getProperties().getTexturePath() != null)
                this.skinPath = type.getProperties().getTexturePath();
        }
    }

    @Override
    public void init(Engine engine) {
        this.world = engine.getWorld();
        // LivingEntity fields
        this.world = engine.getWorld();

        // Physics logic delegated to engine usually, but we keep reference
        this.physics = engine.getPhysics();

        engine.getRenderer().setCamera(camera);

        if (world != null) {
            Vec3 spawn = world.findSpawnPosition();
            setPosition(spawn.x, spawn.y + 1, spawn.z);
        }

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

        // CRITICAL: Save previous state for interpolation
        preTick();

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
        // 3. FISICA: Delega all'engine universale
        if (physics != null) {
            physics.processEntity(this, world, deltaTime);
        }

        // 4. ANIMAZIONE (From LivingEntity)
        updateAnimation(deltaTime);

        // Chunk maintainance for Player only
        if (world != null) {
            world.maintainChunks(x, z);
        }
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

        if (GameKeyBinds.SPRINT.isDown() && !flying) {
            speed *= config.playerSprintMultiplier;
        }

        // Direzione
        float yawRad = (float) Math.toRadians(yaw);
        float fx = (float) Math.cos(yawRad);
        float fz = (float) Math.sin(yawRad);
        float rx = -fz;
        float rz = fx;

        float mx = 0, mz = 0;
        if (GameKeyBinds.FORWARD.isDown()) {
            mx += fx;
            mz += fz;
        }
        if (GameKeyBinds.BACK.isDown()) {
            mx -= fx;
            mz -= fz;
        }
        if (GameKeyBinds.RIGHT.isDown()) {
            mx += rx;
            mz += rz;
        }
        if (GameKeyBinds.LEFT.isDown()) {
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
            if (GameKeyBinds.JUMP.isDown())
                vyInput += speed;
            if (GameKeyBinds.SPRINT.isDown())
                vyInput -= speed;
            this.vy = vyInput;
        } else {
            if (onGround && GameKeyBinds.JUMP.isDown()) {
                this.vy = config.jumpForce;
                this.onGround = false;
            }
        }

        // Toggle Volo
        boolean fDown = GameKeyBinds.FLY_TOGGLE.isDown();
        if (fDown && !flyKeyLatch) {
            flying = !flying;
            flyKeyLatch = true;
            if (flying)
                this.vy = 0;
        }
        if (!fDown)
            flyKeyLatch = false;

        // Toggle View (F5)
        boolean f5Down = GameKeyBinds.CAMERA_MODE.isDown();
        if (f5Down && !viewKeyLatch) {
            thirdPerson = !thirdPerson;
            viewKeyLatch = true;
        }
        if (!f5Down)
            viewKeyLatch = false;

        // Hotbar
        int hotbarSlot = GameKeyBinds.getPressedHotbarSlot();
        if (hotbarSlot >= 0) {
            inventory.setSelectedSlot(hotbarSlot);
        }
    }

    public void updateCamera(float partialTick) {
        float lerpX = getLerpedX(partialTick);
        float lerpY = getLerpedY(partialTick);
        float lerpZ = getLerpedZ(partialTick);
        float lerpYaw = getLerpedYaw(partialTick);
        float lerpPitch = getLerpedPitch(partialTick);

        if (thirdPerson) {
            float intendedDist = 4.0f;
            float actualDist = intendedDist;

            // --- Raycast Collision Check ---
            // Cast a ray from Head to Camera Position
            if (world != null) {
                float headX = lerpX;
                float headY = lerpY + config.playerEyeHeight;
                float headZ = lerpZ;

                // Angle calculations
                float pitchRad = (float) Math.toRadians(lerpPitch);
                float yawRad = (float) Math.toRadians(lerpYaw);

                // Direction Vector (Backward from head)
                float backX = -(float) (Math.cos(pitchRad) * Math.cos(yawRad));
                float backY = -(float) Math.sin(pitchRad);
                float backZ = -(float) (Math.cos(pitchRad) * Math.sin(yawRad));

                // Simple Raycast Step
                float step = 0.2f;
                for (float d = 0; d < intendedDist; d += step) {
                    float checkX = headX + backX * d;
                    float checkY = headY + backY * d;
                    float checkZ = headZ + backZ * d;

                    // Check block solid
                    int bx = (int) Math.floor(checkX);
                    int by = (int) Math.floor(checkY);
                    int bz = (int) Math.floor(checkZ);

                    if (Blocks.isSolid(world.getBlock(bx, by, bz))) {
                        actualDist = d - 0.2f; // Buffer
                        if (actualDist < 0.5f)
                            actualDist = 0.5f; // Minimum distance
                        break;
                    }
                }
            }

            // Recalculate Final Position
            float pitchRad = (float) Math.toRadians(lerpPitch);
            float yawRad = (float) Math.toRadians(lerpYaw);

            float backX = -(float) (Math.cos(pitchRad) * Math.cos(yawRad));
            float backY = -(float) Math.sin(pitchRad);
            float backZ = -(float) (Math.cos(pitchRad) * Math.sin(yawRad));

            float camX = lerpX + backX * actualDist;
            // Floor Clamp: Prevent camera from going below player's feet level + margin
            float minCamY = lerpY + 0.1f;
            float camY = (lerpY + config.playerEyeHeight) + backY * actualDist;
            if (camY < minCamY)
                camY = minCamY;

            float camZ = lerpZ + backZ * actualDist;

            camera.setPosition(camX, camY, camZ);
            camera.setRotation(lerpPitch, lerpYaw);
        } else {
            // 1st Person
            camera.setPosition(lerpX, lerpY + config.playerEyeHeight, lerpZ);
            camera.setRotation(lerpPitch, lerpYaw);
        }

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
        // Trigger Swing Animation
        swing();

        if (interactionManager == null)
            return;

        // Use unified Raycast from InteractionManager
        // This ensures the same target selection as the Wireframe and Right-Click
        engine.interaction.RaycastResult result = interactionManager.performRaycast(this, world, entityManager);

        if (result.isEntity()) {
            // TODO: Attack entity
            miningManager.resetBreaking();
            // Entity attack logic would go here
            // System.out.println("Attacked entity: " + result.getEntity());
        } else if (result.isBlock()) {
            // Mining
            engine.world.BlockPos pos = result.getBlockPos();
            miningManager.processMining(this, world, pos.getX(), pos.getY(), pos.getZ(), deltaTime);
        } else {
            // Miss
            miningManager.resetBreaking();
        }
    }

    private void handleRightClick() {
        // Trigger Swing Animation
        swing();

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

    // ==================== SERIALIZATION ====================

    @Override
    protected void saveAdditional(NBTTagCompound tag) {
        super.saveAdditional(tag);
        // Save inventory
        if (inventory != null) {
            NBTTagCompound invTag = new NBTTagCompound();
            inventory.writeToNBT(invTag);
            tag.setTag("Inventory", invTag);
        }
    }

    @Override
    protected void loadAdditional(NBTTagCompound tag) {
        super.loadAdditional(tag);
        // Load inventory
        if (tag.hasKey("Inventory") && inventory != null) {
            inventory.readFromNBT(tag.getTag("Inventory"));
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

    public boolean isThirdPerson() {
        return thirdPerson;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public String getSkinPath() {
        return skinPath;
    }

    public void setSkinPath(String skinPath) {
        this.skinPath = skinPath;
    }
}