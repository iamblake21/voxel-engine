package game.blockentity;

import engine.entity.Player;
import engine.world.BlockPos;
import engine.world.blockentity.BlockEntityType;
import engine.world.blockentity.ContainerBlockEntity;
import engine.world.blockentity.ITickableBlockEntity;
import engine.world.item.ItemStack;
import engine.world.item.nbt.NBTTagCompound;

/**
 * Furnace block entity with smelting logic.
 * 
 * Slots:
 * - 0: Input (item to smelt)
 * - 1: Fuel
 * - 2: Output
 */
public class FurnaceBlockEntity extends ContainerBlockEntity implements ITickableBlockEntity {
    
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int INVENTORY_SIZE = 3;
    
    // Smelting progress (0 to maxSmeltTime)
    private int smeltProgress = 0;
    private int maxSmeltTime = 200; // 10 seconds at 20 tps
    
    // Fuel burn time
    private int burnTime = 0;
    private int maxBurnTime = 0;
    
    // Lit state (for block model/rendering)
    private boolean lit = false;
    
    public FurnaceBlockEntity(BlockEntityType<?> type, BlockPos pos) {
        super(type, pos, INVENTORY_SIZE);
    }
    
    @Override
    public String getDefaultName() {
        return "Furnace";
    }
    
    // ==================== TICK ====================
    
    @Override
    public void tick() {
        if (world == null) return;
        
        boolean wasLit = lit;
        boolean changed = false;
        
        // Burn fuel
        if (burnTime > 0) {
            burnTime--;
            lit = true;
        } else {
            lit = false;
        }
        
        ItemStack input = getItem(SLOT_INPUT);
        ItemStack fuel = getItem(SLOT_FUEL);
        ItemStack output = getItem(SLOT_OUTPUT);
        
        // Check if we can smelt
        if (canSmelt(input, output)) {
            // Try to consume fuel if not burning
            if (burnTime == 0 && !fuel.isEmpty()) {
                int fuelValue = getFuelValue(fuel);
                if (fuelValue > 0) {
                    burnTime = fuelValue;
                    maxBurnTime = fuelValue;
                    fuel.shrink(1);
                    changed = true;
                    lit = true;
                }
            }
            
            // Progress smelting if burning
            if (burnTime > 0) {
                smeltProgress++;
                
                if (smeltProgress >= maxSmeltTime) {
                    // Complete smelting
                    smeltItem(input, output);
                    smeltProgress = 0;
                    changed = true;
                }
            } else {
                // Not burning, reset progress
                if (smeltProgress > 0) {
                    smeltProgress = Math.max(0, smeltProgress - 2); // Cool down
                }
            }
        } else {
            // Can't smelt, reset progress
            smeltProgress = 0;
        }
        
        // Update lit state in world if changed
        if (wasLit != lit) {
            updateLitState();
            changed = true;
        }
        
        if (changed) {
            setChanged();
        }
    }
    
    // ==================== SMELTING LOGIC ====================
    
    /**
     * Check if the current input can be smelted.
     */
    private boolean canSmelt(ItemStack input, ItemStack output) {
        if (input.isEmpty()) return false;
        
        ItemStack result = getSmeltResult(input);
        if (result.isEmpty()) return false;
        
        // Check if output can accept the result
        if (output.isEmpty()) return true;
        if (!output.canMerge(result)) return false;
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }
    
    /**
     * Actually perform the smelting.
     */
    private void smeltItem(ItemStack input, ItemStack output) {
        ItemStack result = getSmeltResult(input);
        if (result.isEmpty()) return;
        
        if (output.isEmpty()) {
            setItem(SLOT_OUTPUT, result.copy());
        } else {
            output.grow(result.getCount());
        }
        
        input.shrink(1);
    }
    
    /**
     * Get the smelting result for an input.
     * Override or use a recipe system for real implementation.
     */
    protected ItemStack getSmeltResult(ItemStack input) {
        // Placeholder - in real implementation, use a recipe registry
        // For now, return empty (no recipes defined)
        return ItemStack.EMPTY;
    }
    
    /**
     * Get fuel burn time for an item.
     * Override or use a fuel registry for real implementation.
     */
    protected int getFuelValue(ItemStack fuel) {
        // Placeholder values
        // Coal: 1600 ticks (80 seconds)
        // Wood: 300 ticks (15 seconds)
        // In real implementation, use a fuel registry
        return 0;
    }
    
    /**
     * Update the lit state in the world (for block model).
     */
    private void updateLitState() {
        // In real implementation, this would update the block state
        // world.setBlockState(pos, getBlock().with(FURNACE_LIT, lit));
    }
    
    // ==================== GETTERS ====================
    
    public boolean isLit() {
        return lit;
    }
    
    public int getBurnTime() {
        return burnTime;
    }
    
    public int getMaxBurnTime() {
        return maxBurnTime;
    }
    
    public int getSmeltProgress() {
        return smeltProgress;
    }
    
    public int getMaxSmeltTime() {
        return maxSmeltTime;
    }
    
    /**
     * Get burn progress as 0-1 float (for GUI).
     */
    public float getBurnProgress() {
        return maxBurnTime > 0 ? (float) burnTime / maxBurnTime : 0;
    }
    
    /**
     * Get smelt progress as 0-1 float (for GUI).
     */
    public float getSmeltProgressPercent() {
        return maxSmeltTime > 0 ? (float) smeltProgress / maxSmeltTime : 0;
    }
    
    // ==================== SERIALIZATION ====================
    
    @Override
    protected void saveAdditional(NBTTagCompound nbt) {
        super.saveAdditional(nbt);
        nbt.setInt("BurnTime", burnTime);
        nbt.setInt("MaxBurnTime", maxBurnTime);
        nbt.setInt("SmeltProgress", smeltProgress);
    }
    
    @Override
    protected void loadAdditional(NBTTagCompound nbt) {
        super.loadAdditional(nbt);
        burnTime = nbt.getInt("BurnTime");
        maxBurnTime = nbt.getInt("MaxBurnTime");
        smeltProgress = nbt.getInt("SmeltProgress");
        lit = burnTime > 0;
    }
}
