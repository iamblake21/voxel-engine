package engine.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ChunkMaintenanceSourceTest {

    @Test
    void chunkMaintenanceHasSingleRuntimeOwner() throws IOException {
        String player = Files.readString(Path.of("src/main/java/engine/entity/Player.java"));
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));

        assertFalse(player.contains("world.maintainChunks"),
                "Player.update must not run chunk maintenance in addition to World.update");
        assertTrue(world.contains("maintainChunks(p.getX(), p.getZ())"),
                "World.update remains the single owner of chunk maintenance");
    }

    @Test
    void residentBudgetDoesNotEvictCurrentLoadSet() throws IOException {
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));

        assertTrue(world.contains("isProtectedResidentChunk"),
                "resident chunk budget must protect chunks that are still in the active load set");
        assertTrue(world.contains("isInsideCurrentLoadSet"),
                "active load set should include safe radius and current frustum candidates");
        assertTrue(world.contains("isCompletedTerrainStillRelevant"),
                "completed terrain tasks outside the current unload radius must not resurrect unloaded chunks");
    }

    @Test
    void sectionMeshBudgetDoesNotEvictCurrentFrustumSections() throws IOException {
        String world = Files.readString(Path.of("src/main/java/engine/world/World.java"));

        assertTrue(world.contains("if (isSectionInLoadingFrustum(chunk, sy))"),
                "section mesh eviction must not immediately evict visible/current-frustum sections");
    }
}
