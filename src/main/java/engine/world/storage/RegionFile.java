package engine.world.storage;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Region File Format (.mca) implementation.
 * Stores 32x32 chunks (1024 chunks) per file.
 * 
 * Header:
 * - Locations (1024 ints): [offset (24 bits) | sectorCount (8 bits)]
 * - Timestamps (1024 ints): [timestamp (32 bits)]
 * 
 * Sector size: 4 KB (4096 bytes)
 */
public class RegionFile implements AutoCloseable {

    private static final int SECTOR_SIZE = 4096;
    private static final int HEADER_SIZE = 8192; // 4KB locations + 4KB timestamps

    private final Path fileName;
    private FileChannel fileChannel;
    private final IntBuffer locations;
    private final IntBuffer timestamps;
    private final List<Boolean> sectorFree;

    public RegionFile(Path fileName) throws IOException {
        this.fileName = fileName;

        // Ensure directory exists
        if (Files.notExists(fileName.getParent())) {
            Files.createDirectories(fileName.getParent());
        }

        this.fileChannel = FileChannel.open(fileName,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE);

        this.locations = ByteBuffer.allocateDirect(4096).asIntBuffer();
        this.timestamps = ByteBuffer.allocateDirect(4096).asIntBuffer();

        if (fileChannel.size() < HEADER_SIZE) {
            // New file, pad header
            fileChannel.write(ByteBuffer.wrap(new byte[HEADER_SIZE]), 0);
        }

        // Read header
        fileChannel.read(ByteBuffer.wrap(new byte[4096]), 0); // Need to read into buffer correctly
        // Reset and read properly
        ByteBuffer headerOps = ByteBuffer.allocate(8192);
        fileChannel.read(headerOps, 0);
        headerOps.flip();

        IntBuffer headerInts = headerOps.asIntBuffer();

        // Populate locations
        for (int i = 0; i < 1024; i++) {
            locations.put(i, headerInts.get(i));
        }

        // Populate timestamps
        for (int i = 0; i < 1024; i++) {
            timestamps.put(i, headerInts.get(1024 + i));
        }

        // Calculate used sectors
        int fileSize = (int) fileChannel.size();
        int sectorCount = fileSize / SECTOR_SIZE;
        sectorFree = new ArrayList<>(sectorCount);
        for (int i = 0; i < sectorCount; i++)
            sectorFree.add(true);

        sectorFree.set(0, false); // Header location
        sectorFree.set(1, false); // Header timestamp

        for (int i = 0; i < 1024; i++) {
            int loc = locations.get(i);
            if (loc != 0) {
                int offset = loc >> 8;
                int count = loc & 0xFF;
                for (int j = 0; j < count; j++) {
                    if (offset + j < sectorFree.size()) {
                        sectorFree.set(offset + j, false);
                    }
                }
            }
        }
    }

    // Get chunk data input stream
    public DataInputStream getChunkDataInputStream(int x, int z) throws IOException {
        if (outOfBounds(x, z))
            return null;

        int offset = getOffset(x, z);
        if (offset == 0)
            return null; // Chunk not present

        int sectorPos = offset >> 8;
        int numSectors = offset & 0xFF;

        if (sectorPos + numSectors > sectorFree.size())
            return null;

        if (sectorPos + numSectors > sectorFree.size())
            return null;

        // CRITICAL FIX: Use stateless read to allow concurrent access by multiple
        // threads!
        // Do NOT use fileChannel.position() + fileChannel.read() as it is not atomic.
        ByteBuffer buffer = ByteBuffer.allocate(numSectors * SECTOR_SIZE);
        fileChannel.read(buffer, sectorPos * SECTOR_SIZE);
        buffer.flip();

        int length = buffer.getInt(); // Actual length of data
        byte compressionType = buffer.get(); // 1=Gzip, 2=Zlib

        if (length <= 0 || length > buffer.remaining())
            return null;

        byte[] data = new byte[length - 1];
        buffer.get(data);

        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        InputStream is;
        if (compressionType == 1) {
            is = new GZIPInputStream(bis);
        } else if (compressionType == 2) {
            is = new InflaterInputStream(bis);
        } else {
            is = bis; // Uncompressed/Unknown?
        }

        return new DataInputStream(is);
    }

