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
    private float fogStart = 0.85f;       // Start fog at 60% of view distance
    private float fogEnd = 0.95f;        // Full fog at 95% of view distance
    
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

    
    // Fog uniforms
    private int uFogEnabled, uFogColor, uFogStart, uFogEnd;
    
    private float clearR = 0.6f, clearG = 0.8f, clearB = 1.0f;
    private World currentWorld;
    
    public Renderer(Config config) {
        this.config = config;
    }
    
    public void init() {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);

        // atlas 8x8 come hai già nei tuoi shader
        atlasTexture = new TextureArray("atlas.png", 8, 8);

        voxelShader = new Shader(getVertexShader(), getFragmentShader());
        cacheUniformLocations();
        wireframeRenderer = new WireframeRenderer();

        System.out.println("Renderer initialized with LOD and Fog support");
    }
    
    private void cacheUniformLocations() {
        uProj = voxelShader.getUniformLocation("uProj");
        uView = voxelShader.getUniformLocation("uView");
        uModel = voxelShader.getUniformLocation("uModel");
        uTex = voxelShader.getUniformLocation("uTex");
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
        uTileLeavesIndex   = voxelShader.getUniformLocation("uTileLeavesIndex");

        uGrassTint = voxelShader.getUniformLocation("uGrassTint");
        uFoliageTint = voxelShader.getUniformLocation("uFoliageTint");

        // Fog uniforms
        uFogEnabled = voxelShader.getUniformLocation("uFogEnabled");
        uFogColor = voxelShader.getUniformLocation("uFogColor");
        uFogStart = voxelShader.getUniformLocation("uFogStart");
        uFogEnd = voxelShader.getUniformLocation("uFogEnd");

    }
    
    public void beginFrame() {
        beginFrame(currentWorld);
    }
    
    public void beginFrame(World world) {
        this.currentWorld = world;
        
        boolean underwater = isHeadUnderwater(world);
        if (underwater) {
            clearR = 0.10f; clearG = 0.32f; clearB = 0.70f;
        } else {
            clearR = 0.6f; clearG = 0.8f; clearB = 1.0f;
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
        if (camera == null || world == null) return false;
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
        if (!lodEnabled) return 0;
        
        int dx = chunk.getX() - currentPlayerChunkX;
        int dz = chunk.getZ() - currentPlayerChunkZ;
        int distSq = dx * dx + dz * dz;
        
        return MeshBuilder.calculateLOD(distSq);
        */
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

    int grassTopIndex = 0 * tilesX + 0;  // (tileX=0, tileY=0)
    int leavesIndex   = 0 * tilesX + 5;  // (tileX=5, tileY=0)
    // se ti serve uTileWaterIndex puoi farlo allo stesso modo: int waterIndex = 0 * tilesX + 6;

    voxelShader.setUniform(uTileGrassTopIndex, grassTopIndex); // <<< nuovo
    voxelShader.setUniform(uTileLeavesIndex,   leavesIndex);   // <<< nuovo
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
    for (int i = 0; i < 4; i++) lodCounts[i] = 0;
    
    // Render solid (front-to-back)
    glDisable(GL_BLEND);
    glDepthMask(true);
    voxelShader.setUniform(uWaterPass, 0);
    voxelShader.setUniform(uTint, 1f, 1f, 1f, 1f);
    
    for (Chunk chunk : visibleChunks) {
        int lod = calculateChunkLOD(chunk);
        lodCounts[lod]++;
        
        // Get mesh for this LOD level
        Mesh solidMesh = chunk.getSolidMesh(lod);
        if (solidMesh != null && !solidMesh.isEmpty()) {
            Mat4 model = Mat4.translate(
                chunk.getX() * config.chunkSize, 0,
                chunk.getZ() * config.chunkSize
            );
            voxelShader.setUniform(uModel, model);
            solidMesh.draw();
        }
    }
    
    // Block selection
    voxelShader.unbind();
    renderBlockSelection(world);
    voxelShader.bind();
    
    // Transparent (back-to-front for correct alpha blending)
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_CULL_FACE);
    glDepthMask(false);
    voxelShader.setUniform(uWaterPass, 0);
    
    // Iterate back-to-front
    for (int i = visibleChunks.size() - 1; i >= 0; i--) {
        Chunk chunk = visibleChunks.get(i);
        int lod = calculateChunkLOD(chunk);
        
        // At high LOD, transparent mesh is empty (merged into solid)
        Mesh transpMesh = chunk.getTransparentMesh(lod);
        if (transpMesh != null && !transpMesh.isEmpty()) {
            Mat4 model = Mat4.translate(
                chunk.getX() * config.chunkSize, 0,
                chunk.getZ() * config.chunkSize
            );
            voxelShader.setUniform(uModel, model);
            transpMesh.draw();
        }
    }
    
    // Water (back-to-front)
    glDepthMask(true);
    voxelShader.setUniform(uWaterPass, 1);
    
    for (int i = visibleChunks.size() - 1; i >= 0; i--) {
        Chunk chunk = visibleChunks.get(i);
        int lod = calculateChunkLOD(chunk);
        
        Mesh waterMesh = chunk.getWaterMesh(lod);
        if (waterMesh != null && !waterMesh.isEmpty()) {
            Mat4 model = Mat4.translate(
                chunk.getX() * config.chunkSize, 0,
                chunk.getZ() * config.chunkSize
            );
            voxelShader.setUniform(uModel, model);
            waterMesh.draw();
        }
    }
    
    glDepthMask(true);
    voxelShader.unbind();
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
                vp[col * 4 + row] = 
                    p[0 * 4 + row] * v[col * 4 + 0] +
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
    
    public void endFrame() {}
    
    public void cleanup() {
        if (voxelShader != null) voxelShader.cleanup();
        if (atlasTexture != null) atlasTexture.cleanup();
        if (wireframeRenderer != null) wireframeRenderer.cleanup();
    }
    
    private void renderBlockSelection(World world) {
        if (camera == null || world == null) return;
        
        Raycast.RayHit hit = Raycast.cast(
            world,
            camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
            camera.getForward().x, camera.getForward().y, camera.getForward().z,
            6.0f
        );
        
        if (hit.hit) {
            glDisable(GL_BLEND);
            glEnable(GL_DEPTH_TEST);
            wireframeRenderer.draw(
                camera.getProjectionMatrix(), camera.getViewMatrix(),
                hit.blockX, hit.blockY, hit.blockZ,
                0.0f, 0.0f, 0.0f, 1.0f
            );
        }
    }
    
    // === FRUSTUM CONTROLS ===
    
    public void setFrustumCullingEnabled(boolean enabled) { this.frustumCullingEnabled = enabled; }
    public boolean isFrustumCullingEnabled() { return frustumCullingEnabled; }
    public void toggleFrustumCulling() { 
        frustumCullingEnabled = !frustumCullingEnabled;
        System.out.println("Frustum culling: " + (frustumCullingEnabled ? "ON" : "OFF"));
    }
    
    // === LOD CONTROLS ===
    
    public void setLODEnabled(boolean enabled) { this.lodEnabled = enabled; }
    public boolean isLODEnabled() { return lodEnabled; }
    public void toggleLOD() {
        lodEnabled = !lodEnabled;
        System.out.println("LOD system: " + (lodEnabled ? "ON" : "OFF"));
    }
    
    // === FOG CONTROLS ===
    
    public void setFogEnabled(boolean enabled) { this.fogEnabled = enabled; }
    public boolean isFogEnabled() { return fogEnabled; }
    public void toggleFog() {
        fogEnabled = !fogEnabled;
        System.out.println("Fog: " + (fogEnabled ? "ON" : "OFF"));
    }
    
    public void setFogStart(float start) { this.fogStart = Math.max(0, Math.min(1, start)); }
    public void setFogEnd(float end) { this.fogEnd = Math.max(0, Math.min(1, end)); }
    public float getFogStart() { return fogStart; }
    public float getFogEnd() { return fogEnd; }
    
    // === STATS ===
    
    public String getCullingStats() {
        return String.format("Rendered: %d / %d chunks (culled %d)", 
            lastChunksRendered, lastChunksTotal, lastChunksCulled);
    }
    
    public String getLODStats() {
        return String.format("LOD: L0=%d L1=%d L2=%d L3=%d",
            lodCounts[0], lodCounts[1], lodCounts[2], lodCounts[3]);
    }
    
    public int getChunksRendered() { return lastChunksRendered; }
    public int getChunksTotal() { return lastChunksTotal; }
    public int getChunksCulled() { return lastChunksCulled; }
    public int[] getLODCounts() { return lodCounts.clone(); }
    
    public void setCamera(Camera camera) { this.camera = camera; }
    public Camera getCamera() { return camera; }
    
    // === SHADERS WITH FOG SUPPORT ===
    
private String getVertexShader() {
    return "#version 330 core\n" +
        "layout(location=0) in vec3 aPos;\n" +
        "layout(location=1) in vec2 aUV;\n" +
        "layout(location=2) in float aAO;\n" +
        "layout(location=3) in float aFaceIdx;\n" +
        "layout(location=4) in float aTileIndex;\n" + // <<< nuovo
        "uniform mat4 uProj,uView,uModel;\n" +
        "uniform float uTime;\n" +
        "uniform int uWaterPass;\n" +
        "uniform float uWaterLevel;\n" +
        "uniform vec3 uCameraPos;\n" +
        "out vec2 vUV;\n" +
        "out float vAO;\n" +
        "out vec2 vWorldXZ;\n" +
        "out vec3 vWP;\n" +
        "out float vDistFromCamera;\n" +
        "out float vTileIndex;\n" + // <<< nuovo
        "float noise2D(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "float smoothNoise2D(vec2 p){ vec2 i=floor(p), f=fract(p); float a=noise2D(i); float b=noise2D(i+vec2(1,0)); float c=noise2D(i+vec2(0,1)); float d=noise2D(i+vec2(1,1)); vec2 u=f*f*(3.0-2.0*f); return mix(a,b,u.x) + (c-a)*u.y*(1.0-u.x) + (d-b)*u.x*u.y; }\n" +
        "float fbm(vec2 p){ float v=0.0, a=0.5; for(int i=0;i<4;i++){ v+=a*smoothNoise2D(p); p*=2.0; a*=0.5; } return v; }\n" +
        "float heightWater(vec2 xz, float t){ vec2 p = xz*0.18 + vec2(t*0.30, -t*0.26); float h = fbm(p)*2.0-1.0; return 0.06*h; }\n" +
        "void main(){\n" +
        "  vUV=aUV; vAO=aAO;\n" +
        "  vTileIndex = aTileIndex;\n" +
        "  vec4 wp4 = uModel*vec4(aPos,1.0);\n" +
        "  vec3 wp = wp4.xyz;\n" +
        "  vec3 wpO=wp;\n" +
        "  if(uWaterPass==1){\n" +
        "    float surfY=uWaterLevel+1.0;\n" +
        "    float d=abs(wpO.y-surfY);\n" +
        "    float w=1.0 - min(d,1.0);\n" +
        "    w=w*w*(3.0-2.0*w);\n" +
        "    float dh=heightWater(wpO.xz,uTime);\n" +
        "    wp.y += dh*w;\n" +
        "    wp4.xyz = wp;\n" +
        "  }\n" +
        "  vWorldXZ=(uWaterPass==1)?wpO.xz:wp.xz;\n" +
        "  vWP=wp;\n" +
        "  vDistFromCamera=length(wp - uCameraPos);\n" +
        "  gl_Position=uProj*uView*wp4;\n" +
        "}";
}

    
private String getFragmentShader() {
    return "#version 330 core\n" +
        "in vec2 vUV;\n" +
        "in float vAO;\n" +
        "in vec2 vWorldXZ;\n" +
        "in vec3 vWP;\n" +
        "in float vDistFromCamera;\n" +
        "in float vTileIndex;\n" +
        "uniform sampler2DArray uTex;\n" +
        "uniform vec4 uTint;\n" +
        "uniform int uTilesX,uTilesY;\n" +
        "uniform int uTileGrassTopIndex;\n" +
        "uniform int uTileLeavesIndex;\n" +
        "uniform vec3 uGrassTint;\n" +
        "uniform vec3 uFoliageTint;\n" +
        "uniform float uTime;\n" +
        "uniform vec3 uCameraPos;\n" +
        "uniform int uUnderwater;\n" +
        "uniform float uWaterLevel;\n" +
        "uniform int uWaterPass;\n" +
        "uniform int uFogEnabled;\n" +
        "uniform vec3 uFogColor;\n" +
        "uniform float uFogStart;\n" +
        "uniform float uFogEnd;\n" +
        "out vec4 FragColor;\n" +

        "float calculateFog(float dist) {\n" +
        "  if (uFogEnabled == 0) return 0.0;\n" +
        "  float fogFactor = (dist - uFogStart) / (uFogEnd - uFogStart);\n" +
        "  fogFactor = clamp(fogFactor, 0.0, 1.0);\n" +
        "  return fogFactor * fogFactor * (3.0 - 2.0 * fogFactor);\n" +
        "}\n" +

        "float noise2D(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233))) * 43758.5453); }\n" +
        "float smoothNoise2D(vec2 p){ vec2 i=floor(p), f=fract(p); float a=noise2D(i); float b=noise2D(i+vec2(1,0)); float c=noise2D(i+vec2(0,1)); float d=noise2D(i+vec2(1,1)); vec2 u=f*f*(3.0-2.0*f); return mix(a,b,u.x) + (c-a)*u.y*(1.0-u.x) + (d-b)*u.x*u.y; }\n" +
        "float fbm(vec2 p){ float v=0.0, a=0.5; for(int i=0;i<4;i++){ v+=a*smoothNoise2D(p); p*=2.0; a*=0.5; } return v; }\n" +
        "float heightWater(vec2 xz, float t){ vec2 p=xz*0.18 + vec2(t*0.30, -t*0.24); return (fbm(p)*2.0-1.0)*0.3; }\n" +
        "vec2 gradHeight(vec2 xz, float t){ float e=0.15; float hC=heightWater(xz,t); float hX=heightWater(xz+vec2(e,0),t)-hC; float hZ=heightWater(xz+vec2(0,e),t)-hC; return vec2(hX/e, hZ/e); }\n" +

        "void main(){\n" +
        "  float fogAmount = calculateFog(vDistFromCamera);\n" +
        "  int tileIndex = int(vTileIndex + 0.5);\n" +

        "  if(uWaterPass==0){\n" +
        "    vec4 texel = texture(uTex, vec3(vUV, vTileIndex));\n" +
        "    if(texel.a < 0.05) discard;\n" +
        "    bool isGrass  = (tileIndex == uTileGrassTopIndex);\n" +
        "    bool isLeaves = (tileIndex == uTileLeavesIndex);\n" +
        "    vec3 base = texel.rgb * vAO * uTint.rgb;\n" +
        "    if(isGrass)  base *= uGrassTint;\n" +
        "    else if(isLeaves) base *= uFoliageTint;\n" +
        "    float alpha = texel.a * uTint.a;\n" +
        "    if(uUnderwater==1){\n" +
        "      vec3 uwFog=vec3(0.12,0.38,0.85);\n" +
        "      float dist=length(vWP-uCameraPos);\n" +
        "      float fog = 1.0 - exp(-dist*0.10);\n" +
        "      float depth = (uWaterLevel - uCameraPos.y)/8.0;\n" +
        "      fog *= (0.55 + 0.45*clamp(depth,0.0,1.0));\n" +
        "      base = mix(base,uwFog,clamp(fog,0.0,1.0));\n" +
        "    }\n" +
        "    base = mix(base, uFogColor, fogAmount);\n" +
        "    FragColor = vec4(base, alpha);\n" +
        "    return;\n" +
        "  }\n" +

        "  vec2 g = gradHeight(vWorldXZ, uTime);\n" +
        "  vec3 N = normalize(vec3(-g.x,1.0,-g.y));\n" +
        "  vec3 V = normalize(uCameraPos - vWP);\n" +
        "  float viewTop = pow(max(dot(vec3(0,1,0),V),0.0),1.4);\n" +
        "  vec3 deepBlue=vec3(0.06,0.35,0.68);\n" +
        "  vec3 lightBlue=vec3(0.35,0.65,0.90);\n" +
        "  vec3 base=mix(deepBlue,lightBlue,viewTop);\n" +
        "  float steep = min(length(g)*0.9,1.0);\n" +
        "  base += vec3(smoothstep(0.25,0.8,steep)*0.04);\n" +
        "  base *= uTint.rgb;\n" +
        "  float alpha = mix(0.90*uTint.a, 0.45*uTint.a, viewTop);\n" +
        "  vec2 uvTiles=vec2(vUV.x*float(uTilesX), vUV.y*float(uTilesY));\n" +
        "  vec2 tileUV=fract(uvTiles);\n" +
        "  vec2 p=floor(tileUV*10.0)/10.0;\n" +
        "  float s=dot(p, normalize(vec2(1.0,0.25)));\n" +
        "  float tri=1.0-abs(fract(s*6.0+uTime*0.15)*2.0-1.0);\n" +
        "  float stripes=smoothstep(0.70,1.0,tri) * mix(0.7,1.0,smoothNoise2D(p*6.0));\n" +
        "  float vis=mix(0.30,1.00,viewTop);\n" +
        "  base += vec3(1.0)*stripes*vis*0.22;\n" +
        "  if(uUnderwater==1){\n" +
        "    vec3 uwFog=vec3(0.12,0.38,0.85);\n" +
        "    float dist=length(vWP-uCameraPos);\n" +
        "    float fog=1.0-exp(-dist*0.10);\n" +
        "    float depth=(uWaterLevel-uCameraPos.y)/8.0;\n" +
        "    fog*= (0.55+0.45*clamp(depth,0.0,1.0));\n" +
        "    base=mix(base,uwFog,clamp(fog,0.0,1.0));\n" +
        "  }\n" +
        "  base = mix(base, uFogColor, fogAmount);\n" +
        "  FragColor=vec4(base,alpha);\n" +
        "}";
}
}