package engine.entity.model;

import java.util.*;

/**
 * Animation for entity models.
 * Contains keyframes for multiple bones.
 */
public class EntityAnimation {
    
    private final String name;
    private float duration = 1.0f;
    private boolean loop = true;
    
    // Channels per bone
    private final Map<String, AnimationChannel> channels = new HashMap<>();
    
    public EntityAnimation(String name) {
        this.name = name;
    }
    
    // ==================== PROPERTIES ====================
    
    public String getName() { return name; }
    
    public float getDuration() { return duration; }
    public void setDuration(float duration) { this.duration = duration; }
    
    public boolean isLoop() { return loop; }
    public void setLoop(boolean loop) { this.loop = loop; }
    
    // ==================== CHANNELS ====================
    
    public void addChannel(String boneName, AnimationChannel channel) {
        channels.put(boneName, channel);
    }
    
    public AnimationChannel getChannel(String boneName) {
        return channels.get(boneName);
    }
    
    // ==================== APPLY ====================
    
    /**
     * Apply animation to model at given time.
     */
    public void apply(EntityModel model, float time) {
        // Handle looping
        if (loop && duration > 0) {
            time = time % duration;
        } else {
            time = Math.min(time, duration);
        }
        
        for (Map.Entry<String, AnimationChannel> entry : channels.entrySet()) {
            ModelBone bone = model.getBone(entry.getKey());
            if (bone != null) {
                entry.getValue().apply(bone, time);
            }
        }
    }
}
