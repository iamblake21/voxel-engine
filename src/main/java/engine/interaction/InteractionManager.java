package engine.interaction;

import engine.entity.Entity;
import engine.entity.EntityManager;
import engine.entity.Player;
import engine.world.BlockPos;
import engine.world.BlockPos.Direction;
import engine.world.World;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.blockentity.BlockEntity;
import engine.world.item.Item;
import engine.world.item.ItemStack;
import engine.world.blockentity.ContainerBlockEntity;

import java.util.List;

/**
 * Central manager for all player interactions.
 * 
 * Handles the priority chain:
 * 1. Entity interaction (NPCs, animals)
 * 2. Block entity interaction (chests, furnaces)
 * 3. Item use on block (hoe on dirt, etc.)
 * 4. Item use in air (food, potions)
 * 5. Block placement (fallback for BlockItems)
 * 
 * Usage in Player:
 * interactionManager.handleRightClick(player, world, entityManager);
 */
public class InteractionManager {

    private static final float REACH_DISTANCE = 6.0f;
    private static final float ENTITY_REACH = 6.0f;

    // Callback for opening GUIs
    private GuiOpenHandler guiHandler;

    public InteractionManager() {
    }

    /**
     * Set the handler for opening GUIs.
     * This is called when interacting with containers.
     */
    public void setGuiHandler(GuiOpenHandler handler) {
        this.guiHandler = handler;
    }

    // ==================== MAIN INTERACTION ====================

