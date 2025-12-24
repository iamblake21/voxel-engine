package engine.rendering;

/**
 * Runtime-adjustable render settings.
 * 
 * Can be changed during gameplay without restart.
 */
public class RenderSettings {

    // View distance in chunks
    private int viewDistance = 18;
    private int minViewDistance = 2;
    private int maxViewDistance = 32;

    // Fog
    private boolean fogEnabled = true;
    private float fogStart = 0.7f; // As fraction of view distance
    private float fogEnd = 1.0f;

    // Culling
    private boolean frustumCullingEnabled = true;
    private boolean occlusionCullingEnabled = false; // Future feature

    // Quality
    private boolean ambientOcclusionEnabled = true;
    private boolean shadowsEnabled = false; // Future feature

    // Debug
    private boolean wireframeMode = false;
    private boolean showChunkBorders = false;
    private boolean showDebugInfo = false;

    // Stats (read-only, set by renderer)
    private int chunksRendered = 0;
    private int chunksTotal = 0;
    private int chunksCulled = 0;
    private int trianglesRendered = 0;

    // === VIEW DISTANCE ===

    public int getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(int distance) {
        this.viewDistance = Math.max(minViewDistance, Math.min(maxViewDistance, distance));
    }

    public void increaseViewDistance() {
        setViewDistance(viewDistance + 1);
    }

    public void decreaseViewDistance() {
        setViewDistance(viewDistance - 1);
    }

    /**
     * Get view distance in blocks.
     */
    public float getViewDistanceBlocks(int chunkSize) {
        return viewDistance * chunkSize;
    }

    // === FOG ===

    public boolean isFogEnabled() {
        return fogEnabled;
    }

    public void setFogEnabled(boolean enabled) {
        this.fogEnabled = enabled;
    }

    public void toggleFog() {
        this.fogEnabled = !this.fogEnabled;
    }

    public float getFogStart(int chunkSize) {
        return viewDistance * chunkSize * fogStart;
    }

    public float getFogEnd(int chunkSize) {
        return viewDistance * chunkSize * fogEnd;
    }

    public void setFogStart(float start) {
        this.fogStart = Math.max(0, Math.min(1, start));
    }

    public void setFogEnd(float end) {
        this.fogEnd = Math.max(fogStart, Math.min(1, end));
    }

    // === CULLING ===

    public boolean isFrustumCullingEnabled() {
        return frustumCullingEnabled;
    }

    public void setFrustumCullingEnabled(boolean enabled) {
        this.frustumCullingEnabled = enabled;
    }

    public void toggleFrustumCulling() {
        this.frustumCullingEnabled = !this.frustumCullingEnabled;
    }

    // === QUALITY ===

    public boolean isAmbientOcclusionEnabled() {
        return ambientOcclusionEnabled;
    }

    public void setAmbientOcclusionEnabled(boolean enabled) {
        this.ambientOcclusionEnabled = enabled;
    }

    // === DEBUG ===

    public boolean isWireframeMode() {
        return wireframeMode;
    }

    public void setWireframeMode(boolean enabled) {
        this.wireframeMode = enabled;
    }

    public void toggleWireframe() {
        this.wireframeMode = !this.wireframeMode;
    }

    public boolean isShowChunkBorders() {
        return showChunkBorders;
    }

    public void toggleChunkBorders() {
        this.showChunkBorders = !this.showChunkBorders;
    }

    public boolean isShowDebugInfo() {
        return showDebugInfo;
    }

    public void toggleDebugInfo() {
        this.showDebugInfo = !this.showDebugInfo;
    }

    // === STATS ===

    public void updateStats(int rendered, int total, int culled, int triangles) {
        this.chunksRendered = rendered;
        this.chunksTotal = total;
        this.chunksCulled = culled;
        this.trianglesRendered = triangles;
    }

    public int getChunksRendered() {
        return chunksRendered;
    }

    public int getChunksTotal() {
        return chunksTotal;
    }

    public int getChunksCulled() {
        return chunksCulled;
    }

    public int getTrianglesRendered() {
        return trianglesRendered;
    }

    public String getStatsString() {
        return String.format("Chunks: %d/%d (-%d culled) | Tris: %dk | View: %d",
                chunksRendered, chunksTotal, chunksCulled,
                trianglesRendered / 1000,
                viewDistance);
    }

    // === PRESETS ===

    public void applyFastPreset() {
        viewDistance = 4;
        fogEnabled = true;
        frustumCullingEnabled = true;
        ambientOcclusionEnabled = false;
    }

    public void applyBalancedPreset() {
        viewDistance = 8;
        fogEnabled = true;
        frustumCullingEnabled = true;
        ambientOcclusionEnabled = true;
    }

    public void applyFancyPreset() {
        viewDistance = 16;
        fogEnabled = true;
        frustumCullingEnabled = true;
        ambientOcclusionEnabled = true;
    }

    public void applyExtremePreset() {
        viewDistance = 32;
        fogEnabled = false;
        frustumCullingEnabled = true;
        ambientOcclusionEnabled = true;
    }
}
