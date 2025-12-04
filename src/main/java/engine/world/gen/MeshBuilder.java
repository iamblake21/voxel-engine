package engine.world.gen;

import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.block.model.BlockModel;
import engine.world.block.model.BlockModelLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * * Builds chunk meshes from block data with LOD (Level of Detail) support.
 * * * LOD Levels:
 * * - LOD 0: Full detail - all faces, full AO, transparency
 * * - LOD 1: High detail - all faces, simplified AO
 * * - LOD 2: Medium detail - skip some internal faces, no transparency
 * separation
 * * - LOD 3: Low detail - aggressive culling, no AO
 * * * This is a direct port from MinecraftOneFile.java - the face generation
 * * logic is proven to work correctly with the original UV mapping.
 * 
 */
public class MeshBuilder {

    private static final float COS_45 = 0.707106781f; // cos(45 deg)
    private static final float SIN_45 = 0.707106781f; // sin(45 deg)


    private static final Map<String, CompiledModel> COMPILED_MODELS = new HashMap<>();

    private static class CompiledModel {
        boolean isCube = true;
        List<CompiledFace> faces = new ArrayList<>();
    }

    private static class CompiledFace {
        float[][] vertices; // 4 vertici [x,y,z]
        float[][] uvs; // 4 UV coords [u,v]
        int[] normal; // Normale [nx,ny,nz]
        int tileIndex;
        String cullface;
        boolean shade;
        int tintindex;
    }

    // Atlas constants
    private static final int ATLAS_TILES_X = 8;
    private static final int ATLAS_TILES_Y = 8;
    private static final float TILE_U = 1.0f / ATLAS_TILES_X;
    private static final float TILE_V = 1.0f / ATLAS_TILES_Y;

    // Chunk constants
    private final int chunkSize;
    private final int chunkHeight;

    // LOD distance thresholds (in chunk units squared)
    public static final int LOD_0_MAX_DIST_SQ = 4 * 4;// 0-4 chunks: full detail
    public static final int LOD_1_MAX_DIST_SQ = 8 * 8;// 4-8 chunks: high detail
    public static final int LOD_2_MAX_DIST_SQ = 16 * 16;// 8-16 chunks: medium detail
    // Beyond 16 chunks: LOD 3 (low detail)

    public MeshBuilder(int chunkSize, int chunkHeight) {
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
    }

    /**
     * * Calculate LOD level based on chunk distance from camera.
     * * @param chunkDistSq Squared distance in chunk units
     * * @return LOD level 0-3
     * 
     */
    public static int calculateLOD(int chunkDistSq) {
        if (chunkDistSq <= LOD_0_MAX_DIST_SQ)
            return 0;
        if (chunkDistSq <= LOD_1_MAX_DIST_SQ)
            return 1;
        if (chunkDistSq <= LOD_2_MAX_DIST_SQ)
            return 2;
        return 3;
    }

    /**
     * * Build mesh data for a chunk (LOD 0 - full detail).
     * * Backward compatible method - original API.
     * 
     */
    public MeshData buildMesh(ChunkData chunk, WorldAccess world) {
        return buildMeshLOD(chunk, world, 0);
    }

