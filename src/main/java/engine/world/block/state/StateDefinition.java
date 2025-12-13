package engine.world.block.state;

import engine.world.block.Block;
import engine.world.block.state.property.Property;
import java.util.*;
import java.util.stream.Collectors;

public class StateDefinition {
    private final Block owner;
    private final Map<String, Property<?>> properties;
    private final List<BlockState> states;

    private StateDefinition(Block owner, Map<String, Property<?>> properties, List<BlockState> states) {
        this.owner = owner;
        this.properties = properties;
        this.states = states;
    }

    public List<BlockState> getPossibleStates() {
        return Collections.unmodifiableList(states);
    }

    public BlockState any() {
        return states.get(0);
    }

    public Block getOwner() {
        return owner;
    }

    public Collection<Property<?>> getProperties() {
        return properties.values();
    }

    public Property<?> getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Findings the peer state that has the same values as 'original' except for
     * 'property' = 'value'
     */
    public <T extends Comparable<T>, V extends T> BlockState getStateWith(BlockState original, Property<T> property,
            V value) {
        // Linear search for simplicity. In production this should be a look-up table.
        for (BlockState s : states) {
            if (s.get(property).equals(value)) {
                // Check all other properties match
                boolean match = true;
                for (Property<?> p : properties.values()) {
                    if (p != property && !s.get(p).equals(original.get(p))) {
                        match = false;
                        break;
                    }
                }
                if (match)
                    return s;
            }
        }
        return original; // Should not happen
    }

    public static class Builder {
        private final Block owner;
        private final Map<String, Property<?>> properties = new HashMap<>();

        public Builder(Block owner) {
            this.owner = owner;
        }

        public Builder add(Property<?>... props) {
            for (Property<?> p : props) {
                properties.put(p.getName(), p);
            }
            return this;
        }

        public StateDefinition build() {
            List<Property<?>> sortedProps = new ArrayList<>(properties.values());
            // Sort to ensure deterministic order
            sortedProps.sort(Comparator.comparing(Property::getName));

            List<BlockState> createdStates = new ArrayList<>();
            // Cartesian product
            List<Map<Property<?>, Comparable<?>>> permutations = cartesianProduct(sortedProps);

            for (Map<Property<?>, Comparable<?>> map : permutations) {
                createdStates.add(new BlockState(owner, map));
            }

            return new StateDefinition(owner, properties, createdStates);
        }

        // Helper for Cartesian product
        private List<Map<Property<?>, Comparable<?>>> cartesianProduct(List<Property<?>> props) {
            List<Map<Property<?>, Comparable<?>>> result = new ArrayList<>();
            result.add(new HashMap<>()); // Initial empty map

            for (Property<?> prop : props) {
                List<Map<Property<?>, Comparable<?>>> expected = new ArrayList<>();
                for (Map<Property<?>, Comparable<?>> existing : result) {
                    for (Comparable<?> val : prop.getValues()) {
                        Map<Property<?>, Comparable<?>> newMap = new HashMap<>(existing);
                        newMap.put(prop, val);
                        expected.add(newMap);
                    }
                }
                result = expected;
            }
            return result;
        }
    }
}
