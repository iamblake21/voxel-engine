package engine.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SectionMeshPipelineSourceTest {

    @Test
    void meshUploadsCanDrainSectionResultsWithoutColumnSubmission() throws IOException {
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));

        assertTrue(world.contains("MAX_MESH_UPLOADS_PER_FRAME = 64"),
                "upload can drain many completed section tasks now that submission is section-limited");
        assertTrue(world.contains("MAX_MESH_CHUNK_SUBMISSIONS_PER_FRAME = 24"),
                "mesh submission should keep workers fed without reverting to full-column batches");
    }

    @Test
    void rendererDoesNotUseColumnMeshFallbackForChunks() throws IOException {
        String renderer = Files.readString(Path.of("src/main/java/engine/rendering/Renderer.java"));

        assertFalse(renderer.contains("getSolidMesh(lod)"),
                "solid pass must not render a monolithic column mesh");
        assertFalse(renderer.contains("getTransparentMesh(lod)"),
                "transparent pass must not render a monolithic column mesh");
        assertFalse(renderer.contains("getWaterMesh(lod)"),
                "water pass must not render a monolithic column mesh");
        assertFalse(renderer.contains("getCustomMeshes().entrySet()"),
                "custom meshes must be section-scoped in the renderer");
    }

    @Test
    void meshSchedulerSelectsSectionsIncrementally() throws IOException {
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));
        String chunk = Files.readString(Path.of("src/main/java/engine/world/Chunk.java"));

        assertTrue(world.contains("MAX_SECTION_MESH_TASKS_PER_CHUNK = 12"),
                "a hot chunk should enqueue a bounded section batch without reverting to full-column mesh work");
        assertTrue(world.contains("selectSectionsForMesh(chunk"),
                "mesh submission should go through a section selector");
        assertTrue(world.contains("isSectionInLoadingFrustum"),
                "section selection should use section-level frustum checks");
        assertTrue(world.contains("hasAllRenderableSectionMeshes"),
                "partial section batches must not promote the chunk until all required sections are ready");
        assertTrue(chunk.contains("sectionMeshReady"),
                "chunk state must distinguish material sections from sections whose mesh has been built");
    }

    @Test
    void pipelineCapsAreNotUnderfeedingWorkers() throws IOException {
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));

        assertTrue(world.contains("MAX_TERRAIN_SUBMISSIONS_PER_FRAME = 128"),
                "terrain submission should keep async workers fed at high view distance");
        assertTrue(world.contains("MAX_TERRAIN_INTEGRATIONS_PER_FRAME = 128"),
                "completed terrain should be integrated fast enough to unblock feature/light work");
        assertTrue(world.contains("MAX_LIGHT_INTEGRATIONS_PER_FRAME = 128"),
                "completed light tasks should be integrated fast enough to unblock meshing");
        assertTrue(world.contains("MAX_PIPELINE_TASK_SUBMISSIONS_PER_FRAME = 256"),
                "pipeline submission should not drip-feed light and section mesh work");
        assertTrue(world.contains("MAX_PENDING_QUEUE = 1024"),
                "backpressure should allow enough queued section tasks for high render distance");
        assertFalse(world.contains("task.loadedChunk.getLightData()"),
                "loading should not flatten compacted light data just to test one skylight value");
    }

    @Test
    void pipelineBackpressureDoesNotFreezeUnrelatedStages() throws IOException {
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));

        assertTrue(world.contains("int lightQueueBudget = Math.max(0, MAX_PENDING_QUEUE - genExecutor.getLightQueueSize())"),
                "light submission should have its own queue budget");
        assertTrue(world.contains("int meshQueueBudget = Math.max(0, MAX_PENDING_QUEUE - genExecutor.getMeshQueueSize())"),
                "mesh submission should have its own queue budget");
        assertTrue(world.contains("submitMeshTask(chunk, meshSectionBudget)"),
                "mesh batches should be limited by remaining section-task budget");
        assertFalse(world.contains("Pipeline saturated, skip submission for this frame"),
                "one saturated queue must not stop all pipeline stages");
    }

    @Test
    void mainThreadFeatureGenerationIsBudgeted() throws IOException {
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));

        assertTrue(world.contains("MAX_FEATURE_INTEGRATIONS_PER_FRAME"),
                "feature generation runs on the main thread and must have its own frame budget");
        assertTrue(world.contains("featuresIntegrated >= MAX_FEATURE_INTEGRATIONS_PER_FRAME"),
                "terrain-to-features work must stop before it can hitch the frame");
        assertTrue(world.contains("featuresIntegrated++"),
                "feature integration must count against that budget");
    }
}
