package engine.entity;

import engine.entity.ai.EntityBrain;
import engine.world.World;

import engine.loot.LootTable;

/**
 * Entity with health, AI, and physics capabilities.
 * Base class for NPCs, animals, monsters.
 * * FIX: Ora delega la fisica reale al PhysicsEngine universale.
 */
public class LivingEntity extends Entity {
    
    // ==================== HEALTH ====================
    
    protected float health;
    protected float maxHealth;
    protected boolean dead = false;
    protected int deathTime = 0;
    protected int hurtTime = 0;
    protected int invulnerableTime = 0;
    
    // ==================== MOVEMENT ====================
    
    protected float movementSpeed;
    // hasGravity è già in Entity (classe base)
    
    // Movement input (set by AI or player)
    protected float moveForward = 0;
    protected float moveStrafe = 0;
    protected boolean jumping = false;
    
    // ==================== AI ====================
    
    protected EntityBrain brain;
    
    // ==================== ANIMATION ====================
    
    protected float limbSwing = 0;
    protected float limbSwingAmount = 0;
    protected String currentAnimation = "idle";
    protected float animationTime = 0;
    
    // ==================== SPAWN ====================
    
    protected float spawnX, spawnY, spawnZ;
    
    // ==================== WORLD ====================
    
    protected World world;
    
    public LivingEntity(EntityType<?> type) {
        super(type);
        
        // Load from properties
        EntityProperties props = type.getProperties();
        this.maxHealth = props.getMaxHealth();
        this.health = maxHealth;
        this.movementSpeed = props.getMovementSpeed();
        this.hasGravity = props.hasGravity();
        
        // Create AI brain
        this.brain = new EntityBrain(this);
    }
    
    // ==================== LIFECYCLE ====================
    
    @Override
    public void update(float deltaTime) {
        if (dead) {
            deathTime++;
            if (deathTime > 20) {
                remove();
            }
            return;
        }
        
        // Update timers
        if (hurtTime > 0) hurtTime--;
        if (invulnerableTime > 0) invulnerableTime--;
        
        // Update AI (Il cervello decide DOVE andare)
        if (brain != null) {
            brain.tick();
        }
        
        // Apply AI Movement Intent -> Velocity
        // Qui trasformiamo "Voglio andare avanti" (AI) in "Velocità X/Z" (Fisica)
        updateAiMovement();
        
        // NOTA: Non applichiamo più gravità o collisioni qui.
        // L'Engine chiamerà physics.processEntity(this) dopo questo update.
        
        // Update animation
        updateAnimation(deltaTime);
    }

        /**
     * Get the loot table for when this entity dies.
     * Override in subclasses.
     */
    public LootTable getLootTable() {
        return LootTable.EMPTY;
    }
    
    /**
     * Called when entity dies.
     */
    protected void onDeath() {
        // Drop loot
        World world = getWorld(); // Devi avere un riferimento al mondo
        if (world != null && !getLootTable().isEmpty()) {
            world.dropLoot(getLootTable(), getX(), getY(), getZ());
        }
    }
    
    /**
     * Override the kill/damage method to call onDeath.
     */
    public void kill() {
        onDeath();
        remove();
    }
    
    protected void updateAiMovement() {
        // Se l'AI ha deciso di muoversi (moveForward/Strafe != 0)
        if (moveForward != 0 || moveStrafe != 0) {
            float yawRad = (float) Math.toRadians(bodyYaw);
            float sin = (float) Math.sin(yawRad);
            float cos = (float) Math.cos(yawRad);
            
            // Calcola velocità target
            // Se siamo in aria (es. saltando), abbiamo meno controllo (0.2f)
            float speed = movementSpeed * (onGround ? 1f : 0.2f);
            
            // Imposta la velocità fisica
            this.vx += (moveForward * cos - moveStrafe * sin) * speed;
            this.vz += (moveForward * sin + moveStrafe * cos) * speed;
        }
        
        // Jumping (Input AI -> Fisica)
        if (jumping && onGround) {
            this.vy = 8.0f; // Forza salto (puoi renderla configurabile)
            this.onGround = false;
        }
        
        // Reset movement input (L'AI deve reimpostarlo ogni tick se vuole continuare a muoversi)
        moveForward = 0;
        moveStrafe = 0;
        jumping = false;
    }
    
    protected void updateAnimation(float deltaTime) {
        // Calculate limb swing based on horizontal speed
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        
        if (speed > 0.01f) {
            limbSwing += speed * deltaTime * 10f;
            limbSwingAmount = Math.min(1f, limbSwingAmount + deltaTime * 4f);
            currentAnimation = "walk";
        } else {
            limbSwingAmount = Math.max(0f, limbSwingAmount - deltaTime * 4f);
            if (limbSwingAmount < 0.1f) {
                currentAnimation = "idle";
            }
        }
        
        animationTime += deltaTime;
    }
    
    // ==================== HEALTH ====================
    
    public void damage(float amount) {
        if (invulnerableTime > 0 || dead) return;
        
        health -= amount;
        hurtTime = 10;
        invulnerableTime = 20;
        
        // Knockback semplice (opzionale)
        this.vy += 4.0f; 
        
        if (health <= 0) {
            health = 0;
            die();
        }
    }
    
    public void heal(float amount) {
        health = Math.min(maxHealth, health + amount);
    }
    
    protected void die() {
        dead = true;
        deathTime = 0;
        // Qui potresti spawnare particelle o drop
    }
    
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public boolean isDead() { return dead; }
    
    // ==================== MOVEMENT CONTROL (Usati dall'AI) ====================
    
    public void moveToward(float targetX, float targetZ, float speed) {
        float dx = targetX - x;
        float dz = targetZ - z;
        float distSq = dx*dx + dz*dz;
        
        if (distSq > 0.01f) {
            // Face target
            float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            
            // Ruota gradualmente il corpo verso l'obiettivo
            this.bodyYaw = lerpAngle(bodyYaw, targetYaw, 0.1f);
            this.yaw = lerpAngle(yaw, targetYaw, 0.15f); // Anche la testa segue
            
            // Imposta intenzione movimento
            this.moveForward = speed;
        }
    }
    
    public void jump() {
        jumping = true;
    }
    
    public float getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(float speed) { this.movementSpeed = speed; }
    
    // ==================== SPAWN ====================
    
    public void setSpawnPosition(float x, float y, float z) {
        this.spawnX = x; this.spawnY = y; this.spawnZ = z;
    }
    
    public float getSpawnX() { return spawnX; }
    public float getSpawnY() { return spawnY; }
    public float getSpawnZ() { return spawnZ; }
    
    public float getDistanceFromSpawnSq() {
        float dx = x - spawnX;
        float dy = y - spawnY;
        float dz = z - spawnZ;
        return dx * dx + dy * dy + dz * dz;
    }
    
    // ==================== ANIMATION GETTERS ====================
    
    public float getLimbSwing() { return limbSwing; }
    public float getLimbSwingAmount() { return limbSwingAmount; }
    public String getCurrentAnimation() { return currentAnimation; }
    public float getAnimationTime() { return animationTime; }
    
    public void setAnimation(String name) {
        if (!currentAnimation.equals(name)) {
            currentAnimation = name;
            animationTime = 0;
        }
    }
    
    // ==================== AI GETTER ====================
    
    public EntityBrain getBrain() { return brain; }
    
    // ==================== WORLD ====================
    
    public void setWorld(World world) { this.world = world; }
    public World getWorld() { return world; }
}