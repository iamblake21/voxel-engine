package engine.entity;

import engine.registry.ResourceLocation;

/**
 * Base class for all entities in the game.
 * 
 * Entities are dynamic objects that exist in the world:
 * - Players, mobs, animals
 * - Items on ground
 * - Projectiles
 * - Particles (sometimes)
 */
public abstract class Entity {
    
    // Position
    protected float x, y, z;
    
    // Velocity
    protected float vx, vy, vz;
    
    // Rotation (degrees)
    protected float yaw, pitch;
    
    // State
    protected boolean removed = false;
    protected boolean onGround = false;
    
    // Type reference
    protected final EntityType<?> type;
    
    // Unique ID (for networking/saving)
    protected long entityId;
    private static long nextEntityId = 1;
    
    public Entity(EntityType<?> type) {
        this.type = type;
        this.entityId = nextEntityId++;
    }
    
    // ==================== LIFECYCLE ====================
    
    /**
     * Called every tick to update entity logic
     */
    public abstract void update(float deltaTime);
    
    /**
     * Mark entity for removal
     */
    public void remove() {
        this.removed = true;
    }
    
    /**
     * Check if entity should be removed
     */
    public boolean isRemoved() {
        return removed;
    }
    
    // ==================== POSITION ====================
    
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    
    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public void move(float dx, float dy, float dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
    }
    
    // ==================== VELOCITY ====================
    
    public float getVx() { return vx; }
    public float getVy() { return vy; }
    public float getVz() { return vz; }
    
    public void setVelocity(float vx, float vy, float vz) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }
    
    public void addVelocity(float dvx, float dvy, float dvz) {
        this.vx += dvx;
        this.vy += dvy;
        this.vz += dvz;
    }
    
    // ==================== ROTATION ====================
    
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    
    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    // ==================== STATE ====================
    
    public boolean isOnGround() { return onGround; }
    public void setOnGround(boolean onGround) { this.onGround = onGround; }
    
    // ==================== TYPE ====================
    
    public EntityType<?> getType() { return type; }
    
    public float getWidth() { return type.getWidth(); }
    public float getHeight() { return type.getHeight(); }
    
    public ResourceLocation getTypeId() { 
        return type.getRegistryId(); 
    }
    
    // ==================== ID ====================
    
    public long getEntityId() { return entityId; }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + entityId + ", pos=(" + x + "," + y + "," + z + ")}";
    }
}
