package engine.entity;

import engine.world.item.ItemStack;

/**
 * An item entity that exists in the world and can be picked up.
 * 
 * Features:
 * - Bobbing animation
 * - Rotation animation  
 * - Merge with nearby same items
 * - Pickup delay
 * - Despawn timer
 */
public class ItemEntity extends Entity {
    
    private ItemStack stack;
    
    // Pickup
    private int pickupDelay = 10;  // Ticks before can be picked up (0.5 sec)
    private int age = 0;
    private int despawnTime = 6000; // 5 minutes at 20 TPS
    
    // Animation
    private float bobOffset = 0;
    private float spinAngle = 0;
    
    // Merge settings
    private static final float MERGE_RADIUS = 0.5f;
    private static final int MAX_STACK_SIZE = 64;
    
    public ItemEntity(EntityType<?> type) {
        super(type);
        this.hasGravity = true;
        this.isStatic = false;
    }
    
    public ItemEntity(EntityType<?> type, ItemStack stack) {
        this(type);
        this.stack = stack;
    }
    
    /**
     * Set the item stack this entity holds.
     */
    public void setStack(ItemStack stack) {
        this.stack = stack;
    }
    
    public ItemStack getStack() {
        return stack;
    }
    
    /**
     * Set pickup delay in ticks.
     */
    public void setPickupDelay(int ticks) {
        this.pickupDelay = ticks;
    }
    
    /**
     * Check if this item can be picked up.
     */
    public boolean canPickup() {
        return pickupDelay <= 0 && stack != null && !stack.isEmpty();
    }
    
    /**
     * Set despawn time in ticks. Set to -1 for no despawn.
     */
    public void setDespawnTime(int ticks) {
        this.despawnTime = ticks;
    }
    
    @Override
    public void update(float deltaTime) {
        age++;
        
        // Pickup delay countdown
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        
        // Despawn check
        if (despawnTime > 0 && age >= despawnTime) {
            remove();
            return;
        }
        
        // Remove if stack is empty
        if (stack == null || stack.isEmpty()) {
            remove();
            return;
        }
        
        // Animation updates
        bobOffset = (float) Math.sin(age * 0.1f) * 0.1f;
        spinAngle = (age * 3f) % 360f;
        
        // Apply friction when on ground
        if (onGround) {
            vx *= 0.8f;
            vz *= 0.8f;
        }
    }
    
    /**
     * Try to merge with another item entity.
     * Returns true if merge was successful.
     */
    public boolean tryMerge(ItemEntity other) {
        if (other == this || other.isRemoved()) return false;
        if (stack == null || other.stack == null) return false;
        if (!stack.canStackWith(other.stack)) return false;
        
        int maxSize = stack.getItem().getMaxStackSize();
        int total = stack.getCount() + other.stack.getCount();
        
        if (total <= maxSize) {
            // Merge completely into this one
            stack.grow(other.stack.getCount());
            other.remove();
            return true;
        } else if (stack.getCount() < maxSize) {
            // Partial merge
            int transfer = maxSize - stack.getCount();
            stack.grow(transfer);
            other.stack.shrink(transfer);
            return true;
        }
        
        return false;
    }
    
    // ==================== RENDERING HELPERS ====================
    
    public float getBobOffset() {
        return bobOffset;
    }
    
    public float getSpinAngle() {
        return spinAngle;
    }
    
    /**
     * Get interpolated bob offset for smooth rendering.
     */
    public float getLerpedBobOffset(float partialTick) {
        float currentBob = (float) Math.sin((age + partialTick) * 0.1f) * 0.1f;
        return currentBob;
    }
    
    /**
     * Get interpolated spin angle.
     */
    public float getLerpedSpinAngle(float partialTick) {
        return ((age + partialTick) * 3f) % 360f;
    }
    
    @Override
    public float getHeight() {
        return 0.25f;
    }
    
    @Override
    public float getWidth() {
        return 0.25f;
    }
}