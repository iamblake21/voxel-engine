package engine.world.gen;

import engine.utils.Math3D.Vec3i;
import engine.world.World;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a static structure (like a tree, ruin, etc.)
 * efficiently stored with a palette.
 */
public class Structure {

    private final Vec3i size;
    // Palette of block states (to save memory/string lookups)
    private final List<String> palette = new ArrayList<>();
    private final List<StructureBlock> blocks = new ArrayList<>();
    private final List<StructureEntity> entities = new ArrayList<>();

    public Structure(int width, int height, int depth) {
        this.size = new Vec3i(width, height, depth);
    }

    // Constraints
    private List<String> validGround = null; // null = any
    private boolean denyLiquid = false;

    // Options
    private boolean createFoundation = false;
    private String foundationMaterial = "game:stone";

    public void setConstraints(List<String> validGround, boolean denyLiquid) {
        this.validGround = validGround;
        this.denyLiquid = denyLiquid;
    }

    public void setOptions(boolean createFoundation, String foundationMaterial) {
        this.createFoundation = createFoundation;
        this.foundationMaterial = foundationMaterial;
    }

    public List<String> getValidGround() {
        return validGround;
    }

    public boolean shouldDenyLiquid() {
        return denyLiquid;
    }

    public boolean shouldCreateFoundation() {
        return createFoundation;
    }

    public String getFoundationMaterial() {
        return foundationMaterial;
    }

    public void addBlock(int x, int y, int z, String blockId) {
        int stateIndex = palette.indexOf(blockId);
        if (stateIndex == -1) {
            stateIndex = palette.size();
            palette.add(blockId);
        }
        blocks.add(new StructureBlock(x, y, z, stateIndex));
    }

    public interface StructureCallback {
        void setBlock(int worldX, int worldY, int worldZ, int blockId);

        int getBlock(int worldX, int worldY, int worldZ);
    }

    public interface EntityCallback {
        void spawnEntity(String entityId, float x, float y, float z);
    }

    public void place(StructureCallback callback, EntityCallback entityCallback, int startX, int startY, int startZ) {
        // Place Blocks
        for (StructureBlock sb : blocks) {
            String blockId = palette.get(sb.stateIndex);
            int numericId = Blocks.get(blockId).getNumericId();

            callback.setBlock(
                    startX + sb.x,
                    startY + sb.y,
                    startZ + sb.z,
                    numericId);
        }

        // Create Foundation
        if (createFoundation) {
            int foundationBlockId = Blocks.get(foundationMaterial).getNumericId();
            // Find bottom blocks of structure (y=0)
            for (StructureBlock sb : blocks) {
                if (sb.y == 0) {
                    int wx = startX + sb.x;
                    int wz = startZ + sb.z;
                    int currentY = startY + sb.y - 1;

                    // Fill down until solid
                    int maxDepth = 10;
                    for (int i = 0; i < maxDepth; i++) {
                        int y = currentY - i;
                        int existing = callback.getBlock(wx, y, wz);
                        if (Blocks.isSolid(existing)) {
                            break; // Hit ground
                        }
                        // Replace non-solid (air/water/leaves/etc) with foundation
                        callback.setBlock(wx, y, wz, foundationBlockId);
                    }
                }
            }
        }

        // Spawn Entities
        if (entityCallback != null) {
            for (StructureEntity se : entities) {
                // Determine entity alignment? Default to center of block
                entityCallback.spawnEntity(
                        se.entityId,
                        startX + se.x + 0.5f,
                        startY + se.y,
                        startZ + se.z + 0.5f);
            }
        }
    }

    public void addEntity(int x, int y, int z, String entityId) {
        entities.add(new StructureEntity(x, y, z, entityId));
    }

    // Helper to get raw data
    public List<StructureBlock> getBlocks() {
        return blocks;
    }

    public List<StructureEntity> getEntities() {
        return entities;
    }

    public engine.utils.Math3D.AABB getAABB(int startX, int startY, int startZ) {
        return new engine.utils.Math3D.AABB(
                startX, startY, startZ,
                startX + size.x, startY + size.y, startZ + size.z);
    }

    public Vec3i getSize() {
        return size;
    }

    /**
     * Internal representation of a block in the structure
     */
    public static class StructureBlock {
        public final int x, y, z;
        public final int stateIndex;

        public StructureBlock(int x, int y, int z, int stateIndex) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.stateIndex = stateIndex;
        }
    }

    public static class StructureEntity {
        public final int x, y, z;
        public final String entityId;

        public StructureEntity(int x, int y, int z, String entityId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.entityId = entityId;
        }
    }
}
