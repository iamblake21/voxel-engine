package engine.world.block.state.property;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class BooleanProperty implements Property<Boolean> {
    private final String name;
    private static final Collection<Boolean> VALUES = Arrays.asList(true, false);

    private BooleanProperty(String name) {
        this.name = name;
    }

    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<Boolean> getValues() {
        return VALUES;
    }

    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }

    @Override
    public String name(Boolean value) {
        return value.toString();
    }

    @Override
    public Optional<Boolean> parse(String value) {
        if ("true".equals(value))
            return Optional.of(true);
        if ("false".equals(value))
            return Optional.of(false);
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BooleanProperty that = (BooleanProperty) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
