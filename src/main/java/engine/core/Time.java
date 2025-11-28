package engine.core;

/**
 * Time management - provides delta time, game time, etc.
 */
public class Time {
    
    private float deltaTime = 0f;
    private float gameTime = 0f;
    private float alpha = 0f; // Interpolation factor for rendering
    private long frameCount = 0;
    
    public void update(float dt) {
        this.deltaTime = dt;
        this.gameTime += dt;
        this.frameCount++;
    }
    
    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }
    
    /**
     * Time elapsed since last fixed update (in seconds)
     */
    public float getDeltaTime() {
        return deltaTime;
    }
    
    /**
     * Total time since engine start (in seconds)
     */
    public float getGameTime() {
        return gameTime;
    }
    
    /**
     * Interpolation factor between physics frames (0-1)
     * Use this for smooth rendering between fixed updates
     */
    public float getAlpha() {
        return alpha;
    }
    
    /**
     * Total number of fixed updates since start
     */
    public long getFrameCount() {
        return frameCount;
    }
}