package engine.rendering;

import engine.core.Config;
import engine.utils.Math3D.Mat4;
import engine.utils.Math3D.Vec3;
import engine.world.World;
import engine.world.Chunk;
import engine.world.block.Blocks;
import engine.world.gen.MeshBuilder;

import engine.utils.Raycast;

import java.util.ArrayList;
import java.util.Comparator;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*; // VBO, glBufferData, GL_ARRAY_BUFFER, GL_STATIC_DRAW
import static org.lwjgl.opengl.GL20.*; // glEnableVertexAttribArray, glVertexAttribPointer
import static org.lwjgl.opengl.GL30.*; // VAO: glGenVertexArrays, glBindVertexArray, glDeleteVertexArrays

/**
 * Main renderer - coordinates all rendering with LOD and Fog support.
 * 
 * Features:
 * - Frustum culling for efficient rendering
 * - LOD system: renders distant chunks with lower detail meshes
 * - Distance fog: smooth transition to hide chunk pop-in
 * - Front-to-back rendering for better depth performance
 */
public class Renderer {

    private final Config config;
    private Camera camera;
    private Shader voxelShader;
    private TextureArray atlasTexture;
    private WireframeRenderer wireframeRenderer;

    // Frustum culling - the key to infinite view distance!
    private final Frustum frustum = new Frustum();
    private boolean frustumCullingEnabled = true;

    // LOD system
    private boolean lodEnabled = true;
    private int currentPlayerChunkX = 0;
    private int currentPlayerChunkZ = 0;

    // Fog settings
    private boolean fogEnabled = true;
    private float fogStart = 0.40f; // Start fog much closer (40%) for smoother fade
    private float fogEnd = 0.85f; // Full fog at 85% to definitely hide pop-in

    // Stats
    private int lastChunksTotal = 0;
    private int lastChunksRendered = 0;
    private int lastChunksCulled = 0;
    private int[] lodCounts = new int[4]; // Chunks rendered per LOD level

    // Shader uniform locations
    private int uProj, uView, uModel, uTex, uTint;
    private int uTime, uCameraPos, uWaterPass, uUnderwater, uWaterLevel;
    private int uTilesX, uTilesY;
    private int uTileGrassTop, uTileLeaves, uTileWater;
    private int uGrassTint, uFoliageTint;
    private int uTileGrassTopIndex, uTileLeavesIndex; // <--- nuovi
    private int uUseTextureArray, uTex2D; // New uniforms for hybrid rendering

    private int uTimeOfDay;
    private int uSunDir;
    private int uSunColor;
    private int uAmbientColor;

    private Shader skyShader;
    private int skyVAO;
    private int skyVBO;

    // Fog uniforms
    private int uFogEnabled, uFogColor, uFogStart, uFogEnd;

    private float clearR = 0.6f, clearG = 0.8f, clearB = 1.0f;
    private World currentWorld;

    public Renderer(Config config) {
        this.config = config;
    }

