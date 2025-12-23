package engine.world.gen;

import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.block.model.BlockModel;
import engine.world.block.model.BlockModelLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import engine.utils.FloatArrayList;

/**
 * Builds chunk meshes from block data with LOD support.
 * 
 * Uses a canonical vertex ordering system where all faces share the same
 * local coordinate convention, making AO and smooth lighting consistent.
 * 
 * Face vertex order (looking at face from outside):
 * 3 ---- 2
 * | |
 * | |
 * 0 ---- 1
 * 
 * v0 = bottom-left (-U, -V)
 * v1 = bottom-right (+U, -V)
 * v2 = top-right (+U, +V)
 * v3 = top-left (-U, +V)
 */
public class MeshBuilder {

    // ==================== CONSTANTS ====================

    private static final int ATLAS_TILES_X = 8;
    private static final int ATLAS_TILES_Y = 8;

    // Face indices
    private static final int FACE_POS_X = 0;
    private static final int FACE_NEG_X = 1;
    private static final int FACE_POS_Y = 2;
    private static final int FACE_NEG_Y = 3;
    private static final int FACE_POS_Z = 4;
    private static final int FACE_NEG_Z = 5;

    // Face normals [faceIdx] -> {nx, ny, nz}
    private static final int[][] FACE_NORMAL = {
            { 1, 0, 0 }, // +X
            { -1, 0, 0 }, // -X
            { 0, 1, 0 }, // +Y
            { 0, -1, 0 }, // -Y
            { 0, 0, 1 }, // +Z
            { 0, 0, -1 } // -Z
    };

    // Tangent U = "right" direction when looking at face from outside
    private static final int[][] FACE_TANGENT_U = {
            { 0, 0, -1 }, // +X: U points toward -Z
            { 0, 0, 1 }, // -X: U points toward +Z
            { 1, 0, 0 }, // +Y: U points toward +X
            { 1, 0, 0 }, // -Y: U points toward +X
            { 1, 0, 0 }, // +Z: U points toward +X
            { -1, 0, 0 } // -Z: U points toward -X
    };

    // Tangent V = "up" direction when looking at face from outside
    private static final int[][] FACE_TANGENT_V = {
            { 0, 1, 0 }, // +X: V points toward +Y
            { 0, 1, 0 }, // -X: V points toward +Y
            { 0, 0, 1 }, // +Y: V points toward +Z
            { 0, 0, -1 }, // -Y: V points toward -Z
            { 0, 1, 0 }, // +Z: V points toward +Y
            { 0, 1, 0 } // -Z: V points toward +Y
    };

    // LOD thresholds (chunk distance squared)
    public static final int LOD_0_MAX_DIST_SQ = 4 * 4;
    public static final int LOD_1_MAX_DIST_SQ = 8 * 8;
    public static final int LOD_2_MAX_DIST_SQ = 16 * 16;

    // AO intensity mapping (0=darkest, 3=brightest)
    private static final float[] AO_CURVE = { 0.4f, 0.6f, 0.8f, 1.0f };

    // ==================== INSTANCE FIELDS ====================

    private final int chunkSize;
    private final int chunkHeight;

    // Custom model cache
    private static final Map<String, CompiledModel> COMPILED_MODELS = new HashMap<>();

    // ==================== CONSTRUCTOR ====================

    // ==================== CONSTRUCTOR ====================

    // ThreadLocal buffers to avoid allocation churn
    private static final ThreadLocal<MeshBuffers> THREAD_BUFFERS = ThreadLocal.withInitial(MeshBuffers::new);

    private static class MeshBuffers {
        final FloatArrayList solid = new FloatArrayList(16384);
        final FloatArrayList transp = new FloatArrayList(4096);
        final FloatArrayList water = new FloatArrayList(4096);
        // Custom buffers maps usually stay small, but we can reuse the map itself
        final Map<String, FloatArrayList> custom = new HashMap<>();
    }

    public MeshBuilder(int chunkSize, int chunkHeight) {
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
    }

    // ==================== PUBLIC API ====================

    public static int calculateLOD(int chunkDistSq) {
        if (chunkDistSq <= LOD_0_MAX_DIST_SQ)
            return 0;
        if (chunkDistSq <= LOD_1_MAX_DIST_SQ)
            return 1;
        if (chunkDistSq <= LOD_2_MAX_DIST_SQ)
            return 2;
        return 3;
    }

    public MeshData buildMesh(ChunkData chunk, WorldAccess world) {
        return buildMeshLOD(chunk, world, 0);
    }

    public MeshData buildMeshLOD(ChunkData chunk, WorldAccess world, int lod) {
        MeshBuffers buffers = THREAD_BUFFERS.get();
        FloatArrayList solidV = buffers.solid;
        FloatArrayList transpV = buffers.transp;
        FloatArrayList waterV = buffers.water;
        Map<String, FloatArrayList> customBuffers = buffers.custom;

        // Clear and reuse
        solidV.clear();
        transpV.clear();
        waterV.clear();
        customBuffers.clear();

        boolean skipTransparent = lod >= 2;
        boolean simplifiedLighting = lod >= 1;
        boolean aggressiveCull = lod >= 3;

        for (int x = 0; x < chunkSize; x++) {
            for (int y = 0; y < chunkHeight; y++) {
                for (int z = 0; z < chunkSize; z++) {
                    int blockId = chunk.getBlock(x, y, z);
                    Block block = Blocks.get(blockId);
                    engine.world.block.state.BlockState state = Block.STATE_IDS.get(blockId);

                    if (block.isAir())
                        continue;

                    // Fetch Biome Color
                    int color = 0xFFFFFFFF; // Default White
                    if (block.getProperties().hasTintGrass()) {
                        int wx = chunk.getWorldX() + x;
                        int wz = chunk.getWorldZ() + z;
                        color = world.getBiome(wx, wz).getProperties().getGrassColor();
                    } else if (block.getProperties().hasTintFoliage()) {
                        int wx = chunk.getWorldX() + x;
                        int wz = chunk.getWorldZ() + z;
                        color = world.getBiome(wx, wz).getProperties().getFoliageColor();
                    }

                    // Check for custom model from properties OR blockstate system
                    boolean hasBlockState = (state != null
                            && engine.rendering.model.BlockStateLoader.getVariant(state) != null);

                    if (block.getProperties().hasCustomModel() || hasBlockState) {
                        // Pass color to renderCustomModel (needs update signature, skipping for now or
                        // assume white for models)
                        // TODO: Support tint for models if needed
                        renderCustomModel(solidV, transpV, waterV, customBuffers, chunk, world,
                                x, y, z, block, simplifiedLighting, skipTransparent);
                    } else {
                        renderCubeBlock(solidV, transpV, waterV, customBuffers, chunk, world,
                                x, y, z, block, simplifiedLighting, aggressiveCull, skipTransparent, color); // Updated
                                                                                                             // signature
                    }
                }
            }
        }

        // Convert custom buffers to arrays
        java.util.Map<String, float[]> customMeshes = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, FloatArrayList> entry : customBuffers.entrySet()) {
            customMeshes.put(entry.getKey(), entry.getValue().toArray());
        }

