package engine.entity.model;

/**
 * Single keyframe in an animation.
 */
public class Keyframe {
    
    public final float time;
    public final float x, y, z;
    
    public Keyframe(float time, float x, float y, float z) {
        this.time = time;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
