package engine.world.block.state.property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class IntegerProperty implements Property<Integer> {
    private final String name;
    private final List<Integer> values;
    private final int min;
    private final int max;

    private IntegerProperty(String name, int min, int max) {
        this.name = name;
        this.min = min;
        this.max = max;
        this.values = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            values.add(i);
        }
    }

    public static IntegerProperty create(String name, int min, int max) {
        return new IntegerProperty(name, min, max);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<Integer> getValues() {
        return values;
    }

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }

    @Override
    public String name(Integer value) {
        return value.toString();
    }

    @Override
    public Optional<Integer> parse(String value) {
        try {
            int val = Integer.parseInt(value);
            if (val >= min && val <= max) {
                return Optional.of(val);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        IntegerProperty that = (IntegerProperty) o;
        return min == that.min && max == that.max && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + (31 * min + max);
    }
}
