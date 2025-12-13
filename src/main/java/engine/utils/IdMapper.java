package engine.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps objects to integer IDs and back.
 * IDs are assigned sequentially starting from 0.
 */
public class IdMapper<T> {
    private final List<T> idToValue = new ArrayList<>();
    private final Map<T, Integer> valueToId = new HashMap<>();

    public int add(T value) {
        if (valueToId.containsKey(value)) {
            return valueToId.get(value);
        }
        int id = idToValue.size();
        idToValue.add(value);
        valueToId.put(value, id);
        return id;
    }

    public T get(int id) {
        if (id < 0 || id >= idToValue.size()) {
            return null;
        }
        return idToValue.get(id);
    }

    public int getId(T value) {
        return valueToId.getOrDefault(value, -1);
    }

    public int size() {
        return idToValue.size();
    }
}
