package engine.world.light;

public class LightIntQueue {
    private int[] data = new int[4096];
    private int head = 0;
    private int tail = 0;

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
        data[tail++] = (x << 16) | (z << 12) | (y << 4) | level;
    }

    public boolean isEmpty() {
        return head == tail;
    }

    public int poll() {
        return data[head++];
    }

    // Unpack helpers
    public static int unpackX(int val) {
        return (val >> 16) & 0xF;
    }

    public static int unpackZ(int val) {
        return (val >> 12) & 0xF;
    }

    public static int unpackY(int val) {
        return (val >> 4) & 0xFF;
    }

    public static int unpackLevel(int val) {
        return val & 0xF;
    }
}