package engine.world.gen;

import engine.world.block.Block;
import engine.world.block.Blocks;

import java.util.ArrayList;

/**
 * Builds chunk meshes from block data with LOD (Level of Detail) support.
 * 
 * LOD Levels:
 * - LOD 0: Full detail - all faces, full AO, transparency
 * - LOD 1: High detail - all faces, simplified AO
 * - LOD 2: Medium detail - skip some internal faces, no transparency separation
 * - LOD 3: Low detail - aggressive culling, no AO
 * 
 * This is a direct port from MinecraftOneFile.java - the face generation
 * logic is proven to work correctly with the original UV mapping.
 */
public class MeshBuilder {
    
    // Atlas constants
    private static final int ATLAS_TILES_X = 8;
    private static final int ATLAS_TILES_Y = 8;
    private static final float TILE_U = 1.0f / ATLAS_TILES_X;
    private static final float TILE_V = 1.0f / ATLAS_TILES_Y;
    
    // Chunk constants
    private final int chunkSize;
    private final int chunkHeight;
    
    // LOD distance thresholds (in chunk units squared)
    public static final int LOD_0_MAX_DIST_SQ = 4 * 4;      // 0-4 chunks: full detail
    public static final int LOD_1_MAX_DIST_SQ = 8 * 8;      // 4-8 chunks: high detail  
    public static final int LOD_2_MAX_DIST_SQ = 16 * 16;    // 8-16 chunks: medium detail
    // Beyond 16 chunks: LOD 3 (low detail)
    
    public MeshBuilder(int chunkSize, int chunkHeight) {
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
    }
    
    /**
     * Calculate LOD level based on chunk distance from camera.
     * @param chunkDistSq Squared distance in chunk units
     * @return LOD level 0-3
     */
    public static int calculateLOD(int chunkDistSq) {
        if (chunkDistSq <= LOD_0_MAX_DIST_SQ) return 0;
        if (chunkDistSq <= LOD_1_MAX_DIST_SQ) return 1;
        if (chunkDistSq <= LOD_2_MAX_DIST_SQ) return 2;
        return 3;
    }
    
    /**
     * Build mesh data for a chunk (LOD 0 - full detail).
     * Backward compatible method - original API.
     */
    public MeshData buildMesh(ChunkData chunk, WorldAccess world) {
        return buildMeshLOD(chunk, world, 0);
    }
    