    // Get chunk data output stream
    public DataOutputStream getChunkDataOutputStream(int x, int z) {
        if (outOfBounds(x, z))
            return null;

        return new DataOutputStream(
                new BufferedOutputStream(new DeflaterOutputStream(new RegionFileOutputStream(x, z))));
    }

    private class RegionFileOutputStream extends OutputStream {
        private final int x, z;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public RegionFileOutputStream(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            byte[] compressedData = buffer.toByteArray();
            writeChunk(x, z, compressedData, compressedData.length);
        }
    }

    private synchronized void writeChunk(int x, int z, byte[] data, int length) throws IOException {
        int offsetIndex = getOffsetIndex(x, z);
        int oldLoc = locations.get(offsetIndex);
        int oldSectorPos = oldLoc >> 8;
        int oldSectorCount = oldLoc & 0xFF;

        // Total: Length (4) + Compression (1) + Data
        int totalLength = length + 5;
        int sectorsNeeded = (totalLength + SECTOR_SIZE - 1) / SECTOR_SIZE;

        int newSectorPos;

        if (sectorsNeeded >= 256) {
            return; // Too big
        }

        if (oldSectorPos != 0 && oldSectorCount == sectorsNeeded) {
            // Reuse same sectors
            newSectorPos = oldSectorPos;
        } else {
            // Mark old sectors as free
            for (int i = 0; i < oldSectorCount; i++) {
                if (oldSectorPos + i < sectorFree.size())
                    sectorFree.set(oldSectorPos + i, true);
            }

            // Find new sectors
            newSectorPos = findFreeSectors(sectorsNeeded);
        }

        // Write data
        ByteBuffer buffer = ByteBuffer.allocate(sectorsNeeded * SECTOR_SIZE);
        buffer.putInt(length + 1); // Length includes compression byte
        buffer.put((byte) 2); // Zlib compression
        buffer.put(data, 0, length);
        // Padding is handled by ByteBuffer default 0s if we don't fill it?
        // We allocated exactly needed sectors * size, so remaining is padding.

        buffer.flip();
        fileChannel.write(buffer, newSectorPos * SECTOR_SIZE);

        // Update header
        setOffset(x, z, (newSectorPos << 8) | sectorsNeeded);
        setTimestamp(x, z, (int) (System.currentTimeMillis() / 1000L));

        writeHeader(); // Flush header to disk immediately for safety? Or defer.
    }

    private int findFreeSectors(int count) throws IOException {
        int runStart = -1;
        int runLen = 0;

        // Start checking from sector 2 (0 and 1 are header)
        for (int i = 2; i < sectorFree.size(); i++) {
            if (sectorFree.get(i)) {
                if (runStart == -1)
                    runStart = i;
                runLen++;
                if (runLen >= count) {
                    for (int k = 0; k < count; k++)
                        sectorFree.set(runStart + k, false);
                    return runStart;
                }
            } else {
                runStart = -1;
                runLen = 0;
            }
        }

        // Append to end
        int start = sectorFree.size();
        for (int i = 0; i < count; i++)
            sectorFree.add(false);
        // Ensure file size grows
        // fileChannel.size() will grow on write
        return start;
    }

    private void writeHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8192);
        for (int i = 0; i < 1024; i++)
            header.putInt(locations.get(i));
        for (int i = 0; i < 1024; i++)
            header.putInt(timestamps.get(i));
        header.flip();
        fileChannel.write(header, 0);
    }

    private void setOffset(int x, int z, int offset) {
        locations.put(getOffsetIndex(x, z), offset);
    }

    private void setTimestamp(int x, int z, int timestamp) {
        timestamps.put(getOffsetIndex(x, z), timestamp);
    }

    private boolean outOfBounds(int x, int z) {
        return x < 0 || x >= 32 || z < 0 || z >= 32;
    }

    private int getOffset(int x, int z) {
        return locations.get(getOffsetIndex(x, z));
    }

    private int getOffsetIndex(int x, int z) {
        return (x & 31) + (z & 31) * 32;
    }

    @Override
    public void close() throws IOException {
        writeHeader();
        fileChannel.close();
    }
}
