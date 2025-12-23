package engine.entity;

import engine.core.Engine;
import engine.registry.ResourceLocation;
import engine.utils.Math3D.AABB;

public abstract class Entity {

    // Posizione attuale
    protected float x, y, z;
    // Posizione precedente (per getLerpedX/Y/Z)
    protected float prevX, prevY, prevZ;

    // Velocità
    protected float vx, vy, vz;

    // Rotazione
    protected float yaw, pitch;
    protected float prevYaw, prevPitch;
    protected float bodyYaw;
    protected float prevBodyYaw;

    // Stati
    protected boolean removed = false;
    protected boolean onGround = false;
    protected boolean inWater = false;

    // Campi Fisica (Richiesti da PhysicsEngine)
    protected boolean hasGravity = true;
    protected boolean isStatic = false;
    protected AABB boundingBox;

    protected final EntityType<?> type;
    protected long entityId;
    private static long nextEntityId = 1;
    protected int tickCount = 0;

    public Entity(EntityType<?> type) {
        this.type = type;
        this.entityId = nextEntityId++;
        // Inizializza bounding box a zero, verrà aggiornato al primo setPosition
        this.boundingBox = new AABB(0, 0, 0, 0, 0, 0);
        updateBoundingBox();
    }

    public void init(Engine engine) {
    }

    public abstract void update(float deltaTime);

    // Salva lo stato precedente prima dell'update (Essenziale per getLerped)
    public void preTick() {
        prevX = x;
        prevY = y;
        prevZ = z;
        prevYaw = yaw;
        prevPitch = pitch;
        prevBodyYaw = bodyYaw;
    }

    public void postTick() {
        tickCount++;
    }

    public void remove() {
        this.removed = true;
    }

    public boolean isRemoved() {
        return removed;
    }

    // ==================== POSITION ====================
    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        updateBoundingBox();
    }

    public void move(float dx, float dy, float dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        updateBoundingBox();
    }

    protected void updateBoundingBox() {
        float w = getWidth() / 2.0f;
        float h = getHeight();
        this.boundingBox.set(x - w, y, z - w, x + w, y + h, z + w);
    }

    public AABB getBoundingBox() {
        updateBoundingBox();
        return boundingBox;
    }

    // ==================== INTERPOLATION (I metodi che mancavano!)
    // ====================

    public float getLerpedX(float partialTick) {
        return prevX + (x - prevX) * partialTick;
    }

    public float getLerpedY(float partialTick) {
        return prevY + (y - prevY) * partialTick;
    }

    public float getLerpedZ(float partialTick) {
        return prevZ + (z - prevZ) * partialTick;
    }

    public float getLerpedYaw(float partialTick) {
        return lerpAngle(prevYaw, yaw, partialTick);
    }

    public float getLerpedPitch(float partialTick) {
        return prevPitch + (pitch - prevPitch) * partialTick;
    }

    public float getLerpedBodyYaw(float partialTick) {
        return lerpAngle(prevBodyYaw, bodyYaw, partialTick);
    }

    public static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff > 180)
            diff -= 360;
        while (diff < -180)
            diff += 360;
        return from + diff * t;
    }

    // ==================== VELOCITY & PHYSICS COMPATIBILITY ====================

    // Alias per PhysicsEngine
    public float getVelX() {
        return vx;
    }

    public float getVelY() {
        return vy;
    }

    public float getVelZ() {
        return vz;
    }

    // Getter originali
    public float getVx() {
        return vx;
    }

    public float getVy() {
        return vy;
    }

    public float getVz() {
        return vz;
    }

    public void setVelocity(float vx, float vy, float vz) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }

    public void setVelX(float v) {
        this.vx = v;
    }

    public void setVelY(float v) {
        this.vy = v;
    }

    public void setVelZ(float v) {
        this.vz = v;
    }

    public void addVelocity(float dvx, float dvy, float dvz) {
        this.vx += dvx;
        this.vy += dvy;
        this.vz += dvz;
    }

    public boolean hasGravity() {
        return hasGravity;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setSize(float width, float height) {
    } // Empty impl for Player

    // ==================== ROTATION ====================
    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getBodyYaw() {
        return bodyYaw;
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public void setBodyYaw(float bodyYaw) {
        this.bodyYaw = bodyYaw;
    }

    public void lookAt(float targetX, float targetY, float targetZ) {
        float dx = targetX - x;
        float dy = targetY - (y + getEyeHeight());
        float dz = targetZ - z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float newYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float newPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        setRotation(newYaw, newPitch);
    }

    // ==================== STATE & TYPE ====================
    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public boolean isInWater() {
        return inWater;
    }

    public void setInWater(boolean inWater) {
        this.inWater = inWater;
    }

    public int getTickCount() {
        return tickCount;
    }

    public EntityType<?> getType() {
        return type;
    }

    public float getWidth() {
        return type.getWidth();
    }

    public float getHeight() {
        return type.getHeight();
    }

    public float getEyeHeight() {
        return getHeight() * 0.85f;
    }

    public ResourceLocation getTypeId() {
        return type.getRegistryId();
    }

    public long getEntityId() {
        return entityId;
    }

    // ==================== DISTANCE ====================
    public float distanceTo(Entity other) {
        return (float) Math.sqrt(distanceToSq(other));
    }

    public float distanceToSq(Entity other) {
        return distanceToSq(other.x, other.y, other.z);
    }

    public float distanceToSq(float px, float py, float pz) {
        float dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + entityId + "}";
    }

    // ==================== SERIALIZATION ====================

    private java.util.UUID uuid = java.util.UUID.randomUUID();

    public java.util.UUID getUUID() {
        return uuid;
    }

    public void setUUID(java.util.UUID uuid) {
        this.uuid = uuid;
    }

    public engine.world.item.nbt.NBTTagCompound save(engine.world.item.nbt.NBTTagCompound tag) {
        tag.setString("id", type.getRegistryId().toString());
        tag.setDouble("x", x);
        tag.setDouble("y", y);
        tag.setDouble("z", z);
        tag.setFloat("yaw", yaw);
        tag.setFloat("pitch", pitch);
        tag.setDouble("vx", vx);
        tag.setDouble("vy", vy);
        tag.setDouble("vz", vz);
        tag.setLong("uuidMost", uuid.getMostSignificantBits());
        tag.setLong("uuidLeast", uuid.getLeastSignificantBits());

        saveAdditional(tag);
        return tag;
    }

    public void load(engine.world.item.nbt.NBTTagCompound tag) {
        setPosition(
                (float) tag.getDouble("x"),
                (float) tag.getDouble("y"),
                (float) tag.getDouble("z"));
        setRotation(tag.getFloat("yaw"), tag.getFloat("pitch"));
        setVelocity(
                (float) tag.getDouble("vx"),
                (float) tag.getDouble("vy"),
                (float) tag.getDouble("vz"));

        if (tag.hasKey("uuidMost") && tag.hasKey("uuidLeast")) {
            this.uuid = new java.util.UUID(tag.getLong("uuidMost"), tag.getLong("uuidLeast"));
        }

        loadAdditional(tag);
    }

    protected void saveAdditional(engine.world.item.nbt.NBTTagCompound tag) {
    }

    protected void loadAdditional(engine.world.item.nbt.NBTTagCompound tag) {
    }

    // ==================== RENDER DEFAULTS ====================
    public String getCurrentAnimation() {
        return "idle";
    }

    public float getAnimationTime() {
        return tickCount + 0f;
    }

    public float getLimbSwing() {
        return 0;
    }

    public float getLimbSwingAmount() {
        return 0;
    }
}