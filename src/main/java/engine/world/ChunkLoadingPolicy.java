package engine.world;

final class ChunkLoadingPolicy {
    static final int MAX_SAFE_RADIUS = 6;
    static final int UNLOAD_HYSTERESIS = 4;
    static final int MAX_RESIDENT_CHUNKS = 4608;
    static final int MAX_RESIDENT_SECTION_MESHES = 4096;

    private ChunkLoadingPolicy() {
    }

    static int safeRadiusForViewDistance(int viewDistance) {
        return Math.max(1, Math.min(MAX_SAFE_RADIUS, viewDistance));
    }

    static int unloadRadiusForViewDistance(int viewDistance) {
        return Math.max(safeRadiusForViewDistance(viewDistance), viewDistance + UNLOAD_HYSTERESIS);
    }

    static int maxResidentChunksForViewDistance(int viewDistance) {
        int unloadRadius = unloadRadiusForViewDistance(viewDistance);
        int circularBudget = (int) Math.ceil(Math.PI * unloadRadius * unloadRadius);
        int churnHeadroom = Math.max(64, viewDistance * 8);
        return Math.min(MAX_RESIDENT_CHUNKS, Math.max(64, circularBudget + churnHeadroom));
    }

    static int maxResidentSectionMeshesForViewDistance(int viewDistance) {
        return Math.min(MAX_RESIDENT_SECTION_MESHES, maxResidentChunksForViewDistance(viewDistance) * 2);
    }

    static boolean isInsideRadius(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius;
    }
}
