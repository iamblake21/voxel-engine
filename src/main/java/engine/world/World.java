package engine.world;

import engine.core.Config;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.gen.*;
import engine.rendering.Frustum;
import engine.utils.Math3D.Vec3;
import engine.world.light.LightPropagator;

import java.util.*;

public class World implements MeshBuilder.WorldAccess {

    private final Config config;
    private final Map<Long, Chunk> chunks = new HashMap<>();

    private final WorldGenerator worldGenerator;
    private final MeshBuilder meshBuilder; // Usato solo per operazioni main-thread se necessario

    // Async generation
    private final WorldGenerationExecutor genExecutor;
    private final Set<Long> pendingChunks = new HashSet<>();

    // Frustum for view-dependent chunk loading
    private final Frustum loadingFrustum = new Frustum();

    // Camera state
    private float[] viewProjMatrix = new float[16];
    private float cameraDirX = 0, cameraDirZ = -1;
    private boolean cameraUpdated = false;

    // Player chunk position
    private int playerChunkX = 0;
    private int playerChunkZ = 0;

    // Settings
    private int maxLoadDistance;
    private int safeRadius = 4;
    private int preGenRadius = 12;
    private float gameTime = 0f;

    // Day/Night Cycle
    private static final float DAY_LENGTH_SECONDS = 600f;
    private float timeOfDay = 0f;
    private float dayTicks = 0f;

    public World(Config config) {
        this.config = config;
        this.dayTicks = DAY_LENGTH_SECONDS * 0.5f;
        this.maxLoadDistance = config.viewDistance;
        this.preGenRadius = Math.min(16, config.viewDistance / 2);

        this.worldGenerator = new WorldGenerator(
                config.worldSeed,
                config.chunkSize,
                config.worldHeight,
                config.waterLevel);
        this.meshBuilder = new MeshBuilder(config.chunkSize, config.worldHeight);

        int numWorkers = Math.max(2, Runtime.getRuntime().availableProcessors());
        this.genExecutor = new WorldGenerationExecutor(
                worldGenerator,
                config.chunkSize,
                config.worldHeight,
                numWorkers);

        this.safeRadius = this.config.viewDistance;
        this.preGenRadius = this.config.viewDistance;
        System.out.println("[World] Async Pipeline Initialized (Snapshot Mode)" + numWorkers);
    }

    // ==================== UPDATE & LOOP ====================

    public void update(float deltaTime) {
        gameTime += deltaTime;
        dayTicks += deltaTime;
        timeOfDay = (dayTicks / DAY_LENGTH_SECONDS);
        timeOfDay = timeOfDay - (float) Math.floor(timeOfDay);

        pollCompletedChunks();
    }

    /**
     * Controlla se ci sono task completati (sia Terreno che Mesh).
     */
    private void pollCompletedChunks() {
        // 1. Processa nuovi chunk generati (TERRENO)
        int processedTerrain = 0;
        ChunkGenerationTask terrainTask;
        while (processedTerrain < 16 && (terrainTask = genExecutor.pollCompleted()) != null) {
            if (!terrainTask.cancelled) {
                integrateCompletedTerrain(terrainTask);
            }
            processedTerrain++;
        }

        // 2. Processa LUCE completata (Fase 2A)
        int processedLight = 0;
        final int MAX_LIGHT_PER_FRAME = 32;
        LightPropagationTask lightTask;
        while (processedLight < MAX_LIGHT_PER_FRAME && (lightTask = genExecutor.pollCompletedLight()) != null) {
            integrateCompletedLight(lightTask);
            processedLight++;
        }

        // 3. Processa mesh pronte (Fase 2B)
        int processedMesh = 0;
        final int MAX_MESH_UPLOAD_PER_FRAME = 4;
        ChunkMeshTask meshTask;
        while (processedMesh < MAX_MESH_UPLOAD_PER_FRAME && (meshTask = genExecutor.pollCompletedMesh()) != null) {
            integrateCompletedMesh(meshTask);
            processedMesh++;
        }
    }

