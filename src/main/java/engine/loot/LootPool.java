package engine.loot;

import engine.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A pool of loot entries.
 * Can be configured to drop all entries, or select weighted random entries.
 */
public class LootPool {
    
    public enum Mode {
        ALL,            // Drop all entries (each with its own chance)
        WEIGHTED_ONE,   // Pick one entry based on weight
        WEIGHTED_MULTI  // Pick N entries based on weight
    }
    
    private final List<LootEntry> entries;
    private final Mode mode;
    private final int rolls;        // How many times to roll (for WEIGHTED modes)
    private final int bonusRolls;   // Extra rolls based on luck/fortune
    
    private LootPool(Builder builder) {
        this.entries = new ArrayList<>(builder.entries);
        this.mode = builder.mode;
        this.rolls = builder.rolls;
        this.bonusRolls = builder.bonusRolls;
    }
    
    /**
     * Generate loot from this pool.
     */
    public List<ItemStack> generate(Random random, int fortuneLevel) {
        List<ItemStack> results = new ArrayList<>();
        
        int totalRolls = rolls + (bonusRolls * fortuneLevel);
        
        switch (mode) {
            case ALL:
                // Each entry generates independently
                for (LootEntry entry : entries) {
                    ItemStack stack = entry.generate(random);
                    if (stack != null) {
                        results.add(stack);
                    }
                }
                break;
                
            case WEIGHTED_ONE:
            case WEIGHTED_MULTI:
                // Calculate total weight
                float totalWeight = 0;
                for (LootEntry entry : entries) {
                    totalWeight += entry.getWeight();
                }
                
                if (totalWeight <= 0) break;
                
                // Roll N times
                for (int i = 0; i < totalRolls; i++) {
                    float roll = random.nextFloat() * totalWeight;
                    float cumulative = 0;
                    
                    for (LootEntry entry : entries) {
                        cumulative += entry.getWeight();
                        if (roll < cumulative) {
                            ItemStack stack = entry.generate(random);
                            if (stack != null) {
                                results.add(stack);
                            }
                            break;
                        }
                    }
                }
                break;
        }
        
        return results;
    }
    
    // ==================== BUILDER ====================
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final List<LootEntry> entries = new ArrayList<>();
        private Mode mode = Mode.ALL;
        private int rolls = 1;
        private int bonusRolls = 0;
        
        public Builder add(LootEntry entry) {
            entries.add(entry);
            return this;
        }
        
        public Builder add(LootEntry.Builder entryBuilder) {
            entries.add(entryBuilder.build());
            return this;
        }
        
        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }
        
        public Builder rolls(int rolls) {
            this.rolls = rolls;
            return this;
        }
        
        public Builder bonusRolls(int bonusRolls) {
            this.bonusRolls = bonusRolls;
            return this;
        }
        
        public LootPool build() {
            return new LootPool(this);
        }
    }
}