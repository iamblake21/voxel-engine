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

/**
 * Player entity with first-person controls and physics.
 * 
 * Direct port from MinecraftOneFile physics.
 */
public class Player extends Entity {

    private final Config config;
    private final Camera camera;
    private final PlayerInventory inventory;
    private World world;
    private PhysicsEngine physics;

    // Movement state
    private boolean onGround = false;
    private boolean flying = false;
    private boolean flyKeyLatch = false;

    // Water state
    private boolean bodyInWater = false;
    private boolean headInWater = false;
    private boolean bodyWasInWater = false;
    private boolean headWasInWater = false;

    // Selected block for placement - NO LONGER USED, using inventory instead
    // private int selectedBlock = 1;

    public Player(EntityType<?> type, Config config) {
        super(type);
        this.config = config;
        this.camera = new Camera(config);
        this.inventory = new PlayerInventory();
    }

    /**
     * Initialize player with world reference
     */
    public void init(Engine engine) {
        this.world = engine.getWorld();
        this.physics = engine.getPhysics();

        // Set camera
        engine.getRenderer().setCamera(camera);

        // Find spawn position
        if (world != null) {
            Vec3 spawn = world.findSpawnPosition();
            setPosition(spawn.x, spawn.y, spawn.z);
        }

        updateCamera();
    }

    @Override
    public void update(float deltaTime) {
        // Update handled by game class with input
    }

    /**
     * Update player with input
     */
    public void update(float deltaTime, InputManager input) {
        if (world == null)
            return;

        handleInput(input, deltaTime);
        applyPhysics(deltaTime);
        updateCamera();
        world.maintainChunks(x, z);
    }

