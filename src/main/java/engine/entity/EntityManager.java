package engine.entity;

import java.util.*;

import engine.entity.inventory.PlayerInventory;
import engine.world.item.ItemStack;



/**
 * Manages all entities in the world.
 */
public class EntityManager {
    
    private final Map<Long, Entity> entitiesById = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<Entity> toAdd = new ArrayList<>();

    private static final float PICKUP_RADIUS = 1.5f;
    private static final float MAGNET_RADIUS = 2.5f;
    private static final float MAGNET_SPEED = 5.0f;

    
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
 * Process item pickup for player.
 * Call this in update().
 */
private void processItemPickup(float deltaTime) {
    if (player == null) return;
    
    float px = player.getX();
    float py = player.getY();
    float pz = player.getZ();
    
    for (Entity e : entities) {
        if (e instanceof ItemEntity && !e.isRemoved()) {
            ItemEntity item = (ItemEntity) e;
            
            float dx = px - item.getX();
            float dy = (py + 0.5f) - item.getY(); // Aim for player center
            float dz = pz - item.getZ();
            float distSq = dx * dx + dy * dy + dz * dz;
            float dist = (float) Math.sqrt(distSq);
            
            // Magnet effect - pull items toward player
            if (dist < MAGNET_RADIUS && item.canPickup()) {
                float speed = MAGNET_SPEED * deltaTime;
                if (dist > 0.1f) {
                    item.addVelocity(
                        (dx / dist) * speed,
                        (dy / dist) * speed,
                        (dz / dist) * speed
                    );
                }
            }
            
            // Pickup
            if (dist < PICKUP_RADIUS && item.canPickup()) {
                tryPickupItem(item);
            }
        }
    }
}

/**
 * Try to add item to player inventory.
 */
private void tryPickupItem(ItemEntity itemEntity) {
    if (player == null) return;
    
    PlayerInventory inv = player.getInventory();
    ItemStack stack = itemEntity.getStack();
    
    if (stack == null || stack.isEmpty()) {
        itemEntity.remove();
        return;
    }
    
    // Try to add to inventory
    ItemStack remaining = inv.addItem(stack);
    
    if (remaining.isEmpty()) {
        // Fully picked up
        itemEntity.remove();
    } else {
        // Partially picked up
        itemEntity.setStack(remaining);
    }
}

/**
 * Merge nearby item entities.
 */
private void processItemMerging() {
    List<ItemEntity> items = getEntitiesOfType(ItemEntity.class);
    
    for (int i = 0; i < items.size(); i++) {
        ItemEntity a = items.get(i);
        if (a.isRemoved() || !a.canPickup()) continue;
        
        for (int j = i + 1; j < items.size(); j++) {
            ItemEntity b = items.get(j);
            if (b.isRemoved() || !b.canPickup()) continue;
            
            if (a.distanceToSq(b) < 0.5f * 0.5f) {
                a.tryMerge(b);
            }
        }
    }
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
    for (Entity e : toAdd) {
        entities.add(e);
        entitiesById.put(e.getEntityId(), e);
        
        if (e instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) e;
            le.setSpawnPosition(e.getX(), e.getY(), e.getZ());
        }
        
        if (e instanceof LivingEntity && player != null) {
            ((LivingEntity) e).getBrain().remember("nearestPlayer", player);
        }
    }
    toAdd.clear();
    
    // Fixed timestep for game logic
    tickAccumulator += deltaTime;
    
    while (tickAccumulator >= TICK_TIME) {
        // Pre-tick
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
        
        // Item pickup (ogni tick)
        processItemPickup(TICK_TIME);
        
        // Item merging (ogni tick)
        processItemMerging();
        
        tickAccumulator -= TICK_TIME;
    }
    
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