    /**
     * Integra i dati del terreno grezzo. NON lancia la mesh qui.
     */
private void integrateCompletedTerrain(ChunkGenerationTask task) {
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
}


private void integrateCompletedLight(LightPropagationTask task) {
    Chunk chunk = getChunkIfLoaded(task.chunkX, task.chunkZ);
    if (chunk == null) return;
    
    // Se il chunk non è più in pending light, ignora (task obsoleto)
    if (!chunk.isLightPending()) {
        return;
    }

    // Applica i buffer di luce calcolati dal worker
    chunk.applySkyLightData(task.snapshot.getSkyLightWriteBuffer());
    chunk.applyBlockLightData(task.snapshot.getBlockLightWriteBuffer());
    
    chunk.setLightPending(false);
    
    // Promuovi a LIGHT_DONE
    if (chunk.getPhase() == Chunk.Phase.FEATURES) {
        chunk.setPhase(Chunk.Phase.LIGHT_DONE);
    }
    
    // Se ci sono vicini che devono ripropagare, segnalali
    if (!task.neighborsToPropagate.isEmpty()) {
        Set<Long> uniqueNeighbors = new HashSet<>(task.neighborsToPropagate);
        for (long neighborKey : uniqueNeighbors) {
            int ncx = (int) (neighborKey >> 32);
            int ncz = (int) (neighborKey);
            
            Chunk nChunk = getChunkIfLoaded(ncx, ncz);
            if (nChunk != null) {
                // Se il vicino ha già la mesh, deve rigenerarla con la nuova luce
                if (nChunk.getPhase() == Chunk.Phase.MESH_DONE) {
                    nChunk.setPhase(Chunk.Phase.LIGHT_DONE);
                    nChunk.setMeshPending(false);
                }
                // Se il vicino è in FEATURES, deve ricalcolare la luce
                else if (nChunk.getPhase() == Chunk.Phase.FEATURES && !nChunk.isLightPending()) {
                    // Verrà ripreso dalla pipeline normale
                }
            }
        }
    }
}

    private void integrateCompletedMesh(ChunkMeshTask task) {
        Chunk chunk = getChunkIfLoaded(task.chunkX, task.chunkZ);
        if (chunk == null) return;

        chunk.uploadMesh(
                task.meshData.solidVertices,
                task.meshData.transparentVertices,
                task.meshData.waterVertices);
        chunk.setMeshPending(false);

        // ✅ Promozione finale
        if (chunk.getPhase() == Chunk.Phase.LIGHT_DONE) {
            chunk.setPhase(Chunk.Phase.MESH_DONE);
        }
    }
    // ==================== CHUNK MAINTENANCE ====================