    /**
     * Handle player input
     */
    private void handleInput(InputManager input, float deltaTime) {
        // Mouse look
        float sensitivity = 0.1f;
        yaw += input.getMouseDX() * sensitivity;
        pitch += input.getMouseDY() * sensitivity;
        pitch = Math.max(-89f, Math.min(89f, pitch));

        // Movement
        float speed = onGround ? config.playerSpeed : 4f;
        if (flying)
            speed = config.playerFlySpeed;
        if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT) && !flying) {
            speed *= config.playerSprintMultiplier;
        }

        // Calculate forward/right vectors
        float yawRad = (float) Math.toRadians(yaw);
        float fx = (float) Math.cos(yawRad);
        float fz = (float) Math.sin(yawRad);
        float rx = -fz;
        float rz = fx;

        // WASD movement
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

        // Normalize
        float len = (float) Math.sqrt(mx * mx + mz * mz);
        if (len > 0) {
            mx /= len;
            mz /= len;
        }

        vx = mx * speed;
        vz = mz * speed;

        // Flying controls
        if (flying) {
            float vyInput = 0;
            if (input.isKeyDown(GLFW_KEY_SPACE))
                vyInput += speed;
            if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT))
                vyInput -= speed;
            vy = vyInput;
            onGround = false;
        } else {
            // Jump
            if (onGround && input.isKeyDown(GLFW_KEY_SPACE)) {
                vy = config.jumpForce;
                onGround = false;
            }
        }

        // Toggle fly
        boolean fDown = input.isKeyDown(GLFW_KEY_F);
        if (fDown && !flyKeyLatch) {
            flying = !flying;
            flyKeyLatch = true;
            if (flying)
                vy = 0;
        }
        if (!fDown)
            flyKeyLatch = false;

        // Hotbar selection (1-9)
        for (int i = 0; i < 9; i++) {
            if (input.isKeyDown(GLFW_KEY_1 + i)) {
                inventory.setSelectedSlot(i);
            }
        }

    }

    /**
     * Apply physics (gravity, water, collisions)
     */
    private void applyPhysics(float deltaTime) {
        // Gravity
        if (!flying) {
            vy -= config.gravity * deltaTime;
        }

        // Water physics
        float hw = config.playerWidth * 0.5f;
        float hh = config.playerHeight;

        bodyInWater = !flying && isInWater(x, y, z, hw, hh);
        headInWater = !flying && isBlockWater(x, y + config.playerEyeHeight, z);

        boolean justExitedHead = headWasInWater && !headInWater;
        boolean justEnteredBody = !bodyWasInWater && bodyInWater;

        if (bodyInWater) {
            float imm = getImmersionFraction();
            float buoy = headInWater ? 11f : 9f;
            float antiSink = (vy < 0 ? (3.5f + 2.5f * imm) : 0f);
            vy += (buoy + antiSink) * deltaTime;

            float linDrag = 3.5f * imm;
            vx -= vx * linDrag * deltaTime;
            vz -= vz * linDrag * deltaTime;

            float vertDrag = (vy < 0 ? 1.4f : 0.6f) * imm;
            vy -= vy * vertDrag * deltaTime;

            // Clamp velocities
            if (justEnteredBody && vy < -1.2f)
                vy = -1f;
            if (justExitedHead && vy < 7.0f)
                vy = 7.0f;
            if (!headInWater && vy > 20f)
                vy = 20f;
            if (vy < -3.0f)
                vy = -3.0f;
        } else {
            if (vy < -50f)
                vy = -50f;
        }

        // Collision resolution
        resolveMovement(deltaTime, hw, hh);

        // Update water state
        headWasInWater = headInWater;
        bodyWasInWater = bodyInWater;
    }

    /**
     * Resolve movement with collision
     */
    private void resolveMovement(float dt, float hw, float hh) {
        // X movement
        if (collides(x + vx * dt, y, z, hw, hh)) {
            vx *= 0.8f;
            while (!collides(x + Math.signum(vx) * 0.001f, y, z, hw, hh)) {
                x += Math.signum(vx) * 0.001f;
            }
        } else {
            x += vx * dt;
        }

        // Y movement
        if (collides(x, y + vy * dt, z, hw, hh)) {
            float signY = Math.signum(vy);
            if (vy < 0)
                onGround = true;
            vy = 0;
            while (!collides(x, y + signY * 0.001f, z, hw, hh)) {
                y += signY * 0.001f;
            }
        } else {
            y += vy * dt;
            onGround = false;
        }

        // Z movement
        if (collides(x, y, z + vz * dt, hw, hh)) {
            vz *= 0.8f;
            while (!collides(x, y, z + Math.signum(vz) * 0.001f, hw, hh)) {
                z += Math.signum(vz) * 0.001f;
            }
        } else {
            z += vz * dt;
        }
    }

    /**
     * Check collision with world
     */
    private boolean collides(float px, float py, float pz, float hw, float hh) {
        int minX = (int) Math.floor(px - hw);
        int maxX = (int) Math.floor(px + hw);
        int minY = (int) Math.floor(py - 0.1f);
        int maxY = (int) Math.floor(py + hh);
        int minZ = (int) Math.floor(pz - hw);
        int maxZ = (int) Math.floor(pz + hw);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    int blockId = world.getBlock(bx, by, bz);
                    if (Blocks.isSolid(blockId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Check if AABB overlaps water
     */
    private boolean isInWater(float px, float py, float pz, float hw, float hh) {
        int minX = (int) Math.floor(px - hw);
        int maxX = (int) Math.floor(px + hw);
        int minY = (int) Math.floor(py - 0.1f);
        int maxY = (int) Math.floor(py + hh);
        int minZ = (int) Math.floor(pz - hw);
        int maxZ = (int) Math.floor(pz + hw);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    int blockId = world.getBlock(bx, by, bz);
                    if (Blocks.isLiquid(blockId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isBlockWater(float px, float py, float pz) {
        int blockId = world.getBlock((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
        return Blocks.isLiquid(blockId);
    }

    private float getImmersionFraction() {
        float[] offsets = { 0.1f, 0.6f, 1.0f, 1.3f, 1.55f };
        int count = 0;
        for (float off : offsets) {
            if (isBlockWater(x, y + off, z))
                count++;
        }
        return count / (float) offsets.length;
    }

    /**
     * Update camera position/rotation
     */
    private void updateCamera() {
        // 1. aggiorna la camera (posizione e rotazione)
        camera.setPosition(x, y + config.playerEyeHeight, z);
        camera.setRotation(pitch, yaw);
        camera.update(0f);

        // 2. aggiorna anche lo stato camera nel World (frustum + direzione)
        if (world != null) {
            // ATTENZIONE: qui dipende da come esponi i dati di Mat4
            float[] proj = camera.getProjectionMatrix().toArray(); // o .data, .values...
            float[] view = camera.getViewMatrix().toArray();

            world.updateCamera(
                    proj,
                    view,
                    camera.getForward());
        }
    }

    /**
     * Handle block breaking / placing using mouse buttons with inventory items.
     */
    public void handleBlockInteraction(InputManager input) {
        if (world == null)
            return;

        boolean breakBlock = input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_1);
        boolean placeBlock = input.isMouseButtonPressed(GLFW_MOUSE_BUTTON_2);

        if (!breakBlock && !placeBlock) {
            return;
        }

        // Get selected item from inventory
        ItemStack selectedStack = inventory.getSelectedStack();

        // Raycast to find target block
        float eyeX = x;
        float eyeY = y + config.playerEyeHeight;
        float eyeZ = z;

        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        float dirX = (float) (Math.cos(pitchRad) * Math.cos(yawRad));
        float dirY = (float) (Math.sin(pitchRad));
        float dirZ = (float) (Math.cos(pitchRad) * Math.sin(yawRad));

        float maxDist = 6.0f;
        float step = 0.1f;
        float dist = 0f;

        // Last air position for placement
        int lastAirX = 0, lastAirY = 0, lastAirZ = 0;

        // Target block position (hit position)
        float hitX = Float.NaN;
        float hitY = Float.NaN;
        float hitZ = Float.NaN;

        while (dist <= maxDist) {
            float cx = eyeX + dirX * dist;
            float cy = eyeY + dirY * dist;
            float cz = eyeZ + dirZ * dist;

            int bx = (int) Math.floor(cx);
            int by = (int) Math.floor(cy);
            int bz = (int) Math.floor(cz);

            int blockId = world.getBlock(bx, by, bz);

            if (Blocks.isLiquid(blockId)) {
                // Treat liquid as replaceable
                lastAirX = bx;
                lastAirY = by;
                lastAirZ = bz;
            } else if (Blocks.isSolid(blockId)) {
                // Hit a solid block
                hitX = bx;
                hitY = by;
                hitZ = bz;

                if (breakBlock) {
                    // Break block
                    world.setBlock(bx, by, bz, Blocks.AIR().getNumericId());

                    // Damage tool if holding one
                    if (!selectedStack.isEmpty() && selectedStack.isDamageable()) {
                        if (selectedStack.damageItem(1)) {
                            // Tool broke
                            System.out.println("[Player] Tool broke!");
                        }
                    }
                } else if (placeBlock) {
                    // Try to use selected item
                    if (!selectedStack.isEmpty() && selectedStack.getItem() instanceof IUsableItem) {
                        IUsableItem usableItem = (IUsableItem) selectedStack.getItem();
                        boolean used = usableItem.use(world, this, selectedStack,
                                hitX, hitY, hitZ,
                                lastAirX, lastAirY, lastAirZ);

                        if (used) {
                            // Item was used successfully, shrink stack
                            selectedStack.shrink(1);
                            System.out.println("[Player] Used " + selectedStack.getItem().getRegistryId());
                        }
                    }
                }

                break; // Stop raycast
            } else {
                // Air block
                lastAirX = bx;
                lastAirY = by;
                lastAirZ = bz;
            }

            dist += step;
        }
    }

    // ==================== GETTERS ====================

    public Camera getCamera() {
        return camera;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean isHeadInWater() {
        return headInWater;
    }

    public PlayerInventory getInventory() {
        return inventory;
    }

    public void setWorld(World world) {
        this.world = world;
    }
}