        return new MeshData(
                solidV.toArray(),
                skipTransparent ? new float[0] : transpV.toArray(),
                waterV.toArray(),
                customMeshes);
    }

    // ==================== CUBE RENDERING ====================

    private void renderCubeBlock(FloatArrayList solidV, FloatArrayList transpV, FloatArrayList waterV,
            java.util.Map<String, FloatArrayList> customBuffers,
            ChunkData chunk, WorldAccess world,
            int x, int y, int z, Block block,
            boolean simplifiedLighting, boolean aggressiveCull, boolean skipTransparent, int color) {

        float[] heights = new float[4];
        if (block.isLiquid()) {
            boolean isFalling = (getBlockAt(chunk, world, x, y + 1, z) == block.getNumericId());

            if (isFalling) {
                Arrays.fill(heights, 1.0f);
            } else {
                heights[0] = getVertexFluidHeight(chunk, world, x, y, z); // NW Corner (x,z)
                heights[1] = getVertexFluidHeight(chunk, world, x + 1, y, z); // NE Corner (x+1,z)
                heights[2] = getVertexFluidHeight(chunk, world, x + 1, y, z + 1); // SE Corner (x+1,z+1)
                heights[3] = getVertexFluidHeight(chunk, world, x, y, z + 1); // SW Corner (x,z+1)
            }
        } else {
            Arrays.fill(heights, 1.0f);
        }

        // Check if this is a grass block for special rendering
        boolean isGrass = block.getRegistryId() != null && block.getRegistryId().getPath().equals("grass");
        Block dirtBlock = isGrass ? engine.world.block.Blocks.get("game:dirt") : null;

        for (int faceIdx = 0; faceIdx < 6; faceIdx++) {
            int[] n = FACE_NORMAL[faceIdx];

            if (!isFaceVisible(chunk, world, x, y, z, block, n[0], n[1], n[2], aggressiveCull)) {
                continue;
            }

            FloatArrayList target;
            String customTexture = null;

            // Check custom textures
            if (block.getProperties().hasCustomTextures()) {
                if (faceIdx == FACE_POS_Y && block.getProperties().getTextureTop() != null) {
                    customTexture = block.getProperties().getTextureTop();
                } else if (faceIdx == FACE_NEG_Y && block.getProperties().getTextureBottom() != null) {
                    customTexture = block.getProperties().getTextureBottom();
                } else if (faceIdx != FACE_POS_Y && faceIdx != FACE_NEG_Y
                        && block.getProperties().getTextureSide() != null) {
                    customTexture = block.getProperties().getTextureSide();
                }
            }

            if (customTexture != null) {
                target = customBuffers.computeIfAbsent(customTexture, k -> new FloatArrayList());
            } else if (block.isLiquid()) {
                target = waterV;
            } else if (block.isTransparent()) {
                target = skipTransparent ? solidV : transpV;
            } else {
                target = solidV;
            }

            float[] faceHeights = new float[4];
            if (block.isLiquid()) {
                if (faceIdx == FACE_POS_Y) { // Top face
                    faceHeights = heights;
                } else if (faceIdx == FACE_NEG_Y) { // Bottom face
                    Arrays.fill(faceHeights, 0.0f);
                } else {
                    // Side faces
                    faceHeights[0] = 0.0f;
                    faceHeights[1] = 0.0f;

                    if (faceIdx == FACE_NEG_Z) { // North
                        faceHeights[2] = heights[0];
                        faceHeights[3] = heights[1];
                    } else if (faceIdx == FACE_POS_Z) { // South
                        faceHeights[2] = heights[2];
                        faceHeights[3] = heights[3];
                    } else if (faceIdx == FACE_NEG_X) { // West
                        faceHeights[2] = heights[3];
                        faceHeights[3] = heights[0];
                    } else if (faceIdx == FACE_POS_X) { // East
                        faceHeights[2] = heights[1];
                        faceHeights[3] = heights[2];
                    }
                }
            } else {
                Arrays.fill(faceHeights, 1.0f);
            }

            // Handling Grass Block multi-layer rendering
            if (isGrass) {
                if (faceIdx == FACE_POS_Y) {
                    // Top: Biome Color
                    addFace(target, chunk, world, (float) x, (float) y, (float) z, faceIdx, block, simplifiedLighting,
                            faceHeights, color, false);
                } else if (faceIdx == FACE_NEG_Y) {
                    // Bottom: White (Dirt)
                    addFace(target, chunk, world, (float) x, (float) y, (float) z, faceIdx, block, simplifiedLighting,
                            faceHeights, 0xFFFFFFFF, false);
                } else {
                    // Sides: Multi-layer
                    // Layer 0: Dirt (White) -> Solid Buffer
                    if (dirtBlock != null && !dirtBlock.isAir()) {
                        addFace(solidV, chunk, world, (float) x, (float) y, (float) z, faceIdx, dirtBlock,
                                simplifiedLighting, faceHeights, 0xFFFFFFFF, false);
                    }
                    // Layer 1: Overlay (Biome Color) -> Custom Texture Buffer
                    if (!skipTransparent) {
                        // Use the standalone texture for the overlay
                        String overlayTexture = "textures/blocks/grass_block_side_overlay.png";
                        FloatArrayList overlayBuffer = customBuffers.computeIfAbsent(overlayTexture,
                                k -> new FloatArrayList());

                        // Offset overlay slightly to avoid Z-fighting
                        float offset = 0f;
                        float ox = x + n[0] * offset;
                        float oy = y + n[1] * offset;
                        float oz = z + n[2] * offset;

                        addFace(overlayBuffer, chunk, world, ox, oy, oz, faceIdx, block, simplifiedLighting,
                                faceHeights, color, true);
                    }
                }
            } else {
                // Standard block
                boolean flipV = isGrassSide(block, n[1]);
                addFace(target, chunk, world, (float) x, (float) y, (float) z, faceIdx, block, simplifiedLighting,
                        faceHeights, color, flipV);
            }
        }
    }

    private boolean isFaceVisible(ChunkData chunk, WorldAccess world,
            int x, int y, int z, Block self,
            int nx, int ny, int nz, boolean aggressiveCull) {
        int neighborId = getBlockAt(chunk, world, x + nx, y + ny, z + nz);
        Block neighbor = Blocks.get(neighborId);

        // Water special case
        if (self.isLiquid()) {
            if (neighbor.isLiquid()) {
                // Classic culling: never show faces between two liquids
                return false;
            }
            if (neighbor.isAir())
                return true;
            return false;
        }

        // Custom models always render all faces
        if (self.getProperties().hasCustomModel()) {
            return true;
        }

        // Aggressive culling: only show faces to air
        if (aggressiveCull) {
            return neighbor.isAir();
        }

        // Non-solid neighbors always show face
        if (!neighbor.isSolid()) {
            return true;
        }

        // Show face if neighbor is air, liquid, or transparent solid
        return neighbor.isAir() || neighbor.isLiquid() ||
                (neighbor.isTransparent() && neighbor.isSolid());
    }

    // ==================== FACE GENERATION ====================

    private void addFace(FloatArrayList dst, ChunkData chunk, WorldAccess world,
            float bx, float by, float bz, int faceIdx, Block block,
            boolean simplifiedLighting, float[] heights, int color, boolean flipV) {

        int[] n = FACE_NORMAL[faceIdx];
        int nx = n[0], ny = n[1], nz = n[2];

        // 1. Generate vertices in canonical order
        float[][] verts = buildFaceVertices(bx, by, bz, faceIdx, heights);

        // 2. Compute AO and lighting
        float[] ao;
        float[] skyLight;
        float[] blR;
        float[] blG;
        float[] blB;

        // Use integer coordinates for lighting lookups
        int ibx = Math.round(bx);
        int iby = Math.round(by);
        int ibz = Math.round(bz);

        if (simplifiedLighting) {
            ao = computeSimplifiedAO(ny);
            skyLight = new float[4];
            blR = new float[4];
            blG = new float[4];
            blB = new float[4];

            float sky = getSkyLightAt(chunk, world, ibx + nx, iby + ny, ibz + nz) / 15.0f;
            int packedBlk = getBlockLightAt(chunk, world, ibx + nx, iby + ny, ibz + nz);

            float br = ((packedBlk >> 8) & 0xF) / 15.0f;
            float bg = ((packedBlk >> 4) & 0xF) / 15.0f;
            float bb = (packedBlk & 0xF) / 15.0f;

            for (int i = 0; i < 4; i++) {
                skyLight[i] = sky;
                blR[i] = br;
                blG[i] = bg;
                blB[i] = bb;
            }
        } else {
            ao = computeSmoothAO(chunk, world, ibx, iby, ibz, faceIdx);
            SmoothLight light = computeSmoothLight(chunk, world, ibx, iby, ibz, faceIdx);
            skyLight = light.sky;
            blR = light.blockR;
            blG = light.blockG;
            blB = light.blockB;
        }

        // 3. Texture coordinates
        int tileX = block.getTextureTileX(nx, ny, nz);
        int tileY = block.getTextureTileY(nx, ny, nz);
        int tileIndex = tileY * ATLAS_TILES_X + tileX;

        // UV coordinates (standard order matching vertex order)
        float[][] uv = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };

        // Flip V if requested
        float[] vCoord = flipV ? new float[] { 1, 1, 0, 0 } : new float[] { 0, 0, 1, 1 };

        // 4. Emit triangles with anisotropic fix
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        emitQuad(dst, verts, uv, vCoord, ao, skyLight, blR, blG, blB, faceIdx, tileIndex, r, g, b);
    }

    private int getFluidLevelAt(ChunkData chunk, WorldAccess world, int x, int y, int z) {
        if (y < 0 || y >= chunkHeight)
            return 0;
        if (x >= 0 && x < chunkSize && z >= 0 && z < chunkSize) {
            return chunk.getFluidLevel(x, y, z);
        }
        int worldX = chunk.getWorldX() + x;
        int worldZ = chunk.getWorldZ() + z;
        return world.peekFluidLevel(worldX, y, worldZ);
    }

    private float getFluidHeight(int level, boolean isFalling) {
        if (isFalling)
            return 1.0f;
        if (level >= 7)
            return 0.875f;
        return (level + 1) / 9.0f;
    }

    /**
     * Get the effective fluid height of a single block for smoothing purposes.
     * Solid = 1.0 (pushes water up)
     * Air = 0.0 (pulls water down) - actually min height?
     * Water = Calculated height (0.X or 1.0 if falling)
     */
    private float getBaseFluidHeight(ChunkData chunk, WorldAccess world, int x, int y, int z) {
        int blockId = getBlockAt(chunk, world, x, y, z);
        Block block = Blocks.get(blockId);

        if (block.isSolid()) {
            return 1.0f;
        }
        if (!block.isLiquid()) {
            return 0.0f;
        }

        // It is liquid
        int level = getFluidLevelAt(chunk, world, x, y, z);

        // Check if falling (block above is same liquid)
        int blockAboveId = getBlockAt(chunk, world, x, y + 1, z);
        boolean isFalling = (blockAboveId == block.getNumericId());

        return getFluidHeight(level, isFalling);
    }

    /**
     * Calculate fluid height at a vertex (vx, vz) by averaging the 4 surrounding
     * blocks.
     * The blocks touching vertex (vx, vz) are:
     * (vx-1, vz-1), (vx, vz-1), (vx-1, vz), (vx, vz)
     */
    private float getVertexFluidHeight(ChunkData chunk, WorldAccess world, int vx, int vy, int vz) {
        float sum = 0;
        float max = 0;
        int count = 0;

        // Coordinates of the 4 blocks around this vertex
        int[][] neighbors = {
                { vx - 1, vz - 1 },
                { vx, vz - 1 },
                { vx - 1, vz },
                { vx, vz }
        };

        for (int[] pos : neighbors) {
            float h = getBaseFluidHeight(chunk, world, pos[0], vy, pos[1]);

            // Should we ignore 0.0 (Air) if we have liquids?
            // "Minecraft technique":
            // Weighted by coverage?
            // Actually, if a corner touches Air, it should drop down?
            // But if it touches Solid, it works up.

            // Simple average:
            sum += h;
            count++;

            if (h > max)
                max = h;
        }

        // Return average?
        // Standard is average.
        // Some implementations boost it slightly or stick to max if one is falling?
        // Let's stick to pure average for now as requested.

        return Math.max(0.1f, sum / count);
    }

    /**
     * Build 4 vertices for a face in canonical order.
     * v0=(-U,-V), v1=(+U,-V), v2=(+U,+V), v3=(-U,+V)
     * heights: [h0, h1, h2, h3]
     */
    private float[][] buildFaceVertices(float bx, float by, float bz, int faceIdx, float[] heights) {
        int[] n = FACE_NORMAL[faceIdx];
        int[] u = FACE_TANGENT_U[faceIdx];
        int[] v = FACE_TANGENT_V[faceIdx];

        // Face center
        float cx = bx + 0.5f + n[0] * 0.5f;
        float cy = by + 0.5f + n[1] * 0.5f;
        float cz = bz + 0.5f + n[2] * 0.5f;

        float[][] verts = {
                { cx - u[0] * 0.5f - v[0] * 0.5f, cy - u[1] * 0.5f - v[1] * 0.5f, cz - u[2] * 0.5f - v[2] * 0.5f },
                { cx + u[0] * 0.5f - v[0] * 0.5f, cy + u[1] * 0.5f - v[1] * 0.5f, cz + u[2] * 0.5f - v[2] * 0.5f },
                { cx + u[0] * 0.5f + v[0] * 0.5f, cy + u[1] * 0.5f + v[1] * 0.5f, cz + u[2] * 0.5f + v[2] * 0.5f },
                { cx - u[0] * 0.5f + v[0] * 0.5f, cy - u[1] * 0.5f + v[1] * 0.5f, cz - u[2] * 0.5f + v[2] * 0.5f }
        };

        // Apply heights if Top/Bottom face or Side face
        // Actually, only Top face (POS_Y) needs slope.
        // Side faces need to connect to the slope of Top/Bottom?
        // Actually if water is full (1.0), side goes to 1.0
        // If water is sloped, side top-vertices should match top-face vertices?
        // This is complex. For now let's just slope the Top Face.
        // Side faces: If we smooth top, sides should match.

        // Let's assume heights[] contains the Y-offset for each vertex v0..v3
        // Base Y is 'by'. Target Y is 'by + h'.

        for (int i = 0; i < 4; i++) {
            // For Top Face (POS_Y), base is by.
            // We replace Y with by + heights[i].
            if (faceIdx == FACE_POS_Y) {
                verts[i][1] = by + heights[i];
            }
            // For Bottom Face (NEG_Y), usually flat at 0?
            else if (faceIdx == FACE_NEG_Y) {
                // Keep flat
            } else {
                // Side faces.
                // Vertices with Y > center are Top vertices.
                // Vertices with Y < center are Bottom vertices.
                // Identify which are top.
                if (verts[i][1] > by + 0.5f) {
                    // This is a top vertex.
                    // Which height corresponds to this corner?
                    // Need to map side-face vertex index to block-corner index.
                    // This mapping depends on U/V orientation.
                    // Simpler: Just rely on flat top for sides for now?
                    // If we want sides to match slope, we need to pass the correct heights.
                    // Let's implement Top Face Smoothing first.
                    verts[i][1] = by + heights[i]; // Apply same height logic? No, heights are 4 corners.
                    // Side faces share 2 top corners with the top face.
                    // Which ones?
                    // Depends on faceIdx.
                    // Let's simplisticly flatten sides to max of the 2 corners?
                    // Or just clamp to lowest?
                    // Actually, if we pass the CORRECT 4 heights for the SIDE face (2 top, 2
                    // bottom), we are golden.
                    // But getCornerHeight returns 4 top-corners of the block.
                    // Side face needs 2 top corners and 2 bottom (0).
                    // We need to construct heights[] properly before calling this.
                }
            }
        }

        return verts;
    }

    /**
     * Emit a quad as two triangles, choosing diagonal based on AO to fix
     * anisotropy.
     */
    private void emitQuad(FloatArrayList dst, float[][] v, float[][] uv, float[] vCoord,
            float[] ao, float[] sky, float[] blR, float[] blG, float[] blB, int faceIdx, int tileIndex, float r,
            float g, float b) {

        // Choose diagonal that minimizes interpolation artifacts
        if (ao[0] + ao[2] > ao[1] + ao[3]) {
            // Diagonal from v1 to v3
            pushVertex(dst, v[1], uv[1][0], vCoord[1], ao[1], faceIdx, tileIndex, sky[1], blR[1], blG[1], blB[1], r, g,
                    b);
            pushVertex(dst, v[2], uv[2][0], vCoord[2], ao[2], faceIdx, tileIndex, sky[2], blR[2], blG[2], blB[2], r, g,
                    b);
            pushVertex(dst, v[3], uv[3][0], vCoord[3], ao[3], faceIdx, tileIndex, sky[3], blR[3], blG[3], blB[3], r, g,
                    b);

            pushVertex(dst, v[1], uv[1][0], vCoord[1], ao[1], faceIdx, tileIndex, sky[1], blR[1], blG[1], blB[1], r, g,
                    b);
            pushVertex(dst, v[3], uv[3][0], vCoord[3], ao[3], faceIdx, tileIndex, sky[3], blR[3], blG[3], blB[3], r, g,
                    b);
            pushVertex(dst, v[0], uv[0][0], vCoord[0], ao[0], faceIdx, tileIndex, sky[0], blR[0], blG[0], blB[0], r, g,
                    b);
        } else {
            // Diagonal from v0 to v2
            pushVertex(dst, v[0], uv[0][0], vCoord[0], ao[0], faceIdx, tileIndex, sky[0], blR[0], blG[0], blB[0], r, g,
                    b);
            pushVertex(dst, v[1], uv[1][0], vCoord[1], ao[1], faceIdx, tileIndex, sky[1], blR[1], blG[1], blB[1], r, g,
                    b);
            pushVertex(dst, v[2], uv[2][0], vCoord[2], ao[2], faceIdx, tileIndex, sky[2], blR[2], blG[2], blB[2], r, g,
                    b);

            pushVertex(dst, v[0], uv[0][0], vCoord[0], ao[0], faceIdx, tileIndex, sky[0], blR[0], blG[0], blB[0], r, g,
                    b);
            pushVertex(dst, v[2], uv[2][0], vCoord[2], ao[2], faceIdx, tileIndex, sky[2], blR[2], blG[2], blB[2], r, g,
                    b);
            pushVertex(dst, v[3], uv[3][0], vCoord[3], ao[3], faceIdx, tileIndex, sky[3], blR[3], blG[3], blB[3], r, g,
                    b);
        }
    }

    // ==================== AMBIENT OCCLUSION ====================

    private float[] computeSimplifiedAO(int ny) {
        float val = (ny == 1) ? 1.0f : (ny == -1) ? 0.5f : 0.7f;
        return new float[] { val, val, val, val };
    }

    /**
     * Compute smooth AO for all 4 vertices of a face.
     * Uses the canonical tangent system for consistent sampling.
     */
    private float[] computeSmoothAO(ChunkData chunk, WorldAccess world,
            int bx, int by, int bz, int faceIdx) {

        int[] n = FACE_NORMAL[faceIdx];
        int[] u = FACE_TANGENT_U[faceIdx];
        int[] v = FACE_TANGENT_V[faceIdx];

        // Air block position (in front of face)
        int ax = bx + n[0];
        int ay = by + n[1];
        int az = bz + n[2];

        // Sample the 4 side neighbors and 4 corner neighbors
        boolean uNeg = isOccluder(getBlockAt(chunk, world, ax - u[0], ay - u[1], az - u[2]));
        boolean uPos = isOccluder(getBlockAt(chunk, world, ax + u[0], ay + u[1], az + u[2]));
        boolean vNeg = isOccluder(getBlockAt(chunk, world, ax - v[0], ay - v[1], az - v[2]));
        boolean vPos = isOccluder(getBlockAt(chunk, world, ax + v[0], ay + v[1], az + v[2]));

        boolean c00 = isOccluder(getBlockAt(chunk, world, ax - u[0] - v[0], ay - u[1] - v[1], az - u[2] - v[2]));
        boolean c10 = isOccluder(getBlockAt(chunk, world, ax + u[0] - v[0], ay + u[1] - v[1], az + u[2] - v[2]));
        boolean c11 = isOccluder(getBlockAt(chunk, world, ax + u[0] + v[0], ay + u[1] + v[1], az + u[2] + v[2]));
        boolean c01 = isOccluder(getBlockAt(chunk, world, ax - u[0] + v[0], ay - u[1] + v[1], az - u[2] + v[2]));

        // Compute AO level (0-3) for each vertex
        return new float[] {
                AO_CURVE[vertexAO(uNeg, vNeg, c00)], // v0: -U, -V
                AO_CURVE[vertexAO(uPos, vNeg, c10)], // v1: +U, -V
                AO_CURVE[vertexAO(uPos, vPos, c11)], // v2: +U, +V
                AO_CURVE[vertexAO(uNeg, vPos, c01)] // v3: -U, +V
        };
    }

    /**
     * Calcola il livello di AO (0-3).
     * CORRETTO: L'angolo contribuisce all'occlusione solo se non è coperto dai
     * lati.
     */
    private int vertexAO(boolean side1, boolean side2, boolean corner) {
        if (side1 && side2) {
            return 0;
        }
        int occlusion = 0;
        if (side1)
            occlusion++;
        if (side2)
            occlusion++;

        if (corner && (side1 || side2)) {
            occlusion++;
        }

        return 3 - occlusion;
    }

    private boolean isOccluder(int blockId) {
        Block b = Blocks.get(blockId);
        // Treat solid transparent blocks (leaves) as occluders for AO to have
        // self-shadowing
        return b.isOpaque() || (b.isSolid() && b.isTransparent());
    }

    // ==================== SMOOTH LIGHTING ====================

    private static class SmoothLight {
        final float[] sky;
        final float[] blockR;
        final float[] blockG;
        final float[] blockB;

        SmoothLight(float[] s, float[] br, float[] bg, float[] bb) {
            sky = s;
            blockR = br;
            blockG = bg;
            blockB = bb;
        }
    }

    /**
     * Compute smooth lighting for all 4 vertices of a face.
     * Averages light from neighboring blocks, respecting occlusion.
     */
    private SmoothLight computeSmoothLight(ChunkData chunk, WorldAccess world,
            int bx, int by, int bz, int faceIdx) {

        int[] n = FACE_NORMAL[faceIdx];
        int[] u = FACE_TANGENT_U[faceIdx];
        int[] v = FACE_TANGENT_V[faceIdx];

        int ax = bx + n[0];
        int ay = by + n[1];
        int az = bz + n[2];

        float[] skyOut = new float[4];
        float[] blockROut = new float[4];
        float[] blockGOut = new float[4];
        float[] blockBOut = new float[4];

        // Check side occlusion
        boolean uNegOcc = isOccluder(getBlockAt(chunk, world, ax - u[0], ay - u[1], az - u[2]));
        boolean uPosOcc = isOccluder(getBlockAt(chunk, world, ax + u[0], ay + u[1], az + u[2]));
        boolean vNegOcc = isOccluder(getBlockAt(chunk, world, ax - v[0], ay - v[1], az - v[2]));
        boolean vPosOcc = isOccluder(getBlockAt(chunk, world, ax + v[0], ay + v[1], az + v[2]));

        // Gather indices for 9 blocks
        // 0: Center, 1-4: Sides, 5-8: Corners
        int[] indices = new int[9];
        // ... Optimization: just gather values directly

        // Helper to get lights
        int cSky = getSkyLightAt(chunk, world, ax, ay, az);
        int cBlk = getBlockLightAt(chunk, world, ax, ay, az); // Packed RGB

        int uNSky = getSkyLightAt(chunk, world, ax - u[0], ay - u[1], az - u[2]);
        int uNBlk = getBlockLightAt(chunk, world, ax - u[0], ay - u[1], az - u[2]);

        int uPSky = getSkyLightAt(chunk, world, ax + u[0], ay + u[1], az + u[2]);
        int uPBlk = getBlockLightAt(chunk, world, ax + u[0], ay + u[1], az + u[2]);

        int vNSky = getSkyLightAt(chunk, world, ax - v[0], ay - v[1], az - v[2]);
        int vNBlk = getBlockLightAt(chunk, world, ax - v[0], ay - v[1], az - v[2]);

        int vPSky = getSkyLightAt(chunk, world, ax + v[0], ay + v[1], az + v[2]);
        int vPBlk = getBlockLightAt(chunk, world, ax + v[0], ay + v[1], az + v[2]);

        // Corners
        int c00Sky = getSkyLightAt(chunk, world, ax - u[0] - v[0], ay - u[1] - v[1], az - u[2] - v[2]);
        int c00Blk = getBlockLightAt(chunk, world, ax - u[0] - v[0], ay - u[1] - v[1], az - u[2] - v[2]);

        int c10Sky = getSkyLightAt(chunk, world, ax + u[0] - v[0], ay + u[1] - v[1], az + u[2] - v[2]);
        int c10Blk = getBlockLightAt(chunk, world, ax + u[0] - v[0], ay + u[1] - v[1], az + u[2] - v[2]);

        int c11Sky = getSkyLightAt(chunk, world, ax + u[0] + v[0], ay + u[1] + v[1], az + u[2] + v[2]);
        int c11Blk = getBlockLightAt(chunk, world, ax + u[0] + v[0], ay + u[1] + v[1], az + u[2] + v[2]);

        int c01Sky = getSkyLightAt(chunk, world, ax - u[0] + v[0], ay - u[1] + v[1], az - u[2] + v[2]);
        int c01Blk = getBlockLightAt(chunk, world, ax - u[0] + v[0], ay - u[1] + v[1], az - u[2] + v[2]);

        // Vertex 0: -U, -V
        skyOut[0] = averageLight(cSky, uNSky, vNSky, c00Sky, uNegOcc, vNegOcc);
        blockROut[0] = averageLightRGB(cBlk, uNBlk, vNBlk, c00Blk, uNegOcc, vNegOcc, 8); // R
        blockGOut[0] = averageLightRGB(cBlk, uNBlk, vNBlk, c00Blk, uNegOcc, vNegOcc, 4); // G
        blockBOut[0] = averageLightRGB(cBlk, uNBlk, vNBlk, c00Blk, uNegOcc, vNegOcc, 0); // B

        // Vertex 1: +U, -V
        skyOut[1] = averageLight(cSky, uPSky, vNSky, c10Sky, uPosOcc, vNegOcc);
        blockROut[1] = averageLightRGB(cBlk, uPBlk, vNBlk, c10Blk, uPosOcc, vNegOcc, 8);
        blockGOut[1] = averageLightRGB(cBlk, uPBlk, vNBlk, c10Blk, uPosOcc, vNegOcc, 4);
        blockBOut[1] = averageLightRGB(cBlk, uPBlk, vNBlk, c10Blk, uPosOcc, vNegOcc, 0);

        // Vertex 2: +U, +V
        skyOut[2] = averageLight(cSky, uPSky, vPSky, c11Sky, uPosOcc, vPosOcc);
        blockROut[2] = averageLightRGB(cBlk, uPBlk, vPBlk, c11Blk, uPosOcc, vPosOcc, 8);
        blockGOut[2] = averageLightRGB(cBlk, uPBlk, vPBlk, c11Blk, uPosOcc, vPosOcc, 4);
        blockBOut[2] = averageLightRGB(cBlk, uPBlk, vPBlk, c11Blk, uPosOcc, vPosOcc, 0);

        // Vertex 3: -U, +V
        skyOut[3] = averageLight(cSky, uNSky, vPSky, c01Sky, uNegOcc, vPosOcc);
        blockROut[3] = averageLightRGB(cBlk, uNBlk, vPBlk, c01Blk, uNegOcc, vPosOcc, 8);
        blockGOut[3] = averageLightRGB(cBlk, uNBlk, vPBlk, c01Blk, uNegOcc, vPosOcc, 4);
        blockBOut[3] = averageLightRGB(cBlk, uNBlk, vPBlk, c01Blk, uNegOcc, vPosOcc, 0);

        return new SmoothLight(skyOut, blockROut, blockGOut, blockBOut);
    }

    private float averageLightRGB(int center, int side1, int side2, int corner, boolean occ1, boolean occ2, int shift) {
        int c = (center >> shift) & 0xF;
        int s1 = (side1 >> shift) & 0xF;
        int s2 = (side2 >> shift) & 0xF;
        int co = (corner >> shift) & 0xF;
        return averageLight(c, s1, s2, co, occ1, occ2);
    }

    /**
     * Average light values respecting occlusion.
     * If both sides are occluded, only use center.
     * If one side is occluded, don't use corner.
     */
    private float averageLight(int center, int side1, int side2, int corner,
            boolean occ1, boolean occ2) {
        int sum;
        int count;

        if (occ1 && occ2) {
            // Both blocked - only center
            sum = center;
            count = 1;
        } else if (occ1) {
            // Side1 blocked
            sum = center + side2;
            count = 2;
        } else if (occ2) {
            // Side2 blocked
            sum = center + side1;
            count = 2;
        } else {
            // No blocking - use all 4
            sum = center + side1 + side2 + corner;
            count = 4;
        }

        return (sum / (float) count) / 15.0f;
    }

    // ==================== CUSTOM MODELS ====================

    private static class CompiledModel {
        boolean isCube = true;
        List<CompiledFace> faces = new ArrayList<>();
    }

    private static class CompiledFace {
        float[][] vertices;
        float[][] uvs;
        int[] normal;
        int tileIndex;
        String cullface;
        boolean shade;
        int tintindex;
        String texture; // The resolved texture path/name
    }

    private void renderCustomModel(FloatArrayList solidV, FloatArrayList transpV, FloatArrayList waterV,
            java.util.Map<String, FloatArrayList> customBuffers,
            ChunkData chunk, WorldAccess world,
            int bx, int by, int bz, Block block,
            boolean simplifiedLighting, boolean skipTransparent) {

        // Retrieve state early for models
        int blockId = getBlockAt(chunk, world, bx, by, bz);
        engine.world.block.state.BlockState state = Block.STATE_IDS.get(blockId);

        String modelPath = null;
        float rotX = 0;
        float rotY = 0;

        if (state != null) {
            // Try BlockState system first
            engine.rendering.model.ModelVariant variant = engine.rendering.model.BlockStateLoader.getVariant(state);
            if (variant != null) {
                modelPath = variant.getModel();
                rotX = (float) Math.toRadians(variant.getX());
                rotY = (float) Math.toRadians(variant.getY());
            } else {
                // Fallback to code-based path
                modelPath = block.getModelPath(state);
            }
        } else {
            modelPath = block.getProperties().getModelPath();
        }

        if (modelPath == null)
            modelPath = "missing"; // Default to "missing" if path is null

        CompiledModel compiled = getOrCompileModel(modelPath, block);

        if (compiled == null || compiled.isCube) {
            renderCubeBlock(solidV, transpV, waterV, customBuffers, chunk, world,
                    bx, by, bz, block, simplifiedLighting, false, skipTransparent, 0xFFFFFFFF); // White tint for model
            return;
        }

        // Sample sky light at block center
        float skyLight = getSkyLightAt(chunk, world, bx, by, bz) / 15.0f;

        for (CompiledFace face : compiled.faces) {
            // Cullface check
            if (face.cullface != null || block.isLiquid()) {
                // If rotated, cullface check is tricky.
                // For now, if rotated, we might want to disable cullface or rotate the check?
                // Simple approach: Skip cullface optimization for rotated models or transform
                // the normal check.
                // Leaving as is: might cull wrong faces if rotated.
                // TODO: Rotate cullface direction.

                int[] normal = getCullfaceNormal(face.cullface);
                if (!isFaceVisible(chunk, world, bx, by, bz, block,
                        normal[0], normal[1], normal[2], false)) {
                    continue;
                }
            }

            float ao = face.shade && !simplifiedLighting ? 0.85f : 1.0f;
            int faceIdx = normalToFaceIndex(face.normal); // This also needs rotation if we use it for lighting

            // Offset vertices by block position
            float[][] v = new float[4][3];

            for (int i = 0; i < 4; i++) {
                float[] vert = face.vertices[i]; // Copy

                // Apply Rotation (Center 0.5, 0.5, 0.5)
                if (rotX != 0 || rotY != 0) {
                    float ox = 0.5f;
                    float oy = 0.5f;
                    float oz = 0.5f;
                    float vx = vert[0] - ox;
                    float vy = vert[1] - oy;
                    float vz = vert[2] - oz;

                    // Rotate Y
                    if (rotY != 0) {
                        float cosY = (float) Math.cos(rotY);
                        float sinY = (float) Math.sin(rotY);
                        float rx = vx * cosY - vz * sinY;
                        float rz = vx * sinY + vz * cosY;
                        vx = rx;
                        vz = rz;
                    }

                    // Rotate X
                    if (rotX != 0) {
                        float cosX = (float) Math.cos(rotX);
                        float sinX = (float) Math.sin(rotX);
                        float ry = vy * cosX - vz * sinX;
                        float rz = vy * sinX + vz * cosX;
                        vy = ry;
                        vz = rz;
                    }

                    vert = new float[] { vx + ox, vy + oy, vz + oz };
                }

                v[i][0] = bx + vert[0];
                v[i][1] = by + vert[1];
                v[i][2] = bz + vert[2];
            }

            // Determine target buffer
            FloatArrayList dst;
            // If texture is specified and valid, uses custom buffer
            if (face.texture != null && !face.texture.equals("missing") && !face.texture.isEmpty()) {
                dst = customBuffers.computeIfAbsent(face.texture, k -> new FloatArrayList());
            } else {
                // Fallback to atlas buffers
                if (block.isLiquid()) {
                    dst = waterV;
                } else if (!block.isOpaque() && !skipTransparent) {
                    dst = transpV;
                } else {
                    dst = solidV;
                }
            }

            // Two triangles
            float r = 1.0f, g = 1.0f, b = 1.0f; // White for custom models for now

            // Retrieve packed block light again to ensure we have RGB components
            int packedBlk = getBlockLightAt(chunk, world, bx, by, bz);
            float br = ((packedBlk >> 8) & 0xF) / 15.0f;
            float bg = ((packedBlk >> 4) & 0xF) / 15.0f;
            float bb = (packedBlk & 0xF) / 15.0f;

            pushVertex(dst, v[0], face.uvs[0][0], face.uvs[0][1], ao, faceIdx, face.tileIndex, skyLight, br, bg, bb, r,
                    g, b);
            pushVertex(dst, v[1], face.uvs[1][0], face.uvs[1][1], ao, faceIdx, face.tileIndex, skyLight, br, bg, bb, r,
                    g, b);
            pushVertex(dst, v[2], face.uvs[2][0], face.uvs[2][1], ao, faceIdx, face.tileIndex, skyLight, br, bg, bb, r,
                    g, b);

            pushVertex(dst, v[0], face.uvs[0][0], face.uvs[0][1], ao, faceIdx, face.tileIndex, skyLight, br, bg, bb, r,
                    g, b);
            pushVertex(dst, v[2], face.uvs[2][0], face.uvs[2][1], ao, faceIdx, face.tileIndex, skyLight, br, bg, bb, r,
                    g, b);
            pushVertex(dst, v[3], face.uvs[3][0], face.uvs[3][1], ao, faceIdx, face.tileIndex, skyLight, br, bg, bb, r,
                    g, b);
        }
    }

    private CompiledModel getOrCompileModel(String path, Block block) {
        String cacheKey = path + ":" + block.getNumericId();

        if (COMPILED_MODELS.containsKey(cacheKey)) {
            return COMPILED_MODELS.get(cacheKey);
        }

        BlockModel model = BlockModelLoader.load(path);
        if (model == null) {
            return null;
        }

        CompiledModel compiled = compileModel(model, block);
        COMPILED_MODELS.put(cacheKey, compiled);
        return compiled;
    }

    private CompiledModel compileModel(BlockModel model, Block block) {
        CompiledModel compiled = new CompiledModel();

        if (model.elements == null || model.elements.isEmpty()) {
            compiled.isCube = true;
            return compiled;
        }

        compiled.isCube = false;

        for (BlockModel.ModelElement elem : model.elements) {
            float minX = elem.from[0] / 16f;
            float minY = elem.from[1] / 16f;
            float minZ = elem.from[2] / 16f;
            float maxX = elem.to[0] / 16f;
            float maxY = elem.to[1] / 16f;
            float maxZ = elem.to[2] / 16f;

            for (Map.Entry<String, BlockModel.ModelElement.Face> entry : elem.faces.entrySet()) {
                String faceName = entry.getKey();
                BlockModel.ModelElement.Face face = entry.getValue();

                CompiledFace cf = compileFace(faceName, minX, minY, minZ, maxX, maxY, maxZ,
                        face, elem, model, block);
                compiled.faces.add(cf);
            }
        }

        return compiled;
    }

    private CompiledFace compileFace(String faceName, float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            BlockModel.ModelElement.Face face,
            BlockModel.ModelElement elem,
            BlockModel model, Block block) {
        CompiledFace cf = new CompiledFace();

        float[][] baseVertices;
        switch (faceName) {
            case "north":
                baseVertices = new float[][] {
                        { minX, minY, minZ }, { maxX, minY, minZ },
                        { maxX, maxY, minZ }, { minX, maxY, minZ }
                };
                cf.normal = new int[] { 0, 0, -1 };
                break;
            case "south":
                baseVertices = new float[][] {
                        { maxX, minY, maxZ }, { minX, minY, maxZ },
                        { minX, maxY, maxZ }, { maxX, maxY, maxZ }
                };
                cf.normal = new int[] { 0, 0, 1 };
                break;
            case "west":
                baseVertices = new float[][] {
                        { minX, minY, maxZ }, { minX, minY, minZ },
                        { minX, maxY, minZ }, { minX, maxY, maxZ }
                };
                cf.normal = new int[] { -1, 0, 0 };
                break;
            case "east":
                baseVertices = new float[][] {
                        { maxX, minY, minZ }, { maxX, minY, maxZ },
                        { maxX, maxY, maxZ }, { maxX, maxY, minZ }
                };
                cf.normal = new int[] { 1, 0, 0 };
                break;
            case "down":
                baseVertices = new float[][] {
                        { minX, minY, minZ }, { minX, minY, maxZ },
                        { maxX, minY, maxZ }, { maxX, minY, minZ }
                };
                cf.normal = new int[] { 0, -1, 0 };
                break;
            case "up":
                baseVertices = new float[][] {
                        { minX, maxY, minZ }, { maxX, maxY, minZ },
                        { maxX, maxY, maxZ }, { minX, maxY, maxZ }
                };
                cf.normal = new int[] { 0, 1, 0 };
                break;
            default:
                baseVertices = new float[4][3];
                cf.normal = new int[] { 0, 0, 0 };
        }

        // Apply rotation if present
        if (elem.rotation != null && elem.rotation.angle != 0) {
            float ox = elem.rotation.origin[0] / 16f;
            float oy = elem.rotation.origin[1] / 16f;
            float oz = elem.rotation.origin[2] / 16f;
            float angle = (float) Math.toRadians(elem.rotation.angle);

            cf.vertices = new float[4][3];
            for (int i = 0; i < 4; i++) {
                cf.vertices[i] = rotateVertex(baseVertices[i], ox, oy, oz, elem.rotation.axis, angle);
            }
            cf.normal = rotateNormal(cf.normal, elem.rotation.axis, angle);
        } else {
            cf.vertices = baseVertices;
        }

        // UVs
        if (face.uv != null && face.uv.length == 4) {
            cf.uvs = new float[][] {
                    { face.uv[0] / 16f, face.uv[3] / 16f },
                    { face.uv[2] / 16f, face.uv[3] / 16f },
                    { face.uv[2] / 16f, face.uv[1] / 16f },
                    { face.uv[0] / 16f, face.uv[1] / 16f }
            };
        } else {
            cf.uvs = new float[][] { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } };
        }

        cf.tileIndex = block.getTextureTileY(0, 0, 0) * ATLAS_TILES_X + block.getTextureTileX(0, 0, 0);
        cf.cullface = face.cullface;
        cf.shade = elem.shade;
        cf.tintindex = face.tintindex;

        // Resolve texture
        cf.texture = BlockModelLoader.resolveTexture(model, face.texture);

        return cf;
    }

    private float[] rotateVertex(float[] v, float ox, float oy, float oz, String axis, float angle) {
        float x = v[0] - ox;
        float y = v[1] - oy;
        float z = v[2] - oz;

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float nx, ny, nz;
        switch (axis) {
            case "y":
                nx = x * cos - z * sin;
                ny = y;
                nz = x * sin + z * cos;
                break;
            case "x":
                nx = x;
                ny = y * cos - z * sin;
                nz = y * sin + z * cos;
                break;
            default: // z
                nx = x * cos - y * sin;
                ny = x * sin + y * cos;
                nz = z;
        }

        return new float[] { nx + ox, ny + oy, nz + oz };
    }

    private int[] rotateNormal(int[] normal, String axis, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        float x = normal[0];
        float y = normal[1];
        float z = normal[2];

        float nx, ny, nz;
        switch (axis) {
            case "y":
                nx = x * cos - z * sin;
                ny = y;
                nz = x * sin + z * cos;
                break;
            case "x":
                nx = x;
                ny = y * cos - z * sin;
                nz = y * sin + z * cos;
                break;
            default:
                nx = x * cos - y * sin;
                ny = x * sin + y * cos;
                nz = z;
        }

        return new int[] { Math.round(nx), Math.round(ny), Math.round(nz) };
    }

    private int[] getCullfaceNormal(String cullface) {
        switch (cullface) {
            case "east":
                return new int[] { 1, 0, 0 };
            case "west":
                return new int[] { -1, 0, 0 };
            case "up":
                return new int[] { 0, 1, 0 };
            case "down":
                return new int[] { 0, -1, 0 };
            case "south":
                return new int[] { 0, 0, 1 };
            case "north":
                return new int[] { 0, 0, -1 };
            default:
                return new int[] { 0, 0, 0 };
        }
    }

    private int normalToFaceIndex(int[] normal) {
        if (normal[0] == 1)
            return FACE_POS_X;
        if (normal[0] == -1)
            return FACE_NEG_X;
        if (normal[1] == 1)
            return FACE_POS_Y;
        if (normal[1] == -1)
            return FACE_NEG_Y;
        if (normal[2] == 1)
            return FACE_POS_Z;
        return FACE_NEG_Z;
    }

    // ==================== HELPERS ====================

    private boolean isGrassSide(Block block, int ny) {
        if (ny != 0)
            return false;
        if (block.getRegistryId() == null)
            return false;
        return block.getRegistryId().getPath().equals("grass");
    }

    private int getBlockAt(ChunkData chunk, WorldAccess world, int x, int y, int z) {
        if (y < 0 || y >= chunkHeight) {
            return Blocks.AIR().getNumericId();
        }

        if (x >= 0 && x < chunkSize && z >= 0 && z < chunkSize) {
            return chunk.getBlock(x, y, z);
        }

        int worldX = chunk.getWorldX() + x;
        int worldZ = chunk.getWorldZ() + z;
        return world.peekBlock(worldX, y, worldZ);
    }

    private int getSkyLightAt(ChunkData chunk, WorldAccess world, int x, int y, int z) {
        if (y < 0 || y >= chunkHeight)
            return 0;

        if (x >= 0 && x < chunkSize && z >= 0 && z < chunkSize) {
            return chunk.getSkyLight(x, y, z);
        }

        int worldX = chunk.getWorldX() + x;
        int worldZ = chunk.getWorldZ() + z;
        return world.peekSkyLight(worldX, y, worldZ);
    }

    private int getBlockLightAt(ChunkData chunk, WorldAccess world, int x, int y, int z) {
        if (y < 0 || y >= chunkHeight)
            return 0;

        if (x >= 0 && x < chunkSize && z >= 0 && z < chunkSize) {
            return chunk.getBlockLight(x, y, z);
        }

        int worldX = chunk.getWorldX() + x;
        int worldZ = chunk.getWorldZ() + z;
        return world.peekBlockLight(worldX, y, worldZ);
    }

    private void pushVertex(FloatArrayList dst, float[] pos, float u, float v,
            float ao, int faceIdx, int tileIndex,
            float skyLight, float blR, float blG, float blB, float r, float g, float b) {
        dst.add(pos[0]);
        dst.add(pos[1]);
        dst.add(pos[2]);
        dst.add(u);
        dst.add(v);
        dst.add(ao);
        dst.add((float) faceIdx);
        dst.add((float) tileIndex);
        dst.add(skyLight);
        dst.add(blR);
        dst.add(blG);
        dst.add(blB);
        dst.add(r);
        dst.add(g);
        dst.add(b);
    }

    // ==================== INTERFACES ====================

    public interface ChunkData {
        int getBlock(int x, int y, int z);

        int getWorldX();

        int getWorldZ();

        default int getBlockLight(int x, int y, int z) {
            return 0;
        }

        default int getSkyLight(int x, int y, int z) {
            return 0;
        }

        default int getFluidLevel(int x, int y, int z) {
            return 0;
        }
    }

    public interface WorldAccess {
        int peekBlock(int worldX, int worldY, int worldZ);

        int peekSkyLight(int worldX, int worldY, int worldZ);

        int peekBlockLight(int worldX, int worldY, int worldZ);

        default int peekFluidLevel(int worldX, int worldY, int worldZ) {
            return 0;
        }

        default engine.world.biome.Biome getBiome(int worldX, int worldZ) {
            return engine.world.biome.Biomes.DEFAULT();
        }
    }

    public static class MeshData {
        public final float[] solidVertices;
        public final float[] transparentVertices;
        public final float[] waterVertices;
        public final java.util.Map<String, float[]> customMeshes;

        public MeshData(float[] solid, float[] transparent, float[] water,
                java.util.Map<String, float[]> customMeshes) {
            this.solidVertices = solid;
            this.transparentVertices = transparent;
            this.waterVertices = water;
            this.customMeshes = customMeshes != null ? customMeshes : java.util.Collections.emptyMap();
        }

        public MeshData(float[] solid, float[] transparent, float[] water) {
            this(solid, transparent, water, null);
        }
    }
}