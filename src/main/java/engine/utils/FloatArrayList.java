package engine.utils;

import java.util.Arrays;

/**
 * A primitive float array list to avoid boxing/unboxing overhead of
 * ArrayList<Float>.
 */
public class FloatArrayList {
    private float[] data;
    private int size;

    public FloatArrayList() {
        this(1024);
    }

    public FloatArrayList(int capacity) {
        this.data = new float[capacity];
        this.size = 0;
    }

    public void add(float value) {
        if (size == data.length) {
            grow();
        }
        data[size++] = value;
    }

    public void addAll(float[] values) {
        if (size + values.length > data.length) {
            grow(size + values.length);
        }
        System.arraycopy(values, 0, data, size, values.length);
        size += values.length;
    }

    public float get(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException(index);
        return data[index];
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
    }

    public float[] toArray() {
        return Arrays.copyOf(data, size);
    }

    private void grow() {
        grow(size + 1);
    }

    private void grow(int minCapacity) {
        int oldCapacity = data.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        data = Arrays.copyOf(data, newCapacity);
    }
}
