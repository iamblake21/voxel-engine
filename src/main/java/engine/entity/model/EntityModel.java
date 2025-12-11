package engine.entity.model;

import java.util.*;

/**
 * Entity model with bones and animations.
 * Loaded from Blockbench JSON format.
 */
public class EntityModel {
    
    private String name;
    private int textureWidth = 64;
    private int textureHeight = 64;
    
    // All bones by name
    private final Map<String, ModelBone> bones = new LinkedHashMap<>();
    
    // Root bones (no parent)
    private final List<ModelBone> rootBones = new ArrayList<>();
    
    // Animations
    private final Map<String, EntityAnimation> animations = new HashMap<>();
    
    public EntityModel(String name) {
        this.name = name;
    }
    
    // ==================== BONES ====================
    
    public void addBone(ModelBone bone) {
        bones.put(bone.getName(), bone);
        if (bone.getParent() == null) {
            rootBones.add(bone);
        }
    }
    
    public ModelBone getBone(String name) {
        return bones.get(name);
    }
    
    public Collection<ModelBone> getAllBones() {
        return bones.values();
    }
    
    public List<ModelBone> getRootBones() {
        return rootBones;
    }
    
    // ==================== ANIMATIONS ====================
    
    public void addAnimation(String name, EntityAnimation anim) {
        animations.put(name, anim);
    }
    
    public EntityAnimation getAnimation(String name) {
        return animations.get(name);
    }
    
    public Set<String> getAnimationNames() {
        return animations.keySet();
    }
    
    // ==================== POSE ====================
    
    /**
     * Reset all bones to their default pose.
     */
    public void resetPose() {
        for (ModelBone bone : bones.values()) {
            bone.resetPose();
        }
    }
    
    // ==================== PROPERTIES ====================
    
    public String getName() { return name; }
    
    public int getTextureWidth() { return textureWidth; }
    public void setTextureWidth(int w) { this.textureWidth = w; }
    
    public int getTextureHeight() { return textureHeight; }
    public void setTextureHeight(int h) { this.textureHeight = h; }
}
