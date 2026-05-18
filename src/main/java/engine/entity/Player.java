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

import engine.command.CommandSender;

public class Player extends LivingEntity implements CommandSender {
    private final Config config;
    private final Camera camera;
    private final PlayerInventory inventory;
    private final engine.mechanics.MiningManager miningManager = new engine.mechanics.MiningManager();

    private boolean flying = false;
    private boolean flyKeyLatch = false;
    private boolean thirdPerson = false;
    private boolean viewKeyLatch = false;

    private PhysicsEngine physics;

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
        this.setSize(0.6f, 1.8f);

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
        this.physics = engine.getPhysics();
        engine.getRenderer().setCamera(camera);

        if (world != null) {
            Vec3 spawn = world.findSpawnPosition();
            setPosition(spawn.x, spawn.y + 1, spawn.z);
        }

        this.entityManager = engine.getEntityManager();
        this.interactionManager = new InteractionManager();

        interactionManager.setGuiHandler((player, blockEntity) -> {
            System.out.println("[Player] Opening GUI for: " + blockEntity);
        });
    }

    @Override
    public void update(float deltaTime) {
    }

    public void update(float deltaTime, InputManager input, boolean inputEnabled) {
        if (world == null)
            return;

        preTick();

        if (inputEnabled) {
            handleInput(input, deltaTime);
        } else {
            this.vx = 0;
            this.vz = 0;
            if (flying)
                this.vy = 0;
        }

        if (flying) {
            this.hasGravity = false;
            this.onGround = false;
        } else {
            this.hasGravity = true;
        }

        if (physics != null) {
            physics.processEntity(this, world, deltaTime);
        }

        updateAnimation(deltaTime);

    }

    private void handleInput(InputManager input, float deltaTime) {
        float sensitivity = 0.1f;
        yaw += input.getMouseDX() * sensitivity;
        pitch += input.getMouseDY() * sensitivity;
        pitch = Math.max(-89f, Math.min(89f, pitch));

        float speed = onGround ? config.playerSpeed : 4.0f;
        if (flying)
            speed = config.playerFlySpeed;

        if (GameKeyBinds.SPRINT.isDown() && !flying) {
            speed *= config.playerSprintMultiplier;
        }

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

        this.vx = mx * speed;
        this.vz = mz * speed;

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

        boolean fDown = GameKeyBinds.FLY_TOGGLE.isDown();
        if (fDown && !flyKeyLatch) {
            flying = !flying;
            flyKeyLatch = true;
            if (flying)
                this.vy = 0;
        }
        if (!fDown)
            flyKeyLatch = false;

        boolean f5Down = GameKeyBinds.CAMERA_MODE.isDown();
        if (f5Down && !viewKeyLatch) {
            thirdPerson = !thirdPerson;
            viewKeyLatch = true;
        }
        if (!f5Down)
            viewKeyLatch = false;

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

            if (world != null) {
                float headX = lerpX;
                float headY = lerpY + config.playerEyeHeight;
                float headZ = lerpZ;

                float pitchRad = (float) Math.toRadians(lerpPitch);
                float yawRad = (float) Math.toRadians(lerpYaw);

                float backX = -(float) (Math.cos(pitchRad) * Math.cos(yawRad));
                float backY = -(float) Math.sin(pitchRad);
                float backZ = -(float) (Math.cos(pitchRad) * Math.sin(yawRad));

                float step = 0.2f;
                for (float d = 0; d < intendedDist; d += step) {
                    float checkX = headX + backX * d;
                    float checkY = headY + backY * d;
                    float checkZ = headZ + backZ * d;

                    int bx = (int) Math.floor(checkX);
                    int by = (int) Math.floor(checkY);
                    int bz = (int) Math.floor(checkZ);

                    if (Blocks.isSolid(world.getBlock(bx, by, bz))) {
                        actualDist = d - 0.2f;
                        if (actualDist < 0.5f)
                            actualDist = 0.5f;
                        break;
                    }
                }
            }

            float pitchRad = (float) Math.toRadians(lerpPitch);
            float yawRad = (float) Math.toRadians(lerpYaw);

            float backX = -(float) (Math.cos(pitchRad) * Math.cos(yawRad));
            float backY = -(float) Math.sin(pitchRad);
            float backZ = -(float) (Math.cos(pitchRad) * Math.sin(yawRad));

            float camX = lerpX + backX * actualDist;
            float minCamY = lerpY + 0.1f;
            float camY = (lerpY + config.playerEyeHeight) + backY * actualDist;
            if (camY < minCamY)
                camY = minCamY;

            float camZ = lerpZ + backZ * actualDist;

            camera.setPosition(camX, camY, camZ);
            camera.setRotation(lerpPitch, lerpYaw);
        } else {
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

        if (breakBlock) {
            handleLeftClick(input, deltaTime);
        } else {
            miningManager.resetBreaking();
        }

        if (useItemPressed && !rightClickLatch) {
            rightClickLatch = true;
            handleRightClick();
        }
        if (!useItem) {
            rightClickLatch = false;
        }
    }

    private void handleLeftClick(InputManager input, float deltaTime) {
        swing();

        if (interactionManager == null)
            return;

        engine.interaction.RaycastResult result = interactionManager.performRaycast(this, world, entityManager);

        if (result.isEntity()) {
            miningManager.resetBreaking();
        } else if (result.isBlock()) {
            engine.world.BlockPos pos = result.getBlockPos();
            miningManager.processMining(this, world, pos.getX(), pos.getY(), pos.getZ(), deltaTime);
        } else {
            miningManager.resetBreaking();
        }
    }

    private void handleRightClick() {
        swing();

        if (interactionManager == null || entityManager == null) {
            return;
        }

        interactionManager.handleRightClick(this, world, entityManager);
    }

    @Override
    protected void saveAdditional(NBTTagCompound tag) {
        super.saveAdditional(tag);
        if (inventory != null) {
            NBTTagCompound invTag = new NBTTagCompound();
            inventory.writeToNBT(invTag);
            tag.setTag("Inventory", invTag);
        }
    }

    @Override
    protected void loadAdditional(NBTTagCompound tag) {
        super.loadAdditional(tag);
        if (tag.hasKey("Inventory") && inventory != null) {
            inventory.readFromNBT(tag.getTag("Inventory"));
        }
    }

    // CommandSender Implementation
    @Override
    public void sendMessage(String text) {
        // We will hook this into ChatGui later. For now, print to console.
        // In a real implementation, we would access the Game instance or a static Chat
        // manager.
        // game.ui.ChatGui.addMessage(text);
        System.out.println("[CHAT] " + getName() + ": " + text);

        // STATIC HOOK (Simplest way to get message to UI without passing Game
        // references everywhere)
        // We can use a static event bus or direct callback if available.
        // For now, let's assume ExampleGame will poll messages or we'll add a specific
        // static method in ChatGui.
        game.ui.ChatGui.addMessage(text);
    }

    @Override
    public boolean hasPermission(String node) {
        return true; // Simple permission model
    }

    @Override
    public String getName() {
        return "Player";
    }

    // Getters/Setters
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
