package engine.world.gen;

import engine.world.Chunk;
import engine.world.ChunkSection;
import engine.world.biome.Biome;

import java.util.Arrays;

/**
 * Immutable snapshot of a chunk and its 8 neighbours for async light/mesh work.
 *
 * For compacted chunks (all chunks at FEATURES phase or later), block/fluid/light
 * data is accessed via zero-copy ChunkSection[] references.  Only the center
 * chunk's light write-buffer is a separate allocation (192 KB) needed for the
 * light propagation output.
 *
 * Light format: [15:4] = RGB blocklight (0xRGB), [3:0] = skylight (0-15)
 */
public class ChunkSnapshot implements MeshBuilder.WorldAccess, MeshBuilder.ChunkData {

    public final int centerX;
    public final int centerZ;

    private final int chunkSize;
    private final int chunkHeight;
    private final int sectionCount; // chunkHeight / 16

    // Write buffer for the center chunk's light. Allocated lazily for light tasks;
    // mesh tasks read compacted section light directly and do not need this buffer.
    private short[] lightWrite;

    // Per-neighbour data (index = (dz+1)*3 + (dx+1), centre = 4).
    // For compacted chunks: sections[] is non-null, flat arrays are null.
    // For non-compacted chunks (rare edge-case): flat arrays are non-null.
    private final ChunkSection[][] neighborSections;   // [9][sectionCount] – zero-copy refs
    private final int[]            neighborTopSection; // [9] highest filled section per neighbour
    private final short[][]        neighborBlocks;     // [9] flat fallback (non-compacted only)
    private final short[][]        neighborLight;      // [9] flat fallback (non-compacted only)
    private final byte[][]         neighborFluidData;  // [9] flat fallback (non-compacted only)
    private final boolean[]        neighborExists;
    private final boolean[]        neighborCompacted;

    private final BiomeProvider biomeProvider;

    // ==========================================================

