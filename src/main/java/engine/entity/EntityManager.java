package engine.entity;

import engine.rendering.Renderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages all entities in the game.
 */
public class EntityManager {
    
    private final List<Entity> entities = new ArrayList<>();
    
    public EntityManager() {
    }
    
    /**
     * Add an entity
     */
    public void addEntity(Entity entity) {
        entities.add(entity);
    }
    
    /**
     * Remove an entity
     */
    public void removeEntity(Entity entity) {
        entities.remove(entity);
    }
    
    /**
     * Update all entities
     */
    public void update(float deltaTime) {
        // Remove dead entities
        entities.removeIf(Entity::isRemoved);
        
        // Update remaining
        for (Entity entity : entities) {
            entity.update(deltaTime);
        }
    }
    
    /**
     * Render all entities
     */
    public void render(Renderer renderer) {
        // Entity rendering not implemented yet
        // Would render models for each entity
    }
    
    /**
     * Get all entities
     */
    public List<Entity> getEntities() {
        return entities;
    }
    
    /**
     * Get entity count
     */
    public int getEntityCount() {
        return entities.size();
    }
    
    /**
     * Cleanup
     */
    public void cleanup() {
        entities.clear();
    }
}