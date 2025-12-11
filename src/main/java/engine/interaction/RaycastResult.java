package engine.interaction;

import engine.entity.Entity;
import engine.world.BlockPos;
import engine.world.BlockPos.Direction;

/**
 * Result of a raycast that can hit either a block or an entity.
 * 
 * Used by InteractionManager to determine what the player is looking at.
 */
public class RaycastResult {
    
    public enum Type {
        MISS,       // Hit nothing
        BLOCK,      // Hit a block
        ENTITY      // Hit an entity
    }
    
    private final Type type;
    
    // Block hit data
    private final BlockPos blockPos;
    private final Direction face;
    private final BlockPos placePos;  // Position where a block would be placed
    
    // Entity hit data
    private final Entity entity;
    
    // Common data
    private final float hitX, hitY, hitZ;  // Exact hit position
    private final float distance;
    
    // ==================== CONSTRUCTORS ====================
    
    private RaycastResult(Type type, BlockPos blockPos, Direction face, BlockPos placePos,
                          Entity entity, float hitX, float hitY, float hitZ, float distance) {
        this.type = type;
        this.blockPos = blockPos;
        this.face = face;
        this.placePos = placePos;
        this.entity = entity;
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.distance = distance;
    }
    
    /**
     * Create a miss result.
     */
    public static RaycastResult miss() {
        return new RaycastResult(Type.MISS, null, null, null, null, 0, 0, 0, Float.MAX_VALUE);
    }
    
    /**
     * Create a block hit result.
     */
    public static RaycastResult block(BlockPos pos, Direction face, BlockPos placePos,
                                       float hitX, float hitY, float hitZ, float distance) {
        return new RaycastResult(Type.BLOCK, pos, face, placePos, null, hitX, hitY, hitZ, distance);
    }
    
    /**
     * Create an entity hit result.
     */
    public static RaycastResult entity(Entity entity, float hitX, float hitY, float hitZ, float distance) {
        return new RaycastResult(Type.ENTITY, null, null, null, entity, hitX, hitY, hitZ, distance);
    }
    
    // ==================== GETTERS ====================
    
    public Type getType() { return type; }
    
    public boolean isMiss() { return type == Type.MISS; }
    public boolean isBlock() { return type == Type.BLOCK; }
    public boolean isEntity() { return type == Type.ENTITY; }
    
    /**
     * Get the block position that was hit.
     * Only valid if type == BLOCK.
     */
    public BlockPos getBlockPos() { return blockPos; }
    
    /**
     * Get the face of the block that was hit.
     * Only valid if type == BLOCK.
     */
    public Direction getFace() { return face; }
    
    /**
     * Get the position where a block would be placed.
     * This is the air block adjacent to the hit face.
     * Only valid if type == BLOCK.
     */
    public BlockPos getPlacePos() { return placePos; }
    
    /**
     * Get the entity that was hit.
     * Only valid if type == ENTITY.
     */
    public Entity getEntity() { return entity; }
    
    /**
     * Get exact hit position X.
     */
    public float getHitX() { return hitX; }
    
    /**
     * Get exact hit position Y.
     */
    public float getHitY() { return hitY; }
    
    /**
     * Get exact hit position Z.
     */
    public float getHitZ() { return hitZ; }
    
    /**
     * Get distance from ray origin to hit point.
     */
    public float getDistance() { return distance; }
    
    @Override
    public String toString() {
        switch (type) {
            case BLOCK:
                return "RaycastResult{BLOCK " + blockPos + " face=" + face + "}";
            case ENTITY:
                return "RaycastResult{ENTITY " + entity + "}";
            default:
                return "RaycastResult{MISS}";
        }
    }
}
