package engine.entity;

import java.util.*;

/**
 * Manages all entities in the world.
 */
public class EntityManager {
    
    private final Map<Long, Entity> entitiesById = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Entity> toAdd = new ArrayList<>();
    
    // Player reference for AI
    private Player player;
    
    // Tick timing
    private static final float TICK_RATE = 20f; // 20 ticks per second
    private static final float TICK_TIME = 1f / TICK_RATE;
    private float tickAccumulator = 0;
    private float partialTick = 0;
    
    public EntityManager() {}
    
    // ==================== ENTITY MANAGEMENT ====================
    
    /**
     * Add an entity to the world.
     */
    public void addEntity(Entity entity) {
        toAdd.add(entity);
    }
    
    /**
     * Remove an entity from the world.
     */
    public void removeEntity(Entity entity) {
        entity.remove();
    }
    
    /**
     * Get entity by ID.
     */
    public Entity getEntity(long id) {
        return entitiesById.get(id);
    }
    
    /**
     * Get all entities.
     */
    public List<Entity> getEntities() {
        return Collections.unmodifiableList(entities);
    }
    
    /**
     * Get entities of a specific type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> List<T> getEntitiesOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Entity e : entities) {
            if (type.isInstance(e)) {
                result.add((T) e);
            }
        }
        return result;
    }
    
    /**
     * Get entities near a position.
     */
    public List<Entity> getEntitiesNear(float x, float y, float z, float radius) {
        List<Entity> result = new ArrayList<>();
        float radiusSq = radius * radius;
        
        for (Entity e : entities) {
            float dx = e.getX() - x;
            float dy = e.getY() - y;
            float dz = e.getZ() - z;
            if (dx * dx + dy * dy + dz * dz < radiusSq) {
                result.add(e);
            }
        }
        return result;
    }
    
    // ==================== PLAYER ====================
    
    public void setPlayer(Player player) {
        this.player = player;
    }
    
    public Player getPlayer() {
        return player;
    }
    
    // ==================== UPDATE ====================
    
    /**
     * Update all entities.
     * 
     * @param deltaTime Time since last frame
     */
    public void update(float deltaTime) {
        // Add pending entities
        for (Entity e : toAdd) {
            entities.add(e);
            entitiesById.put(e.getEntityId(), e);
            
            // Set spawn position for living entities
            if (e instanceof LivingEntity) {
                LivingEntity le = (LivingEntity) e;
                le.setSpawnPosition(e.getX(), e.getY(), e.getZ());
            }
            
            // Update AI memory with player reference
            if (e instanceof LivingEntity && player != null) {
                ((LivingEntity) e).getBrain().remember("nearestPlayer", player);
            }
        }
        toAdd.clear();
        
        // Fixed timestep for game logic
        tickAccumulator += deltaTime;
        
        while (tickAccumulator >= TICK_TIME) {
            // Pre-tick (save previous state)
            for (Entity e : entities) {
                e.preTick();
            }
            
            // Tick
            for (Entity e : entities) {
                if (!e.isRemoved()) {
                    e.update(TICK_TIME);
                }
            }
            
            // Post-tick
            for (Entity e : entities) {
                e.postTick();
            }
            
            tickAccumulator -= TICK_TIME;
        }
        
        // Calculate partial tick for interpolation
        partialTick = tickAccumulator / TICK_TIME;
        
        // Remove dead entities
        Iterator<Entity> iter = entities.iterator();
        while (iter.hasNext()) {
            Entity e = iter.next();
            if (e.isRemoved()) {
                entitiesById.remove(e.getEntityId());
                iter.remove();
            }
        }
    }
    
    /**
     * Get partial tick for rendering interpolation.
     */
    public float getPartialTick() {
        return partialTick;
    }
    
    // ==================== CLEANUP ====================
    
    public void cleanup() {
        entities.clear();
        entitiesById.clear();
        toAdd.clear();
    }
    
    // ==================== DEBUG ====================
    
    public int getEntityCount() {
        return entities.size();
    }
}
