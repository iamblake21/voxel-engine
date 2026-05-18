package engine.world;

import engine.core.Config;
import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.gen.*;
import engine.rendering.Frustum;
import engine.utils.Math3D.Vec3;
import engine.world.light.LightPropagator;
import engine.world.fluid.FluidManager;
import engine.world.blockentity.BlockEntity;

import engine.entity.EntityManager;
import engine.entity.EntityTypes;
import engine.entity.Player;
import engine.entity.Entity;
import engine.entity.ItemEntity; // Correct import
import engine.loot.LootTable; // Correct import
import engine.world.item.ItemStack;
import java.util.Random;

import java.util.*;

public class World implements MeshBuilder.WorldAccess {

    private final Config config;
    private final Map<Long, Chunk> chunks = new HashMap<>();

    private WorldGenerator worldGenerator;
    private final MeshBuilder meshBuilder; // Usato solo per operazioni main-thread se necessario

    // Async generation
    private final WorldGenerationExecutor genExecutor;
    private final FluidManager fluidManager;
    private final Set<Long> pendingChunks = new HashSet<>();
    private final Map<Long, Long> chunkLastAccessTick = new HashMap<>();
    private long chunkAccessClock = 0;

    private float fluidTickAccumulator = 0;
    private static final float FLUID_TICK_INTERVAL = 0.05f; // 20 TPS

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
    private static final int MAX_TERRAIN_SUBMISSIONS_PER_FRAME = 128;
    private static final int MAX_TERRAIN_INTEGRATIONS_PER_FRAME = 128;
    private static final int MAX_FEATURE_INTEGRATIONS_PER_FRAME = 24;
    private static final int MAX_LIGHT_INTEGRATIONS_PER_FRAME = 128;
    private static final int MAX_MESH_UPLOADS_PER_FRAME = 64;
    private static final int MAX_PIPELINE_TASK_SUBMISSIONS_PER_FRAME = 256;
    private static final int MAX_MESH_CHUNK_SUBMISSIONS_PER_FRAME = 24;
    private static final int MAX_PENDING_QUEUE = 1024;
    private static final int MAX_SECTION_MESH_TASKS_PER_CHUNK = 12;

    private EntityManager entityManager;

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Get the biome at the given world coordinates.
     * 
     * @param worldX X coordinate in world space
     * @param worldZ Z coordinate in world space
     * @return The biome at those coordinates
     */
    public engine.world.biome.Biome getBiome(int worldX, int worldZ) {
        if (worldGenerator != null) {
            return worldGenerator.getBiome(worldX, worldZ);
        }
        // Fallback to default biome if generator not available
        return engine.world.biome.Biomes.DEFAULT();
    }

    // Day/Night Cycle
    private static final float DAY_LENGTH_SECONDS = 600f;
    private float timeOfDay = 0f;
    private float dayTicks = 0f;

    private final engine.world.storage.WorldStorage worldStorage;

    public World(Config config) {
        this.config = config;
        this.dayTicks = DAY_LENGTH_SECONDS * 0.5f;
        this.maxLoadDistance = config.viewDistance;
        this.preGenRadius = Math.min(16, config.viewDistance / 2);

        this.worldStorage = new engine.world.storage.WorldStorage(new java.io.File("."));
        this.worldStorage.prepareWorld(config.worldName);

        this.worldGenerator = new WorldGenerator(
                config.worldSeed,
                config.chunkSize,
                config.worldHeight,
                config.waterLevel);
        this.meshBuilder = new MeshBuilder(config.chunkSize, config.worldHeight);
        this.fluidManager = new FluidManager(this);

        int numWorkers = Math.max(2, Runtime.getRuntime().availableProcessors());

        // Try to load level.dat
        engine.world.item.nbt.NBTTagCompound levelData = worldStorage.loadLevelData();
        if (levelData != null) {
            System.out.println("[World] Loaded level.dat");
            this.gameTime = levelData.getFloat("Time");
            this.dayTicks = levelData.getFloat("DayTime");

            // CRITICAL: Restore Seed!
            // If we don't restore the seed, new chunks will be generated with a DIFFERENT
            // seed
            // than the old ones, causing massive terrain discontinuities ("walls").
            long savedSeed = levelData.getLong("Seed");
            if (savedSeed != 0) {
                System.out.println("[World] Restoring Saved Seed: " + savedSeed);
                // Re-initialize generator with the CORRECT seed
                this.worldGenerator = new WorldGenerator(
                        savedSeed,
                        config.chunkSize,
                        config.worldHeight,
                        config.waterLevel);
            }
        }

        this.genExecutor = new WorldGenerationExecutor(
                worldGenerator,
                config.chunkSize,
                config.worldHeight,
                numWorkers,
                this,
                worldStorage);

        this.safeRadius = ChunkLoadingPolicy.safeRadiusForViewDistance(this.maxLoadDistance);
        this.preGenRadius = Math.min(this.maxLoadDistance, this.safeRadius + 8);

        System.out.println("[World] Async Pipeline Initialized (Snapshot Mode)" + numWorkers);
    }

    // ... update method ...