    private void initSkyboxMesh() {
        float[] vertices = {
                // pos (cube -1..1)
                -1, 1, -1, -1, -1, -1, 1, -1, -1,
                1, -1, -1, 1, 1, -1, -1, 1, -1,

                -1, -1, 1, -1, -1, -1, -1, 1, -1,
                -1, 1, -1, -1, 1, 1, -1, -1, 1,

                1, -1, -1, 1, -1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, -1, 1, -1, -1,

                -1, -1, 1, -1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, -1, 1, -1, -1, 1,

                -1, 1, -1, 1, 1, -1, 1, 1, 1,
                1, 1, 1, -1, 1, 1, -1, 1, -1,

                -1, -1, -1, -1, -1, 1, 1, -1, -1,
                1, -1, -1, -1, -1, 1, 1, -1, 1
        };

        skyVAO = glGenVertexArrays();
        skyVBO = glGenBuffers();

        glBindVertexArray(skyVAO);
        glBindBuffer(GL_ARRAY_BUFFER, skyVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void init() {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);

        // atlas 8x8 come hai già nei tuoi shader
        atlasTexture = new TextureArray("atlas.png", 8, 8);

        voxelShader = new Shader(getVertexShader(), getFragmentShader());
        cacheUniformLocations();
        wireframeRenderer = new WireframeRenderer();

        skyShader = new Shader(getSkyVertexShader(), getSkyFragmentShader());
        initSkyboxMesh();

        System.out.println("Renderer initialized with LOD and Fog support");

        // Set default uniforms
        voxelShader.bind();
        voxelShader.setUniform(uTex, 0); // Unit 0 for Atlas
        voxelShader.setUniform(uTex2D, 1); // Unit 1 for Standalone
        voxelShader.setUniform(uUseTextureArray, 1); // Default to Atlas
        voxelShader.unbind();
    }

    private void cacheUniformLocations() {
        uProj = voxelShader.getUniformLocation("uProj");
        uView = voxelShader.getUniformLocation("uView");
        uModel = voxelShader.getUniformLocation("uModel");
        uTex = voxelShader.getUniformLocation("uTex");
        uTex2D = voxelShader.getUniformLocation("uTex2D");
        uUseTextureArray = voxelShader.getUniformLocation("uUseTextureArray");
        uTint = voxelShader.getUniformLocation("uTint");
        uTime = voxelShader.getUniformLocation("uTime");
        uCameraPos = voxelShader.getUniformLocation("uCameraPos");
        uWaterPass = voxelShader.getUniformLocation("uWaterPass");
        uUnderwater = voxelShader.getUniformLocation("uUnderwater");
        uWaterLevel = voxelShader.getUniformLocation("uWaterLevel");
        uTilesX = voxelShader.getUniformLocation("uTilesX");
        uTilesY = voxelShader.getUniformLocation("uTilesY");

        // invece di uTileGrassTop/uTileLeaves come ivec2:
        uTileGrassTopIndex = voxelShader.getUniformLocation("uTileGrassTopIndex");
        uTileLeavesIndex = voxelShader.getUniformLocation("uTileLeavesIndex");

        uGrassTint = voxelShader.getUniformLocation("uGrassTint");
        uFoliageTint = voxelShader.getUniformLocation("uFoliageTint");

        // Fog uniforms
        uFogEnabled = voxelShader.getUniformLocation("uFogEnabled");
        uFogColor = voxelShader.getUniformLocation("uFogColor");
        uFogStart = voxelShader.getUniformLocation("uFogStart");
        uFogEnd = voxelShader.getUniformLocation("uFogEnd");

        uTimeOfDay = voxelShader.getUniformLocation("uTimeOfDay");
        uSunDir = voxelShader.getUniformLocation("uSunDir");
        uSunColor = voxelShader.getUniformLocation("uSunColor");
        uAmbientColor = voxelShader.getUniformLocation("uAmbientColor");

    }

    public void beginFrame() {
        beginFrame(currentWorld);
    }

    public void beginFrame(World world) {
        this.currentWorld = world;

        boolean underwater = isHeadUnderwater(world);
        if (underwater) {
            clearR = 0.10f;
            clearG = 0.32f;
            clearB = 0.70f;
        } else {
            clearR = 0.6f;
            clearG = 0.8f;
            clearB = 1.0f;
        }

        glClearColor(clearR, clearG, clearB, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glViewport(0, 0, config.windowWidth, config.windowHeight);

        // Update player chunk position for LOD calculations
        if (camera != null) {
            currentPlayerChunkX = (int) Math.floor(camera.getPosition().x / config.chunkSize);
            currentPlayerChunkZ = (int) Math.floor(camera.getPosition().z / config.chunkSize);
        }
    }

    private boolean isHeadUnderwater(World world) {
        if (camera == null || world == null)
            return false;
        int bx = (int) Math.floor(camera.getPosition().x);
        int by = (int) Math.floor(camera.getPosition().y);
        int bz = (int) Math.floor(camera.getPosition().z);
        int blockId = world.getBlock(bx, by, bz);
        return Blocks.isLiquid(blockId);
    }

    /**
     * Calculate LOD level for a chunk based on distance.
     * Currently returns 0 always until LOD system is fully working.
     */
    private int calculateChunkLOD(Chunk chunk) {
        // DISABLED FOR NOW - always use full detail until LOD is stable
        return 0;

        /*
         * if (!lodEnabled) return 0;
         * 
         * int dx = chunk.getX() - currentPlayerChunkX;
         * int dz = chunk.getZ() - currentPlayerChunkZ;
         * int distSq = dx * dx + dz * dz;
         * 
         * return MeshBuilder.calculateLOD(distSq);
         */
    }

    private String getSkyVertexShader() {
        return "#version 330 core\n" +
                "layout(location=0) in vec3 aPos;\n" +
                "out vec3 vDir;\n" +
                "uniform mat4 uProj;\n" +
                "uniform mat4 uView;\n" +
                "void main(){\n" +
                "  vDir = aPos;\n" +
                "  vec4 pos = uProj * uView * vec4(aPos, 1.0);\n" +
                "  gl_Position = pos.xyww; // z = w per stare sempre in fondo\n" +
                "}\n";
    }

    private String getSkyFragmentShader() {
        return "#version 330 core\n" +
                "in vec3 vDir;\n" +
                "out vec4 FragColor;\n" +
                "\n" +
                "uniform float uTime;       // se vuoi usalo per stelline ecc.\n" +
                "uniform float uTimeOfDay;  // 0..1 dal World\n" +
                "uniform vec3  uSunDir;     // direzione del sole dal World\n" +
                "uniform vec3  uSunColor;   // colore sole dal World\n" +
                "uniform vec3  uAmbientColor; // ambient dal World\n" +
                "\n" +
                "// Colore cielo in base a direzione e altezza del sole\n" +
                "vec3 computeSkyColor(vec3 dir, vec3 sunDir, float sunStrength){\n" +
                "  dir = normalize(dir);\n" +
                "  float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0); // -1..1 -> 0..1\n" +
                "\n" +
                "  vec3 dayHorizon   = vec3(0.55, 0.75, 0.95);\n" +
                "  vec3 dayZenith    = vec3(0.10, 0.45, 0.90);\n" +
                "  vec3 nightHorizon = vec3(0.02, 0.05, 0.10);\n" +
                "  vec3 nightZenith  = vec3(0.00, 0.02, 0.05);\n" +
                "\n" +
                "  float night = 1.0 - clamp(sunStrength * 1.3, 0.0, 1.0);\n" +
                "  vec3 dayCol   = mix(dayHorizon,   dayZenith,   h);\n" +
                "  vec3 nightCol = mix(nightHorizon, nightZenith, h);\n" +
                "  vec3 col = mix(nightCol, dayCol, 1.0 - night);\n" +
                "\n" +
                "  // alone del sole\n" +
                "  float sunAmt = pow(max(dot(dir, -sunDir), 0.0), 512.0);\n" +
                "  col += uSunColor * sunAmt * 1.5;\n" +
                "\n" +
                "  return col;\n" +
                "}\n" +
                "\n" +
                "void main(){\n" +
                "  vec3 sunDir = normalize(uSunDir);\n" +
                "  float sunStrength = clamp(sunDir.y, 0.0, 1.0);\n" +
                "\n" +
                "  vec3 sky = computeSkyColor(vDir, sunDir, sunStrength);\n" +
                "  FragColor = vec4(sky, 1.0);\n" +
                "}\n";
    }

    private void renderSkybox(World world) {
        if (camera == null || world == null)
            return;

        // Se sei sott'acqua, niente skybox
        if (isHeadUnderwater(world)) {
            return;
        }

        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);

        skyShader.bind();
        skyShader.setUniform("uProj", camera.getProjectionMatrix());

        // View senza traslazione
        Mat4 view = camera.getViewMatrix();
        Mat4 viewNoTrans = new Mat4();
        System.arraycopy(view.m, 0, viewNoTrans.m, 0, 16);
        viewNoTrans.m[12] = 0f;
        viewNoTrans.m[13] = 0f;
        viewNoTrans.m[14] = 0f;
        skyShader.setUniform("uView", viewNoTrans);

        // --- QUI sincronizziamo col World / Renderer ---
        float timeOfDay = world.getTimeOfDay(); // 0..1
        skyShader.setUniform("uTimeOfDay", timeOfDay);

        // Direzione del sole dal World
        Vec3 sunDir = world.getSunDirection();
        skyShader.setUniform("uSunDir", sunDir);

        // Stessa logica che ti ho proposto per il Renderer dei blocchi
        float sunHeight = Math.max(0f, sunDir.y);
        float sunStrength = (float) Math.pow(sunHeight, 1.2f);

        Vec3 sunColor = new Vec3(
                1.0f * sunStrength,
                0.95f * sunStrength,
                0.85f * sunStrength);
        skyShader.setUniform("uSunColor", sunColor);

        float ambientStrength = 0.35f + 0.35f * sunHeight;
        Vec3 ambientColor = new Vec3(
                0.6f * ambientStrength,
                0.7f * ambientStrength,
                0.9f * ambientStrength);
        skyShader.setUniform("uAmbientColor", ambientColor);

        // Se vuoi ancora usare uTime per effetti extra
        skyShader.setUniform("uTime", world.getGameTime());

        glBindVertexArray(skyVAO);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);

        skyShader.unbind();

        glDepthMask(true);
        glDepthFunc(GL_LESS);
    }

