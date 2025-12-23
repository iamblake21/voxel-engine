package engine.world.item.nbt;

import java.util.*;

/**
 * Simple NBT (Named Binary Tag) implementation for item metadata.
 * Supports basic data types: int, float, String, boolean, and Lists.
 */
public class NBTTagCompound {

    private final Map<String, Object> data;

    public NBTTagCompound() {
        this.data = new HashMap<>();
    }

    // ==================== SETTERS ====================

    public void setInt(String key, int value) {
        data.put(key, value);
    }

    public void setFloat(String key, float value) {
        data.put(key, value);
    }

    public void setString(String key, String value) {
        data.put(key, value);
    }

    public void setBoolean(String key, boolean value) {
        data.put(key, value);
    }

    public void setList(String key, List<?> value) {
        data.put(key, new ArrayList<>(value));
    }

    public void setTag(String key, NBTTagCompound tag) {
        data.put(key, tag);
    }

    // ==================== GETTERS ====================

    public int getInt(String key) {
        Object value = data.get(key);
        return value instanceof Integer ? (Integer) value : 0;
    }

    public float getFloat(String key) {
        Object value = data.get(key);
        return value instanceof Float ? (Float) value : 0.0f;
    }

    public String getString(String key) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : "";
    }

    public boolean getBoolean(String key) {
        Object value = data.get(key);
        return value instanceof Boolean ? (Boolean) value : false;
    }

    public List<?> getList(String key) {
        Object value = data.get(key);
        return value instanceof List ? (List<?>) value : new ArrayList<>();
    }

    public NBTTagCompound getTag(String key) {
        Object value = data.get(key);
        return value instanceof NBTTagCompound ? (NBTTagCompound) value : new NBTTagCompound();
    }

    // ==================== UTILITY ====================

    /**
     * Check if a key exists
     */
    public boolean hasKey(String key) {
        return data.containsKey(key);
    }

    /**
     * Remove a key
     */
    public void removeKey(String key) {
        data.remove(key);
    }

    /**
     * Get all keys
     */
    public Set<String> getKeys() {
        return Collections.unmodifiableSet(data.keySet());
    }

    /**
     * Check if empty
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Get size (number of keys)
     */
    public int size() {
        return data.size();
    }

    /**
     * Create a deep copy of this tag
     */
    public NBTTagCompound copy() {
        NBTTagCompound copy = new NBTTagCompound();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Deep copy for nested tags and lists
            if (value instanceof NBTTagCompound) {
                copy.setTag(key, ((NBTTagCompound) value).copy());
            } else if (value instanceof List) {
                copy.setList(key, new ArrayList<>((List<?>) value));
            } else {
                copy.data.put(key, value);
            }
        }
        return copy;
    }

    /**
     * Merge another tag into this one (overwrites existing keys)
     */
    public void merge(NBTTagCompound other) {
        for (Map.Entry<String, Object> entry : other.data.entrySet()) {
            data.put(entry.getKey(), entry.getValue());
        }
    }

    // ==================== RAW ACCESS (For Serialization) ====================

    public Object getRaw(String key) {
        return data.get(key);
    }

    public void setRaw(String key, Object value) {
        data.put(key, value);
    }

    public void setLong(String key, long value) {
        data.put(key, value);
    }

    public long getLong(String key) {
        Object value = data.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    public void setByte(String key, byte value) {
        data.put(key, value);
    }

    public byte getByte(String key) {
        Object value = data.get(key);
        return value instanceof Number ? ((Number) value).byteValue() : (byte) 0;
    }

    public void setByteArray(String key, byte[] value) {
        data.put(key, value);
    }

    public byte[] getByteArray(String key) {
        Object value = data.get(key);
        return value instanceof byte[] ? (byte[]) value : new byte[0];
    }

    public void setDouble(String key, double value) {
        data.put(key, value);
    }

    public double getDouble(String key) {
        Object value = data.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    @Override
    public String toString() {
        return "NBT" + data.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof NBTTagCompound))
            return false;
        NBTTagCompound other = (NBTTagCompound) obj;
        return data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
