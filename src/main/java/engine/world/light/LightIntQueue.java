package engine.world.light;

public class LightIntQueue {
    private int[] data = new int[4096];
    private int head = 0;
    private int tail = 0;

    /**
     * Add a node to the queue.
     * PACKING LAYOUT (32-bit int):
     * Bits 0-11: Level (12 bits) - Supports 0xRGB (4096 values)
     * Bits 12-20: Y (9 bits) - Supports 0-511 Height
     * Bits 21-24: Z (4 bits) - Supports 0-15 (Chunk relative)
     * Bits 25-28: X (4 bits) - Supports 0-15 (Chunk relative)
     */
    public void add(int x, int y, int z, int level) {
        if (tail == data.length) {
            // Grow
            int[] newData = new int[data.length * 2];
            System.arraycopy(data, head, newData, 0, tail - head);
            tail -= head;
            head = 0;
            data = newData;
        }
        // Pack
        // Note: x and z must be 0-15. y must be 0-511. level must be 0-4095.
        data[tail++] = (x << 25) | (z << 21) | (y << 12) | (level & 0xFFF);
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public int poll() {
        return data[head++];
    }

    // Unpack helpers
    public static int unpackX(int val) {
        return (val >> 25) & 0xF;
    }

    public static int unpackZ(int val) {
        return (val >> 21) & 0xF;
    }

    public static int unpackY(int val) {
        return (val >> 12) & 0x1FF; // 9 bits mask (511)
    }

    public static int unpackLevel(int val) {
        return val & 0xFFF; // 12 bits mask (RGB)
    }

    public void clear() {
        head = 0;
        tail = 0;
    }
}