    private boolean submitChunkIfNeeded(int cx, int cz, ChunkGenerationTask.Priority priority) {
        long key = chunkKey(cx, cz);
        Chunk chunk = chunks.get(key);
        if (chunk != null && chunk.getPhase() != Chunk.Phase.EMPTY) {
            touchChunk(key);
            return false;
        }
        if (chunk == null || chunk.getPhase() == Chunk.Phase.EMPTY) {
            if (!pendingChunks.contains(key)) {
                // Submit to executor (handles check disk -> check generate)
                if (genExecutor.submit(cx, cz, priority)) {
                    pendingChunks.add(key);
                    touchChunk(key);
                    if (chunk == null) {
                        chunks.put(key, new Chunk(cx, cz)); // Placeholder
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void touchChunk(long key) {
        chunkLastAccessTick.put(key, ++chunkAccessClock);
    }

    private void touchChunk(Chunk chunk) {
        touchChunk(chunkKey(chunk.getX(), chunk.getZ()));
    }

    public void saveWorld() {
        worldStorage.saveWorld(this, config.worldName);
    }

    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    // ==================== UPDATE & LOOP ====================

    public void update(float deltaTime) {
        gameTime += deltaTime;
        dayTicks += deltaTime;
        timeOfDay = (dayTicks / DAY_LENGTH_SECONDS);
        timeOfDay = timeOfDay - (float) Math.floor(timeOfDay);

        // Ensure chunks are loaded around the player
        if (entityManager != null && entityManager.getPlayer() != null) {
            Player p = entityManager.getPlayer();
            maintainChunks(p.getX(), p.getZ());
        }

        pollCompletedChunks();

        // Fluid Tick (20 TPS)
        fluidTickAccumulator += deltaTime;
        fluidTickAccumulator -= FLUID_TICK_INTERVAL;
        fluidManager.tick();
        tickBlockEntities();

    }

    /**
     * Tick all block entities in loaded chunks.
     * Call this in update() along with fluid ticking.
     */
    private void tickBlockEntities() {
        for (Chunk chunk : chunks.values()) {
            if (chunk.getPhase().ordinal() >= Chunk.Phase.TERRAIN.ordinal()) {
                chunk.tickBlockEntities();
            }
        }
    }

    /**
     * Controlla se ci sono task completati (sia Terreno che Mesh).
     */
    private void pollCompletedChunks() {
        // 1. Processa nuovi chunk generati (TERRENO)
        int processedTerrain = 0;
        ChunkGenerationTask terrainTask;
        while (processedTerrain < MAX_TERRAIN_INTEGRATIONS_PER_FRAME && (terrainTask = genExecutor.pollCompleted()) != null) {
            if (!terrainTask.cancelled) {
                integrateCompletedTerrain(terrainTask);
            }
            processedTerrain++;
        }

        // 2. Processa LUCE completata (Fase 2A)
        int processedLight = 0;
        LightPropagationTask lightTask;
        while (processedLight < MAX_LIGHT_INTEGRATIONS_PER_FRAME && (lightTask = genExecutor.pollCompletedLight()) != null) {
            integrateCompletedLight(lightTask);
            processedLight++;
        }

        // 3. Processa mesh pronte (Fase 2B)
        int processedMesh = 0;
        ChunkMeshTask meshTask;
        while (processedMesh < MAX_MESH_UPLOADS_PER_FRAME && (meshTask = genExecutor.pollCompletedMesh()) != null) {
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
        if (!isCompletedTerrainStillRelevant(task.chunkX, task.chunkZ)) {
            if (task.loadedChunk != null) {
                task.loadedChunk.cleanup();
            }
            chunks.remove(key);
            chunkLastAccessTick.remove(key);
            return;
        }

        if (task.loadedChunk != null) {
            // Chunk loaded from disk!
            chunks.put(key, task.loadedChunk);
            touchChunk(key);
            int skyComponent = task.loadedChunk.getSkyLight(8, 200, 8);

            // If skylight at y=200 is not full (15), force recalculation
            if (skyComponent != 15) {
                // Keep in FEATURES phase to trigger light calculation
                task.loadedChunk.setPhase(Chunk.Phase.FEATURES);
                System.out.println("[DEBUG Load] Forcing light recalc for chunk " + task.chunkX + "," + task.chunkZ);
            } else {
                // Light data exists, just remesh
                invalidateChunkForRemesh(task.chunkX, task.chunkZ);
            }
            return;
        }

        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = new Chunk(task.chunkX, task.chunkZ);
            chunks.put(key, chunk);
            touchChunk(key);
        } else if (chunk.getPhase().ordinal() >= Chunk.Phase.TERRAIN.ordinal()) {
            // Chunk already generated (e.g., by generateChunkSync), skip overwrite
            System.out.println("[DEBUG Terrain] Skipping overwrite for chunk " + task.chunkX + "," + task.chunkZ
                    + " phase=" + chunk.getPhase());
            return;
        }

        System.arraycopy(task.blockData, 0, chunk.getBlockData(), 0, task.blockData.length);
        System.arraycopy(task.heightMap, 0, chunk.getHeightMapData(), 0, task.heightMap.length);
        if (task.fluidData != null) {
            chunk.setFluidData(task.fluidData);
        }

        chunk.setPhase(Chunk.Phase.TERRAIN);
        touchChunk(key);
    }

    private void integrateCompletedLight(LightPropagationTask task) {
        Chunk chunk = getChunkIfLoaded(task.chunkX, task.chunkZ);
        if (chunk == null) {
            System.out.println("[DEBUG Light] Chunk not loaded: " + task.chunkX + "," + task.chunkZ);
            return;
        }

        // Se il chunk non è più in pending light, ignora (task obsoleto)
        if (!chunk.isLightPending()) {
            System.out.println("[DEBUG Light] NOT pending, skipping: " + task.chunkX + "," + task.chunkZ + " phase="
                    + chunk.getPhase());
            return;
        }

        // Applica il buffer di luce calcolato dal worker (packed format)
        short[] lightBuffer = task.snapshot.getLightWriteBuffer();
        chunk.applyLightData(lightBuffer);

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
        if (chunk == null)
            return;

        if (task.sectionY >= 0) {
            if (!chunk.isCurrentMeshBatch(task.batchId)) {
                return;
            }

            chunk.uploadSectionMesh(
                    task.sectionY,
                    0,
                    task.meshData.solidVertices,
                    task.meshData.transparentVertices,
                    task.meshData.waterVertices);
            chunk.uploadSectionCustomMeshes(task.sectionY, task.meshData.customMeshes);

            if (chunk.finishSectionMesh(task.batchId)
                    && chunk.getPhase() == Chunk.Phase.LIGHT_DONE
                    && chunk.hasAllRenderableSectionMeshes()) {
                chunk.setPhase(Chunk.Phase.MESH_DONE);
            }
            return;
        }

        chunk.uploadMesh(
                task.meshData.solidVertices,
                task.meshData.transparentVertices,
                task.meshData.waterVertices);

        chunk.uploadCustomMeshes(task.meshData.customMeshes);

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
        playerChunkX = pcx;
        playerChunkZ = pcz;

        // ========== FASE 1: CARICAMENTO TERRENO ==========
        int maxSubmitPerFrame = MAX_TERRAIN_SUBMISSIONS_PER_FRAME;
        int submitted = 0;

        // 1a. Safe radius
        for (int dz = -safeRadius; dz <= safeRadius && submitted < maxSubmitPerFrame; dz++) {
            for (int dx = -safeRadius; dx <= safeRadius && submitted < maxSubmitPerFrame; dx++) {
                if (!ChunkLoadingPolicy.isInsideRadius(dx, dz, safeRadius)) {
                    continue;
                }
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
                    if (!ChunkLoadingPolicy.isInsideRadius(dx, dz, R)) {
                        continue;
                    }
                    if (ChunkLoadingPolicy.isInsideRadius(dx, dz, safeRadius)) {
                        continue;
                    }
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    if (!isChunkInFrustum(cx, cz))
                        continue;
                    int distSq = dx * dx + dz * dz;
                    ChunkGenerationTask.Priority p = (distSq <= 64) ? ChunkGenerationTask.Priority.HIGH
                            : ChunkGenerationTask.Priority.NORMAL;
                    if (submitChunkIfNeeded(cx, cz, p))
                        submitted++;
                }
            }
        }

        // ========== FASE 2: PIPELINE ==========
        int tasksSubmitted = 0;
        int meshChunksSubmitted = 0;
        int featuresIntegrated = 0;
        final int MAX_TASKS_PER_FRAME = MAX_PIPELINE_TASK_SUBMISSIONS_PER_FRAME;
        int lightQueueBudget = Math.max(0, MAX_PENDING_QUEUE - genExecutor.getLightQueueSize());
        int meshQueueBudget = Math.max(0, MAX_PENDING_QUEUE - genExecutor.getMeshQueueSize());

        for (Chunk chunk : chunks.values()) {
            if (tasksSubmitted >= MAX_TASKS_PER_FRAME)
                break;

            // Skip se task in corso
            if (chunk.isLightPending() || chunk.isMeshPending()) {
                continue;
            }

            // STEP 1: TERRAIN → FEATURES
            if (chunk.getPhase() == Chunk.Phase.TERRAIN) {
                if (featuresIntegrated >= MAX_FEATURE_INTEGRATIONS_PER_FRAME) {
                    continue;
                }
                if (areNeighborsAtLeast(chunk, Chunk.Phase.TERRAIN)) {
                    ensureFeatures(chunk);
                    featuresIntegrated++;
                } else if (chunk.getX() == 0 && chunk.getZ() == 0) {
                    System.out.println("[DEBUG Pipeline] Chunk 0,0 TERRAIN waiting for neighbors");
                }
            }

            // STEP 2: FEATURES → LIGHT_DONE
            else if (chunk.getPhase() == Chunk.Phase.FEATURES) {
                if (lightQueueBudget <= 0) {
                    continue;
                }
                if (areNeighborsAtLeast(chunk, Chunk.Phase.FEATURES)) {
                    submitLightTask(chunk);
                    tasksSubmitted++;
                    lightQueueBudget--;
                } else if (chunk.getX() == 0 && chunk.getZ() == 0) {
                    System.out.println("[DEBUG Pipeline] Chunk 0,0 FEATURES waiting for neighbors");
                }
            }

            // STEP 3: LIGHT_DONE → MESH_DONE
            else if (chunk.getPhase() == Chunk.Phase.LIGHT_DONE) {
                if (meshChunksSubmitted >= MAX_MESH_CHUNK_SUBMISSIONS_PER_FRAME || meshQueueBudget <= 0) {
                    continue;
                }
                if (areNeighborsAtLeast(chunk, Chunk.Phase.LIGHT_DONE)) {
                    int meshSectionBudget = Math.min(MAX_SECTION_MESH_TASKS_PER_CHUNK,
                            Math.min(meshQueueBudget, MAX_TASKS_PER_FRAME - tasksSubmitted));
                    int sectionTasks = submitMeshTask(chunk, meshSectionBudget);
                    if (sectionTasks > 0) {
                        tasksSubmitted += sectionTasks;
                        meshQueueBudget -= sectionTasks;
                        meshChunksSubmitted++;
                    }
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
                            // 1. Set Block (Raw)
                            target.setBlock(lx, ly, lz, blockId);

                            // 2. Create Block Entity (Fixes Chests)
                            Block block = Blocks.get(blockId);
                            if (block.hasBlockEntity()) {
                                BlockEntity be = block.createBlockEntity(new BlockPos(
                                        cx * config.chunkSize + lx,
                                        ly,
                                        cz * config.chunkSize + lz));
                                if (be != null) {
                                    target.setBlockEntity(lx, ly, lz, be);
                                    be.setWorld(World.this);
                                }
                            }

                            // 3. Invalidate Neighbor Chunk if modified (Fixes Light/Mesh)
                            // If we modify a neighbor that was already finished, we must invalidate it.
                            if (target != chunk && target.getPhase().ordinal() >= Chunk.Phase.LIGHT_DONE.ordinal()) {
                                invalidateChunkLight(cx, cz);
                            }
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
                },
                (id, x, y, z) -> {
                    // Spawn entity
                    EntityTypes.tryGet(id).ifPresent(type -> {
                        Entity entity = type.create();
                        entity.setPosition(x, y, z);
                        if (entityManager != null) {
                            entityManager.addEntity(entity);
                        }
                    });
                });

        // RIMOSSO: LightPropagator.recomputeChunkSkyLightVertical(this, chunk);

        chunk.setPhase(Chunk.Phase.FEATURES);
        // Compact flat arrays into sparse sections now that block data is final.
        // This frees the large blocks[]/light[]/fluidData[] flat arrays and replaces
        // them with ChunkSection objects only for non-empty 16×16×16 y-slices.
        chunk.compactToSections();
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

    private int submitMeshTask(Chunk chunk, int maxSectionTasks) {
        if (maxSectionTasks <= 0) {
            return 0;
        }

        ArrayList<Integer> sectionsToMesh = selectSectionsForMesh(chunk, maxSectionTasks);
        if (sectionsToMesh.isEmpty()) {
            if (chunk.getPhase() == Chunk.Phase.LIGHT_DONE && chunk.hasAllRenderableSectionMeshes()) {
                chunk.setPhase(Chunk.Phase.MESH_DONE);
            }
            return 0;
        }

        Chunk[][] neighbors = new Chunk[3][3];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                neighbors[dz + 1][dx + 1] = getChunkIfLoaded(chunk.getX() + dx, chunk.getZ() + dz);
            }
        }

        ChunkSnapshot snapshot = new ChunkSnapshot(
                chunk.getX(), chunk.getZ(),
                neighbors,
                config.chunkSize, config.worldHeight, worldGenerator.getBiomeProvider());

        int batchId = chunk.beginSectionMeshBatch(sectionsToMesh.size());
        for (int sy : sectionsToMesh) {
            genExecutor.submitMeshTask(chunk.getX(), chunk.getZ(), snapshot, sy, batchId);
        }
        return sectionsToMesh.size();
    }

    private ArrayList<Integer> selectSectionsForMesh(Chunk chunk, int maxSections) {
        ArrayList<Integer> visibleSections = new ArrayList<>();
        ArrayList<Integer> nearBackgroundSections = new ArrayList<>();
        boolean nearPlayer = isNearPlayerChunk(chunk);

        for (int sy = 0; sy < Chunk.SECTION_COUNT; sy++) {
            if (!chunk.hasRenderableSection(sy) || chunk.isSectionMeshReady(sy)) {
                continue;
            }

            if (isSectionInLoadingFrustum(chunk, sy)) {
                visibleSections.add(sy);
            } else if (nearPlayer) {
                nearBackgroundSections.add(sy);
            }
        }

        Comparator<Integer> byVerticalPriority = Comparator.comparingInt(this::sectionDistanceFromPlayer);
        visibleSections.sort(byVerticalPriority);
        nearBackgroundSections.sort(byVerticalPriority);

        ArrayList<Integer> selected = new ArrayList<>(Math.min(maxSections, Chunk.SECTION_COUNT));
        appendLimitedSections(selected, visibleSections, maxSections);
        appendLimitedSections(selected, nearBackgroundSections, maxSections);
        return selected;
    }

    private void appendLimitedSections(ArrayList<Integer> selected, ArrayList<Integer> source, int maxSections) {
        for (int sy : source) {
            if (selected.size() >= maxSections) {
                return;
            }
            selected.add(sy);
        }
    }

    private boolean isNearPlayerChunk(Chunk chunk) {
        return Math.abs(chunk.getX() - playerChunkX) <= safeRadius
                && Math.abs(chunk.getZ() - playerChunkZ) <= safeRadius;
    }

    private int sectionDistanceFromPlayer(int sectionY) {
        int playerSection = Math.max(0, Math.min(Chunk.SECTION_COUNT - 1,
                (int) Math.floor(currentPlayerY() / ChunkSection.SIZE)));
        return Math.abs(sectionY - playerSection);
    }

    private float currentPlayerY() {
        if (entityManager != null && entityManager.getPlayer() != null) {
            return entityManager.getPlayer().getY();
        }
        return config.worldHeight * 0.5f;
    }

    private boolean isSectionInLoadingFrustum(Chunk chunk, int sectionY) {
        if (!cameraUpdated) {
            return true;
        }

        float minX = chunk.getX() * config.chunkSize;
        float minY = sectionY * ChunkSection.SIZE;
        float minZ = chunk.getZ() * config.chunkSize;
        float maxX = minX + config.chunkSize;
        float maxY = Math.min(config.worldHeight, minY + ChunkSection.SIZE);
        float maxZ = minZ + config.chunkSize;

        return loadingFrustum.testAABB(minX, minY, minZ, maxX, maxY, maxZ);
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
                config.chunkSize, config.worldHeight, worldGenerator.getBiomeProvider());

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

    /**
     * Get block entity at world position.
     */
    public BlockEntity getBlockEntity(BlockPos pos) {
        return getBlockEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Get block entity at world position.
     */
    public BlockEntity getBlockEntity(int x, int y, int z) {
        if (y < 0 || y >= config.worldHeight)
            return null;

        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        Chunk chunk = getChunkIfLoaded(cx, cz);

        if (chunk == null)
            return null;

        int lx = mod(x, config.chunkSize);
        int lz = mod(z, config.chunkSize);

        return chunk.getBlockEntity(lx, y, lz);
    }

    /**
     * Set block entity at world position.
     */
    public void setBlockEntity(BlockPos pos, BlockEntity blockEntity) {
        setBlockEntity(pos.getX(), pos.getY(), pos.getZ(), blockEntity);
    }

    /**
     * Set block entity at world position.
     */
    public void setBlockEntity(int x, int y, int z, BlockEntity blockEntity) {
        if (y < 0 || y >= config.worldHeight)
            return;

        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        Chunk chunk = getChunkIfLoaded(cx, cz);

        if (chunk == null)
            return;

        int lx = mod(x, config.chunkSize);
        int lz = mod(z, config.chunkSize);

        chunk.setBlockEntity(lx, y, lz, blockEntity);

        if (blockEntity != null) {
            blockEntity.setWorld(this);
        }
    }

    public BlockEntity removeBlockEntity(BlockPos pos) {
        return removeBlockEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Remove block entity at world position.
     */
    public BlockEntity removeBlockEntity(int x, int y, int z) {
        if (y < 0 || y >= config.worldHeight)
            return null;

        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        Chunk chunk = getChunkIfLoaded(cx, cz);

        if (chunk == null)
            return null;

        int lx = mod(x, config.chunkSize);
        int lz = mod(z, config.chunkSize);

        return chunk.removeBlockEntity(lx, y, lz);
    }

    /**
     * Create a block entity for a block if it has one.
     */
    public BlockEntity createBlockEntity(BlockPos pos, Block block) {
        if (!block.hasBlockEntity())
            return null;

        BlockEntity be = block.createBlockEntity(pos);
        if (be != null) {
            setBlockEntity(pos, be);
        }
        return be;
    }

    // ==================== BLOCK ACCESS ====================

    /**
     * Imposta un blocco nel mondo e gestisce CORRETTAMENTE la propagazione della
     * luce.
     * 
     * Casi gestiti:
     * 1. Piazzamento torcia (nuova sorgente blocklight)
     * 2. Rimozione torcia (rimuovi sorgente blocklight)
     * 3. Piazzamento blocco opaco (blocca luce)
     * 4. Rimozione blocco opaco (luce può entrare)
     */
    public void setBlock(int x, int y, int z, int blockId) {
        if (y < 0 || y >= config.worldHeight)
            return;

        int cx = floorDiv(x, config.chunkSize);
        int cz = floorDiv(z, config.chunkSize);
        int lx = mod(x, config.chunkSize);
        int lz = mod(z, config.chunkSize);

        Chunk chunk = getChunkIfLoaded(cx, cz);
        if (chunk == null || chunk.getPhase().ordinal() < Chunk.Phase.FEATURES.ordinal()) {
            return;
        }

        int oldBlockId = chunk.getBlock(lx, y, lz);
        if (oldBlockId == blockId)
            return;

        Block oldBlock = Blocks.get(oldBlockId);
        Block newBlock = Blocks.get(blockId);

        int oldBlockLight = chunk.getBlockLight(lx, y, lz);
        int oldSkyLight = chunk.getSkyLight(lx, y, lz);

        // Modifica il blocco (Moved UP to prevent infinite recursion in onRemove)
        chunk.setBlock(lx, y, lz, blockId);

        // Remove old block entity if block changed
        if (oldBlockId != blockId) {
            engine.world.block.state.BlockState oldState = engine.world.block.Block.STATE_IDS.get(oldBlockId);
            // Fallback if state lookup fails (should not happen if registered correctly)
            if (oldState == null)
                oldState = oldBlock.getDefaultState();

            oldBlock.onRemove(this, x, y, z, oldState);
            BlockEntity oldBE = removeBlockEntity(x, y, z);
            // oldBE's items should be dropped here if needed
        }

        // Create new block entity if new block has one
        if (newBlock.hasBlockEntity()) {
            createBlockEntity(new BlockPos(x, y, z), newBlock);
        }

        boolean oldIsOpaque = oldBlock.isOpaque();
        boolean newIsOpaque = newBlock.isOpaque();
        int oldEmission = oldBlock.getLightLevel();
        int newEmission = newBlock.getLightLevel();

        // Notify placement
        if (oldBlockId != blockId) {
            newBlock.onPlace(this, x, y, z);
        }

        // Fluid Level Init/Reset
        if (newBlock.isLiquid()) {
            // If we place water manually, give it max level (source)
            if (oldBlock.isLiquid() && chunk.getFluidLevel(lx, y, lz) > 0) {
                // Already liquid, maybe keep level?
                // If the user places "Water" bucket, it should become Source (max level).
                // So we force max level.
                chunk.setFluidLevel(lx, y, lz, newBlock.getMaxFluidLevel());
            } else {
                chunk.setFluidLevel(lx, y, lz, newBlock.getMaxFluidLevel());
            }
        } else {
            // Replaced block (maybe air or solid) -> reset fluid level
            // Unless we didn't mean to destroy fluid?
            // But setBlock is authoritative.
            chunk.setFluidLevel(lx, y, lz, 0);
        }

        // Fluid updates
        fluidManager.scheduleUpdate(x, y, z);
        fluidManager.scheduleUpdate(x + 1, y, z);
        fluidManager.scheduleUpdate(x - 1, y, z);
        fluidManager.scheduleUpdate(x, y + 1, z);
        fluidManager.scheduleUpdate(x, y - 1, z);
        fluidManager.scheduleUpdate(x, y, z + 1);
        fluidManager.scheduleUpdate(x, y, z - 1);

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
        // La luce è stata già aggiornata dalla propagazione real-time sopra.
        // NON dobbiamo ricalcolare la luce (invalidateChunkLight) perché sovrascrive
        // i dati appena propagati. Dobbiamo solo rigenerare la mesh.
        invalidateChunkForRemesh(cx, cz);

        // I vicini devono anche rigenerare la mesh per vedere la nuova luce
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0)
                    continue; // Skip center chunk
                invalidateChunkForRemesh(cx + dx, cz + dz);
            }
        }
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
        if (c == null)
            return;

        if (c.getPhase() == Chunk.Phase.MESH_DONE) {
            c.setPhase(Chunk.Phase.LIGHT_DONE);
            c.setMeshPending(false);
            c.invalidateSectionMeshReadiness();
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
        if (c == null)
            return;

        // Per forzare ricalcolo completo della luce:
        if (c.getPhase().ordinal() >= Chunk.Phase.FEATURES.ordinal()) {
            c.setPhase(Chunk.Phase.FEATURES);
            c.setLightPending(false);
            c.setMeshPending(false);
            c.invalidateSectionMeshReadiness();
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

    public long getSeed() {
        return config.worldSeed;
    }

    public float getTime() {
        return gameTime;
    }

    public float getDayTime() {
        return dayTicks;
    }

    public Player getPlayer() {
        return entityManager != null ? entityManager.getPlayer() : null;
    }

    private void unloadChunksOutsideView(int pcx, int pcz) {
        int unloadRadius = ChunkLoadingPolicy.unloadRadiusForViewDistance(maxLoadDistance);
        int unloadDistSq = unloadRadius * unloadRadius;

        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> entry = it.next();
            Chunk chunk = entry.getValue();

            int dx = chunk.getX() - pcx;
            int dz = chunk.getZ() - pcz;
            int distSq = dx * dx + dz * dz;

            if (distSq <= unloadDistSq) {
                continue;
            }
            unloadChunk(entry.getKey(), chunk);
            it.remove();
        }

        enforceResidentChunkBudget(pcx, pcz);
        enforceResidentSectionMeshBudget(pcx, pcz);
    }

    private void enforceResidentChunkBudget(int pcx, int pcz) {
        int maxResident = ChunkLoadingPolicy.maxResidentChunksForViewDistance(maxLoadDistance);
        if (chunks.size() <= maxResident) {
            return;
        }

        ArrayList<Map.Entry<Long, Chunk>> candidates = new ArrayList<>(chunks.entrySet());
        candidates.removeIf(entry -> pendingChunks.contains(entry.getKey())
                || isProtectedResidentChunk(entry.getValue(), pcx, pcz));
        candidates.sort((a, b) -> {
            long ta = chunkLastAccessTick.getOrDefault(a.getKey(), 0L);
            long tb = chunkLastAccessTick.getOrDefault(b.getKey(), 0L);
            if (ta != tb) {
                return Long.compare(ta, tb);
            }
            return Integer.compare(distanceSq(b.getValue(), pcx, pcz), distanceSq(a.getValue(), pcx, pcz));
        });

        for (Map.Entry<Long, Chunk> entry : candidates) {
            if (chunks.size() <= maxResident) {
                return;
            }
            if (chunks.remove(entry.getKey(), entry.getValue())) {
                unloadChunk(entry.getKey(), entry.getValue());
            }
        }
    }

    private void enforceResidentSectionMeshBudget(int pcx, int pcz) {
        int maxSections = ChunkLoadingPolicy.maxResidentSectionMeshesForViewDistance(maxLoadDistance);
        int meshedSections = getMeshedSectionCount();
        if (meshedSections <= maxSections) {
            return;
        }

        ArrayList<SectionMeshCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
            Chunk chunk = entry.getValue();
            if (isInsideSafeRadius(chunk, pcx, pcz)) {
                continue;
            }
            for (int sy = 0; sy < Chunk.SECTION_COUNT; sy++) {
                if (!chunk.isSectionMeshReady(sy)) {
                    continue;
                }
                if (isSectionInLoadingFrustum(chunk, sy)) {
                    continue;
                }
                long lastAccess = chunkLastAccessTick.getOrDefault(entry.getKey(), 0L);
                candidates.add(new SectionMeshCandidate(chunk, sy, lastAccess, distanceSq(chunk, pcx, pcz)));
            }
        }

        candidates.sort((a, b) -> {
            if (a.lastAccess != b.lastAccess) {
                return Long.compare(a.lastAccess, b.lastAccess);
            }
            return Integer.compare(b.distanceSq, a.distanceSq);
        });

        for (SectionMeshCandidate candidate : candidates) {
            if (meshedSections <= maxSections) {
                return;
            }
            if (candidate.chunk.evictSectionMesh(candidate.sectionY)) {
                if (candidate.chunk.getPhase() == Chunk.Phase.MESH_DONE) {
                    candidate.chunk.setPhase(Chunk.Phase.LIGHT_DONE);
                    candidate.chunk.setMeshPending(false);
                }
                meshedSections--;
            }
        }
    }

    private int getMeshedSectionCount() {
        int count = 0;
        for (Chunk chunk : chunks.values()) {
            count += chunk.getMeshedSectionCount();
        }
        return count;
    }

    private boolean isInsideSafeRadius(Chunk chunk, int pcx, int pcz) {
        return ChunkLoadingPolicy.isInsideRadius(chunk.getX() - pcx, chunk.getZ() - pcz, safeRadius);
    }

    private boolean isProtectedResidentChunk(Chunk chunk, int pcx, int pcz) {
        return isInsideSafeRadius(chunk, pcx, pcz) || isInsideCurrentLoadSet(chunk, pcx, pcz);
    }

    private boolean isInsideCurrentLoadSet(Chunk chunk, int pcx, int pcz) {
        int dx = chunk.getX() - pcx;
        int dz = chunk.getZ() - pcz;
        if (!ChunkLoadingPolicy.isInsideRadius(dx, dz, maxLoadDistance)) {
            return false;
        }
        if (ChunkLoadingPolicy.isInsideRadius(dx, dz, safeRadius)) {
            return true;
        }
        return !cameraUpdated || isChunkInFrustum(chunk.getX(), chunk.getZ());
    }

    private boolean isCompletedTerrainStillRelevant(int cx, int cz) {
        int unloadRadius = ChunkLoadingPolicy.unloadRadiusForViewDistance(maxLoadDistance);
        return ChunkLoadingPolicy.isInsideRadius(cx - playerChunkX, cz - playerChunkZ, unloadRadius);
    }

    private int distanceSq(Chunk chunk, int pcx, int pcz) {
        int dx = chunk.getX() - pcx;
        int dz = chunk.getZ() - pcz;
        return dx * dx + dz * dz;
    }

    private void unloadChunk(long key, Chunk chunk) {
        chunk.cleanup();
        pendingChunks.remove(key);
        chunkLastAccessTick.remove(key);
    }

    private static final class SectionMeshCandidate {
        final Chunk chunk;
        final int sectionY;
        final long lastAccess;
        final int distanceSq;

        SectionMeshCandidate(Chunk chunk, int sectionY, long lastAccess, int distanceSq) {
            this.chunk = chunk;
            this.sectionY = sectionY;
            this.lastAccess = lastAccess;
            this.distanceSq = distanceSq;
        }
    }

    // Wrapper helpers
    public long chunkKey(int cx, int cz) {
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

    public Block getBlockType(int x, int y, int z) {
        return Blocks.get(getBlock(x, y, z));
    }

    @Override
    public int peekBlock(int x, int y, int z) {
        return getBlock(x, y, z);
    }

    private final Random lootRandom = new Random();

    /**
     * Spawn an item entity at position.
     */
    public ItemEntity spawnItem(ItemStack stack, float x, float y, float z) {
        System.out.println("[World] spawnItem called at " + x + ", " + y + ", " + z);
        System.out.println("[World] Stack: " + stack);
        System.out.println("[World] EntityManager: " + entityManager);

        if (stack == null || stack.isEmpty() || entityManager == null) {
            System.out.println("[World] ABORT: stack empty or no entityManager!");
            return null;
        }

        ItemEntity item = EntityTypes.ITEM.create();
        System.out.println("[World] Created ItemEntity: " + item);

        item.setStack(stack.copy());
        item.setPosition(x, y, z);

        float spread = 0.2f;
        item.setVelocity(
                (lootRandom.nextFloat() - 0.5f) * spread,
                0.2f + lootRandom.nextFloat() * 0.1f,
                (lootRandom.nextFloat() - 0.5f) * spread);

        entityManager.addEntity(item);
        System.out.println("[World] Item added to EntityManager!");

        return item;
    }

    /**
     * Spawn item at block center.
     */
    public ItemEntity spawnItem(ItemStack stack, int x, int y, int z) {
        return spawnItem(stack, x + 0.5f, y + 0.5f, z + 0.5f);
    }

    /**
     * Drop all items from a loot table at position.
     */
    public void dropLoot(LootTable table, float x, float y, float z) {
        dropLoot(table, x, y, z, 0);
    }

    /**
     * Drop all items from a loot table with fortune level.
     */
    public void dropLoot(LootTable table, float x, float y, float z, int fortuneLevel) {
        if (table == null || table.isEmpty())
            return;

        for (ItemStack stack : table.generateLoot(lootRandom, fortuneLevel)) {
            spawnItem(stack, x, y, z);
        }
    }

    /**
     * Drop loot at block position.
     */
    public void dropLoot(LootTable table, int x, int y, int z) {
        dropLoot(table, x + 0.5f, y + 0.5f, z + 0.5f, 0);
    }

    /**
     * Drop block's default loot.
     */
    public void dropBlockLoot(int x, int y, int z, Block block) {
        dropBlockLoot(x, y, z, block, 0);
    }

    /**
     * Drop block's loot with fortune.
     */
    public void dropBlockLoot(int x, int y, int z, Block block, int fortuneLevel) {
        if (block.hasLoot()) {
            dropLoot(block.getLootTable(), x, y, z, fortuneLevel);
        }
    }

    public int getFluidLevel(int x, int y, int z) {
        Chunk chunk = getChunkIfLoaded(x >> 4, z >> 4);
        if (chunk == null)
            return 0;
        int lx = x & 15;
        int ly = y;
        int lz = z & 15;
        if (ly < 0 || ly >= Chunk.HEIGHT)
            return 0;
        return chunk.getFluidLevel(lx, ly, lz);
    }

    public void setFluidLevel(int x, int y, int z, int level) {
        Chunk chunk = getChunkIfLoaded(x >> 4, z >> 4);
        if (chunk == null)
            return;
        int lx = x & 15;
        int ly = y;
        int lz = z & 15;
        if (ly < 0 || ly >= Chunk.HEIGHT)
            return;
        chunk.setFluidLevel(lx, ly, lz, level);

        // Invalidate mesh for visual update
        int cx = x >> 4;
        int cz = z >> 4;
        invalidateChunkForRemesh(cx, cz);

        if (lx == 0)
            invalidateChunkForRemesh(cx - 1, cz);
        if (lx == 15)
            invalidateChunkForRemesh(cx + 1, cz);
        if (lz == 0)
            invalidateChunkForRemesh(cx, cz - 1);
        if (lz == 15)
            invalidateChunkForRemesh(cx, cz + 1);
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

    public void setTime(long ticks) {
        float normalized = (ticks % 24000) / 24000f;
        this.dayTicks = normalized * DAY_LENGTH_SECONDS;
        this.timeOfDay = normalized;
        System.out.println("[World] Set time to " + ticks + " ticks (" + normalized + ")");
    }

    public void cleanup() {
        genExecutor.shutdown();
        chunks.values().forEach(Chunk::cleanup);
        chunks.clear();
        pendingChunks.clear();
        chunkLastAccessTick.clear();
    }

    public ArrayList<Chunk> getVisibleChunks(Vec3 pos) {
        ArrayList<Chunk> visible = new ArrayList<>();
        for (Chunk c : chunks.values()) {
            // Renderizziamo anche se è in fase TERRAIN, anche se magari la mesh non è
            // aggiornatissima
            if (c.getPhase().ordinal() >= Chunk.Phase.TERRAIN.ordinal() && c.hasRenderableMesh()) {
                touchChunk(c);
                visible.add(c);
            }
        }
        return visible;
    }

    public WorldMemoryStats getMemoryStats() {
        int allocatedSections = 0;
        int meshedSections = 0;
        long sectionBytes = 0;
        long vboBytes = 0;
        for (Chunk chunk : chunks.values()) {
            allocatedSections += chunk.getAllocatedSectionCount();
            meshedSections += chunk.getMeshedSectionCount();
            sectionBytes += chunk.getEstimatedSectionBytes();
            vboBytes += chunk.getEstimatedVboBytes();
        }

        Runtime runtime = Runtime.getRuntime();
        return new WorldMemoryStats(
                chunks.size(),
                pendingChunks.size(),
                allocatedSections,
                meshedSections,
                sectionBytes,
                vboBytes,
                runtime.totalMemory() - runtime.freeMemory(),
                runtime.maxMemory(),
                safeRadius,
                ChunkLoadingPolicy.unloadRadiusForViewDistance(maxLoadDistance),
                ChunkLoadingPolicy.maxResidentChunksForViewDistance(maxLoadDistance),
                ChunkLoadingPolicy.maxResidentSectionMeshesForViewDistance(maxLoadDistance),
                genExecutor.getTerrainQueueSize(),
                genExecutor.getLightQueueSize(),
                genExecutor.getMeshQueueSize(),
                genExecutor.getNumWorkers());
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
        this.safeRadius = ChunkLoadingPolicy.safeRadiusForViewDistance(this.maxLoadDistance);
        this.preGenRadius = Math.min(this.maxLoadDistance, this.safeRadius + 8);
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
        if (chunks.containsKey(key))
            return;

        Chunk chunk = new Chunk(cx, cz);
        short[] blocks = new short[config.chunkSize * config.chunkSize * config.worldHeight];
        int[] height = new int[config.chunkSize * config.chunkSize];
        byte[] fluid = new byte[config.chunkSize * config.chunkSize * config.worldHeight];

        worldGenerator.generateTerrain(cx, cz, blocks, height, fluid);

        System.arraycopy(blocks, 0, chunk.getBlockData(), 0, blocks.length);
        System.arraycopy(height, 0, chunk.getHeightMapData(), 0, height.length);
        chunk.setFluidData(fluid);

        chunk.setPhase(Chunk.Phase.TERRAIN);
        chunks.put(key, chunk);
        touchChunk(key);
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

    public boolean isBlockTransparent(int x, int y, int z) {
        int blockId = getBlock(x, y, z);
        // ID 0 è Aria (trasparente). Se hai vetro o acqua, controlla
        // Blocks.get(blockId).isTransparent()
        if (blockId == 0)
            return true;

        Block b = Blocks.get(blockId);
        return b != null && !b.isOpaque();
    }
}