    /**
     * * Build mesh data for a chunk at specified LOD level.
     * * * @param chunk The chunk data
     * * @param world World access for neighbor lookups
     * * @param lod LOD level (0-3)
     * * @return Mesh data with solid, transparent, and water vertices
     * 
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

                    if (block.isAir())
                        continue;

                    // ✅ NUOVO: Check se ha modello custom
                    if (block.getProperties().hasCustomModel()) {
                        renderCustomModel(solidV, transpV, waterV, chunk, world,
                                x, y, z, block, simplifiedAO, skipTransparent);
                    } else {
                        renderCubeFaces(solidV, transpV, waterV, chunk, world,
                                x, y, z, block, simplifiedAO, aggressiveCull, skipTransparent);
                    }
                }
            }
        }

        return new MeshData(
                toFloatArray(solidV),
                skipTransparent ? new float[0] : toFloatArray(transpV),
                toFloatArray(waterV));
    }

    /**
     * Check if a face should be rendered.
     * Direct port from MinecraftOneFile.faceVisible() with LOD awareness.
     * * ✅ FIX: Aggiunto check per blocchi NON solidi (come fiori/modelli custom)
     * per
     * prevenire il culling della faccia del blocco di supporto.
     */
    private boolean faceVisible(ChunkData chunk, WorldAccess world,
            int x, int y, int z, Block self,
            int nx, int ny, int nz, boolean aggressiveCull) {
        int neighborId = getBlockAt(chunk, world, x + nx, y + ny, z + nz);
        Block neighbor = Blocks.get(neighborId);

        // Water special case
        if (self.isLiquid()) {
            if (neighbor.isLiquid())
                return false; // Same liquid type
            if (neighbor.isAir())
                return true; // Air shows face
            return false; // Solid hides face
        }

        if (self.getProperties().hasCustomModel()) {
            return true;
        }

        // Aggressive culling (LOD 3): only show faces to air
        if (aggressiveCull) {
            return neighbor.isAir();
        }

        // === FIX PER MODELLI CUSTOM NON-CUBICI / NON-SOLIDI ===
        // Se il blocco vicino NON è solido (come un fiore, una torcia, ecc.),
        // la faccia del blocco corrente DEVE essere visibile.
        if (!neighbor.isSolid()) {
            return true;
        }
        // ======================================================

        // Logica originale (per blocchi solidi e trasparenti come foglie/vetro):
        // show face if neighbor is air, liquid, or transparent leaves
        return neighbor.isAir() || neighbor.isLiquid() ||
                (neighbor.isTransparent() && neighbor.isSolid());
    }

