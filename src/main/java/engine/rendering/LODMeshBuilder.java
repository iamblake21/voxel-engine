package engine.world.gen;

import engine.world.Chunk;
import engine.world.block.Block;
import engine.world.block.Blocks;

import java.util.ArrayList;

/**
 * Builds chunk meshes at different LOD levels.
 * 
 * LOD 0: Full detail - every visible block face
 * LOD 1: 2x2 blocks merged - uses heightmap, dominant color
 * LOD 2: 4x4 blocks merged
 * LOD 3: 8x8 blocks merged - for extreme distances, chunk is ~4 quads
 */
public class LODMeshBuilder {

    private static final int ATLAS_TILES_X = 8;
    private static final int ATLAS_TILES_Y = 8;
    private static final float TILE_U = 1.0f / ATLAS_TILES_X;
    private static final float TILE_V = 1.0f / ATLAS_TILES_Y;

    private final int chunkSize;
    private final int chunkHeight;
    private final MeshBuilder fullDetailBuilder;

    public LODMeshBuilder(int chunkSize, int chunkHeight) {
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.fullDetailBuilder = new MeshBuilder(chunkSize, chunkHeight);
    }

    /**
     * Build mesh at specified LOD level.
     * 
     * @param chunk The chunk data
     * @param world World access for neighbors
     * @param lod   LOD level (0 = full, 1 = 2x2, 2 = 4x4, 3 = 8x8)
     */
    public MeshBuilder.MeshData buildMesh(MeshBuilder.ChunkData chunk, MeshBuilder.WorldAccess world, int lod) {
        if (lod <= 0) {
            // Full detail - use standard builder
            return fullDetailBuilder.buildMesh(chunk, world);
        }

        int scale = 1 << lod; // 2, 4, 8
        return buildSimplifiedMesh(chunk, scale);
    }

    /**
     * Build a simplified mesh by merging blocks.
     */
    private MeshBuilder.MeshData buildSimplifiedMesh(MeshBuilder.ChunkData chunk, int scale) {
        ArrayList<Float> solidV = new ArrayList<>();
        ArrayList<Float> waterV = new ArrayList<>();

        int gridSize = chunkSize / scale;

        for (int gx = 0; gx < gridSize; gx++) {
            for (int gz = 0; gz < gridSize; gz++) {
                int startX = gx * scale;
                int startZ = gz * scale;

                // Analyze this cell
                CellInfo info = analyzeCell(chunk, startX, startZ, scale);

                if (info.maxHeight < 0)
                    continue; // All air

                // Create simplified geometry for this cell
                if (info.hasWater) {
                    addSimplifiedWater(waterV, gx, gz, scale, info);
                }

                if (info.hasSolid) {
                    addSimplifiedTerrain(solidV, gx, gz, scale, info);
                }
            }
        }

        return new MeshBuilder.MeshData(
                toFloatArray(solidV),
                new float[0], // No transparent at LOD > 0
                toFloatArray(waterV));
    }

    /**
     * Analyze a cell to determine its simplified representation.
     */
    private CellInfo analyzeCell(MeshBuilder.ChunkData chunk, int startX, int startZ, int scale) {
        CellInfo info = new CellInfo();
        info.maxHeight = -1;
        info.minHeight = chunkHeight;

        int[] blockCounts = new int[64]; // Simple histogram
        int totalHeight = 0;
        int heightSamples = 0;

        for (int dx = 0; dx < scale; dx++) {
            for (int dz = 0; dz < scale; dz++) {
                int x = startX + dx;
                int z = startZ + dz;

                // Find surface
                for (int y = chunkHeight - 1; y >= 0; y--) {
                    int blockId = chunk.getBlock(x, y, z);

                    if (blockId == Blocks.AIR().getNumericId())
                        continue;

                    Block block = Blocks.get(blockId);

                    if (block.isLiquid()) {
                        info.hasWater = true;
                        info.waterLevel = Math.max(info.waterLevel, y);
                    } else if (!block.isAir()) {
                        info.hasSolid = true;
                        info.maxHeight = Math.max(info.maxHeight, y);
                        info.minHeight = Math.min(info.minHeight, y);
                        totalHeight += y;
                        heightSamples++;

                        // Count block type
                        if (blockId < blockCounts.length) {
                            blockCounts[blockId]++;
                        }
                        break;
                    }
                }
            }
        }

        if (heightSamples > 0) {
            info.avgHeight = totalHeight / heightSamples;
        }

        // Find dominant block
        int maxCount = 0;
        for (int i = 1; i < blockCounts.length; i++) {
            if (blockCounts[i] > maxCount) {
                maxCount = blockCounts[i];
                info.dominantBlock = i;
            }
        }

        return info;
    }