    /**
     * Gestisce il caricamento chunk e l'avvio dei task di mesh.
     */
        public void maintainChunks(float playerX, float playerZ) {
            int pcx = floorDiv((int) playerX, config.chunkSize);
            int pcz = floorDiv((int) playerZ, config.chunkSize);

            // ========== FASE 1: CARICAMENTO TERRENO ==========
            int maxSubmitPerFrame = 8;
            int submitted = 0;

            // 1a. Safe radius
            for (int dz = -safeRadius; dz <= safeRadius && submitted < maxSubmitPerFrame; dz++) {
                for (int dx = -safeRadius; dx <= safeRadius && submitted < maxSubmitPerFrame; dx++) {
                    if (submitChunkIfNeeded(pcx + dx, pcz + dz, ChunkGenerationTask.Priority.CRITICAL)) {
                        submitted++;
                    }
                }
            }

            // 1b. Frustum loading
            if (cameraUpdated) {
                int R = maxLoadDistance;
                for (int dx = -R; dx <= R && submitted < maxSubmitPerFrame; dx++) {
                    for (int dz = -R; dz <= R && submitted < maxSubmitPerFrame; dz++) {
                        if (Math.abs(dx) <= safeRadius && Math.abs(dz) <= safeRadius)
                            continue;
                        int cx = pcx + dx;
                        int cz = pcz + dz;
                        if (!isChunkInFrustum(cx, cz))
                            continue;
                        int distSq = dx * dx + dz * dz;
                        ChunkGenerationTask.Priority p = (distSq <= 64) ? 
                            ChunkGenerationTask.Priority.HIGH : ChunkGenerationTask.Priority.NORMAL;
                        if (submitChunkIfNeeded(cx, cz, p))
                            submitted++;
                    }
                }
                submitted = preGenerateAhead(pcx, pcz, submitted, maxSubmitPerFrame);
            }

            // ========== FASE 2: PIPELINE ==========
            int tasksSubmitted = 0;
            final int MAX_TASKS_PER_FRAME = 16;

            for (Chunk chunk : chunks.values()) {
                if (tasksSubmitted >= MAX_TASKS_PER_FRAME) break;
                
                // Skip se task in corso
                if (chunk.isLightPending() || chunk.isMeshPending()) {
                    continue;
                }

                // STEP 1: TERRAIN → FEATURES
                if (chunk.getPhase() == Chunk.Phase.TERRAIN) {
                    if (areNeighborsAtLeast(chunk, Chunk.Phase.TERRAIN)) {
                        ensureFeatures(chunk);
                    }
                }
                
                // STEP 2: FEATURES → LIGHT_DONE
                else if (chunk.getPhase() == Chunk.Phase.FEATURES) {
                    if (areNeighborsAtLeast(chunk, Chunk.Phase.FEATURES)) {
                        submitLightTask(chunk);
                        tasksSubmitted++;
                    }
                }
                
                // STEP 3: LIGHT_DONE → MESH_DONE
                else if (chunk.getPhase() == Chunk.Phase.LIGHT_DONE) {
                    if (areNeighborsAtLeast(chunk, Chunk.Phase.LIGHT_DONE)) {
                        submitMeshTask(chunk);
                        tasksSubmitted++;
                    }
                }
            }

            unloadChunksOutsideView(pcx, pcz);
        }

        private boolean areNeighborsAtLeast(Chunk center, Chunk.Phase minPhase) {
            int minOrdinal = minPhase.ordinal();
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk n = getChunkIfLoaded(center.getX() + dx, center.getZ() + dz);
                    if (n == null || n.getPhase().ordinal() < minOrdinal) {
                        return false;
                    }
                }
            }
            return true;
        }

private void ensureFeatures(Chunk chunk) {
    if (chunk.getPhase().ordinal() >= Chunk.Phase.FEATURES.ordinal()) {
        return;
    }

    if (!areNeighborsTerrainReady(chunk.getX(), chunk.getZ())) {
        return;
    }
    
    worldGenerator.generateFeatures(
            chunk.getX(), chunk.getZ(),
            chunk.getBlockData(),
            chunk.getHeightMapData(),
            new FeatureGenerator.BlockPlacer() {
                @Override
                public void setBlock(int cx, int cz, int lx, int ly, int lz, int blockId) {
                    Chunk target = getChunkIfLoaded(cx, cz);
                    if (target != null) {
                        target.setBlock(lx, ly, lz, blockId);
                    }
                }

                @Override
                public int getBlock(int cx, int cz, int lx, int ly, int lz) {
                    Chunk target = getChunkIfLoaded(cx, cz);
                    return (target != null) ? target.getBlock(lx, ly, lz) : 0;
                }

                @Override
                public boolean canPlace(int cx, int cz) {
                    return true;
                }
            });

    // ❌ RIMOSSO: LightPropagator.recomputeChunkSkyLightVertical(this, chunk);

    chunk.setPhase(Chunk.Phase.FEATURES);
}

    /**
     * Verifica che i 8 vicini (+ centro) abbiano almeno la fase TERRAIN.
     */
    private boolean areNeighborsTerrainReady(int cx, int cz) {
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

    private void submitMeshTask(Chunk chunk) {
        Chunk[][] neighbors = new Chunk[3][3];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                neighbors[dz + 1][dx + 1] = getChunkIfLoaded(chunk.getX() + dx, chunk.getZ() + dz);
            }
        }

        ChunkSnapshot snapshot = new ChunkSnapshot(
                chunk.getX(), chunk.getZ(),
                neighbors,
                config.chunkSize, config.worldHeight);

        chunk.setMeshPending(true);
        genExecutor.submitMeshTask(chunk.getX(), chunk.getZ(), snapshot);
    }

    private void submitLightTask(Chunk chunk) {
        Chunk[][] neighbors = new Chunk[3][3];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                neighbors[dz + 1][dx + 1] = getChunkIfLoaded(chunk.getX() + dx, chunk.getZ() + dz);
            }
        }

        ChunkSnapshot snapshot = new ChunkSnapshot(
                chunk.getX(), chunk.getZ(),
                neighbors,
                config.chunkSize, config.worldHeight);

        chunk.setLightPending(true);
        genExecutor.submitLightTask(chunk.getX(), chunk.getZ(), snapshot);
    }


    private int preGenerateAhead(int pcx, int pcz, int currentSubmitted, int maxSubmit) {
        int submitted = currentSubmitted;
        for (int dist = safeRadius + 1; dist <= preGenRadius && submitted < maxSubmit; dist++) {
            int arcWidth = Math.min(dist / 2 + 2, 6);
            for (int side = -arcWidth; side <= arcWidth && submitted < maxSubmit; side++) {
                float aheadX = cameraDirX * dist - cameraDirZ * side * 0.4f;
                float aheadZ = cameraDirZ * dist + cameraDirX * side * 0.4f;
                int cx = pcx + Math.round(aheadX);
                int cz = pcz + Math.round(aheadZ);
                if (submitChunkIfNeeded(cx, cz, ChunkGenerationTask.Priority.NORMAL)) {
                    submitted++;
                }
            }
        }
        return submitted;
    }

    // ==================== BLOCK ACCESS ====================