    /**
     * * Renderizza blocco cubico standard (logica esistente estratta)
     * 
     */
    private void renderCubeFaces(ArrayList<Float> solidV, ArrayList<Float> transpV, ArrayList<Float> waterV,
            ChunkData chunk, WorldAccess world, int x, int y, int z,
            Block block, boolean simplifiedAO, boolean aggressiveCull, boolean skipTransparent) {

        // ✅ COPIA TUTTO IL CONTENUTO DEL LOOP ESISTENTE QUI
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

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
                    addFace(solidV, chunk, world, x, y, z, nx, ny, nz, block, false, simplifiedAO);
                }
            } else {
                addFace(solidV, chunk, world, x, y, z, nx, ny, nz, block, false, simplifiedAO);
            }
        }
    }

    /**
     * Renderizza blocco con modello custom JSON
     */
    private void renderCustomModel(ArrayList<Float> solidV, ArrayList<Float> transpV, ArrayList<Float> waterV,
            ChunkData chunk, WorldAccess world, int bx, int by, int bz,
            Block block, boolean simplifiedAO, boolean skipTransparent) {

        String modelPath = block.getProperties().getModelPath();
        CompiledModel compiled = getOrCompileModel(modelPath, block);

        if (compiled == null || compiled.isCube) {
            // Fallback a cubo
            renderCubeFaces(solidV, transpV, waterV, chunk, world,
                    bx, by, bz, block, simplifiedAO, false, skipTransparent);
            return;
        }

        // Target mesh: Se il blocco non è liquido e non è opaco, usa il buffer
        // trasparente.
        ArrayList<Float> dst;
        if (block.isLiquid()) {
            dst = waterV;
        } else if (!block.isOpaque() && !skipTransparent) {
            dst = transpV;
        } else {
            dst = solidV;
        }

        // Luce (campionata al centro del blocco)
        float skyLight = getSkyLightAt(chunk, world, bx, by, bz) / 15.0f;
        float blockLight = getBlockLightAt(chunk, world, bx, by, bz) / 15.0f;

        // DEBUG: verifica se ci sono facce da renderizzare
        if (compiled.faces.isEmpty()) {
            System.out.println(
                    "[ERROR MeshBuilder] Modello custom " + modelPath + " non ha facce compilate. Controlla il JSON.");
            return;
        }

        for (CompiledFace face : compiled.faces) {
            // Face culling
            if (face.cullface != null) {
                int[] normal = getNormalFromCullface(face.cullface);
                if (!faceVisible(chunk, world, bx, by, bz, block,
                        normal[0], normal[1], normal[2], false)) {
                    continue;
                }
            }

            float ao = face.shade && !simplifiedAO ? 0.85f : 1.0f;
            int faceIdx = getFaceIndexFromNormal(face.normal);

            // Offset vertici con posizione blocco
            float[][] v = new float[4][3];
            for (int i = 0; i < 4; i++) {
                // NOTA: I vertici in face.vertices sono già ruotati da compileFace
                v[i][0] = bx + face.vertices[i][0];
                v[i][1] = by + face.vertices[i][1];
                v[i][2] = bz + face.vertices[i][2];
            }
            System.out.println("--- Vertici Rotati per Blocco a " + bx + "," + by + "," + bz + " ---");
            for (int i = 0; i < 4; i++) {
                System.out.println("V" + i + ": (" + v[i][0] + ", " + v[i][1] + ", " + v[i][2] + ")");
            }

        // Due triangoli
        push(dst, v[0], face.uvs[0][0], face.uvs[0][1], ao, faceIdx, face.tileIndex, skyLight, blockLight);
        push(dst, v[1], face.uvs[1][0], face.uvs[1][1], ao, faceIdx, face.tileIndex, skyLight, blockLight);
        push(dst, v[2], face.uvs[2][0], face.uvs[2][1], ao, faceIdx, face.tileIndex, skyLight, blockLight);
        
        push(dst, v[0], face.uvs[0][0], face.uvs[0][1], ao, faceIdx, face.tileIndex, skyLight, blockLight);
        push(dst, v[2], face.uvs[2][0], face.uvs[2][1], ao, faceIdx, face.tileIndex, skyLight, blockLight);
        push(dst, v[3], face.uvs[3][0], face.uvs[3][1], ao, faceIdx, face.tileIndex, skyLight, blockLight);
        }
    }

    /**
     * * Carica e compila modello JSON
     * 
     */
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

    /**
     *  * Compila elementi JSON in geometry
     *  
     */
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

    // ✅ Genera vertici base (senza rotazione)
    float[][] baseVertices;
    switch (faceName) {
        case "north": // -Z
            baseVertices = new float[][] {
                { minX, minY, minZ }, { maxX, minY, minZ },
                { maxX, maxY, minZ }, { minX, maxY, minZ }
            };
            cf.normal = new int[] { 0, 0, -1 };
            break;
        case "south": // +Z
            baseVertices = new float[][] {
                { maxX, minY, maxZ }, { minX, minY, maxZ },
                { minX, maxY, maxZ }, { maxX, maxY, maxZ }
            };
            cf.normal = new int[] { 0, 0, 1 };
            break;
        case "west": // -X
            baseVertices = new float[][] {
                { minX, minY, maxZ }, { minX, minY, minZ },
                { minX, maxY, minZ }, { minX, maxY, maxZ }
            };
            cf.normal = new int[] { -1, 0, 0 };
            break;
        case "east": // +X
            baseVertices = new float[][] {
                { maxX, minY, minZ }, { maxX, minY, maxZ },
                { maxX, maxY, maxZ }, { maxX, maxY, minZ }
            };
            cf.normal = new int[] { 1, 0, 0 };
            break;
        case "down": // -Y
            baseVertices = new float[][] {
                { minX, minY, minZ }, { minX, minY, maxZ },
                { maxX, minY, maxZ }, { maxX, minY, minZ }
            };
            cf.normal = new int[] { 0, -1, 0 };
            break;
        case "up": // +Y
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

    // ✅ APPLICA ROTAZIONE se presente
    if (elem.rotation != null && elem.rotation.angle != 0) {
        float ox = elem.rotation.origin[0] / 16f;
        float oy = elem.rotation.origin[1] / 16f;
        float oz = elem.rotation.origin[2] / 16f;
        float angle = (float) Math.toRadians(elem.rotation.angle);

        cf.vertices = new float[4][3];
        for (int i = 0; i < 4; i++) {
            cf.vertices[i] = rotateVertex(baseVertices[i], ox, oy, oz, 
                                         elem.rotation.axis, angle);
        }
        
        // Ruota anche la normale
        cf.normal = rotateNormal(cf.normal, elem.rotation.axis, angle);
    } else {
        cf.vertices = baseVertices;
    }

if (face.uv != null && face.uv.length == 4) {
    cf.uvs = new float[][] {
        { face.uv[0] / 16f, face.uv[3] / 16f },  // ← SWAP: Bottom-left usa uv[3]
        { face.uv[2] / 16f, face.uv[3] / 16f },  // ← SWAP: Bottom-right usa uv[3]
        { face.uv[2] / 16f, face.uv[1] / 16f },  // ← SWAP: Top-right usa uv[1]
        { face.uv[0] / 16f, face.uv[1] / 16f }   // ← SWAP: Top-left usa uv[1]
    };
} else {
    cf.uvs = new float[][] { {0,1}, {1,1}, {1,0}, {0,0} };  // ← ANCHE QUI: inverto Y
}

    cf.tileIndex = block.getTextureTileY(0,0,0) * ATLAS_TILES_X + block.getTextureTileX(0,0,0);
    cf.cullface = face.cullface;
    cf.shade = elem.shade;
    cf.tintindex = face.tintindex;
    return cf;
}

