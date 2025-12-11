package game.init;

import engine.world.blockentity.BlockEntityType;
import engine.world.blockentity.BlockEntityTypes;
import game.blockentity.ChestBlockEntity;
import game.blockentity.FurnaceBlockEntity;

/**
 * All block entity types for the game.
 * 
 * Call GameBlockEntities.register() in GameInit.registerContent()
 */
public final class GameBlockEntities {
    
    public static BlockEntityType<ChestBlockEntity> CHEST;
    public static BlockEntityType<FurnaceBlockEntity> FURNACE;
    
    private GameBlockEntities() {}
    
    public static void register() {
        System.out.println("[GameBlockEntities] Registering block entity types...");
        
        CHEST = BlockEntityTypes.register("game:chest",
            BlockEntityType.<ChestBlockEntity>builder(ChestBlockEntity::new)
                // .validBlocks(GameBlocks.CHEST) // Add when you have the block
                .build());
        
        FURNACE = BlockEntityTypes.register("game:furnace",
            BlockEntityType.<FurnaceBlockEntity>builder(FurnaceBlockEntity::new)
                // .validBlocks(GameBlocks.FURNACE) // Add when you have the block
                .build());
        
        System.out.println("[GameBlockEntities] Registered " + 
            engine.registry.Registries.BLOCK_ENTITY_TYPES.size() + " block entity types");
    }
}
