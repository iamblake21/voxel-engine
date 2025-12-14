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
