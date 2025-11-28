package engine.world;

import engine.core.Config;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.gen.*;
import engine.rendering.Frustum;
import engine.utils.Math3D.Vec3;

import java.util.*;

/**
 * The game world - manages chunks, generation, and block access.
 * 
 * Features:
 * - Frustum-based chunk loading (load what you see)
 * - LOD-aware mesh building (distant chunks = simpler meshes)
 * - Aggressive pre-generation ahead of camera
 * - Priority-based generation queue
 */
public class World implements MeshBuilder.WorldAccess {
    
    private final Config config;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    
    private final WorldGenerator worldGenerator;
    private final MeshBuilder meshBuilder;
    
    // Async generation
    private final WorldGenerationExecutor genExecutor;
    private final Set<Long> pendingChunks = new HashSet<>();
    
    // Frustum for view-dependent chunk loading
    private final Frustum loadingFrustum = new Frustum();
    
    // Camera state - updated every frame
    private float[] viewProjMatrix = new float[16];
    private float cameraDirX = 0, cameraDirZ = -1;
    private boolean cameraUpdated = false;
    
    // Player chunk position for LOD calculations
    private int playerChunkX = 0;
    private int playerChunkZ = 0;
    
    // How far to load chunks (in chunk units)
    private int maxLoadDistance = 128;
    
    // Small radius around player that's always loaded (for collision)
    private int safeRadius = 4;
    
    // Pre-generation ahead of camera
    private int preGenRadius = 12;
    
    private float gameTime = 0f;
    
    public World(Config config) {
        this.config = config;
        this.maxLoadDistance = config.viewDistance;
        this.preGenRadius = Math.min(16, config.viewDistance / 2);
        
        System.out.println("[World] Created with maxLoadDistance=" + maxLoadDistance + " (view-dependent loading)");
        System.out.println("[World] Pre-generation radius: " + preGenRadius + " chunks");
        
        this.worldGenerator = new WorldGenerator(
            config.worldSeed,
            config.chunkSize,
            config.worldHeight,
            config.waterLevel
        );
        this.meshBuilder = new MeshBuilder(config.chunkSize, config.worldHeight);
        
        // Start async generator with more workers
        int numWorkers = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.genExecutor = new WorldGenerationExecutor(
            worldGenerator, 
            config.chunkSize, 
            config.worldHeight,
            numWorkers
        );
    }
    
    /**
     * Set max load distance at runtime.
     */
    public void setViewDistance(int distance) {
        this.maxLoadDistance = Math.max(4, distance);
        this.preGenRadius = Math.min(16, distance / 2);
    }
    
    public int getViewDistance() {
        return maxLoadDistance;
    }
    