    /**
     * Render world - uses Frustum culling, LOD, and Fog!
     */
    public void renderWorld(World world) {
        if (camera == null) {
            System.err.println("Warning: No camera set");
            return;
        }

        this.currentWorld = world;
        boolean underwater = isHeadUnderwater(world);

        // Update frustum from camera matrices
        if (frustumCullingEnabled) {
            updateFrustum();
        }

        // --- SKYBOX ---
        renderSkybox(world);
        // --- FINE SKYBOX ---

        voxelShader.bind();
        atlasTexture.bind(0); // TextureArray o Texture classica, il bind non cambia

        // Calculate view distance in world units for fog
        float viewDistanceWorld = config.viewDistance * config.chunkSize;

        // Set uniforms base
        voxelShader.setUniform(uProj, camera.getProjectionMatrix());
        voxelShader.setUniform(uView, camera.getViewMatrix());
        voxelShader.setUniform(uTex, 0);
        voxelShader.setUniform(uTime, world.getGameTime());
        voxelShader.setUniform(uCameraPos, camera.getPosition());
        voxelShader.setUniform(uWaterLevel, config.waterLevel);
        voxelShader.setUniform(uUnderwater, underwater ? 1 : 0);
        voxelShader.setUniform(uUseTextureArray, 1); // Enable Atlas for blocks

        // Rimangono se ti servono nello shader (per l'acqua usi ancora uTilesX/uTilesY)
        voxelShader.setUniform(uTilesX, 8);
        voxelShader.setUniform(uTilesY, 8);

        voxelShader.setUniform(uGrassTint, 0.54f, 0.78f, 0.38f);
        voxelShader.setUniform(uFoliageTint, 0.52f, 0.75f, 0.35f);

        // --- QUI È IL CAMBIO IMPORTANTE ---

        // Prima avevi:
        // voxelShader.setUniform2i(uTileGrassTop, 0, 0);
        // voxelShader.setUniform2i(uTileLeaves, 5, 0);
        // voxelShader.setUniform2i(uTileWater, 6, 0);

        // Ora calcoliamo gli INDICI layer per l'atlas 8x8:
        // layerIndex = tileY * tilesX + tileX;
        int tilesX = 8;

        int grassTopIndex = 0 * tilesX + 0; // (tileX=0, tileY=0)
        int leavesIndex = 0 * tilesX + 5; // (tileX=5, tileY=0)
        // se ti serve uTileWaterIndex puoi farlo allo stesso modo: int waterIndex = 0 *
        // tilesX + 6;

        voxelShader.setUniform(uTileGrassTopIndex, grassTopIndex); // <<< nuovo
        voxelShader.setUniform(uTileLeavesIndex, leavesIndex); // <<< nuovo
        // niente più setUniform2i(uTileWater,...) perché nel tuo fragment non lo usi

        // --- FINE CAMBIO IMPORTANTE ---

        // Fog uniforms
        float fogColorR = underwater ? 0.10f : clearR;
        float fogColorG = underwater ? 0.32f : clearG;
        float fogColorB = underwater ? 0.70f : clearB;
        voxelShader.setUniform(uFogEnabled, fogEnabled ? 1 : 0);
        voxelShader.setUniform(uFogColor, fogColorR, fogColorG, fogColorB);
        voxelShader.setUniform(uFogStart, fogStart * viewDistanceWorld);
        voxelShader.setUniform(uFogEnd, fogEnd * viewDistanceWorld);

        // Get ALL loaded chunks, then filter by frustum
        ArrayList<Chunk> allChunks = world.getVisibleChunks(camera.getPosition());
        ArrayList<Chunk> visibleChunks = filterByFrustum(allChunks);

        // Sort by distance (front-to-back for better early-z rejection)
        Vec3 camPos = camera.getPosition();
        visibleChunks.sort((a, b) -> {
            float distA = chunkDistanceSq(a, camPos);
            float distB = chunkDistanceSq(b, camPos);
            return Float.compare(distA, distB);
        });

        // Update stats
        lastChunksTotal = allChunks.size();
        lastChunksRendered = visibleChunks.size();
        lastChunksCulled = lastChunksTotal - lastChunksRendered;

        // Reset LOD counts
        for (int i = 0; i < 4; i++)
            lodCounts[i] = 0;

        // Render solid (front-to-back)
        glDisable(GL_BLEND);
        glDepthMask(true);
        voxelShader.setUniform(uWaterPass, 0);
        voxelShader.setUniform(uTint, 1f, 1f, 1f, 1f);

        // Time of day 0..1, se ti serve in altri effetti
        float timeOfDay = world.getTimeOfDay();
        voxelShader.setUniform(uTimeOfDay, timeOfDay);

        // Direzione del sole calcolata dal World
        Vec3 sunDir = world.getSunDirection();
        voxelShader.setUniform(uSunDir, sunDir);

        // Altezza del sole sopra l'orizzonte (0 = sotto, 1 = molto alto)
        float sunHeight = Math.max(0f, sunDir.y);

        // Curva un po' morbida: vicino a mezzogiorno più forte, ma non istantaneo
        float sunStrength = (float) Math.pow(sunHeight, 1.2f);

        // Colore base del sole (giallo caldo), poi scalato per intensità
        float sunR = 1.0f * sunStrength;
        float sunG = 0.95f * sunStrength;
        float sunB = 0.85f * sunStrength;
        Vec3 sunColor = new Vec3(sunR, sunG, sunB);
        voxelShader.setUniform(uSunColor, sunColor);

        // Ambient: non deve andare a zero totale, altrimenti è pitch black
        float ambientStrength = 0.35f + 0.35f * sunHeight; // 0.35 notte, ~0.7 giorno
        Vec3 ambientColor = new Vec3(
                0.6f * ambientStrength,
                0.7f * ambientStrength,
                0.9f * ambientStrength);
        voxelShader.setUniform(uAmbientColor, ambientColor);

        for (Chunk chunk : visibleChunks) {
            int lod = calculateChunkLOD(chunk);
            lodCounts[lod]++;

            // Get mesh for this LOD level
            Mesh solidMesh = chunk.getSolidMesh(lod);
            if (solidMesh != null && !solidMesh.isEmpty()) {
                Mat4 model = Mat4.translate(
                        chunk.getX() * config.chunkSize, 0,
                        chunk.getZ() * config.chunkSize);
                voxelShader.setUniform(uModel, model);
                solidMesh.draw();
            }
        }

        // Block selection
        voxelShader.unbind();
        renderBlockSelection(world);
        voxelShader.bind();

        // Transparent & Water (back-to-front for correct alpha blending)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        // Important: Transparent blocks (like leaves) SHOULD write depth to work
        // correctly with water
        // consistently. Our fragment shader uses discard for alpha < 0.1, so holes
        // won't write depth.
        glDepthMask(true);

        // Iterate back-to-front once for all transparent types
        for (int i = visibleChunks.size() - 1; i >= 0; i--) {
            Chunk chunk = visibleChunks.get(i);
            int lod = calculateChunkLOD(chunk);
            Mat4 model = Mat4.translate(
                    chunk.getX() * config.chunkSize, 0,
                    chunk.getZ() * config.chunkSize);

            // 1. Transparent Mesh (Leaves, Flowers, etc.)
            Mesh transpMesh = chunk.getTransparentMesh(lod);
            if (transpMesh != null && !transpMesh.isEmpty()) {
                voxelShader.setUniform(uWaterPass, 0); // Treated as normal blocks
                voxelShader.setUniform(uUseTextureArray, 1);
                voxelShader.setUniform(uModel, model);
                atlasTexture.bind(0);
                transpMesh.draw();
            }

            // 2. Custom Meshes (Models)
            for (java.util.Map.Entry<String, engine.rendering.Mesh> entry : chunk.getCustomMeshes().entrySet()) {
                String texturePath = entry.getKey();
                engine.rendering.Mesh customMesh = entry.getValue();

                if (customMesh != null && !customMesh.isEmpty()) {
                    Texture tex = getCustomTexture(texturePath);
                    if (tex != null) {
                        voxelShader.setUniform(uWaterPass, 0);
                        voxelShader.setUniform(uUseTextureArray, 0); // Disable Atlas
                        tex.bind(1); // Bind to slot 1
                        voxelShader.setUniform(uModel, model);

                        // Fix Z-fighting for overlays (e.g. grass side) using Polygon Offset
                        // Push fragments towards the camera
                        boolean isOverlay = texturePath.contains("grass_block_side_overlay");
                        if (isOverlay) {
                            glEnable(GL_POLYGON_OFFSET_FILL);
                            glPolygonOffset(-1.0f, -1.0f);
                        }

                        customMesh.draw();

                        if (isOverlay) {
                            glDisable(GL_POLYGON_OFFSET_FILL);
                        }

                        voxelShader.setUniform(uUseTextureArray, 1); // Re-enable Atlas
                    }
                }
            }

            // 3. Water Mesh
            // Water is consistently drawn after solids/cutouts in the same chunk.
            // Since we iterate back-to-front, this is the correct order for "painter's
            // algorithm"
            // combined with depth buffering.
            Mesh waterMesh = chunk.getWaterMesh(lod);
            if (waterMesh != null && !waterMesh.isEmpty()) {
                voxelShader.setUniform(uWaterPass, 1); // Water logic
                voxelShader.setUniform(uUseTextureArray, 1);
                voxelShader.setUniform(uModel, model);
                atlasTexture.bind(0);
                waterMesh.draw();
            }
        }

        glDepthMask(true);
        voxelShader.unbind();
    }

