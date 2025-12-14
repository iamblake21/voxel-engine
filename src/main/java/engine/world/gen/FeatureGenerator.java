package engine.world.gen;

import engine.world.biome.Biome;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.gen.Structure;
import engine.world.gen.WeightedStructure;
import engine.registry.Registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates world features (trees, structures, etc.)
 * 
 * Features are generated after terrain, and can span multiple chunks.
 * Uses deferred block operations for cross-chunk placement.
 */
public class FeatureGenerator {

    private final PerlinNoise noise;
    private final BiomeProvider biomeProvider;

    private final int chunkSize;
    private final int chunkHeight;
    private final float waterLevel;

    // Deferred operations for cross-chunk placement
    private final Map<Long, List<BlockOp>> deferredOps = new HashMap<>();

    public FeatureGenerator(long seed, BiomeProvider biomeProvider, WorldGenerator worldGenerator,
            int chunkSize, int chunkHeight, float waterLevel) {
        this.noise = new PerlinNoise(seed + 99999);
        this.biomeProvider = biomeProvider;
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.waterLevel = waterLevel;
    }

    /**
     * Generate features for a chunk.
     * Call AFTER terrain is generated for this chunk and all neighbors.
     */
    public void generateFeatures(int chunkX, int chunkZ, int[] blockData, int[] heightMap,
            BlockPlacer placer, Structure.EntityCallback entityCallback) {
        // Legacy/Procedural generation (keeping for now as requested or fallback)
        // generateTrees(chunkX, chunkZ, blockData, heightMap, placer);

        // Data-Driven Structure generation
        generateStructures(chunkX, chunkZ, blockData, heightMap, placer, entityCallback);

        // Apply any deferred operations for this chunk
        applyDeferred(chunkX, chunkZ, blockData);
    }

    private void generateStructures(int chunkX, int chunkZ, int[] blockData, int[] heightMap,
            BlockPlacer placer, Structure.EntityCallback entityCallback) {
        // Sample biome at chunk center
        int centerWorldX = chunkX * chunkSize + (chunkSize / 2);
        int centerWorldZ = chunkZ * chunkSize + (chunkSize / 2);
        Biome biome = biomeProvider.getBiome(centerWorldX, centerWorldZ);

        List<WeightedStructure> structures = new ArrayList<>(biome.getStructures());
        if (structures.isEmpty())
            return;

        // SORT: Low density (large/rare structures) first
        structures.sort((a, b) -> Float.compare(a.getDensity(), b.getDensity()));

        List<engine.utils.Math3D.AABB> allocatedSpace = new ArrayList<>();

        // Randomly attempt to place structures
        for (WeightedStructure ws : structures) {
            float density = ws.getDensity();
            int count = (int) density;
            // Add probabilistic extra attempt
            if (hash01(chunkX * 34123 + ws.getStructureId().hashCode(), chunkZ * 12312) < (density - count)) {
                count++;
            }

            for (int i = 0; i < count; i++) {
                // Get Structure from registry
                Structure structure = engine.registry.Registries.STRUCTURES.get(ws.getStructureId()).orElse(null);
                if (structure == null)
                    continue;

                // Pick random position in chunk (padding for size)
                // Use 'i' in hash to vary position for each tree
                int rx = Math.abs(hash2i(chunkX + i, chunkZ + ws.getWeight())) % (chunkSize - 4) + 2;
                int rz = Math.abs(hash2i(chunkX + ws.getWeight(), chunkZ + i)) % (chunkSize - 4) + 2;

                int worldX = chunkX * chunkSize + rx;
                int worldZ = chunkZ * chunkSize + rz;

                // Determine Y (surface)
                // Check bounds just case
                if (rx < 0 || rx >= chunkSize || rz < 0 || rz >= chunkSize)
                    continue;

                int sy = heightMap[rz * chunkSize + rx];

                // === CONSTRAINT CHECKS ===

                // 1. Check Liquid
                if (structure.shouldDenyLiquid()) {
                    // Check if spawn point is below/in water
                    if (sy <= waterLevel) {
                        continue;
                    }
                }

                // 2. Check Valid Ground
                List<String> validGround = structure.getValidGround();
                if (validGround != null && !validGround.isEmpty()) {
                    // Check block BELOW spawn
                    int groundY = sy - 1;
                    if (groundY >= 0) {
                        // We need to get the block ID at this position
                        // Same problem as callback: we need to READ the block.
                        // But here we are in the main chunk generation loop, so we have access to
                        // current chunk data!

                        int wx = worldX;
                        int wz = worldZ;

                        // Note: rx, rz are local to chunk
                        int lx = rx;
                        int lz = rz;

                        // We can read directly from blockData since rx/rz valid range check passed
                        // BUT blockData assumes linear index: (y * chunkSize + z) * chunkSize + x
                        // AND it stores numeric IDs. We need string IDs for comparison.

                        int groundBlockId = blockData[(groundY * chunkSize + lz) * chunkSize + lx];
                        String groundBlockStringId = Blocks.get(groundBlockId).getRegistryId().toString();

                        if (!validGround.contains(groundBlockStringId)) {
                            continue;
                        }
                    }
                }

                // Collision Check
                engine.utils.Math3D.AABB boundingBox = structure.getAABB(worldX, sy, worldZ);
                // Shrink slightly to allow touching? Or keep strict. Strict is safer.
                boolean collides = false;
                for (engine.utils.Math3D.AABB existing : allocatedSpace) {
                    if (boundingBox.intersects(existing)) {
                        collides = true;
                        break;
                    }
                }

                if (collides) {
                    continue;
                }

                // Add to allocated space
                allocatedSpace.add(boundingBox);

                // Place
                structure.place(
                        new Structure.StructureCallback() {
                            @Override
                            public void setBlock(int wx, int wy, int wz, int bid) {
                                placeBlock(placer, chunkX, chunkZ, blockData, wx, wy, wz, bid);
                            }

                            @Override
                            public int getBlock(int wx, int wy, int wz) {
                                // Best effort read
                                if (wy < 0 || wy >= chunkHeight)
                                    return 0; // Air

                                int targetChunkX = floorDiv(wx, chunkSize);
                                int targetChunkZ = floorDiv(wz, chunkSize);
                                int lx = mod(wx, chunkSize);
                                int lz = mod(wz, chunkSize);

                                if (targetChunkX == chunkX && targetChunkZ == chunkZ) {
                                    return blockData[(wy * chunkSize + lz) * chunkSize + lx];
                                } else if (placer != null && placer.canPlace(targetChunkX, targetChunkZ)) {
                                    return placer.getBlock(targetChunkX, targetChunkZ, lx, wy, lz);
                                }
                                return 0; // Unknown/Unloaded -> treat as Air? Or Solid?
                                // Ideally deferred ops handle writes, but for reads we might be out of luck if
                                // not loaded.
                            }
                        },
                        entityCallback,
                        worldX, sy, worldZ);
            }
        }
    }

