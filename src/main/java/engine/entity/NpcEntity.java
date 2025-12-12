package engine.entity;

import engine.entity.ai.goals.*;
import engine.interaction.IInteractable;
import engine.interaction.InteractionResult;
import engine.registry.ResourceLocation;

/**
 * NPC entity with dialogue, professions, and schedules.
 * 
 * Updated to implement IInteractable for the new interaction system.
 */
public class NpcEntity extends LivingEntity implements IInteractable {
    
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
        brain.getGoalSelector().addGoal(10, new IdleGoal(this));
    }
    
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
    }
    
    // ==================== IINTERACTABLE ====================
    
    @Override
    public InteractionResult onInteract(Player player) {
        // Face the player
        lookAt(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
        
        // Show dialogue
        String dialogue = getCurrentDialogue();
        System.out.println("[" + displayName + "] " + dialogue);
        
        // Advance dialogue for next interaction
        advanceDialogue();
        
        // TODO: Trigger dialogue GUI or trade GUI based on profession
        return InteractionResult.SUCCESS;
    }
    
    @Override
    public boolean canInteract(Player player) {
        // Can interact if within range and not dead
        return !isDead() && distanceTo(player) <= getInteractionRange();
    }
    
    @Override
    public float getInteractionRange() {
        return 4.0f; // NPCs have shorter interaction range
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
    
    public void resetDialogue() {
        dialogueIndex = 0;
    }
}