    // ==================== CUSTOM TEXTURE MANAGEMENT ====================
    private final java.util.Map<String, Texture> customTextures = new java.util.HashMap<>();

    private Texture getCustomTexture(String path) {
        if (customTextures.containsKey(path)) {
            return customTextures.get(path);
        }

        // Load new texture
        // Path might be "block/torch" -> needs to find "textures/blocks/torch.png"
        // But Texture class takes a resource path.
        // Let's try heuristic:
        // 1. Try exact path
        // 2. Try adding "textures/" prefix and ".png" suffix
        // 3. Try adding "textures/blocks/" if it starts with "block/"

        String loadPath = path;
        // Simple mapping for now based on what I saw in resources
        if (!path.endsWith(".png")) {
            if (path.startsWith("block/")) {
                loadPath = "textures/blocks/" + path.substring(6) + ".png";
            } else {
                loadPath = "textures/" + path + ".png";
            }
        }

        Texture tex = new Texture(loadPath);
        customTextures.put(path, tex);
        return tex;
    }

    private float chunkDistanceSq(Chunk chunk, Vec3 camPos) {
        float cx = (chunk.getX() + 0.5f) * config.chunkSize;
        float cz = (chunk.getZ() + 0.5f) * config.chunkSize;
        float dx = cx - camPos.x;
        float dz = cz - camPos.z;
        return dx * dx + dz * dz;
    }