    /**
     * Add simplified terrain geometry for a cell.
     */
    private void addSimplifiedTerrain(ArrayList<Float> vertices, int gx, int gz, int scale, CellInfo info) {
        float x1 = gx * scale;
        float z1 = gz * scale;
        float x2 = x1 + scale;
        float z2 = z1 + scale;

        // Get texture for dominant block
        Block block = Blocks.get(info.dominantBlock);
        int tileX = block.getTextureTileX(0, 1, 0); // Top face
        int tileY = block.getTextureTileY(0, 1, 0);
        float u0 = tileX * TILE_U;
        float v0 = tileY * TILE_V;
        float u1 = u0 + TILE_U;
        float v1 = v0 + TILE_V;

        // Use average height with slight variation
        float y = info.avgHeight + 1;

        // Top face (2 triangles)
        // AO = 1.0 for simplified mesh
        addVertex(vertices, x1, y, z1, u0, v0, 1.0f, 2);
        addVertex(vertices, x2, y, z1, u1, v0, 1.0f, 2);
        addVertex(vertices, x2, y, z2, u1, v1, 1.0f, 2);

        addVertex(vertices, x1, y, z1, u0, v0, 1.0f, 2);
        addVertex(vertices, x2, y, z2, u1, v1, 1.0f, 2);
        addVertex(vertices, x1, y, z2, u0, v1, 1.0f, 2);

        // Add side faces if there's height variation
        float bottomY = Math.max(0, info.minHeight);

        // Simple side faces
        // North face (z = z1)
        addVertex(vertices, x1, bottomY, z1, u0, v1, 0.8f, 5);
        addVertex(vertices, x2, bottomY, z1, u1, v1, 0.8f, 5);
        addVertex(vertices, x2, y, z1, u1, v0, 0.8f, 5);

        addVertex(vertices, x1, bottomY, z1, u0, v1, 0.8f, 5);
        addVertex(vertices, x2, y, z1, u1, v0, 0.8f, 5);
        addVertex(vertices, x1, y, z1, u0, v0, 0.8f, 5);

        // South face (z = z2)
        addVertex(vertices, x1, bottomY, z2, u0, v1, 0.8f, 4);
        addVertex(vertices, x1, y, z2, u0, v0, 0.8f, 4);
        addVertex(vertices, x2, y, z2, u1, v0, 0.8f, 4);

        addVertex(vertices, x1, bottomY, z2, u0, v1, 0.8f, 4);
        addVertex(vertices, x2, y, z2, u1, v0, 0.8f, 4);
        addVertex(vertices, x2, bottomY, z2, u1, v1, 0.8f, 4);

        // West face (x = x1)
        addVertex(vertices, x1, bottomY, z1, u0, v1, 0.7f, 1);
        addVertex(vertices, x1, y, z1, u0, v0, 0.7f, 1);
        addVertex(vertices, x1, y, z2, u1, v0, 0.7f, 1);

        addVertex(vertices, x1, bottomY, z1, u0, v1, 0.7f, 1);
        addVertex(vertices, x1, y, z2, u1, v0, 0.7f, 1);
        addVertex(vertices, x1, bottomY, z2, u1, v1, 0.7f, 1);

        // East face (x = x2)
        addVertex(vertices, x2, bottomY, z1, u0, v1, 0.7f, 0);
        addVertex(vertices, x2, bottomY, z2, u1, v1, 0.7f, 0);
        addVertex(vertices, x2, y, z2, u1, v0, 0.7f, 0);

        addVertex(vertices, x2, bottomY, z1, u0, v1, 0.7f, 0);
        addVertex(vertices, x2, y, z2, u1, v0, 0.7f, 0);
        addVertex(vertices, x2, y, z1, u0, v0, 0.7f, 0);
    }

    /**
     * Add simplified water geometry.
     */
    private void addSimplifiedWater(ArrayList<Float> vertices, int gx, int gz, int scale, CellInfo info) {
        float x1 = gx * scale;
        float z1 = gz * scale;
        float x2 = x1 + scale;
        float z2 = z1 + scale;
        float y = info.waterLevel + 0.9f;

        // Water texture
        int tileX = 6; // Water tile
        int tileY = 0;
        float u0 = tileX * TILE_U;
        float v0 = tileY * TILE_V;
        float u1 = u0 + TILE_U;
        float v1 = v0 + TILE_V;

        // Top face only
        addVertex(vertices, x1, y, z1, u0, v0, 1.0f, 2);
        addVertex(vertices, x2, y, z1, u1, v0, 1.0f, 2);
        addVertex(vertices, x2, y, z2, u1, v1, 1.0f, 2);

        addVertex(vertices, x1, y, z1, u0, v0, 1.0f, 2);
        addVertex(vertices, x2, y, z2, u1, v1, 1.0f, 2);
        addVertex(vertices, x1, y, z2, u0, v1, 1.0f, 2);
    }

    private void addVertex(ArrayList<Float> list, float x, float y, float z,
            float u, float v, float ao, int faceIdx) {
        list.add(x);
        list.add(y);
        list.add(z);
        list.add(u);
        list.add(v);
        list.add(ao);
        list.add((float) faceIdx);
    }

    private float[] toFloatArray(ArrayList<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * Cell analysis result.
     */
    private static class CellInfo {
        int maxHeight = -1;
        int minHeight = 256;
        int avgHeight = 0;
        int waterLevel = 0;
        int dominantBlock = 1;
        boolean hasSolid = false;
        boolean hasWater = false;
    }
}
