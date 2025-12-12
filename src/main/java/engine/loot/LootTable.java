package engine.loot;

import engine.registry.ResourceLocation;
import engine.world.item.Item;
import engine.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A complete loot table with multiple pools.
 * 
 * Usage:
 *   LootTable table = LootTable.builder()
 *       .pool(LootPool.builder()
 *           .add(LootEntry.builder(Items.DIAMOND).count(1, 3).chance(0.5f))
 *           .add(LootEntry.builder(Items.COAL).count(2, 5)))
 *       .build();
 */
public class LootTable {
    
    public static final LootTable EMPTY = LootTable.builder().build();
    
    private final List<LootPool> pools;
    private ResourceLocation registryId;
    
    private LootTable(Builder builder) {
        this.pools = new ArrayList<>(builder.pools);
    }
    
    /**
     * Generate all loot from this table.
     */
    public List<ItemStack> generateLoot(Random random) {
        return generateLoot(random, 0);
    }
    
    /**
     * Generate all loot with fortune/looting level.
     */
    public List<ItemStack> generateLoot(Random random, int fortuneLevel) {
        List<ItemStack> results = new ArrayList<>();
        
        for (LootPool pool : pools) {
            results.addAll(pool.generate(random, fortuneLevel));
        }
        
        return results;
    }
    
    public boolean isEmpty() {
        return pools.isEmpty();
    }
    
    // Registry
    public ResourceLocation getRegistryId() { return registryId; }
    public void setRegistryId(ResourceLocation id) { this.registryId = id; }
    
    // ==================== BUILDER ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Quick builder for simple single-item drops.
     */
    public static LootTable singleDrop(Item item) {
        return builder()
            .pool(LootPool.builder()
                .add(LootEntry.builder(item).count(1)))
            .build();
    }
    
    /**
     * Quick builder for single item with count range.
     */
    public static LootTable singleDrop(Item item, int minCount, int maxCount) {
        return builder()
            .pool(LootPool.builder()
                .add(LootEntry.builder(item).count(minCount, maxCount)))
            .build();
    }
    
    public static class Builder {
        private final List<LootPool> pools = new ArrayList<>();
        
        public Builder pool(LootPool pool) {
            pools.add(pool);
            return this;
        }
        
        public Builder pool(LootPool.Builder poolBuilder) {
            pools.add(poolBuilder.build());
            return this;
        }
        
        public LootTable build() {
            return new LootTable(this);
        }
    }
}