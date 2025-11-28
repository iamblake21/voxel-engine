package engine.entity;

import engine.registry.ResourceLocation;

/**
 * Defines a type of entity (Player, Zombie, etc.)
 * Contains factory for creating instances and type properties.
 * 
 * @param <T> The entity class this type creates
 */
public class EntityType<T extends Entity> {
    
    private final EntityFactory<T> factory;
    private final float width;
    private final float height;
    private final boolean persistent;  // Should be saved to disk
    private final boolean summonable;  // Can be spawned by commands
    
    // Registry info
    private ResourceLocation registryId;
    private int numericId = -1;
    
    private EntityType(Builder<T> builder) {
        this.factory = builder.factory;
        this.width = builder.width;
        this.height = builder.height;
        this.persistent = builder.persistent;
        this.summonable = builder.summonable;
    }
    
    /**
     * Create a new entity of this type
     */
    public T create() {
        T entity = factory.create(this);
        return entity;
    }
    
    // ==================== PROPERTIES ====================
    
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public boolean isPersistent() { return persistent; }
    public boolean isSummonable() { return summonable; }
    
    // ==================== REGISTRY ====================
    
    public void setRegistryInfo(ResourceLocation id, int numericId) {
        this.registryId = id;
        this.numericId = numericId;
    }
    
    public ResourceLocation getRegistryId() { return registryId; }
    public int getNumericId() { return numericId; }
    
    // ==================== FACTORY ====================
    
    @FunctionalInterface
    public interface EntityFactory<T extends Entity> {
        T create(EntityType<T> type);
    }
    
    // ==================== BUILDER ====================
    
    public static <T extends Entity> Builder<T> builder(EntityFactory<T> factory) {
        return new Builder<>(factory);
    }
    
    public static class Builder<T extends Entity> {
        private final EntityFactory<T> factory;
        private float width = 0.6f;
        private float height = 1.8f;
        private boolean persistent = true;
        private boolean summonable = true;
        
        private Builder(EntityFactory<T> factory) {
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
        
        public EntityType<T> build() {
            return new EntityType<>(this);
        }
    }
    
    @Override
    public String toString() {
        return "EntityType{" + (registryId != null ? registryId : "unregistered") + "}";
    }
}