    /**
     * Build mesh data for a chunk at specified LOD level.
     * 
     * @param chunk The chunk data
     * @param world World access for neighbor lookups
     * @param lod LOD level (0-3)
     * @return Mesh data with solid, transparent, and water vertices
     */
    public MeshData buildMeshLOD(ChunkData chunk, WorldAccess world, int lod) {
        ArrayList<Float> solidV = new ArrayList<>();
        ArrayList<Float> transpV = new ArrayList<>();
        ArrayList<Float> waterV = new ArrayList<>();
        
        // LOD 2+: Skip transparent mesh (merge into solid)
        boolean skipTransparent = lod >= 2;
        // LOD 1+: Use simplified AO
        boolean simplifiedAO = lod >= 1;
        // LOD 3: More aggressive face culling
        boolean aggressiveCull = lod >= 3;
        
        for (int x = 0; x < chunkSize; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < chunkSize; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    Block block = Blocks.get(blockId);
                    
                    if (block.isAir()) continue;
                    
                    // Check all 6 faces
                    int[][] dirs = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
                    
                    for (int[] d : dirs) {
                        int nx = d[0], ny = d[1], nz = d[2];
                        
                        if (!faceVisible(chunk, world, x, y, z, block, nx, ny, nz, aggressiveCull)) {
                            continue;
                        }
                        
                        // Determine which mesh to add to
                        if (block.isLiquid()) {
                            addFace(waterV, chunk, world, x, y, z, nx, ny, nz, block, true, simplifiedAO);
                        } else if (block.isTransparent()) {
                            if (!skipTransparent) {
                                addFace(transpV, chunk, world, x, y, z, nx, ny, nz, block, true, simplifiedAO);
                            } else {
                                // At LOD 2+, treat transparent as solid
                                addFace(solidV, chunk, world, x, y, z, nx, ny, nz, block, false, simplifiedAO);
                            }
                        } else {
                            addFace(solidV, chunk, world, x, y, z, nx, ny, nz, block, false, simplifiedAO);
                        }
                    }
                }
            }
        }
        
        return new MeshData(
            toFloatArray(solidV),
            skipTransparent ? new float[0] : toFloatArray(transpV),
            toFloatArray(waterV)
        );
    }
    
    /**
     * Check if a face should be rendered.
     * Direct port from MinecraftOneFile.faceVisible() with LOD awareness.
     */
    private boolean faceVisible(ChunkData chunk, WorldAccess world, 
                                int x, int y, int z, Block self, 
                                int nx, int ny, int nz, boolean aggressiveCull) {
        int neighborId = getBlockAt(chunk, world, x + nx, y + ny, z + nz);
        Block neighbor = Blocks.get(neighborId);
        
        // Water special case
        if (self.isLiquid()) {
            if (neighbor.isLiquid()) return false;  // Same liquid type
            if (neighbor.isAir()) return true;       // Air shows face
            return false;                            // Solid hides face
        }
        
        // Aggressive culling (LOD 3): only show faces to air
        if (aggressiveCull) {
            return neighbor.isAir();
        }
        
        // Normal blocks: show face if neighbor is air, liquid, or transparent leaves
        return neighbor.isAir() || neighbor.isLiquid() || 
               (neighbor.isTransparent() && neighbor.isSolid());
    }
    
    /**
     * Get block at position, handling cross-chunk access.
     */
    private int getBlockAt(ChunkData chunk, WorldAccess world, int x, int y, int z) {
        if (y < 0 || y >= chunkHeight) {
            return Blocks.AIR().getNumericId();
        }
        
        if (x >= 0 && x < chunkSize && z >= 0 && z < chunkSize) {
            return chunk.getBlock(x, y, z);
        }
        
        // Cross-chunk access
        int worldX = chunk.getWorldX() + x;
        int worldZ = chunk.getWorldZ() + z;
        return world.peekBlock(worldX, y, worldZ);
    }
    
    /**
     * Add a face to the vertex list.
     * Direct port from MinecraftOneFile.addFaceInto() with simplified AO option.
     */
    private void addFace(ArrayList<Float> dst, ChunkData chunk, WorldAccess world,
                         int bx, int by, int bz, int nx, int ny, int nz, 
                         Block block, boolean isTransp, boolean simplifiedAO) {
        
        // Vertex positions - exact copy from original
        float[][] v;
        if (nx == 1) {
            v = new float[][] {
                {bx+1, by, bz}, {bx+1, by+1, bz}, 
                {bx+1, by+1, bz+1}, {bx+1, by, bz+1}
            };
        } else if (nx == -1) {
            v = new float[][] {
                {bx, by, bz}, {bx, by, bz+1}, 
                {bx, by+1, bz+1}, {bx, by+1, bz}
            };
        } else if (ny == 1) {
            v = new float[][] {
                {bx, by+1, bz}, {bx+1, by+1, bz}, 
                {bx+1, by+1, bz+1}, {bx, by+1, bz+1}
            };
        } else if (ny == -1) {
            v = new float[][] {
                {bx, by, bz}, {bx, by, bz+1}, 
                {bx+1, by, bz+1}, {bx+1, by, bz}
            };
        } else if (nz == 1) {
            v = new float[][] {
                {bx, by, bz+1}, {bx, by+1, bz+1}, 
                {bx+1, by+1, bz+1}, {bx+1, by, bz+1}
            };
        } else { // nz == -1
            v = new float[][] {
                {bx, by, bz}, {bx+1, by, bz}, 
                {bx+1, by+1, bz}, {bx, by+1, bz}
            };
        }
        
        // UV coordinates - exact copy from original
        float[][] uv;
        if (nx == 1) {
            uv = new float[][] {{0,0}, {0,1}, {1,1}, {1,0}};
        } else if (nx == -1) {
            uv = new float[][] {{1,0}, {0,0}, {0,1}, {1,1}};
        } else if (ny == 1) {
            uv = new float[][] {{0,1}, {1,1}, {1,0}, {0,0}};
        } else if (ny == -1) {
            uv = new float[][] {{0,0}, {0,1}, {1,1}, {1,0}};
        } else if (nz == 1) {
            uv = new float[][] {{1,0}, {1,1}, {0,1}, {0,0}};
        } else { // nz == -1
            uv = new float[][] {{0,0}, {1,0}, {1,1}, {0,1}};
        }
        
        // Compute AO - use simplified version for higher LOD
        float[] ao;
        if (simplifiedAO) {
            // Simplified AO: just directional lighting
            float brightness;
            if (ny == 1) brightness = 1.0f;        // Top face: full bright
            else if (ny == -1) brightness = 0.5f;  // Bottom face: darkest
            else if (nx != 0) brightness = 0.7f;   // X faces: medium
            else brightness = 0.8f;                 // Z faces: slightly brighter
            ao = new float[] {brightness, brightness, brightness, brightness};
        } else {
            ao = computeFaceAO(chunk, world, bx, by, bz, nx, ny, nz);
        }
        
        // Get tile from block
        int tileX = block.getTextureTileX(nx, ny, nz);
        int tileY = block.getTextureTileY(nx, ny, nz);
        float u0 = tileX * TILE_U;
        float v0 = tileY * TILE_V;
        
        // Grass side flip - from original
        boolean flipV = isGrassSide(block, ny);
        
        float v0loc = flipV ? 1f - uv[0][1] : uv[0][1];
        float v1loc = flipV ? 1f - uv[1][1] : uv[1][1];
        float v2loc = flipV ? 1f - uv[2][1] : uv[2][1];
        float v3loc = flipV ? 1f - uv[3][1] : uv[3][1];
        
        int faceIdx = (nx == 1) ? 0 : (nx == -1) ? 1 : (ny == 1) ? 2 : (ny == -1) ? 3 : (nz == 1) ? 4 : 5;
        
        // Two triangles
        push(dst, v[0], u0 + uv[0][0] * TILE_U, v0 + v0loc * TILE_V, ao[0], faceIdx);
        push(dst, v[1], u0 + uv[1][0] * TILE_U, v0 + v1loc * TILE_V, ao[1], faceIdx);
        push(dst, v[2], u0 + uv[2][0] * TILE_U, v0 + v2loc * TILE_V, ao[2], faceIdx);
        
        push(dst, v[0], u0 + uv[0][0] * TILE_U, v0 + v0loc * TILE_V, ao[0], faceIdx);
        push(dst, v[2], u0 + uv[2][0] * TILE_U, v0 + v2loc * TILE_V, ao[2], faceIdx);
        push(dst, v[3], u0 + uv[3][0] * TILE_U, v0 + v3loc * TILE_V, ao[3], faceIdx);
    }
    
    /**
     * Check if this is a grass side that needs V flip
     */
    private boolean isGrassSide(Block block, int ny) {
        // Grass sides need flipping (ny == 0 means horizontal face)
        if (ny != 0) return false;
        
        // Check if it's grass by looking at registry ID
        if (block.getRegistryId() == null) return false;
        return block.getRegistryId().getPath().equals("grass");
    }
    
    /**
     * Compute ambient occlusion for face corners.
     * Direct port from MinecraftOneFile.computeFaceAO()
     */
    private float[] computeFaceAO(ChunkData chunk, WorldAccess world,
                                   int x, int y, int z, int nx, int ny, int nz) {
        float[] out = new float[4];
        int fx = x + nx, fy = y + ny, fz = z + nz;
        
        if (nx != 0) {
            boolean sYp = isOccluder(getBlockAt(chunk, world, fx, fy + 1, fz));
            boolean sYm = isOccluder(getBlockAt(chunk, world, fx, fy - 1, fz));
            boolean sZp = isOccluder(getBlockAt(chunk, world, fx, fy, fz + 1));
            boolean sZm = isOccluder(getBlockAt(chunk, world, fx, fy, fz - 1));
            boolean c1 = isOccluder(getBlockAt(chunk, world, fx, fy + 1, fz - 1));
            boolean c2 = isOccluder(getBlockAt(chunk, world, fx, fy + 1, fz + 1));
            boolean c3 = isOccluder(getBlockAt(chunk, world, fx, fy - 1, fz + 1));
            boolean c4 = isOccluder(getBlockAt(chunk, world, fx, fy - 1, fz - 1));
            out[0] = cornerAO(sYp, sZm, c1);
            out[1] = cornerAO(sYp, sZp, c2);
            out[2] = cornerAO(sYm, sZp, c3);
            out[3] = cornerAO(sYm, sZm, c4);
        } else if (ny != 0) {
            boolean sXp = isOccluder(getBlockAt(chunk, world, fx + 1, fy, fz));
            boolean sXm = isOccluder(getBlockAt(chunk, world, fx - 1, fy, fz));
            boolean sZp = isOccluder(getBlockAt(chunk, world, fx, fy, fz + 1));
            boolean sZm = isOccluder(getBlockAt(chunk, world, fx, fy, fz - 1));
            boolean c1 = isOccluder(getBlockAt(chunk, world, fx - 1, fy, fz - 1));
            boolean c2 = isOccluder(getBlockAt(chunk, world, fx + 1, fy, fz - 1));
            boolean c3 = isOccluder(getBlockAt(chunk, world, fx + 1, fy, fz + 1));
            boolean c4 = isOccluder(getBlockAt(chunk, world, fx - 1, fy, fz + 1));
            out[0] = cornerAO(sXm, sZm, c1);
            out[1] = cornerAO(sXp, sZm, c2);
            out[2] = cornerAO(sXp, sZp, c3);
            out[3] = cornerAO(sXm, sZp, c4);
        } else { // nz != 0
            boolean sXp = isOccluder(getBlockAt(chunk, world, fx + 1, fy, fz));
            boolean sXm = isOccluder(getBlockAt(chunk, world, fx - 1, fy, fz));
            boolean sYp = isOccluder(getBlockAt(chunk, world, fx, fy + 1, fz));
            boolean sYm = isOccluder(getBlockAt(chunk, world, fx, fy - 1, fz));
            boolean c1 = isOccluder(getBlockAt(chunk, world, fx - 1, fy + 1, fz));
            boolean c2 = isOccluder(getBlockAt(chunk, world, fx + 1, fy + 1, fz));
            boolean c3 = isOccluder(getBlockAt(chunk, world, fx + 1, fy - 1, fz));
            boolean c4 = isOccluder(getBlockAt(chunk, world, fx - 1, fy - 1, fz));
            out[0] = cornerAO(sXm, sYp, c1);
            out[1] = cornerAO(sXp, sYp, c2);
            out[2] = cornerAO(sXp, sYm, c3);
            out[3] = cornerAO(sXm, sYm, c4);
        }
        
        return out;
    }
    
    private boolean isOccluder(int blockId) {
        Block block = Blocks.get(blockId);
        return block.isOpaque();
    }
    
    private float cornerAO(boolean s1, boolean s2, boolean c) {
        int occ = (s1 ? 1 : 0) + (s2 ? 1 : 0) + (c ? 1 : 0);
        return occ == 0 ? 1.00f : occ == 1 ? 0.90f : occ == 2 ? 0.75f : 0.60f;
    }
    
    private void push(ArrayList<Float> a, float[] pos, float u, float v, float ao, int faceIdx) {
        a.add(pos[0]);
        a.add(pos[1]);
        a.add(pos[2]);
        a.add(u);
        a.add(v);
        a.add(ao);
        a.add((float) faceIdx);
    }
    
    private float[] toFloatArray(ArrayList<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
    
    // ==================== INTERFACES ====================
    
    /**
     * Interface for accessing chunk block data
     */
    public interface ChunkData {
        int getBlock(int x, int y, int z);
        int getWorldX();
        int getWorldZ();
    }
    
    /**
     * Interface for cross-chunk block access
     */
    public interface WorldAccess {
        int peekBlock(int worldX, int worldY, int worldZ);
    }
    
    /**
     * Result of mesh building
     */
    public static class MeshData {
        public final float[] solidVertices;
        public final float[] transparentVertices;
        public final float[] waterVertices;
        
        public MeshData(float[] solid, float[] transparent, float[] water) {
            this.solidVertices = solid;
            this.transparentVertices = transparent;
            this.waterVertices = water;
        }
    }
}