// ✅ AGGIUNGI questi metodi helper
private float[] rotateVertex(float[] v, float ox, float oy, float oz, 
                             String axis, float angle) {
    float x = v[0] - ox;
    float y = v[1] - oy;
    float z = v[2] - oz;
    
    float cos = (float) Math.cos(angle);
    float sin = (float) Math.sin(angle);
    
    float nx, ny, nz;
    if ("y".equals(axis)) {
        nx = x * cos - z * sin;
        ny = y;
        nz = x * sin + z * cos;
    } else if ("x".equals(axis)) {
        nx = x;
        ny = y * cos - z * sin;
        nz = y * sin + z * cos;
    } else { // z
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
    if ("y".equals(axis)) {
        nx = x * cos - z * sin;
        ny = y;
        nz = x * sin + z * cos;
    } else if ("x".equals(axis)) {
        nx = x;
        ny = y * cos - z * sin;
        nz = y * sin + z * cos;
    } else {
        nx = x * cos - y * sin;
        ny = x * sin + y * cos;
        nz = z;
    }
    
    return new int[] { Math.round(nx), Math.round(ny), Math.round(nz) };
}

    // Helper methods
    private int getFaceIndexFromNormal(int[] normal) {
        int nx = normal[0];
        int ny = normal[1];
        int nz = normal[2];
        
        if (ny != 0) return (ny == 1) ? 2 : 3;
        if (Math.abs(nx) >= Math.abs(nz)) return (nx > 0) ? 0 : 1;
        return (nz > 0) ? 4 : 5;
    }

    private int[] getNormalFromCullface(String cullface) {
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

    /**
     * * Get block at position, handling cross-chunk access.
     * 
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
                    { bx + 1, by, bz }, { bx + 1, by + 1, bz },
                    { bx + 1, by + 1, bz + 1 }, { bx + 1, by, bz + 1 }
            };
        } else if (nx == -1) {
            v = new float[][] {
                    { bx, by, bz }, { bx, by, bz + 1 },
                    { bx, by + 1, bz + 1 }, { bx, by + 1, bz }
            };
        } else if (ny == 1) {
            v = new float[][] {
                    { bx, by + 1, bz }, { bx + 1, by + 1, bz },
                    { bx + 1, by + 1, bz + 1 }, { bx, by + 1, bz + 1 }
            };
        } else if (ny == -1) {
            v = new float[][] {
                    { bx, by, bz }, { bx, by, bz + 1 },
                    { bx + 1, by, bz + 1 }, { bx + 1, by, bz }
            };
        } else if (nz == 1) {
            v = new float[][] {
                    { bx, by, bz + 1 }, { bx, by + 1, bz + 1 },
                    { bx + 1, by + 1, bz + 1 }, { bx + 1, by, bz + 1 }
            };
        } else { // nz == -1
            v = new float[][] {
                    { bx, by, bz }, { bx + 1, by, bz },
                    { bx + 1, by + 1, bz }, { bx, by + 1, bz }
            };
        }

        // UV coordinates - exact copy from original
        float[][] uv;
        if (nx == 1) {
            uv = new float[][] { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } };
        } else if (nx == -1) {
            uv = new float[][] { { 1, 0 }, { 0, 0 }, { 0, 1 }, { 1, 1 } };
        } else if (ny == 1) {
            uv = new float[][] { { 0, 1 }, { 1, 1 }, { 1, 0 }, { 0, 0 } };
        } else if (ny == -1) {
            uv = new float[][] { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 } };
        } else if (nz == 1) {
            uv = new float[][] { { 1, 0 }, { 1, 1 }, { 0, 1 }, { 0, 0 } };
        } else { // nz == -1
            uv = new float[][] { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
        }

        // Compute AO - use simplified version for higher LOD
        float[] ao;
        if (simplifiedAO) {
            // Simplified AO: just directional lighting
            float brightness;
            if (ny == 1)
                brightness = 1.0f; // Top face: full bright
            else if (ny == -1)
                brightness = 0.5f; // Bottom face: darkest
            else if (nx != 0)
                brightness = 0.7f; // X faces: medium
            else
                brightness = 0.8f; // Z faces: slightly brighter
            ao = new float[] { brightness, brightness, brightness, brightness };
        } else {
            ao = computeFaceAO(chunk, world, bx, by, bz, nx, ny, nz);
        }

        float[] skyLight = new float[4];
        float[] blockLight = new float[4];

        for (int i = 0; i < 4; i++) {
            int vx = Math.round(v[i][0]);
            int vy = Math.round(v[i][1]);
            int vz = Math.round(v[i][2]);

            int skyL = getSkyLightAt(chunk, world, vx, vy, vz); // 0..15
            int blockL = getBlockLightAt(chunk, world, vx, vy, vz); // 0..15

            // Normalize to 0.0..1.0
            skyLight[i] = skyL / 15.0f;
            blockLight[i] = blockL / 15.0f;
        }

        int tileX = block.getTextureTileX(nx, ny, nz);
        int tileY = block.getTextureTileY(nx, ny, nz);

        // Indice del layer nella TextureArray (atlas 8x8 -> 64 layer)
        int tileIndex = tileY * ATLAS_TILES_X + tileX;

        // Grass side flip - from original
        boolean flipV = isGrassSide(block, ny);

        // vLoc = uv locale [0..1] (non più offset sull’atlas!)
        float v0loc = flipV ? 1f - uv[0][1] : uv[0][1];
        float v1loc = flipV ? 1f - uv[1][1] : uv[1][1];
        float v2loc = flipV ? 1f - uv[2][1] : uv[2][1];
        float v3loc = flipV ? 1f - uv[3][1] : uv[3][1];

        int faceIdx = (nx == 1) ? 0 : (nx == -1) ? 1 : (ny == 1) ? 2 : (ny == -1) ? 3 : (nz == 1) ? 4 : 5;

        // Two triangles – adesso usiamo uv locali (0..1) e tileIndex
        push(dst, v[0], uv[0][0], v0loc, ao[0], faceIdx, tileIndex, skyLight[0], blockLight[0]);
        push(dst, v[1], uv[1][0], v1loc, ao[1], faceIdx, tileIndex, skyLight[1], blockLight[1]);
        push(dst, v[2], uv[2][0], v2loc, ao[2], faceIdx, tileIndex, skyLight[2], blockLight[2]);

        push(dst, v[0], uv[0][0], v0loc, ao[0], faceIdx, tileIndex, skyLight[0], blockLight[0]);
        push(dst, v[2], uv[2][0], v2loc, ao[2], faceIdx, tileIndex, skyLight[2], blockLight[2]);
        push(dst, v[3], uv[3][0], v3loc, ao[3], faceIdx, tileIndex, skyLight[3], blockLight[3]);

    }

    /**
     * * Check if this is a grass side that needs V flip
     * 
     */
    private boolean isGrassSide(Block block, int ny) {
        // Grass sides need flipping (ny == 0 means horizontal face)
        if (ny != 0)
            return false;

        // Check if it's grass by looking at registry ID
        if (block.getRegistryId() == null)
            return false;
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

    private void push(ArrayList<Float> a, float[] pos, float u, float v, float ao,
            int faceIdx, int tileIndex, float skyLight, float blockLight) {


                
        a.add(pos[0]); // x
        a.add(pos[1]); // y
        a.add(pos[2]); // z
        a.add(u); // u
        a.add(v); // v
        a.add(ao); // ao (ONLY ambient occlusion, no light!)
        a.add((float) faceIdx); // face index
        a.add((float) tileIndex); // layer index
        a.add(skyLight);
        a.add(blockLight);
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
     * * Interface for accessing chunk block data
     * 
     */
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

    }

    /**
     * * Interface for cross-chunk block access
     * 
     */
    public interface WorldAccess {
        int peekBlock(int worldX, int worldY, int worldZ);

        int peekSkyLight(int worldX, int worldY, int worldZ);

        int peekBlockLight(int worldX, int worldY, int worldZ);

    }

    /**
     * * Result of mesh building
     * 
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