/**
 * Imposta un blocco nel mondo e gestisce CORRETTAMENTE la propagazione della luce.
 * 
 * Casi gestiti:
 * 1. Piazzamento torcia (nuova sorgente blocklight)
 * 2. Rimozione torcia (rimuovi sorgente blocklight)
 * 3. Piazzamento blocco opaco (blocca luce)
 * 4. Rimozione blocco opaco (luce può entrare)
 */
public void setBlock(int x, int y, int z, int blockId) {
    if (y < 0 || y >= config.worldHeight) return;

    int cx = floorDiv(x, config.chunkSize);
    int cz = floorDiv(z, config.chunkSize);
    int lx = mod(x, config.chunkSize);
    int lz = mod(z, config.chunkSize);

    Chunk chunk = getChunkIfLoaded(cx, cz);
    if (chunk == null || chunk.getPhase().ordinal() < Chunk.Phase.FEATURES.ordinal()) {
        return;
    }

    int oldBlockId = chunk.getBlock(lx, y, lz);
    if (oldBlockId == blockId) return;

    Block oldBlock = Blocks.get(oldBlockId);
    Block newBlock = Blocks.get(blockId);

    boolean oldIsOpaque = oldBlock.isOpaque();
    boolean newIsOpaque = newBlock.isOpaque();
    int oldEmission = oldBlock.getLightLevel();
    int newEmission = newBlock.getLightLevel();

    int oldBlockLight = chunk.getBlockLight(lx, y, lz);
    int oldSkyLight = chunk.getSkyLight(lx, y, lz);

    // Modifica il blocco
    chunk.setBlock(lx, y, lz, blockId);

    // === GESTIONE BLOCKLIGHT ===
    
    if (newEmission > 0 && newEmission > oldEmission) {
        // Nuova sorgente o sorgente più forte
        LightPropagator.addBlockLight(this, x, y, z, newEmission);
    } else if (oldEmission > 0 && newEmission < oldEmission) {
        // Rimozione sorgente
        chunk.setBlockLight(lx, y, lz, 0);
        LightPropagator.removeLightAt(this, x, y, z, oldEmission, false);
    } else if (newIsOpaque && !oldIsOpaque && oldBlockLight > 0) {
        // Blocco opaco piazzato dove c'era luce
        chunk.setBlockLight(lx, y, lz, 0);
        LightPropagator.removeLightAt(this, x, y, z, oldBlockLight, false);
    } else if (oldIsOpaque && !newIsOpaque) {
        // Rimosso blocco opaco - la luce può entrare
        LightPropagator.fillLightFromNeighbors(this, x, y, z);
    }

    // === GESTIONE SKYLIGHT ===
    
    if (oldIsOpaque != newIsOpaque) {
        if (newIsOpaque) {
            // Piazzato blocco opaco dove prima passava luce
            if (oldSkyLight > 0) {
                // Rimuovi la luce che era qui E tutta quella propagata DA qui
                chunk.setSkyLight(lx, y, lz, 0);
                LightPropagator.removeSkyLightFrom(this, x, y, z, oldSkyLight);
            }
            // Se questo blocco era in una colonna aperta al cielo,
            // ricalcola anche la colonna sotto
            LightPropagator.recalculateSkyColumn(this, x, z);
        } else {
            // Rimosso blocco opaco - la luce può entrare
            // Prima ricalcola la colonna (potrebbe aprirsi al cielo)
            LightPropagator.recalculateSkyColumn(this, x, z);
            // Poi cerca luce dai vicini (luce orizzontale)
            LightPropagator.fillLightFromNeighbors(this, x, y, z);
        }
    }
    // === INVALIDAZIONE MESH ===
    invalidateChunkLight(cx, cz);
    
    if (lx == 0) invalidateChunkLight(cx - 1, cz);
    if (lx == config.chunkSize - 1) invalidateChunkLight(cx + 1, cz);
    if (lz == 0) invalidateChunkLight(cx, cz - 1);
    if (lz == config.chunkSize - 1) invalidateChunkLight(cx, cz + 1);
}

