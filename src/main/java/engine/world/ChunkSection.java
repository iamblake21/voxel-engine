package engine.world;

/**
 * A 16×16×16 section of a chunk column.
 *
 * Memory layout per section (only allocated for non-empty y-slices):
 *   blocks:    8 KB  (always present when section exists)
 *   fluidData: 4 KB  (always present when section exists)
 *   light:     8 KB  (lazily allocated only after light pass)
 * Total: 12–20 KB vs 20 KB/section in the old flat layout (but null sections cost 0).
 */
public final class ChunkSection {

    public static final int SIZE = 16;
    public static final int TOTAL = SIZE * SIZE * SIZE; // 4096

    final short[] blocks;
    final byte[] fluidData;
    short[] light; // lazy: null until light data is written

    ChunkSection(int airId) {
        this.blocks = new short[TOTAL];
        this.fluidData = new byte[TOTAL];
        // Pre-fill blocks to air; light stays null.
        java.util.Arrays.fill(blocks, (short) airId);
    }

    // ---- indexing ----

    static int index(int x, int y, int z) {
        // y is section-local (0-15)
        return (y * SIZE + z) * SIZE + x;
    }

    // ---- block ----

    public int getBlock(int x, int y, int z) {
        return blocks[index(x, y, z)] & 0xFFFF;
    }

    public void setBlock(int x, int y, int z, int id) {
        blocks[index(x, y, z)] = (short) id;
    }

    boolean hasMaterial(int airId) {
        for (short block : blocks) {
            if ((block & 0xFFFF) != airId) {
                return true;
            }
        }
        for (byte fluid : fluidData) {
            if (fluid != 0) {
                return true;
            }
        }
        return false;
    }

    // ---- fluid ----

    public int getFluidLevel(int x, int y, int z) {
        return fluidData[index(x, y, z)] & 0xFF;
    }

    public void setFluidLevel(int x, int y, int z, int level) {
        fluidData[index(x, y, z)] = (byte) Math.max(0, Math.min(15, level));
    }

    // ---- light (packed: [15:4]=RGB blocklight, [3:0]=skylight) ----

    public int getSkyLight(int x, int y, int z) {
        short[] currentLight = light;
        if (currentLight == null) return 0;
        return currentLight[index(x, y, z)] & 0xF;
    }

    public void setSkyLight(int x, int y, int z, int level) {
        ensureLight();
        int i = index(x, y, z);
        int rgb = (light[i] >> 4) & 0xFFF;
        light[i] = (short) ((rgb << 4) | Math.max(0, Math.min(15, level)));
    }

    public int getBlockLight(int x, int y, int z) {
        short[] currentLight = light;
        if (currentLight == null) return 0;
        return (currentLight[index(x, y, z)] >> 4) & 0xFFF;
    }

    public void setBlockLight(int x, int y, int z, int level) {
        ensureLight();
        int i = index(x, y, z);
        int sky = light[i] & 0xF;
        light[i] = (short) (((level & 0xFFF) << 4) | sky);
    }

    public int getPackedLight(int x, int y, int z) {
        short[] currentLight = light;
        if (currentLight == null) return 0;
        return currentLight[index(x, y, z)] & 0xFFFF;
    }

    /**
     * Bulk-apply light data from a flat buffer slice.
     * bufferBase is the index in the flat buffer where this section starts.
     */
    /** Returns the raw packed-light array, or null if no light has been set yet. */
    public short[] getLightArray() { return light; }

    public void clearLight() {
        light = null;
    }

    public void applyLightData(short[] buf, int bufferBase) {
        ensureLight();
        System.arraycopy(buf, bufferBase, light, 0, TOTAL);
    }

    private void ensureLight() {
        if (light == null) light = new short[TOTAL];
    }
}
