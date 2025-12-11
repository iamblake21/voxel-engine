package game.blockentity;

import engine.entity.Player;
import engine.world.BlockPos;
import engine.world.blockentity.BlockEntityType;
import engine.world.blockentity.ContainerBlockEntity;

/**
 * Chest block entity - 27 slot storage container.
 */
public class ChestBlockEntity extends ContainerBlockEntity {
    
    public static final int INVENTORY_SIZE = 27; // 3 rows of 9
    
    // Animation
    private float lidAngle = 0;
    private float prevLidAngle = 0;
    private int openCount = 0;
    
    public ChestBlockEntity(BlockEntityType<?> type, BlockPos pos) {
        super(type, pos, INVENTORY_SIZE);
    }
    
    @Override
    public String getDefaultName() {
        return "Chest";
    }
    
    // ==================== LID ANIMATION ====================
    
    public void updateLidAnimation() {
        prevLidAngle = lidAngle;
        
        if (openCount > 0 && lidAngle < 1.0f) {
            lidAngle += 0.1f;
            if (lidAngle > 1.0f) lidAngle = 1.0f;
        } else if (openCount == 0 && lidAngle > 0.0f) {
            lidAngle -= 0.1f;
            if (lidAngle < 0.0f) lidAngle = 0.0f;
        }
    }
    
    public float getLidAngle(float partialTick) {
        return prevLidAngle + (lidAngle - prevLidAngle) * partialTick;
    }
    
    // ==================== OPEN/CLOSE ====================
    
    @Override
    protected void onOpen(Player player) {
        super.onOpen(player);
        openCount++;
        // Play open sound
        // world.playSound(pos, "block.chest.open");
    }
    
    @Override
    public void onClose(Player player) {
        super.onClose(player);
        openCount = Math.max(0, openCount - 1);
        // Play close sound
        // world.playSound(pos, "block.chest.close");
    }
    
    public boolean isOpen() {
        return openCount > 0;
    }
    
    public int getOpenCount() {
        return openCount;
    }
}