/**
 * Ricalcola la skylight per i blocchi sotto una certa Y nella colonna.
 * Usato quando si piazza un blocco opaco che blocca il cielo.
 */
private void recalculateSkyColumnBelow(Chunk chunk, int lx, int startY, int lz) {
    // Trova se c'è ancora cielo visibile sopra startY
    boolean hasSkyAbove = false;
    for (int y = startY + 1; y < config.worldHeight; y++) {
        if (Blocks.get(chunk.getBlock(lx, y, lz)).isOpaque()) {
            break;
        }
        if (chunk.getSkyLight(lx, y, lz) == 15) {
            hasSkyAbove = true;
            break;
        }
    }

    if (!hasSkyAbove) {
        // Nessun cielo sopra, azzera tutta la skylight diretta sotto
        for (int y = startY; y >= 0; y--) {
            if (Blocks.get(chunk.getBlock(lx, y, lz)).isOpaque()) {
                break;
            }
            int currentSky = chunk.getSkyLight(lx, y, lz);
            if (currentSky == 15) {
                chunk.setSkyLight(lx, y, lz, 0);
                // La luce potrebbe comunque arrivare dai lati
                int wx = chunk.getWorldX() + lx;
                int wz = chunk.getWorldZ() + lz;
                LightPropagator.fillLightFromNeighbors(this, wx, y, wz);
            }
        }
    }
}

/**
 * Invalida un chunk per rigenerare la mesh (ma non la luce).
 * Il chunk passerà da MESH_DONE → LIGHT_DONE nella prossima manutenzione.
 */
private void invalidateChunkForRemesh(int cx, int cz) {
    Chunk c = getChunkIfLoaded(cx, cz);
    if (c == null) return;

    if (c.getPhase() == Chunk.Phase.MESH_DONE) {
        c.setPhase(Chunk.Phase.LIGHT_DONE);
        c.setMeshPending(false);
    }
}


// =================================================================================
// SOSTITUISCI invalidateChunkLight CON QUESTO (se serve invalidazione completa)
// =================================================================================

/**
 * Invalida completamente la luce di un chunk.
 * Il chunk tornerà a FEATURES e ricalcolerà luce + mesh.
 */