    /**
     * Generate trees based on biome density.
     * Direct port from MinecraftOneFile.ensureFeatures()
     */
    private void generateTrees(int chunkX, int chunkZ, int[] blockData, int[] heightMap,
            BlockPlacer placer) {
        final int baseX = chunkX * chunkSize;
        final int baseZ = chunkZ * chunkSize;

        final int CELL = 4;
        final int MARGIN = 2;

        int woodId = Blocks.get("game:wood").getNumericId();
        int leavesId = Blocks.get("game:leaves").getNumericId();

        for (int x = 0; x < chunkSize; x++) {
            for (int z = 0; z < chunkSize; z++) {
                // Grid-based placement
                if (((baseX + x) % CELL) != 0 || ((baseZ + z) % CELL) != 0) {
                    continue;
                }

                int gx = (baseX + x) / CELL;
                int gz = (baseZ + z) / CELL;

                // Jitter within cell
                int jx = Math.round(hash01(gx, gz) * (CELL - 1));
                int jz = Math.round(hash01(gx + 12345, gz - 54321) * (CELL - 1));
                int tx = x + jx;
                int tz = z + jz;

                if (tx < 0 || tx >= chunkSize || tz < 0 || tz >= chunkSize) {
                    continue;
                }

                // Check margins
                if (tx < MARGIN || tx >= chunkSize - MARGIN ||
                        tz < MARGIN || tz >= chunkSize - MARGIN) {
                    continue;
                }

                int sy = heightMap[tz * chunkSize + tx];

                // Must be above water
                if (sy <= waterLevel + 3 || sy >= chunkHeight - 10) {
                    continue;
                }

                // Get biome tree density
                int wx = baseX + tx;
                int wz = baseZ + tz;
                Biome biome = biomeProvider.getBiome(wx, wz);
                float baseDensity = biome.getTreeDensity();

                // Forest mask from noise
                float forestMask = (float) noise.noise2(wx * 0.003, wz * 0.003);
                forestMask = forestMask * 0.5f + 0.5f;

                float chance = baseDensity * forestMask;

                if (hash01(gx * 911, gz * 997) >= chance) {
                    continue;
                }

                // Generate tree
                int treeH = 4 + (hash2i(gx * 13, gz * 7) % 3);

                // Trunk
                for (int ty = 0; ty < treeH && sy + 1 + ty < chunkHeight; ty++) {
                    placeBlock(placer, chunkX, chunkZ, blockData,
                            wx, sy + 1 + ty, wz, woodId);
                }

                // Leaves
                int top = sy + 1 + treeH;
                placeLeafBlob(placer, chunkX, chunkZ, blockData, wx, wz, top, leavesId);
            }
        }
    }

