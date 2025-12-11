package engine.entity;

/**
 * Properties for entity types.
 * Follows same pattern as BlockProperties.
 */
public class EntityProperties {
    
    private float maxHealth = 20f;
    private float movementSpeed = 0.5f;
    private float knockbackResistance = 0f;
    private String modelPath = null;
    private String texturePath = null;
    private boolean hasGravity = true;
    private boolean canSwim = true;
    private boolean fireImmune = false;
    
    public EntityProperties() {}
    
    public static EntityProperties create() {
        return new EntityProperties();
    }
    
    // ==================== BUILDER METHODS ====================
    
    public EntityProperties maxHealth(float health) {
        this.maxHealth = health;
        return this;
    }
    
    public EntityProperties movementSpeed(float speed) {
        this.movementSpeed = speed;
        return this;
    }
    
    public EntityProperties knockbackResistance(float resistance) {
        this.knockbackResistance = resistance;
        return this;
    }
    
    public EntityProperties model(String path) {
        this.modelPath = path;
        return this;
    }
    
    public EntityProperties texture(String path) {
        this.texturePath = path;
        return this;
    }
    
    public EntityProperties noGravity() {
        this.hasGravity = false;
        return this;
    }
    
    public EntityProperties cantSwim() {
        this.canSwim = false;
        return this;
    }
    
    public EntityProperties fireImmune() {
        this.fireImmune = true;
        return this;
    }
    
    // ==================== PRESETS ====================
    
    public EntityProperties humanoid() {
        this.maxHealth = 20f;
        this.movementSpeed = 0.5f;
        this.modelPath = "models/entity/humanoid.geo.json";
        return this;
    }
    
    public EntityProperties animal() {
        this.maxHealth = 10f;
        this.movementSpeed = 0.4f;
        return this;
    }
    
    // ==================== GETTERS ====================
    
    public float getMaxHealth() { return maxHealth; }
    public float getMovementSpeed() { return movementSpeed; }
    public float getKnockbackResistance() { return knockbackResistance; }
    public String getModelPath() { return modelPath; }
    public String getTexturePath() { return texturePath; }
    public boolean hasGravity() { return hasGravity; }
    public boolean canSwim() { return canSwim; }
    public boolean isFireImmune() { return fireImmune; }
}