private void invalidateChunkLight(int cx, int cz) {
    Chunk c = getChunkIfLoaded(cx, cz);
    if (c == null) return;
    
    // Per forzare ricalcolo completo della luce:
    if (c.getPhase().ordinal() >= Chunk.Phase.FEATURES.ordinal()) {
        c.setPhase(Chunk.Phase.FEATURES);
        c.setLightPending(false);
        c.setMeshPending(false);
    }
}





    // ==================== UTILS & GETTERS ====================

    public void updateCamera(float[] projMatrix, float[] viewMatrix, Vec3 forward) {
        // Calcola View-Projection
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                viewProjMatrix[col * 4 + row] = projMatrix[0 * 4 + row] * viewMatrix[col * 4 + 0] +
                        projMatrix[1 * 4 + row] * viewMatrix[col * 4 + 1] +
                        projMatrix[2 * 4 + row] * viewMatrix[col * 4 + 2] +
                        projMatrix[3 * 4 + row] * viewMatrix[col * 4 + 3];
            }
        }
        loadingFrustum.update(viewProjMatrix);

        float len = (float) Math.sqrt(forward.x * forward.x + forward.z * forward.z);
        if (len > 0.001f) {
            cameraDirX = forward.x / len;
            cameraDirZ = forward.z / len;
        }
        cameraUpdated = true;
    }

    private boolean submitChunkIfNeeded(int cx, int cz, ChunkGenerationTask.Priority priority) {
        long key = chunkKey(cx, cz);
        Chunk chunk = chunks.get(key);
        if (chunk == null || chunk.getPhase() == Chunk.Phase.EMPTY) {
            if (!pendingChunks.contains(key)) {
                if (genExecutor.submit(cx, cz, priority)) {
                    pendingChunks.add(key);
                    if (chunk == null)
                        chunks.put(key, new Chunk(cx, cz));
                    return true;
                }
            }
        }
        return false;
    }

    private void unloadChunksOutsideView(int pcx, int pcz) {
        int safeRadiusSq = (safeRadius + 2) * (safeRadius + 2);
        int maxDistSq = (maxLoadDistance + 8) * (maxLoadDistance + 8);
        int unloadDistSq = (maxLoadDistance + 16) * (maxLoadDistance + 16);

        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> entry = it.next();
            Chunk chunk = entry.getValue();

            int dx = chunk.getX() - pcx;
            int dz = chunk.getZ() - pcz;
            int distSq = dx * dx + dz * dz;

            if (distSq <= safeRadiusSq)
                continue;
            if (distSq <= maxDistSq)
                continue;

            if (distSq > unloadDistSq) {
                chunk.cleanup();
                pendingChunks.remove(entry.getKey());
                it.remove();
            }
        }
    }

    // Wrapper helpers
    private long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public Chunk getChunkIfLoaded(int cx, int cz) {
        return chunks.get(chunkKey(cx, cz));
    }

    private boolean isChunkInFrustum(int cx, int cz) {
        float s = config.chunkSize;
        return loadingFrustum.testAABB(cx * s, 0, cz * s, (cx + 1) * s, config.worldHeight, (cz + 1) * s);
    }

    private static int floorDiv(int a, int b) {
        int q = a / b;
        if ((a ^ b) < 0 && (a % b != 0))
            q--;
        return q;
    }

    private static int mod(int a, int b) {
        int m = a % b;
        if (m < 0)
            m += b;
        return m;
    }

    // Interfacce WorldAccess (per main thread raycast/physics)
    public int getBlock(int x, int y, int z) {
        if (y < 0 || y >= config.worldHeight)
            return 0;
        Chunk c = getChunkIfLoaded(floorDiv(x, config.chunkSize), floorDiv(z, config.chunkSize));
        return (c != null) ? c.getBlock(mod(x, config.chunkSize), y, mod(z, config.chunkSize)) : 0;
    }

    @Override
    public int peekBlock(int x, int y, int z) {
        return getBlock(x, y, z);
    }

    @Override
    public int peekSkyLight(int x, int y, int z) {
        if (y < 0 || y >= config.worldHeight)
            return 15;
        Chunk c = getChunkIfLoaded(floorDiv(x, config.chunkSize), floorDiv(z, config.chunkSize));
        return (c != null) ? c.getSkyLight(mod(x, config.chunkSize), y, mod(z, config.chunkSize)) : 15;
    }

    @Override
    public int peekBlockLight(int x, int y, int z) {
        if (y < 0 || y >= config.worldHeight)
            return 0;
        Chunk c = getChunkIfLoaded(floorDiv(x, config.chunkSize), floorDiv(z, config.chunkSize));
        return (c != null) ? c.getBlockLight(mod(x, config.chunkSize), y, mod(z, config.chunkSize)) : 0;
    }

    public Chunk getChunkAtWorld(int wx, int wz) {
        return getChunkIfLoaded(floorDiv(wx, config.chunkSize), floorDiv(wz, config.chunkSize));
    }

    public float getGameTime() {
        return gameTime;
    }

    public float getTimeOfDay() {
        return timeOfDay;
    }

    public Config getConfig() {
        return config;
    }

    public void cleanup() {
        genExecutor.shutdown();
        chunks.values().forEach(Chunk::cleanup);
        chunks.clear();
    }

    public ArrayList<Chunk> getVisibleChunks(Vec3 pos) {
        ArrayList<Chunk> visible = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            // Renderizziamo anche se è in fase TERRAIN, anche se magari la mesh non è
            // aggiornatissima
            if (c.getPhase().ordinal() >= Chunk.Phase.TERRAIN.ordinal() && c.getSolidMesh() != null
                    && !c.getSolidMesh().isEmpty()) {
                visible.add(c);
            }
        }
        return visible;
    }

    public Vec3 getSunDirection() {
        float angle = (timeOfDay * 2f * (float) Math.PI) - (float) Math.PI / 2f;
        return new Vec3((float) Math.cos(angle) * 0.3f, (float) Math.sin(angle), 0f);
    }

    /**
     * Imposta la distanza di visione a runtime.
     */
    public void setViewDistance(int distance) {
        this.maxLoadDistance = Math.max(4, distance);
        this.preGenRadius = Math.min(16, distance / 2);
    }

    public int getViewDistance() {
        return maxLoadDistance;
    }

    /**
     * Trova una posizione di spawn sicura (chiamato all'avvio).
     * Usa generazione sincrona per assicurarsi che il terreno esista subito.
     */
    public Vec3 findSpawnPosition() {
        int spawnRadius = 2;
        // Forza la generazione dell'area centrale
        for (int dz = -spawnRadius; dz <= spawnRadius; dz++) {
            for (int dx = -spawnRadius; dx <= spawnRadius; dx++) {
                generateChunkSync(dx, dz);
            }
        }

        // Cerca un punto alto ma non troppo
        for (int r = 0; r <= 32; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz : new int[] { r, -r }) {
                    int sy = getSurfaceHeight(dx, dz);
                    if (sy > config.waterLevel + 1 && isHeadroomClear(dx, sy, dz)) {
                        return new Vec3(dx + 0.5f, sy + 1.0f, dz + 0.5f);
                    }
                }
            }
        }
        return new Vec3(0, 100, 0); // Fallback
    }

private void generateChunkSync(int cx, int cz) {
    long key = chunkKey(cx, cz);
    if (chunks.containsKey(key)) return;

    Chunk chunk = new Chunk(cx, cz);
    int[] blocks = new int[config.chunkSize * config.chunkSize * config.worldHeight];
    int[] height = new int[config.chunkSize * config.chunkSize];

    worldGenerator.generateTerrain(cx, cz, blocks, height);
    
    System.arraycopy(blocks, 0, chunk.getBlockData(), 0, blocks.length);
    System.arraycopy(height, 0, chunk.getHeightMapData(), 0, height.length);
        
    chunk.setPhase(Chunk.Phase.TERRAIN);
    chunks.put(key, chunk);
}



    private int getSurfaceHeight(int x, int z) {
        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        Chunk c = getChunkIfLoaded(cx, cz);
        if (c == null)
            return 0;
        return c.getHeight(mod(x, config.chunkSize), mod(z, config.chunkSize));
    }

    private boolean isHeadroomClear(int x, int y, int z) {
        return getBlock(x, y + 1, z) == 0 && getBlock(x, y + 2, z) == 0;
    }
}