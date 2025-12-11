package engine.entity;

import engine.entity.ai.goals.*;
import engine.registry.ResourceLocation;

/**
 * NPC entity with dialogue, professions, and schedules.
 */
public class NpcEntity extends LivingEntity {
    
    // Identity
    protected String displayName = "Villager";
    protected String profession = "none";
    
    // Model/texture override (can differ from type's default)
    protected ResourceLocation modelId;
    protected ResourceLocation textureId;
    
    // Dialogue
    protected String[] dialogueLines;
    protected int dialogueIndex = 0;
    
    // Schedule
    protected ResourceLocation scheduleId;
    
    // Relationships
    protected long targetEntityId = -1;
    
    public NpcEntity(EntityType<?> type) {
        super(type);
        initDefaultAI();
    }
    
    protected void initDefaultAI() {
        // Default NPC AI goals (lower priority = higher importance)
        brain.getGoalSelector().addGoal(1, new LookAtPlayerGoal(this, 8f, 0.8f));
        brain.getGoalSelector().addGoal(5, new WanderGoal(this, 0.6f, 20, 100));
        brain.getGoalSelector().addGoal(7, new ReturnHomeGoal(this, 32f, 1.2f));
        brain.getGoalSelector().addGoal(10, new IdleGoal(this));
    }
    
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        
        // Additional NPC logic here
    }
    
    // ==================== IDENTITY ====================
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }
    
    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }
    
    // ==================== MODEL/TEXTURE ====================
    
    public ResourceLocation getModelId() { 
        if (modelId != null) return modelId;
        String path = type.getModelPath();
        return path != null ? ResourceLocation.of(path) : null;
    }
    
    public void setModelId(ResourceLocation id) { this.modelId = id; }
    
    public ResourceLocation getTextureId() {
        if (textureId != null) return textureId;
        String path = type.getTexturePath();
        return path != null ? ResourceLocation.of(path) : null;
    }
    
    public void setTextureId(ResourceLocation id) { this.textureId = id; }
    
    // ==================== DIALOGUE ====================
    
    public String[] getDialogueLines() { return dialogueLines; }
    public void setDialogueLines(String[] lines) { this.dialogueLines = lines; }
    
    public String getCurrentDialogue() {
        if (dialogueLines == null || dialogueLines.length == 0) {
            return "...";
        }
        return dialogueLines[dialogueIndex % dialogueLines.length];
    }
    
    public void advanceDialogue() {
        if (dialogueLines != null && dialogueLines.length > 0) {
            dialogueIndex = (dialogueIndex + 1) % dialogueLines.length;
        }
    }
    
    // ==================== INTERACTION ====================
    
    public void onInteract(Entity interactor) {
        // Face the interactor
        lookAt(interactor.getX(), interactor.getY() + interactor.getEyeHeight(), interactor.getZ());
        
        // Show dialogue
        System.out.println("[" + displayName + "] " + getCurrentDialogue());
        advanceDialogue();
    }
}
