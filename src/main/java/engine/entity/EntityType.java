package engine.entity;

import engine.registry.ResourceLocation;

import java.util.function.Function;

/**
 * Defines an entity type with factory and properties.
 * 
 * Usage:
 *   EntityType<NpcEntity> VILLAGER = EntityTypes.register("game:villager",
 *       EntityType.Builder.of(NpcEntity::new)
 *           .size(0.6f, 1.8f)
 *           .properties(EntityProperties.create()
 *               .maxHealth(20)
 *               .model("models/entity/humanoid.geo.json")
 *               .texture("textures/entity/villager.png"))
 *           .build());
 */
public class EntityType<T extends Entity> {
    
    private final Function<EntityType<T>, T> factory;
    private final float width;
    private final float height;
    private final boolean persistent;
    private final boolean summonable;
    private final EntityProperties properties;
    
    // Registry info (set by EntityTypes.register)
    private ResourceLocation registryId;
    private int numericId = -1;
    
    private EntityType(Builder<T> builder) {
        this.factory = builder.factory;
        this.width = builder.width;
        this.height = builder.height;
        this.persistent = builder.persistent;
        this.summonable = builder.summonable;
        this.properties = builder.properties != null ? builder.properties : new EntityProperties();
    }
    
    // ==================== FACTORY ====================
    
    /**
     * Create a new entity of this type.
     */
    public T create() {
        return factory.apply(this);
    }
    
    // ==================== PROPERTIES ====================
    
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public boolean isPersistent() { return persistent; }
    public boolean isSummonable() { return summonable; }
    public EntityProperties getProperties() { return properties; }
    
    // Shortcuts to properties
    public float getMaxHealth() { return properties.getMaxHealth(); }
    public String getModelPath() { return properties.getModelPath(); }
    public String getTexturePath() { return properties.getTexturePath(); }
    
    // ==================== REGISTRY ====================
    
    public ResourceLocation getRegistryId() { return registryId; }
    public int getNumericId() { return numericId; }
    
    public void setRegistryInfo(ResourceLocation id, int numericId) {
        this.registryId = id;
        this.numericId = numericId;
    }
    
    @Override
    public String toString() {
        return "EntityType{" + registryId + "}";
    }
    
    // ==================== BUILDER ====================
    
    public static <T extends Entity> Builder<T> builder(Function<EntityType<T>, T> factory) {
        return new Builder<>(factory);
    }
    
    public static class Builder<T extends Entity> {
        private final Function<EntityType<T>, T> factory;
        private float width = 0.6f;
        private float height = 1.8f;
        private boolean persistent = true;
        private boolean summonable = true;
        private EntityProperties properties;
        
        private Builder(Function<EntityType<T>, T> factory) {
            this.factory = factory;
        }
        
        public Builder<T> size(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        public Builder<T> persistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }
        
        public Builder<T> summonable(boolean summonable) {
            this.summonable = summonable;
            return this;
        }
        
        public Builder<T> properties(EntityProperties properties) {
            this.properties = properties;
            return this;
        }
        
        public EntityType<T> build() {
            return new EntityType<>(this);
        }
    }
}
