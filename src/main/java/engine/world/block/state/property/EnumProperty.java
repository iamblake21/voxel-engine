package engine.world.block.state.property;

import engine.utils.StringRepresentable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EnumProperty<T extends Enum<T> & StringRepresentable> implements Property<T> {
    private final String name;
    private final Class<T> clazz;
    private final Collection<T> values;
    private final Map<String, T> nameToValue = new HashMap<>();

    private EnumProperty(String name, Class<T> clazz, Collection<T> values) {
        this.name = name;
        this.clazz = clazz;
        this.values = values;
        for (T t : values) {
            String id = t.getSerializedName();
            if (nameToValue.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate name '" + id + "' in enum property '" + name + "'");
            }
            nameToValue.put(id, t);
        }
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) {
        return create(name, clazz, Arrays.asList(clazz.getEnumConstants()));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz,
            Collection<T> values) {
        return new EnumProperty<>(name, clazz, values);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<T> getValues() {
        return values;
    }

    @Override
    public Class<T> getType() {
        return clazz;
    }

    @Override
    public String name(T value) {
        return value.getSerializedName();
    }

    @Override
    public Optional<T> parse(String value) {
        return Optional.ofNullable(nameToValue.get(value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EnumProperty<?> that = (EnumProperty<?>) o;
        return name.equals(that.name) && clazz.equals(that.clazz) && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + values.hashCode();
    }
}
