package engine.entity.model;

import java.util.*;

/**
 * Animation channel for a single bone.
 * Contains keyframes for rotation, position, scale.
 */
public class AnimationChannel {
    
    private final List<Keyframe> rotationKeys = new ArrayList<>();
    private final List<Keyframe> positionKeys = new ArrayList<>();
    private final List<Keyframe> scaleKeys = new ArrayList<>();
    
    public AnimationChannel() {}
    
    // ==================== KEYFRAMES ====================
    
    public void addRotationKey(float time, float x, float y, float z) {
        rotationKeys.add(new Keyframe(time, x, y, z));
        rotationKeys.sort(Comparator.comparingDouble(k -> k.time));
    }
    
    public void addPositionKey(float time, float x, float y, float z) {
        positionKeys.add(new Keyframe(time, x, y, z));
        positionKeys.sort(Comparator.comparingDouble(k -> k.time));
    }
    
    public void addScaleKey(float time, float x, float y, float z) {
        scaleKeys.add(new Keyframe(time, x, y, z));
        scaleKeys.sort(Comparator.comparingDouble(k -> k.time));
    }
    
    // ==================== APPLY ====================
    
    public void apply(ModelBone bone, float time) {
        // Apply rotation
        if (!rotationKeys.isEmpty()) {
            float[] rot = interpolate(rotationKeys, time);
            bone.setRotation(
                bone.getDefaultRotX() + rot[0],
                bone.getDefaultRotY() + rot[1],
                bone.getDefaultRotZ() + rot[2]
            );
        }
        
        // Apply position
        if (!positionKeys.isEmpty()) {
            float[] pos = interpolate(positionKeys, time);
            bone.setPosition(pos[0], pos[1], pos[2]);
        }
        
        // Apply scale
        if (!scaleKeys.isEmpty()) {
            float[] scale = interpolate(scaleKeys, time);
            bone.setScale(scale[0], scale[1], scale[2]);
        }
    }
    
    private float[] interpolate(List<Keyframe> keys, float time) {
        if (keys.isEmpty()) {
            return new float[]{0, 0, 0};
        }
        
        // Before first keyframe
        if (time <= keys.get(0).time) {
            Keyframe k = keys.get(0);
            return new float[]{k.x, k.y, k.z};
        }
        
        // After last keyframe
        if (time >= keys.get(keys.size() - 1).time) {
            Keyframe k = keys.get(keys.size() - 1);
            return new float[]{k.x, k.y, k.z};
        }
        
        // Find surrounding keyframes
        Keyframe prev = keys.get(0);
        Keyframe next = keys.get(0);
        
        for (int i = 0; i < keys.size() - 1; i++) {
            if (time >= keys.get(i).time && time < keys.get(i + 1).time) {
                prev = keys.get(i);
                next = keys.get(i + 1);
                break;
            }
        }
        
        // Interpolate
        float t = (time - prev.time) / (next.time - prev.time);
        return new float[]{
            lerp(prev.x, next.x, t),
            lerp(prev.y, next.y, t),
            lerp(prev.z, next.z, t)
        };
    }
    
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
