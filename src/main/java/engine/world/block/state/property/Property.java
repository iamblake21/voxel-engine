package engine.world.block.state.property;

import java.util.Collection;
import java.util.Optional;

public interface Property<T extends Comparable<T>> {
    String getName();

    Collection<T> getValues();

    Class<T> getType();

    String name(T value);

    Optional<T> parse(String value);
}