    public ChunkSnapshot(int cx, int cz, Chunk[][] neighbors,
                         int chunkSize, int chunkHeight, BiomeProvider biomeProvider) {
        this.centerX      = cx;
        this.centerZ      = cz;
        this.chunkSize    = chunkSize;
        this.chunkHeight  = chunkHeight;
        this.sectionCount = chunkHeight / ChunkSection.SIZE;
        this.biomeProvider = biomeProvider;

        this.neighborSections   = new ChunkSection[9][];
        this.neighborTopSection = new int[9];
        this.neighborBlocks     = new short[9][];
        this.neighborLight      = new short[9][];
        this.neighborFluidData  = new byte[9][];
        this.neighborExists     = new boolean[9];
        this.neighborCompacted  = new boolean[9];

        Arrays.fill(neighborTopSection, -1);

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int idx = (dz + 1) * 3 + (dx + 1);
                Chunk c = neighbors[dz + 1][dx + 1];
                if (c == null) continue;

                neighborExists[idx] = true;

                if (c.isCompacted()) {
                    neighborCompacted[idx]  = true;
                    neighborSections[idx]   = c.getSections().clone();   // stable section slots
                    neighborTopSection[idx] = c.getTopFilledSection();
                } else {
                    // Fallback for chunks not yet compacted (should be rare/edge-case).
                    neighborCompacted[idx] = false;
                    neighborBlocks[idx]    = c.getBlockData();
                    neighborLight[idx]     = c.getLightData();
                    neighborFluidData[idx] = c.getFluidData();
                }
            }
        }

        this.lightWrite = null;
    }

    // ==========================================================
    // LOCAL (center chunk) – used by LightPropagator
    // ==========================================================

    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize)
            return 0;
        return sampleBlock(4, x, y, z);
    }

    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize)
            return 0;
        if (lightWrite != null) {
            return lightWrite[localIndex(x, y, z)] & 0xF;
        }
        return sampleSkyLight(4, x, y, z);
    }

    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize)
            return;
        short[] target = ensureLightWrite();
        int idx = localIndex(x, y, z);
        int clamped = Math.max(0, Math.min(15, level));
        int rgb = (target[idx] >> 4) & 0xFFF;
        target[idx] = (short) ((rgb << 4) | clamped);
    }

    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize)
            return 0;
        if (lightWrite != null) {
            return (lightWrite[localIndex(x, y, z)] >> 4) & 0xFFF;
        }
        return sampleBlockLight(4, x, y, z);
    }

    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize)
            return;
        short[] target = ensureLightWrite();
        int idx = localIndex(x, y, z);
        int sky = target[idx] & 0xF;
        target[idx] = (short) (((level & 0xFFF) << 4) | sky);
    }

    public int getFluidLevel(int x, int y, int z) {
        if (x < 0 || x >= chunkSize || y < 0 || y >= chunkHeight || z < 0 || z >= chunkSize)
            return 0;
        return sampleFluid(4, x, y, z);
    }

    // ==========================================================
    // WORLD ACCESS (peek – global coords, used by MeshBuilder)
    // ==========================================================

    @Override
    public int peekBlock(int globalX, int globalY, int globalZ) {
        if (globalY < 0 || globalY >= chunkHeight) return 0;
        int idx = neighborIdx(globalX, globalZ);
        if (idx < 0 || !neighborExists[idx]) return 0;
        int lx = Math.floorMod(globalX, chunkSize);
        int lz = Math.floorMod(globalZ, chunkSize);
        return sampleBlock(idx, lx, globalY, lz);
    }

    @Override
    public int peekSkyLight(int globalX, int globalY, int globalZ) {
        if (globalY < 0) return 0;
        if (globalY >= chunkHeight) return 15;

        int idx = neighborIdx(globalX, globalZ);
        if (idx < 0 || !neighborExists[idx]) return 0;

        // Center chunk: read from write buffer.
        if (idx == 4) {
            int lx = Math.floorMod(globalX, chunkSize);
            int lz = Math.floorMod(globalZ, chunkSize);
            if (lightWrite != null) {
                return lightWrite[localIndex(lx, globalY, lz)] & 0xF;
            }
            return sampleSkyLight(4, lx, globalY, lz);
        }

        int lx = Math.floorMod(globalX, chunkSize);
        int lz = Math.floorMod(globalZ, chunkSize);
        return sampleSkyLight(idx, lx, globalY, lz);
    }

    @Override
    public int peekBlockLight(int globalX, int globalY, int globalZ) {
        if (globalY < 0 || globalY >= chunkHeight) return 0;
        int idx = neighborIdx(globalX, globalZ);
        if (idx < 0 || !neighborExists[idx]) return 0;

        if (idx == 4) {
            int lx = Math.floorMod(globalX, chunkSize);
            int lz = Math.floorMod(globalZ, chunkSize);
            if (lightWrite != null) {
                return (lightWrite[localIndex(lx, globalY, lz)] >> 4) & 0xFFF;
            }
            return sampleBlockLight(4, lx, globalY, lz);
        }

        int lx = Math.floorMod(globalX, chunkSize);
        int lz = Math.floorMod(globalZ, chunkSize);
        return sampleBlockLight(idx, lx, globalY, lz);
    }

    public int peekFluidLevel(int globalX, int globalY, int globalZ) {
        if (globalY < 0 || globalY >= chunkHeight) return 0;
        int idx = neighborIdx(globalX, globalZ);
        if (idx < 0 || !neighborExists[idx]) return 0;
        int lx = Math.floorMod(globalX, chunkSize);
        int lz = Math.floorMod(globalZ, chunkSize);
        return sampleFluid(idx, lx, globalY, lz);
    }

    // ==========================================================
    // INTERNAL SAMPLING HELPERS
    // ==========================================================

    private int sampleBlock(int idx, int lx, int gy, int lz) {
        if (neighborCompacted[idx]) {
            ChunkSection sec = neighborSections[idx][gy >> 4];
            return sec == null ? 0 : sec.getBlock(lx, gy & 15, lz);
        }
        short[] arr = neighborBlocks[idx];
        return arr == null ? 0 : arr[localIndex(lx, gy, lz)] & 0xFFFF;
    }

    private int sampleSkyLight(int idx, int lx, int gy, int lz) {
        if (neighborCompacted[idx]) {
            int sy = gy >> 4;
            ChunkSection sec = neighborSections[idx][sy];
            if (sec == null) return (sy > neighborTopSection[idx]) ? 15 : 0;
            if (sec.getLightArray() == null) return (sy > neighborTopSection[idx]) ? 15 : 0;
            return sec.getSkyLight(lx, gy & 15, lz);
        }
        short[] arr = neighborLight[idx];
        return arr == null ? 0 : arr[localIndex(lx, gy, lz)] & 0xF;
    }

    private int sampleBlockLight(int idx, int lx, int gy, int lz) {
        if (neighborCompacted[idx]) {
            ChunkSection sec = neighborSections[idx][gy >> 4];
            return sec == null ? 0 : sec.getBlockLight(lx, gy & 15, lz);
        }
        short[] arr = neighborLight[idx];
        return arr == null ? 0 : (arr[localIndex(lx, gy, lz)] >> 4) & 0xFFF;
    }

    private int sampleFluid(int idx, int lx, int gy, int lz) {
        if (neighborCompacted[idx]) {
            ChunkSection sec = neighborSections[idx][gy >> 4];
            return sec == null ? 0 : sec.getFluidLevel(lx, gy & 15, lz);
        }
        byte[] arr = neighborFluidData[idx];
        return arr == null ? 0 : arr[localIndex(lx, gy, lz)] & 0xFF;
    }

    /** Returns the 3×3 grid index for global (x,z), or -1 if out of range. */
    private int neighborIdx(int globalX, int globalZ) {
        int cx = Math.floorDiv(globalX, chunkSize);
        int cz = Math.floorDiv(globalZ, chunkSize);
        int dx = cx - centerX;
        int dz = cz - centerZ;
        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) return -1;
        return (dz + 1) * 3 + (dx + 1);
    }

    private int localIndex(int x, int y, int z) {
        return (y * chunkSize + z) * chunkSize + x;
    }

    // ==========================================================
    // ACCESSORS
    // ==========================================================

    public short[] getLightWriteBuffer() { return ensureLightWrite(); }

    private short[] ensureLightWrite() {
        if (lightWrite == null) {
            lightWrite = new short[chunkSize * chunkSize * chunkHeight];
            copyCenterLightInto(lightWrite);
        }
        return lightWrite;
    }

    private void copyCenterLightInto(short[] target) {
        int ci = 4;
        if (!neighborExists[ci]) {
            return;
        }

        if (neighborCompacted[ci] && neighborSections[ci] != null) {
            int N = ChunkSection.TOTAL;
            for (int sy = 0; sy < sectionCount; sy++) {
                int base = sy * N;
                ChunkSection sec = neighborSections[ci][sy];
                if (sec != null && sec.getLightArray() != null) {
                    System.arraycopy(sec.getLightArray(), 0, target, base, N);
                } else if (sy > neighborTopSection[ci]) {
                    Arrays.fill(target, base, base + N, (short) 15);
                }
            }
        } else if (neighborLight[ci] != null) {
            System.arraycopy(neighborLight[ci], 0, target, 0, target.length);
        }
    }

    public int getWorldX()    { return centerX * chunkSize; }
    public int getWorldZ()    { return centerZ * chunkSize; }
    public int getChunkSize() { return chunkSize; }
    public int getChunkHeight(){ return chunkHeight; }

    @Override
    public Biome getBiome(int worldX, int worldZ) {
        if (biomeProvider == null) return engine.world.biome.Biomes.DEFAULT();
        return biomeProvider.getBiome(worldX, worldZ);
    }
}