    /**
     * Update frustum planes from camera.
     */
    private void updateFrustum() {
        float[] p = camera.getProjectionMatrix().m;
        float[] v = camera.getViewMatrix().m;

        // Compute VP = P * V (column-major)
        float[] vp = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                vp[col * 4 + row] = p[0 * 4 + row] * v[col * 4 + 0] +
                        p[1 * 4 + row] * v[col * 4 + 1] +
                        p[2 * 4 + row] * v[col * 4 + 2] +
                        p[3 * 4 + row] * v[col * 4 + 3];
            }
        }

        frustum.update(vp);
    }

    /**
     * Filter chunks by frustum - only keep visible ones!
     */
    private ArrayList<Chunk> filterByFrustum(ArrayList<Chunk> chunks) {
        if (!frustumCullingEnabled) {
            return chunks;
        }

        ArrayList<Chunk> visible = new ArrayList<>();
        for (Chunk chunk : chunks) {
            float minX = chunk.getX() * config.chunkSize;
            float minZ = chunk.getZ() * config.chunkSize;
            float maxX = minX + config.chunkSize;
            float maxZ = minZ + config.chunkSize;

            // Test chunk bounding box against frustum
            if (frustum.testAABB(minX, 0, minZ, maxX, config.worldHeight, maxZ)) {
                visible.add(chunk);
            }
        }

        return visible;
    }

    public void endFrame() {
    }

    public void cleanup() {
        if (voxelShader != null)
            voxelShader.cleanup();
        if (atlasTexture != null)
            atlasTexture.cleanup();
        if (wireframeRenderer != null)
            wireframeRenderer.cleanup();

        if (skyShader != null)
            skyShader.cleanup();
        if (skyVAO != 0)
            glDeleteVertexArrays(skyVAO);
        if (skyVBO != 0)
            glDeleteBuffers(skyVBO);

        for (Texture tex : customTextures.values()) {
            tex.cleanup();
        }
        customTextures.clear();
    }

    private void renderBlockSelection(World world) {
        if (camera == null || world == null)
            return;

        Raycast.RayHit hit = Raycast.cast(
                world,
                camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
                camera.getForward().x, camera.getForward().y, camera.getForward().z,
                6.0f);

        if (hit.hit) {
            glDisable(GL_BLEND);
            glEnable(GL_DEPTH_TEST);
            wireframeRenderer.draw(
                    camera.getProjectionMatrix(), camera.getViewMatrix(),
                    hit.blockX, hit.blockY, hit.blockZ,
                    0.0f, 0.0f, 0.0f, 1.0f);
        }
    }

    // === FRUSTUM CONTROLS ===

    public void setFrustumCullingEnabled(boolean enabled) {
        this.frustumCullingEnabled = enabled;
    }

    public boolean isFrustumCullingEnabled() {
        return frustumCullingEnabled;
    }

    public void toggleFrustumCulling() {
        frustumCullingEnabled = !frustumCullingEnabled;
        System.out.println("Frustum culling: " + (frustumCullingEnabled ? "ON" : "OFF"));
    }

    // === LOD CONTROLS ===

    public void setLODEnabled(boolean enabled) {
        this.lodEnabled = enabled;
    }

    public boolean isLODEnabled() {
        return lodEnabled;
    }

    public void toggleLOD() {
        lodEnabled = !lodEnabled;
        System.out.println("LOD system: " + (lodEnabled ? "ON" : "OFF"));
    }

    // === FOG CONTROLS ===

    public void setFogEnabled(boolean enabled) {
        this.fogEnabled = enabled;
    }

    public boolean isFogEnabled() {
        return fogEnabled;
    }

    public void toggleFog() {
        fogEnabled = !fogEnabled;
        System.out.println("Fog: " + (fogEnabled ? "ON" : "OFF"));
    }

    public void setFogStart(float start) {
        this.fogStart = Math.max(0, Math.min(1, start));
    }

    public void setFogEnd(float end) {
        this.fogEnd = Math.max(0, Math.min(1, end));
    }

    public float getFogStart() {
        return fogStart;
    }

    public float getFogEnd() {
        return fogEnd;
    }

    // === STATS ===

    public String getCullingStats() {
        return String.format("Rendered: %d / %d chunks (culled %d)",
                lastChunksRendered, lastChunksTotal, lastChunksCulled);
    }

    public String getLODStats() {
        return String.format("LOD: L0=%d L1=%d L2=%d L3=%d",
                lodCounts[0], lodCounts[1], lodCounts[2], lodCounts[3]);
    }

    public int getChunksRendered() {
        return lastChunksRendered;
    }

    public int getChunksTotal() {
        return lastChunksTotal;
    }

    public int getChunksCulled() {
        return lastChunksCulled;
    }

    public int[] getLODCounts() {
        return lodCounts.clone();
    }

    public TextureArray getAtlasTexture() {
        return atlasTexture;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public Camera getCamera() {
        return camera;
    }

    // === SHADERS WITH FOG SUPPORT ===

    private String getVertexShader() {
        return "#version 330 core\n" +
                "layout(location=0) in vec3 aPos;\n" +
                "layout(location=1) in vec2 aUV;\n" +
                "layout(location=2) in float aAO;\n" +
                "layout(location=3) in float aFaceIdx;\n" +
                "layout(location=4) in float aTileIndex;\n" +
                "layout(location=5) in float aSkyLight;\n" + // ✅ NEW
                "layout(location=6) in float aBlockLight;\n" + // ✅ NEW
                "layout(location=7) in vec3 aColor;\n" + // ✅ NEW: Vertex Color
                "\n" +
                "uniform mat4 uProj, uView, uModel;\n" +
                "uniform float uTime;\n" +
                "uniform int uWaterPass;\n" +
                "uniform float uWaterLevel;\n" +
                "uniform vec3 uCameraPos;\n" +
                "\n" +
                "out vec2 vUV;\n" +
                "out float vAO;\n" +
                "out float vSkyLight;\n" + // ✅ NEW
                "out float vBlockLight;\n" + // ✅ NEW
                "out vec2 vWorldXZ;\n" +
                "out vec3 vWP;\n" +
                "out float vDistFromCamera;\n" +
                "out float vTileIndex;\n" +
                "out vec3 vColor;\n" +
                "out vec3 vNormal;\n" +
                "\n" +
                "float noise2D(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
                "float smoothNoise2D(vec2 p){ vec2 i=floor(p), f=fract(p); float a=noise2D(i); float b=noise2D(i+vec2(1,0)); float c=noise2D(i+vec2(0,1)); float d=noise2D(i+vec2(1,1)); vec2 u=f*f*(3.0-2.0*f); return mix(a,b,u.x) + (c-a)*u.y*(1.0-u.x) + (d-b)*u.x*u.y; }\n"
                +
                "float fbm(vec2 p){ float v=0.0, a=0.5; for(int i=0;i<4;i++){ v+=a*smoothNoise2D(p); p*=2.0; a*=0.5; } return v; }\n"
                +
                "float heightWater(vec2 xz, float t){ vec2 p = xz*0.18 + vec2(t*0.30, -t*0.26); float h = fbm(p)*2.0-1.0; return 0.06*h; }\n"
                +
                "\n" +
                "vec3 faceNormal(float idx){\n" +
                "  if (idx < 0.5) return vec3( 1.0, 0.0, 0.0);\n" +
                "  else if (idx < 1.5) return vec3(-1.0, 0.0, 0.0);\n" +
                "  else if (idx < 2.5) return vec3(0.0, 1.0, 0.0);\n" +
                "  else if (idx < 3.5) return vec3(0.0,-1.0, 0.0);\n" +
                "  else if (idx < 4.5) return vec3(0.0, 0.0, 1.0);\n" +
                "  else               return vec3(0.0, 0.0,-1.0);\n" +
                "}\n" +
                "\n" +
                "void main(){\n" +
                "  vUV = aUV;\n" +
                "  vAO = aAO;\n" +
                "  vSkyLight = aSkyLight;\n" + // ✅ Pass through
                "  vBlockLight = aBlockLight;\n" + // ✅ Pass through
                "  vTileIndex = aTileIndex;\n" +
                "\n" +
                "  vec4 wp4 = uModel * vec4(aPos, 1.0);\n" +
                "  vec3 wp  = wp4.xyz;\n" +
                "  vec3 wpO = wp;\n" +
                "\n" +
                "  vec3 localN = faceNormal(aFaceIdx);\n" +
                "  vNormal = mat3(uModel) * localN;\n" +
                "\n" +
                "  if(uWaterPass==1){\n" +
                "    float surfY = uWaterLevel + 1.0;\n" +
                "    float d = abs(wpO.y - surfY);\n" +
                "    float w = 1.0 - min(d, 1.0);\n" +
                "    w = w*w*(3.0 - 2.0*w);\n" +
                "    float dh = heightWater(wpO.xz, uTime);\n" +
                "    wp.y += dh*w;\n" +
                "    wp4.xyz = wp;\n" +
                "    vNormal = vec3(0.0,1.0,0.0);\n" +
                "  }\n" +
                "\n" +
                "  vWorldXZ = (uWaterPass==1) ? wpO.xz : wp.xz;\n" +
                "  vWP = wp;\n" +
                "  vDistFromCamera = length(wp - uCameraPos);\n" +
                "  vColor = aColor;\n" +
                "  gl_Position = uProj * uView * wp4;\n" +
                "}\n";
    }

    private String getFragmentShader() {
        return "#version 330 core\n" +
                "in vec2 vUV;\n" +
                "in float vAO;\n" +
                "in float vSkyLight;\n" +
                "in float vBlockLight;\n" +
                "in vec2 vWorldXZ;\n" +
                "in vec3 vWP;\n" +
                "in float vDistFromCamera;\n" +
                "in float vTileIndex;\n" +
                "in vec3 vColor;\n" +
                "in vec3 vNormal;\n" +
                "\n" +
                "uniform sampler2DArray uTex;\n" +
                "uniform sampler2D uTex2D;\n" +
                "uniform int uUseTextureArray;\n" +
                "uniform vec4 uTint;\n" +
                "uniform int uTileGrassTopIndex;\n" +
                "uniform int uTileLeavesIndex;\n" +
                "uniform vec3 uGrassTint;\n" +
                "uniform vec3 uFoliageTint;\n" +
                "uniform vec3 uCameraPos;\n" +
                "uniform int uUnderwater;\n" +
                "uniform float uWaterLevel;\n" +
                "uniform int uWaterPass;\n" +
                "uniform int uFogEnabled;\n" +
                "uniform float uFogStart;\n" +
                "uniform float uFogEnd;\n" +
                "uniform vec3 uSunDir;\n" +
                "uniform vec3 uSunColor;\n" + // ADDED
                "uniform vec3 uAmbientColor;\n" + // ADDED
                "\n" +
                "out vec4 FragColor;\n" +
                "\n" +
                "float calculateFog(float dist) {\n" +
                "  if (uFogEnabled == 0) return 0.0;\n" +
                // Use smoothstep for a softer, more natural transition than linear clamp
                "  return smoothstep(uFogStart, uFogEnd, dist);\n" +
                "}\n" +
                "\n" +
                // Same logic as Skybox to ensure perfect blending
                "vec3 computeSkyColor(vec3 dir, vec3 sunDir, float sunStrength){\n" +
                "  dir = normalize(dir);\n" +
                "  float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);\n" +
                "\n" +
                "  vec3 dayHorizon   = vec3(0.55, 0.75, 0.95);\n" +
                "  vec3 dayZenith    = vec3(0.10, 0.45, 0.90);\n" +
                "  vec3 nightHorizon = vec3(0.02, 0.05, 0.10);\n" +
                "  vec3 nightZenith  = vec3(0.00, 0.02, 0.05);\n" +
                "\n" +
                "  float night = 1.0 - clamp(sunStrength * 1.3, 0.0, 1.0);\n" +
                "  vec3 dayCol   = mix(dayHorizon,   dayZenith,   h);\n" +
                "  vec3 nightCol = mix(nightHorizon, nightZenith, h);\n" +
                "  vec3 col = mix(nightCol, dayCol, 1.0 - night);\n" +
                "\n" +
                "  float sunAmt = pow(max(dot(dir, -sunDir), 0.0), 512.0);\n" +
                "  col += uSunColor * sunAmt * 1.5;\n" +
                "  return col;\n" +
                "}\n" +
                "\n" +
                "void main(){\n" +
                "  int tileIndex = int(vTileIndex + 0.5);\n" +
                "  float dist = length(vWP - uCameraPos);\n" +
                "  float fogAmount = calculateFog(dist);\n" +
                "\n" +
                // Recalculate sun strength for sky color logic
                "  float sunHeight = max(0.0, uSunDir.y);\n" +
                "  float sunStrength = pow(sunHeight, 1.2);\n" + // Approximate match to Java side w/ logic
                "\n" +
                "  float skyIntensity = clamp(sunHeight * 1.0 + 0.2, 0.2, 1.0);\n" + // RE-ADDED this line
                "  float skyLum = vSkyLight * skyIntensity;\n" +
                "  float blockLum = vBlockLight;\n" +
                "  float lightLevel = max(skyLum, blockLum);\n" +
                "\n" +
                "  vec4 texColor;\n" +
                "  vec3 finalColor;\n" +
                "\n" +
                "  if (uWaterPass == 0) {\n" +
                "     if (uUseTextureArray == 1) {\n" +
                "         texColor = texture(uTex, vec3(vUV, tileIndex));\n" +
                "     } else {\n" +
                "         texColor = texture(uTex2D, vUV);\n" +
                "     }\n" +
                "     if(texColor.a < 0.1) discard;\n" +
                "\n" +
                "     vec3 terrainTint = uTint.rgb * vColor;\n" +
                "     finalColor = texColor.rgb * terrainTint;\n" +
                "     \n" +
                "     lightLevel = max(lightLevel, 0.1);\n" +
                "     finalColor *= vAO;\n" +
                "     finalColor *= lightLevel;\n" +
                "\n" +
                "  } else {\n" +
                "     texColor = vec4(0.2, 0.5, 0.9, 0.6);\n" +
                "     finalColor = texColor.rgb * lightLevel * vAO;\n" +
                "  }\n" +
                "\n" +
                "  // Dynamic Fog Color matching Skybox\n" +
                "  vec3 viewDir = normalize(vWP - uCameraPos);\n" +
                "  // Tweak: Flatten the gradient for fog so it looks like 'mist' (closer to horizon color)\n" +
                "  // instead of matching the deep blue zenith exactly.\n" +
                "  vec3 fogViewDir = viewDir;\n" +
                "  fogViewDir.y *= 0.3; // Flatten vertical component\n" +
                "  fogViewDir = normalize(fogViewDir);\n" +
                "\n" +
                "  vec3 skyColor = computeSkyColor(viewDir, normalize(uSunDir), sunStrength);\n" +
                "  vec3 fogCompColor = computeSkyColor(fogViewDir, normalize(uSunDir), sunStrength);\n" +
                "  \n" +
                "  // Mix a bit of 'white mist' based on sun height (daytime mist is whitish)\n" +
                "  vec3 mistColor = vec3(0.9, 0.95, 1.0) * skyIntensity;\n" +
                "  vec3 fogColor = mix(fogCompColor, mistColor, 0.2); // 20% white haze\n" +
                "\n" +
                "  if(uUnderwater == 1){\n" +
                "      fogColor = vec3(0.1, 0.3, 0.7);\n" +
                "      fogAmount = 1.0 - exp(-dist * 0.15);\n" +
                "  }\n" +
                "\n" +
                "  finalColor = mix(finalColor, fogColor, fogAmount);\n" +
                "\n" +
                "  FragColor = vec4(finalColor, texColor.a * uTint.a);\n" +
                "}\n";
    }
}