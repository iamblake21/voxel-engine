package engine.world;

import engine.world.gen.ChunkSnapshot;
import engine.world.gen.MeshBuilder;
import engine.world.gen.WorldGenerator;
import engine.world.light.LightPropagator;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.*;

public class WorldGenerationExecutor {

    private final ExecutorService executor;
    private final int numWorkers;
    private final WorldGenerator worldGenerator;
    private final int chunkSize;
    private final int chunkHeight;

    // Code separate per Terreno e Mesh
    private final PriorityBlockingQueue<ChunkGenerationTask> terrainQueue;
    private final ConcurrentLinkedQueue<LightPropagationTask> lightQueue; // Coda prioritaria per la luce
    private final ConcurrentLinkedQueue<ChunkMeshTask> meshQueue; // Coda FIFO per mesh

    private final ConcurrentLinkedQueue<ChunkGenerationTask> completedTerrain;
    private final ConcurrentLinkedQueue<LightPropagationTask> completedLight;
    private final ConcurrentLinkedQueue<ChunkMeshTask> completedMesh;

    private final Map<Long, Object> activeTasksMap = new ConcurrentHashMap<>();

    private volatile boolean shutdown = false;

    public WorldGenerationExecutor(WorldGenerator worldGenerator, int chunkSize, int chunkHeight, int numWorkers) {
        this.worldGenerator = worldGenerator;
        this.chunkSize = chunkSize;
        this.chunkHeight = chunkHeight;
        this.numWorkers = Math.max(1, numWorkers);

        this.terrainQueue = new PriorityBlockingQueue<>(64, Comparator.comparingInt(t -> t.priority.ordinal()));
        this.lightQueue = new ConcurrentLinkedQueue<>();
        this.meshQueue = new ConcurrentLinkedQueue<>();

        this.completedTerrain = new ConcurrentLinkedQueue<>();
        this.completedLight = new ConcurrentLinkedQueue<>();
        this.completedMesh = new ConcurrentLinkedQueue<>();

        this.executor = Executors.newFixedThreadPool(this.numWorkers, r -> {
            Thread t = new Thread(r, "WorldGen-Worker");
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < this.numWorkers; i++)
            executor.submit(this::workerLoop);
    }

    private void workerLoop() {
        while (!shutdown) {
            try {
                boolean workDone = false;

                // 1. Luce
                LightPropagationTask lightTask = lightQueue.poll();
                if (lightTask != null) {
                    processLightTask(lightTask);
                    workDone = true;
                }

                // 2. Mesh
                ChunkMeshTask meshTask = meshQueue.poll();
                if (meshTask != null) {
                    processMeshTask(meshTask);
                    workDone = true;
                }

                // 3. Terreno
                ChunkGenerationTask terrainTask = terrainQueue.poll(); // Non-blocking poll
                if (terrainTask != null) {
                    processTerrainTask(terrainTask);
                    workDone = true;
                }

                // Se non c'è lavoro, dormi un po' per non bruciare CPU
                if (!workDone) {
                    Thread.sleep(5);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable e) { // Catch Throwable to ensure we see everything
                e.printStackTrace();
            }
        }
    }

    private void processTerrainTask(ChunkGenerationTask task) {
        if (task.cancelled)
            return;
        int[] blocks = new int[chunkSize * chunkSize * chunkHeight];
        int[] height = new int[chunkSize * chunkSize];
        worldGenerator.generateTerrain(task.chunkX, task.chunkZ, blocks, height);
        task.blockData = blocks;
        task.heightMap = height;
        task.complete = true;
        completedTerrain.offer(task);
        activeTasksMap.remove(task.getChunkKey());
    }

    private void processMeshTask(ChunkMeshTask task) {
        // 1. Calcola Luce su Snapshot (SOLO SE NUOVO CHUNK)
        LightPropagator.computeFullLightForSnapshot(task.snapshot, chunkSize, chunkHeight, task.neighborsToPropagate);

        // 2. Costruisci Mesh su Snapshot
        MeshBuilder builder = new MeshBuilder(chunkSize, chunkHeight);

        // Adattatore ChunkData per il MeshBuilder che legge dallo snapshot
        MeshBuilder.ChunkData adapter = new MeshBuilder.ChunkData() {
            @Override
            public int getBlock(int x, int y, int z) {
                return task.snapshot.getBlock(x, y, z);
            }

            @Override
            public int getWorldX() {
                return task.chunkX * chunkSize;
            }

            @Override
            public int getWorldZ() {
                return task.chunkZ * chunkSize;
            }

            @Override
            public int getBlockLight(int x, int y, int z) {
                return task.snapshot.peekBlockLight(getWorldX() + x, y, getWorldZ() + z);
            }

            @Override
            public int getSkyLight(int x, int y, int z) {
                return task.snapshot.peekSkyLight(getWorldX() + x, y, getWorldZ() + z);
            }
        };

        task.meshData = builder.buildMesh(adapter, task.snapshot); // snapshot agisce come WorldAccess
        task.complete = true;
        completedMesh.offer(task);
    }

    private void processLightTask(LightPropagationTask task) {
        LightPropagator.propagateLightOnlyForSnapshot(task.snapshot, chunkSize, chunkHeight, task.neighborsToPropagate);
        task.complete = true;
        completedLight.offer(task);
    }

    public boolean submit(int cx, int cz, ChunkGenerationTask.Priority p) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        if (activeTasksMap.containsKey(key))
            return false;

        ChunkGenerationTask t = new ChunkGenerationTask(cx, cz, p);
        activeTasksMap.put(key, t);
        terrainQueue.offer(t);
        return true;
    }

    public void submitMeshTask(int cx, int cz, ChunkSnapshot snapshot) {
        ChunkMeshTask t = new ChunkMeshTask(cx, cz, snapshot);
        meshQueue.offer(t);
    }

    public void submitLightTask(int cx, int cz, ChunkSnapshot snapshot) {
        LightPropagationTask t = new LightPropagationTask(cx, cz, snapshot);
        lightQueue.offer(t);
    }

    public ChunkGenerationTask pollCompleted() {
        return completedTerrain.poll();
    }

    public ChunkMeshTask pollCompletedMesh() {
        return completedMesh.poll();
    }

    public LightPropagationTask pollCompletedLight() {
        return completedLight.poll();
    }

    public void shutdown() {
        shutdown = true;
        executor.shutdownNow();
    }

    public int getNumWorkers() {
        return numWorkers;
    }
}