    /**
     * Place leaf blob for tree.
     * Direct port from MinecraftOneFile.placeLeafBlobWorld()
     */
    private void placeLeafBlob(BlockPlacer placer, int chunkX, int chunkZ, int[] blockData,
            int wxCenter, int wzCenter, int yTop, int leavesId) {
        placeLeafLayer(placer, chunkX, chunkZ, blockData, wxCenter, wzCenter, yTop - 1, 2, true, yTop, leavesId);
        placeLeafLayer(placer, chunkX, chunkZ, blockData, wxCenter, wzCenter, yTop - 2, 2, true, yTop, leavesId);
        placeLeafLayer(placer, chunkX, chunkZ, blockData, wxCenter, wzCenter, yTop, 1, false, yTop, leavesId);
        placeLeafLayer(placer, chunkX, chunkZ, blockData, wxCenter, wzCenter, yTop - 3, 1, false, yTop, leavesId);
    }

    private void placeLeafLayer(BlockPlacer placer, int chunkX, int chunkZ, int[] blockData,
            int wxCenter, int wzCenter, int y, int R, boolean round,
            int yTop, int leavesId) {
        if (y < 0 || y >= chunkHeight)
            return;

        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (round && Math.abs(dx) == R && Math.abs(dz) == R)
                    continue;
                if (dx == 0 && dz == 0 && y < yTop)
                    continue;

                placeBlock(placer, chunkX, chunkZ, blockData,
                        wxCenter + dx, y, wzCenter + dz, leavesId);
            }
        }
    }

    /**
     * Place a block, handling cross-chunk placement.
     */
    private void placeBlock(BlockPlacer placer, int currentChunkX, int currentChunkZ,
            int[] currentChunkData, int wx, int y, int wz, int blockId) {
        if (y < 0 || y >= chunkHeight)
            return;

        int targetChunkX = floorDiv(wx, chunkSize);
        int targetChunkZ = floorDiv(wz, chunkSize);
        int lx = mod(wx, chunkSize);
        int lz = mod(wz, chunkSize);

        if (targetChunkX == currentChunkX && targetChunkZ == currentChunkZ) {
            // Same chunk - place directly if not hard
            int existing = currentChunkData[(y * chunkSize + lz) * chunkSize + lx];
            if (!Blocks.isHard(existing)) {
                currentChunkData[(y * chunkSize + lz) * chunkSize + lx] = blockId;
            }
        } else {
            // Different chunk - try to place via placer, or defer
            if (placer != null && placer.canPlace(targetChunkX, targetChunkZ)) {
                int existing = placer.getBlock(targetChunkX, targetChunkZ, lx, y, lz);
                if (!Blocks.isHard(existing)) {
                    placer.setBlock(targetChunkX, targetChunkZ, lx, y, lz, blockId);
                }
            } else {
                // Defer for later
                long key = chunkKey(targetChunkX, targetChunkZ);
                deferredOps.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new BlockOp(lx, y, lz, blockId));
            }
        }
    }

    /**
     * Apply deferred operations to a chunk.
     */
    private void applyDeferred(int chunkX, int chunkZ, int[] blockData) {
        long key = chunkKey(chunkX, chunkZ);
        List<BlockOp> ops = deferredOps.remove(key);

        if (ops != null) {
            for (BlockOp op : ops) {
                int existing = blockData[(op.y * chunkSize + op.z) * chunkSize + op.x];
                if (!Blocks.isHard(existing)) {
                    blockData[(op.y * chunkSize + op.z) * chunkSize + op.x] = op.blockId;
                }
            }
        }
    }

    /**
     * Check if there are deferred operations for a chunk.
     */
    public boolean hasDeferredOps(int chunkX, int chunkZ) {
        return deferredOps.containsKey(chunkKey(chunkX, chunkZ));
    }

    // Utility methods

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int floorDiv(int a, int b) {
        int q = a / b;
        int r = a % b;
        if ((r != 0) && ((a ^ b) < 0))
            q--;
        return q;
    }

    private static int mod(int a, int b) {
        int m = a % b;
        if (m < 0)
            m += b;
        return m;
    }

    private static int hash2i(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    private static float hash01(int x, int z) {
        return (hash2i(x, z) & 0x7fffffff) / (float) 0x7fffffff;
    }

    // ==================== INTERFACES ====================

    /**
     * Interface for placing blocks in other chunks
     */
    public interface BlockPlacer {
        boolean canPlace(int chunkX, int chunkZ);

        int getBlock(int chunkX, int chunkZ, int lx, int ly, int lz);

        void setBlock(int chunkX, int chunkZ, int lx, int ly, int lz, int blockId);
    }

    /**
     * Deferred block operation
     */
    private static class BlockOp {
        final int x, y, z;
        final int blockId;

        BlockOp(int x, int y, int z, int blockId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
        }
    }
}
