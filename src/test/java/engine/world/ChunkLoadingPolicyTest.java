package engine.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkLoadingPolicyTest {

    @Test
    void safeRadiusIsCappedBelowViewDistance() {
        assertEquals(6, ChunkLoadingPolicy.safeRadiusForViewDistance(32));
        assertEquals(4, ChunkLoadingPolicy.safeRadiusForViewDistance(4));
    }

    @Test
    void loadAreaIsCircularNotSquare() {
        assertTrue(ChunkLoadingPolicy.isInsideRadius(32, 0, 32));
        assertFalse(ChunkLoadingPolicy.isInsideRadius(32, 32, 32));
    }

    @Test
    void unloadHysteresisIsShortAndBudgeted() {
        assertEquals(36, ChunkLoadingPolicy.unloadRadiusForViewDistance(32));
        assertTrue(ChunkLoadingPolicy.maxResidentChunksForViewDistance(32) > Math.ceil(Math.PI * 36 * 36),
                "resident chunk budget needs headroom above the unload disk to avoid churn at the boundary");
        assertTrue(ChunkLoadingPolicy.maxResidentChunksForViewDistance(32) <= 4608);
        assertTrue(ChunkLoadingPolicy.maxResidentSectionMeshesForViewDistance(32) <= 4096);
    }
}
