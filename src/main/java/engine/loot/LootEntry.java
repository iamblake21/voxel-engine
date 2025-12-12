package engine.loot;

import engine.world.item.Item;
import engine.world.item.ItemStack;

import java.util.Random;

/**
 * A single entry in a loot table.
 * Represents one possible drop with count range and weight.
 */
public class LootEntry {
    
    private final Item item;
    private final int minCount;
    private final int maxCount;
    private final float weight;
    private final float chance; // 0.0 - 1.0
    
    private LootEntry(Builder builder) {
        this.item = builder.item;
        this.minCount = builder.minCount;
        this.maxCount = builder.maxCount;
        this.weight = builder.weight;
        this.chance = builder.chance;
    }
    
    /**
     * Generate the item stack for this entry.
     * Returns null if chance roll fails.
     */
    public ItemStack generate(Random random) {
        // Check chance
        if (chance < 1.0f && random.nextFloat() > chance) {
            return null;
        }
        
        // Calculate count
        int count = minCount;
        if (maxCount > minCount) {
            count = minCount + random.nextInt(maxCount - minCount + 1);
        }
        
        if (count <= 0) return null;
        
        return new ItemStack(item, count);
    }
    
    public Item getItem() { return item; }
    public int getMinCount() { return minCount; }
    public int getMaxCount() { return maxCount; }
    public float getWeight() { return weight; }
    public float getChance() { return chance; }
    
    // ==================== BUILDER ====================
    
    public static Builder builder(Item item) {
        return new Builder(item);
    }
    
    public static class Builder {
        private final Item item;
        private int minCount = 1;
        private int maxCount = 1;
        private float weight = 1.0f;
        private float chance = 1.0f;
        
        private Builder(Item item) {
            this.item = item;
        }
        
        public Builder count(int count) {
            this.minCount = count;
            this.maxCount = count;
            return this;
        }
        
        public Builder count(int min, int max) {
            this.minCount = min;
            this.maxCount = max;
            return this;
        }
        
        public Builder weight(float weight) {
            this.weight = weight;
            return this;
        }
        
        public Builder chance(float chance) {
            this.chance = Math.max(0f, Math.min(1f, chance));
            return this;
        }
        
        public LootEntry build() {
            return new LootEntry(this);
        }
    }
}