    /**
     * UPDATE CAMERA STATE - call this every frame from Player!
     */
    public void updateCamera(float[] projMatrix, float[] viewMatrix, Vec3 forward) {
        // Compute View-Projection matrix
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                viewProjMatrix[col * 4 + row] = 
                    projMatrix[0 * 4 + row] * viewMatrix[col * 4 + 0] +
                    projMatrix[1 * 4 + row] * viewMatrix[col * 4 + 1] +
                    projMatrix[2 * 4 + row] * viewMatrix[col * 4 + 2] +
                    projMatrix[3 * 4 + row] * viewMatrix[col * 4 + 3];
            }
        }
        
        // Update frustum
        loadingFrustum.update(viewProjMatrix);
        
        // Store camera direction (XZ plane only)
        float len = (float) Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        if (len > 0.001f) {
            cameraDirX = forward.x / len;
            cameraDirZ = forward.z / len;
        }
        
        cameraUpdated = true;
    }
    
    /**
     * Check if a chunk is visible in the camera frustum.
     */
    private boolean isChunkInFrustum(int cx, int cz) {
        float minX = cx * config.chunkSize;
        float minZ = cz * config.chunkSize;
        float maxX = minX + config.chunkSize;
        float maxZ = minZ + config.chunkSize;
        
        return loadingFrustum.testAABB(minX, 0, minZ, maxX, config.worldHeight, maxZ);
    }
    
    /**
     * Calculate LOD level for a chunk.
     */
    private int calculateLOD(int cx, int cz) {
        int dx = cx - playerChunkX;
        int dz = cz - playerChunkZ;
        int distSq = dx * dx + dz * dz;
        return MeshBuilder.calculateLOD(distSq);
    }
    
    // ==================== BLOCK ACCESS ====================
    
    public int getBlock(int x, int y, int z) {
        if (y < 0 || y >= config.worldHeight) {
            return Blocks.AIR().getNumericId();
        }
        
        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        int lx = mod(x, config.chunkSize);
        int lz = mod(z, config.chunkSize);
        
        Chunk chunk = getChunkIfLoaded(cx, cz);
        if (chunk == null || chunk.getPhase() == Chunk.Phase.EMPTY) {
            return Blocks.AIR().getNumericId();
        }
        
        return chunk.getBlock(lx, y, lz);
    }
    
    public Block getBlockType(int x, int y, int z) {
        return Blocks.get(getBlock(x, y, z));
    }
    
    public void setBlock(int x, int y, int z, int blockId) {
        if (y < 0 || y >= config.worldHeight) return;
        
        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        int lx = mod(x, config.chunkSize);
        int lz = mod(z, config.chunkSize);
        
        Chunk chunk = getChunkIfLoaded(cx, cz);
        if (chunk == null || chunk.getPhase() == Chunk.Phase.EMPTY) return;
        
        chunk.setBlock(lx, y, lz, blockId);
        
        if (lx == 0) markChunkDirty(cx - 1, cz);
        if (lx == config.chunkSize - 1) markChunkDirty(cx + 1, cz);
        if (lz == 0) markChunkDirty(cx, cz - 1);
        if (lz == config.chunkSize - 1) markChunkDirty(cx, cz + 1);
    }
    
    public void setBlock(int x, int y, int z, String blockId) {
        Block block = Blocks.get(blockId);
        setBlock(x, y, z, block.getNumericId());
    }
    
    @Override
    public int peekBlock(int x, int y, int z) {
        if (y < 0 || y >= config.worldHeight) {
            return Blocks.AIR().getNumericId();
        }
        
        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        int lx = mod(x, config.chunkSize);
        int lz = mod(z, config.chunkSize);
        
        Chunk chunk = chunks.get(chunkKey(cx, cz));
        if (chunk == null) {
            return Blocks.AIR().getNumericId();
        }
        
        return chunk.getBlock(lx, y, lz);
    }
    
    // ==================== BLOCK PROPERTIES ====================
    
    public boolean isSolid(int blockId) { return Blocks.isSolid(blockId); }
    public boolean isOpaque(int blockId) { return Blocks.isOpaque(blockId); }
    public boolean isLiquid(int blockId) { return Blocks.isLiquid(blockId); }
    
    // ==================== CHUNK MANAGEMENT ====================
    
    private long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
    
    public Chunk getChunkIfLoaded(int cx, int cz) {
        return chunks.get(chunkKey(cx, cz));
    }
    
    private void markChunkDirty(int cx, int cz) {
        Chunk chunk = getChunkIfLoaded(cx, cz);
        if (chunk != null) {
            chunk.setDirty(true);
        }
    }
    
    // ==================== GENERATION ====================
    
    private void ensureFeatures(Chunk chunk) {
        if (chunk.getPhase().ordinal() >= Chunk.Phase.FEATURES.ordinal()) {
            return;
        }
        
        if (!neighborsHaveTerrain(chunk.getX(), chunk.getZ())) {
            return;
        }
        
        worldGenerator.generateFeatures(
            chunk.getX(), chunk.getZ(),
            chunk.getBlockData(),
            chunk.getHeightMapData(),
            createBlockPlacer()
        );
        
        chunk.setPhase(Chunk.Phase.FEATURES);
        chunk.setDirty(true);
        
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                markChunkDirty(chunk.getX() + dx, chunk.getZ() + dz);
            }
        }
    }
    
    private boolean neighborsHaveTerrain(int cx, int cz) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                Chunk n = getChunkIfLoaded(cx + dx, cz + dz);
                if (n == null || n.getPhase().ordinal() < Chunk.Phase.TERRAIN.ordinal()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private FeatureGenerator.BlockPlacer createBlockPlacer() {
        return new FeatureGenerator.BlockPlacer() {
            @Override
            public boolean canPlace(int cx, int cz) {
                Chunk chunk = getChunkIfLoaded(cx, cz);
                return chunk != null && chunk.getPhase().ordinal() >= Chunk.Phase.TERRAIN.ordinal();
            }
            
            @Override
            public int getBlock(int cx, int cz, int lx, int ly, int lz) {
                Chunk chunk = getChunkIfLoaded(cx, cz);
                return chunk != null ? chunk.getBlock(lx, ly, lz) : Blocks.AIR().getNumericId();
            }
            
            @Override
            public void setBlock(int cx, int cz, int lx, int ly, int lz, int blockId) {
                Chunk chunk = getChunkIfLoaded(cx, cz);
                if (chunk != null) {
                    chunk.setBlock(lx, ly, lz, blockId);
                }
            }
        };
    }
    
    /**
     * Build mesh for a chunk - always builds LOD 0 (full detail).
     * The Renderer will use LOD 0 for all chunks for now until
     * the LOD system is properly implemented with all levels pre-built.
     */
    private void buildMesh(Chunk chunk) {
        if (!chunk.isDirty()) return;
        
        // Always build LOD 0 - this is the safe approach
        MeshBuilder.MeshData meshData = meshBuilder.buildMesh(
            new ChunkDataAdapter(chunk),
            this
        );
        
        // Use original uploadMesh which uploads to LOD 0
        chunk.uploadMesh(
            meshData.solidVertices,
            meshData.transparentVertices,
            meshData.waterVertices
        );
    }
    
    // ==================== UPDATE ====================
    
    public void update(float deltaTime) {
        gameTime += deltaTime;
        pollCompletedChunks();
    }
    
    private void pollCompletedChunks() {
        // Process more chunks per frame
        int maxPerFrame = 16;
        int processed = 0;
        
        ChunkGenerationTask completed;
        while (processed < maxPerFrame && (completed = genExecutor.pollCompleted()) != null) {
            if (!completed.cancelled) {
                integrateCompletedChunk(completed);
            }
            processed++;
        }
    }
    
    private void integrateCompletedChunk(ChunkGenerationTask task) {
        long key = chunkKey(task.chunkX, task.chunkZ);
        pendingChunks.remove(key);
        
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = new Chunk(task.chunkX, task.chunkZ);
            chunks.put(key, chunk);
        }
        
        System.arraycopy(task.blockData, 0, chunk.getBlockData(), 0, task.blockData.length);
        System.arraycopy(task.heightMap, 0, chunk.getHeightMapData(), 0, task.heightMap.length);
        
        chunk.setPhase(Chunk.Phase.TERRAIN);
        chunk.setDirty(true);
    }
    
    /**
     * Maintain chunks - with aggressive pre-generation ahead of camera.
     */
    public void maintainChunks(float playerX, float playerZ) {
        int pcx = floorDiv((int) playerX, config.chunkSize);
        int pcz = floorDiv((int) playerZ, config.chunkSize);
        
        playerChunkX = pcx;
        playerChunkZ = pcz;
        
        int maxSubmitPerFrame = 512;
        int submitted = 0;
        
        // 1. Always load chunks near player (for collision)
        for (int dz = -safeRadius; dz <= safeRadius && submitted < maxSubmitPerFrame; dz++) {
            for (int dx = -safeRadius; dx <= safeRadius && submitted < maxSubmitPerFrame; dx++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                
                if (submitChunkIfNeeded(cx, cz, ChunkGenerationTask.Priority.CRITICAL)) {
                    submitted++;
                }
            }
        }
        
        // 2. PRE-GENERATE ahead of camera direction
        if (cameraUpdated) {
            submitted = preGenerateAhead(pcx, pcz, submitted, maxSubmitPerFrame);
        }
        
        // 3. Load chunks in VIEW FRUSTUM
        if (cameraUpdated) {
            int R = maxLoadDistance;
            
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (submitted >= maxSubmitPerFrame) break;
                    
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    
                    // Skip already-covered chunks
                    if (Math.abs(dx) <= safeRadius && Math.abs(dz) <= safeRadius) continue;
                    
                    // Must be in frustum
                    if (!isChunkInFrustum(cx, cz)) continue;
                    
                    int distSq = dx * dx + dz * dz;
                    ChunkGenerationTask.Priority priority;
                    if (distSq <= 8 * 8) {
                        priority = ChunkGenerationTask.Priority.HIGH;
                    } else if (distSq <= 32 * 32) {
                        priority = ChunkGenerationTask.Priority.NORMAL;
                    } else {
                        priority = ChunkGenerationTask.Priority.LOW;
                    }
                    
                    if (submitChunkIfNeeded(cx, cz, priority)) {
                        submitted++;
                    }
                }
            }
        }
        
        // 4. Process features for chunks that have neighbors
        int featureRadius = Math.min(maxLoadDistance, 24);
        for (Chunk chunk : chunks.values()) {
            int dx = chunk.getX() - pcx;
            int dz = chunk.getZ() - pcz;
            if (dx * dx + dz * dz > featureRadius * featureRadius) continue;
            
            if (chunk.getPhase() == Chunk.Phase.TERRAIN) {
                ensureFeatures(chunk);
            }
        }
        
        // 5. Build meshes for visible chunks with LOD awareness
        buildMeshesWithLOD(pcx, pcz);
        
        // 6. Unload chunks outside view AND far from player
        unloadChunksOutsideView(pcx, pcz);
    }
    
    /**
     * Pre-generate chunks ahead of camera direction.
     */
    private int preGenerateAhead(int pcx, int pcz, int currentSubmitted, int maxSubmit) {
        int submitted = currentSubmitted;
        
        // Generate in a cone ahead of camera
        for (int dist = safeRadius + 1; dist <= preGenRadius && submitted < maxSubmit; dist++) {
            int arcWidth = Math.min(dist / 2 + 2, 6);
            
            for (int side = -arcWidth; side <= arcWidth && submitted < maxSubmit; side++) {
                // Position ahead of camera
                float aheadX = cameraDirX * dist - cameraDirZ * side * 0.4f;
                float aheadZ = cameraDirZ * dist + cameraDirX * side * 0.4f;
                
                int cx = pcx + Math.round(aheadX);
                int cz = pcz + Math.round(aheadZ);
                
                ChunkGenerationTask.Priority priority;
                if (Math.abs(side) <= 1 && dist <= 6) {
                    priority = ChunkGenerationTask.Priority.CRITICAL;
                } else if (Math.abs(side) <= 2) {
                    priority = ChunkGenerationTask.Priority.HIGH;
                } else {
                    priority = ChunkGenerationTask.Priority.NORMAL;
                }
                
                if (submitChunkIfNeeded(cx, cz, priority)) {
                    submitted++;
                }
            }
        }
        
        return submitted;
    }
    
    /**
     * Build meshes with LOD awareness.
     */
    private void buildMeshesWithLOD(int pcx, int pcz) {
        // Collect and sort dirty chunks by priority
        List<Chunk> dirtyChunks = new ArrayList<>();
        for (Chunk chunk : chunks.values()) {
            if (chunk.isDirty() && chunk.getPhase().ordinal() >= Chunk.Phase.TERRAIN.ordinal()) {
                dirtyChunks.add(chunk);
            }
        }
        
        // Sort: near player first, then in-frustum, then by distance
        dirtyChunks.sort((a, b) -> {
            int dxA = a.getX() - pcx, dzA = a.getZ() - pcz;
            int dxB = b.getX() - pcx, dzB = b.getZ() - pcz;
            int distA = dxA * dxA + dzA * dzA;
            int distB = dxB * dxB + dzB * dzB;
            
            boolean nearA = distA <= safeRadius * safeRadius;
            boolean nearB = distB <= safeRadius * safeRadius;
            if (nearA != nearB) return nearA ? -1 : 1;
            
            boolean inViewA = isChunkInFrustum(a.getX(), a.getZ());
            boolean inViewB = isChunkInFrustum(b.getX(), b.getZ());
            if (inViewA != inViewB) return inViewA ? -1 : 1;
            
            return Integer.compare(distA, distB);
        });
        
        // Build meshes
        int meshesBuilt = 0;
        int maxMeshesPerFrame = config.maxChunkUpdatesPerFrame * 2;
        
        for (Chunk chunk : dirtyChunks) {
            if (meshesBuilt >= maxMeshesPerFrame) break;
            
            int dx = chunk.getX() - pcx;
            int dz = chunk.getZ() - pcz;
            int distSq = dx * dx + dz * dz;
            
            // Build mesh (always LOD 0 for stability)
            buildMesh(chunk);
            
            meshesBuilt++;
        }
    }
    
    /**
     * Submit a chunk for generation if not already loaded/pending.
     */
    private boolean submitChunkIfNeeded(int cx, int cz, ChunkGenerationTask.Priority priority) {
        long key = chunkKey(cx, cz);
        Chunk chunk = chunks.get(key);
        
        if (chunk == null || chunk.getPhase() == Chunk.Phase.EMPTY) {
            if (!pendingChunks.contains(key)) {
                if (genExecutor.submit(cx, cz, priority)) {
                    pendingChunks.add(key);
                    if (chunk == null) {
                        chunks.put(key, new Chunk(cx, cz));
                    }
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Unload chunks outside view and far from player.
     */
    private void unloadChunksOutsideView(int pcx, int pcz) {
        int safeRadiusSq = (safeRadius + 2) * (safeRadius + 2);
        int maxDistSq = (maxLoadDistance + 8) * (maxLoadDistance + 8);
        
        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> entry = it.next();
            Chunk chunk = entry.getValue();
            
            int dx = chunk.getX() - pcx;
            int dz = chunk.getZ() - pcz;
            int distSq = dx * dx + dz * dz;
            
            // Keep chunks near player (always)
            if (distSq <= safeRadiusSq) continue;
            
            // Keep chunks within max load distance
            if (distSq <= maxDistSq) continue;
            
            // Only unload if REALLY far away (beyond max + buffer)
            int unloadDistSq = (maxLoadDistance + 16) * (maxLoadDistance + 16);
            if (distSq > unloadDistSq) {
                chunk.cleanup();
                pendingChunks.remove(entry.getKey());
                it.remove();
            }
        }
    }
    
    public String getGenStats() {
        return String.format("Chunks: %d loaded, %d pending | Workers: %d",
            chunks.size(), pendingChunks.size(), genExecutor.getNumWorkers());
    }
    
    /**
     * Get ALL loaded chunks for rendering.
     */
    public ArrayList<Chunk> getVisibleChunks(Vec3 cameraPos) {
        ArrayList<Chunk> visible = new ArrayList<>();
        
        for (Chunk chunk : chunks.values()) {
            if (chunk.getPhase().ordinal() >= Chunk.Phase.TERRAIN.ordinal()) {
                visible.add(chunk);
            }
        }
        
        return visible;
    }
    
    // ==================== SPAWN ====================
    
    public Vec3 findSpawnPosition() {
        int spawnRadius = 2;
        for (int dz = -spawnRadius; dz <= spawnRadius; dz++) {
            for (int dx = -spawnRadius; dx <= spawnRadius; dx++) {
                generateChunkSync(dx, dz);
            }
        }
        
        for (int r = 0; r <= 32; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz : new int[]{r, -r}) {
                    int sy = getSurfaceHeight(dx, dz);
                    if (sy > config.waterLevel + 1 && isHeadroomClear(dx, sy, dz)) {
                        return new Vec3(dx + 0.5f, sy + 1.0f, dz + 0.5f);
                    }
                }
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                for (int dx : new int[]{r, -r}) {
                    int sy = getSurfaceHeight(dx, dz);
                    if (sy > config.waterLevel + 1 && isHeadroomClear(dx, sy, dz)) {
                        return new Vec3(dx + 0.5f, sy + 1.0f, dz + 0.5f);
                    }
                }
            }
        }
        
        return new Vec3(0.5f, config.waterLevel + 10, 0.5f);
    }
    
    private void generateChunkSync(int cx, int cz) {
        long key = chunkKey(cx, cz);
        Chunk chunk = chunks.get(key);
        
        if (chunk == null) {
            chunk = new Chunk(cx, cz);
            chunks.put(key, chunk);
        }
        
        if (chunk.getPhase() == Chunk.Phase.EMPTY) {
            worldGenerator.generateTerrain(cx, cz, chunk.getBlockData(), chunk.getHeightMapData());
            chunk.setPhase(Chunk.Phase.TERRAIN);
            chunk.setDirty(true);
        }
    }
    
    public int getSurfaceHeight(int x, int z) {
        for (int y = config.worldHeight - 1; y >= 0; y--) {
            int blockId = getBlock(x, y, z);
            Block block = Blocks.get(blockId);
            if (!block.isAir() && !block.isLiquid() && !block.isTransparent()) {
                return y;
            }
        }
        return 0;
    }
    
    private boolean isHeadroomClear(int x, int topY, int z) {
        int need = (int) Math.ceil(config.playerHeight);
        for (int dy = 1; dy <= need; dy++) {
            int blockId = getBlock(x, topY + dy, z);
            if (Blocks.isSolid(blockId) || Blocks.isLiquid(blockId)) {
                return false;
            }
        }
        return true;
    }
    
    // ==================== UTILITY ====================
    
    public float getGameTime() { return gameTime; }
    public Config getConfig() { return config; }
    public WorldGenerator getWorldGenerator() { return worldGenerator; }
    
    private static int floorDiv(int a, int b) {
        int q = a / b;
        int r = a % b;
        if ((r != 0) && ((a ^ b) < 0)) q--;
        return q;
    }
    
    private static int mod(int a, int b) {
        int m = a % b;
        if (m < 0) m += b;
        return m;
    }
    
    // ==================== CLEANUP ====================
    
    public void cleanup() {
        genExecutor.shutdown();
        for (Chunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        chunks.clear();
        pendingChunks.clear();
    }
    
    // ==================== ADAPTER ====================
    
    private static class ChunkDataAdapter implements MeshBuilder.ChunkData {
        private final Chunk chunk;
        
        ChunkDataAdapter(Chunk chunk) { this.chunk = chunk; }
        
        @Override public int getBlock(int x, int y, int z) { return chunk.getBlock(x, y, z); }
        @Override public int getWorldX() { return chunk.getWorldX(); }
        @Override public int getWorldZ() { return chunk.getWorldZ(); }
    }
}