    /**
     * Handle right-click interaction.
     * 
     * @param player        The player
     * @param world         The world
     * @param entityManager Entity manager for entity raycasting
     * @return true if something was interacted with
     */
    public boolean handleRightClick(Player player, World world, EntityManager entityManager) {
        // Get what player is looking at
        RaycastResult raycast = performRaycast(player, world, entityManager);
        ItemStack heldItem = player.getInventory().getSelectedStack();

        // 1. Entity interaction (highest priority)
        if (raycast.isEntity()) {
            Entity entity = raycast.getEntity();
            if (entity instanceof IInteractable) {
                IInteractable interactable = (IInteractable) entity;
                if (interactable.canInteract(player)) {
                    InteractionResult result = interactable.onInteract(player);
                    if (result.isSuccess()) {
                        return true;
                    }
                }
            }
        }

        // 2. Block entity interaction
        if (raycast.isBlock()) {
            BlockPos pos = raycast.getBlockPos();
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (blockEntity instanceof IInteractable) {
                IInteractable interactable = (IInteractable) blockEntity;
                if (interactable.canInteract(player)) {
                    InteractionResult result = interactable.onInteract(player);
                    if (result.isSuccess()) {
                        // Open GUI if this is a container
                        if (blockEntity instanceof ContainerBlockEntity container && guiHandler != null) {
                            guiHandler.openBlockEntityGui(player, blockEntity);
                        }
                        return true;
                    }
                }
            }

            // 2b. Generic Block interaction (e.g. Doors, Levers)
            // Only if not holding shift? For now always try block first.
            engine.world.block.state.BlockState state = Block.STATE_IDS
                    .get(world.getBlock(pos.getX(), pos.getY(), pos.getZ()));
            if (state != null) {
                if (state.getBlock().onInteract(world, pos.getX(), pos.getY(), pos.getZ(), player)) {
                    return true;
                }
            }
        }

        // 3. Item use on block
        if (raycast.isBlock() && !heldItem.isEmpty()) {
            Item item = heldItem.getItem();
            InteractionResult result = item.onUseOnBlock(
                    world, player, heldItem,
                    raycast.getBlockPos(), raycast.getFace(), raycast.getPlacePos());

            if (result.isSuccess()) {
                if (result.shouldConsume()) {
                    heldItem.shrink(1);
                }
                return true;
            }
        }

        // 4. Item use in air (food, potions, etc.)
        if (!heldItem.isEmpty()) {
            Item item = heldItem.getItem();
            InteractionResult result = item.onUse(world, player, heldItem);

            if (result.isSuccess()) {
                if (result.shouldConsume()) {
                    heldItem.shrink(1);
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Handle left-click (attack/break).
     * This is mainly for entity attacks; block breaking is handled by
     * MiningManager.
     */

    public boolean handleLeftClick(Player player, World world, EntityManager entityManager) {
        RaycastResult raycast = performRaycast(player, world, entityManager);

        // Entity attack
        if (raycast.isEntity()) {
            Entity entity = raycast.getEntity();
            // TODO: Implement attack logic
            // For now, just return true to indicate we hit something
            return true;
        }

        return false;
    }

    // ==================== RAYCAST ====================

    /**
     * Perform a raycast from player's eye position.
     * Checks both blocks and entities, returns the closest hit.
     */
    public RaycastResult performRaycast(Player player, World world, EntityManager entityManager) {
        // Get ray origin and direction
        float eyeX = player.getX();
        float eyeY = player.getY() + player.getEyeHeight();
        float eyeZ = player.getZ();

        float yawRad = (float) Math.toRadians(player.getYaw());
        float pitchRad = (float) Math.toRadians(player.getPitch());

        float dirX = (float) (Math.cos(pitchRad) * Math.cos(yawRad));
        float dirY = (float) (Math.sin(pitchRad));
        float dirZ = (float) (Math.cos(pitchRad) * Math.sin(yawRad));

        // Raycast blocks
        RaycastResult blockResult = raycastBlocks(world, eyeX, eyeY, eyeZ, dirX, dirY, dirZ, REACH_DISTANCE);

        // Raycast entities
        RaycastResult entityResult = raycastEntities(entityManager, player, eyeX, eyeY, eyeZ, dirX, dirY, dirZ,
                ENTITY_REACH);

        // Return closest hit
        if (blockResult.isMiss() && entityResult.isMiss()) {
            return RaycastResult.miss();
        }

        if (entityResult.isMiss()) {
            return blockResult;
        }

        if (blockResult.isMiss()) {
            return entityResult;
        }

        // Both hit - return closest
        return entityResult.getDistance() < blockResult.getDistance() ? entityResult : blockResult;
    }

    /**
     * Raycast against blocks in the world.
     */
    private RaycastResult raycastBlocks(World world, float eyeX, float eyeY, float eyeZ,
            float dirX, float dirY, float dirZ, float maxDist) {
        float step = 0.05f;
        float dist = 0;

        int lastAirX = 0, lastAirY = 0, lastAirZ = 0;
        Direction lastFace = Direction.UP;

        while (dist <= maxDist) {
            float cx = eyeX + dirX * dist;
            float cy = eyeY + dirY * dist;
            float cz = eyeZ + dirZ * dist;

            int bx = (int) Math.floor(cx);
            int by = (int) Math.floor(cy);
            int bz = (int) Math.floor(cz);

            int blockId = world.getBlock(bx, by, bz);
            Block block = Blocks.get(blockId);

            if (block.isLiquid() || block.isAir()) {
                // Track last air position for placement
                lastAirX = bx;
                lastAirY = by;
                lastAirZ = bz;

                // Calculate face based on direction
                lastFace = calculateFace(dirX, dirY, dirZ);
            } else {
                // Hit a block (solid or custom like Door/Torch)
                BlockPos hitPos = new BlockPos(bx, by, bz);
                BlockPos placePos = new BlockPos(lastAirX, lastAirY, lastAirZ);

                // Determine which face was hit
                Direction face = determineFace(cx - bx, cy - by, cz - bz);

                return RaycastResult.block(hitPos, face, placePos, cx, cy, cz, dist);
            }

            dist += step;
        }

        return RaycastResult.miss();
    }

    /**
     * Raycast against entities.
     */
    private RaycastResult raycastEntities(EntityManager entityManager, Player player,
            float eyeX, float eyeY, float eyeZ,
            float dirX, float dirY, float dirZ, float maxDist) {
        if (entityManager == null) {
            return RaycastResult.miss();
        }

        // Get nearby entities
        List<Entity> nearby = entityManager.getEntitiesNear(eyeX, eyeY, eyeZ, maxDist + 2);

        Entity closestEntity = null;
        float closestDist = maxDist;
        float hitX = 0, hitY = 0, hitZ = 0;

        for (Entity entity : nearby) {
            // Skip self
            if (entity == player)
                continue;
            if (entity.isRemoved())
                continue;

            // Simple AABB intersection test
            float[] hit = rayAABBIntersect(
                    eyeX, eyeY, eyeZ, dirX, dirY, dirZ,
                    entity.getX() - entity.getWidth() / 2,
                    entity.getY(),
                    entity.getZ() - entity.getWidth() / 2,
                    entity.getX() + entity.getWidth() / 2,
                    entity.getY() + entity.getHeight(),
                    entity.getZ() + entity.getWidth() / 2);

            if (hit != null && hit[3] < closestDist) {
                closestEntity = entity;
                closestDist = hit[3];
                hitX = hit[0];
                hitY = hit[1];
                hitZ = hit[2];
            }
        }

        if (closestEntity != null) {
            return RaycastResult.entity(closestEntity, hitX, hitY, hitZ, closestDist);
        }

        return RaycastResult.miss();
    }

    /**
     * Ray-AABB intersection test.
     * Returns [hitX, hitY, hitZ, distance] or null if no hit.
     */
    private float[] rayAABBIntersect(float ox, float oy, float oz, float dx, float dy, float dz,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float tmin = 0;
        float tmax = Float.MAX_VALUE;

        // X axis
        if (Math.abs(dx) < 0.0001f) {
            if (ox < minX || ox > maxX)
                return null;
        } else {
            float t1 = (minX - ox) / dx;
            float t2 = (maxX - ox) / dx;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax)
                return null;
        }

        // Y axis
        if (Math.abs(dy) < 0.0001f) {
            if (oy < minY || oy > maxY)
                return null;
        } else {
            float t1 = (minY - oy) / dy;
            float t2 = (maxY - oy) / dy;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax)
                return null;
        }

        // Z axis
        if (Math.abs(dz) < 0.0001f) {
            if (oz < minZ || oz > maxZ)
                return null;
        } else {
            float t1 = (minZ - oz) / dz;
            float t2 = (maxZ - oz) / dz;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax)
                return null;
        }

        if (tmin < 0)
            return null;

        return new float[] {
                ox + dx * tmin,
                oy + dy * tmin,
                oz + dz * tmin,
                tmin
        };
    }

    /**
     * Determine which face of a block was hit based on local hit position.
     */
    private Direction determineFace(float localX, float localY, float localZ) {
        // Find which face is closest
        float distTop = 1 - localY;
        float distBottom = localY;
        float distNorth = localZ;
        float distSouth = 1 - localZ;
        float distWest = localX;
        float distEast = 1 - localX;

        float minDist = distTop;
        Direction face = Direction.UP;

        if (distBottom < minDist) {
            minDist = distBottom;
            face = Direction.DOWN;
        }
        if (distNorth < minDist) {
            minDist = distNorth;
            face = Direction.NORTH;
        }
        if (distSouth < minDist) {
            minDist = distSouth;
            face = Direction.SOUTH;
        }
        if (distWest < minDist) {
            minDist = distWest;
            face = Direction.WEST;
        }
        if (distEast < minDist) {
            face = Direction.EAST;
        }

        return face;
    }

    /**
     * Calculate face based on ray direction (for tracking last air block).
     */
    private Direction calculateFace(float dirX, float dirY, float dirZ) {
        float absX = Math.abs(dirX);
        float absY = Math.abs(dirY);
        float absZ = Math.abs(dirZ);

        if (absY > absX && absY > absZ) {
            return dirY > 0 ? Direction.DOWN : Direction.UP;
        } else if (absX > absZ) {
            return dirX > 0 ? Direction.WEST : Direction.EAST;
        } else {
            return dirZ > 0 ? Direction.NORTH : Direction.SOUTH;
        }
    }

    // ==================== GUI HANDLER ====================

    /**
     * Request to open a GUI for a block entity.
     */
    public void openBlockEntityGui(Player player, BlockEntity blockEntity) {
        if (guiHandler != null) {
            guiHandler.openBlockEntityGui(player, blockEntity);
        }
    }

    /**
     * Interface for GUI opening callbacks.
     */
    public interface GuiOpenHandler {
        void openBlockEntityGui(Player player, BlockEntity blockEntity);
    }
}
