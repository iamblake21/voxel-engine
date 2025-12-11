package game.init;

import engine.entity.EntityType;
import engine.entity.EntityTypes;
import engine.entity.EntityProperties;
import engine.entity.NpcEntity;

/**
 * All entity types for the game.
 * Follows same pattern as GameBlocks and GameItems.
 */
public final class GameEntities {
    
    public static EntityType<NpcEntity> VILLAGER;
    public static EntityType<NpcEntity> GUARD;
    public static EntityType<NpcEntity> MERCHANT;
    
    private GameEntities() {}
    
    public static void register() {
        System.out.println("[GameEntities] Registering entity types...");
        
        VILLAGER = EntityTypes.register("game:villager",
            EntityType.<NpcEntity>builder(NpcEntity::new)
                .size(0.6f, 1.8f)
                .properties(EntityProperties.create()
                    .humanoid()
                    .texture("textures/entity/villager.png"))
                .build());
        
        GUARD = EntityTypes.register("game:guard",
            EntityType.<NpcEntity>builder(type -> {
                NpcEntity npc = new NpcEntity(type);
                npc.setDisplayName("Guard");
                npc.setProfession("guard");
                return npc;
            })
                .size(0.6f, 1.9f)
                .properties(EntityProperties.create()
                    .humanoid()
                    .maxHealth(40f)
                    .texture("textures/entity/guard.png"))
                .build());
        
        MERCHANT = EntityTypes.register("game:merchant",
            EntityType.<NpcEntity>builder(type -> {
                NpcEntity npc = new NpcEntity(type);
                npc.setDisplayName("Merchant");
                npc.setProfession("merchant");
                npc.setDialogueLines(new String[]{
                    "Welcome! See anything you like?",
                    "Best prices in town!",
                    "Come back soon!"
                });
                return npc;
            })
                .size(0.6f, 1.8f)
                .properties(EntityProperties.create()
                    .humanoid()
                    .texture("textures/entity/merchant.png"))
                .build());
        
        System.out.println("[GameEntities] Registered " + 
            engine.registry.Registries.ENTITY_TYPES.size() + " entity types");
    }
}
