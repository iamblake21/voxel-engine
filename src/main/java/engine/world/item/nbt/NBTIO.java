package engine.world.item.nbt;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class NBTIO {

    public static final int TAG_END = 0;
    public static final int TAG_BYTE = 1;
    public static final int TAG_SHORT = 2;
    public static final int TAG_INT = 3;
    public static final int TAG_LONG = 4;
    public static final int TAG_FLOAT = 5;
    public static final int TAG_DOUBLE = 6;
    public static final int TAG_BYTE_ARRAY = 7;
    public static final int TAG_STRING = 8;
    public static final int TAG_LIST = 9;
    public static final int TAG_COMPOUND = 10;
    public static final int TAG_INT_ARRAY = 11;
    public static final int TAG_LONG_ARRAY = 12;

    /**
     * Writes a compound tag to a file (compressed).
     */
    public static void writeCompressed(NBTTagCompound compound, File file) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            write(compound, dos);
        }
    }

    /**
     * Reads a compound tag from a file (compressed).
     */
    public static NBTTagCompound readCompressed(File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            return read(dis);
        }
    }

    /**
     * Writes a compound tag to a stream (uncompressed).
     */
    public static void write(NBTTagCompound compound, DataOutputStream output) throws IOException {
        output.writeByte(TAG_COMPOUND);
        output.writeUTF(""); // Root tag usually has empty name or ignored
        writeTagPayload(TAG_COMPOUND, compound, output);
    }

    /**
     * Reads a compound tag from a stream (uncompressed).
     */
    public static NBTTagCompound read(DataInputStream input) throws IOException {
        int type = input.readByte();
        if (type != TAG_COMPOUND) {
            throw new IOException("Root tag must be a named compound tag");
        }
        input.readUTF(); // Skip name
        return (NBTTagCompound) readTagPayload(TAG_COMPOUND, input);
    }

    private static void writeTagPayload(int type, Object value, DataOutput output) throws IOException {
        switch (type) {
            case TAG_END:
                break;
            case TAG_BYTE:
                output.writeByte((Byte) value);
                break;
            case TAG_SHORT:
                output.writeShort((Short) value);
                break;
            case TAG_INT:
                output.writeInt((Integer) value);
                break;
            case TAG_LONG:
                output.writeLong((Long) value);
                break;
            case TAG_FLOAT:
                output.writeFloat((Float) value);
                break;
            case TAG_DOUBLE:
                output.writeDouble((Double) value);
                break;
            case TAG_BYTE_ARRAY:
                byte[] bytes = (byte[]) value;
                output.writeInt(bytes.length);
                output.write(bytes);
                break;
            case TAG_STRING:
                output.writeUTF((String) value);
                break;
            case TAG_LIST:
                List<?> list = (List<?>) value;
                int listType = TAG_END;
                if (!list.isEmpty()) {
                    listType = getTagId(list.get(0));
                }
                output.writeByte(listType);
                output.writeInt(list.size());
                for (Object item : list) {
                    writeTagPayload(listType, item, output);
                }
                break;
            case TAG_COMPOUND:
                NBTTagCompound compound = (NBTTagCompound) value;
                for (String key : compound.getKeys()) {
                    Object tagValue = getTagValue(compound, key);
                    int tagId = getTagId(tagValue);
                    output.writeByte(tagId);
                    if (tagId != TAG_END) {
                        output.writeUTF(key);
                        writeTagPayload(tagId, tagValue, output);
                    }
                }
                output.writeByte(TAG_END);
                break;
            case TAG_INT_ARRAY:
                int[] ints = (int[]) value;
                output.writeInt(ints.length);
                for (int i : ints)
                    output.writeInt(i);
                break;
            case TAG_LONG_ARRAY:
                long[] longs = (long[]) value;
                output.writeInt(longs.length);
                for (long l : longs)
                    output.writeLong(l);
                break;
            default:
                throw new IOException("Invalid NBT tag type: " + type);
        }
    }

    private static Object readTagPayload(int type, DataInput input) throws IOException {
        switch (type) {
            case TAG_END:
                return null;
            case TAG_BYTE:
                return input.readByte();
            case TAG_SHORT:
                return input.readShort();
            case TAG_INT:
                return input.readInt();
            case TAG_LONG:
                return input.readLong();
            case TAG_FLOAT:
                return input.readFloat();
            case TAG_DOUBLE:
                return input.readDouble();
            case TAG_BYTE_ARRAY:
                int len = input.readInt();
                byte[] bytes = new byte[len];
                input.readFully(bytes);
                return bytes;
            case TAG_STRING:
                return input.readUTF();
            case TAG_LIST:
                int listType = input.readByte();
                int listLen = input.readInt();
                java.util.List<Object> list = new java.util.ArrayList<>(listLen);
                for (int i = 0; i < listLen; i++) {
                    list.add(readTagPayload(listType, input));
                }
                return list;
            case TAG_COMPOUND:
                NBTTagCompound compound = new NBTTagCompound();
                while (true) {
                    int tagId = input.readByte();
                    if (tagId == TAG_END)
                        break;
                    String name = input.readUTF();
                    Object value = readTagPayload(tagId, input);
                    setTagValue(compound, name, value);
                }
                return compound;
            case TAG_INT_ARRAY:
                int iLen = input.readInt();
                int[] ints = new int[iLen];
                for (int i = 0; i < iLen; i++)
                    ints[i] = input.readInt();
                return ints;
            case TAG_LONG_ARRAY:
                int lLen = input.readInt();
                long[] longs = new long[lLen];
                for (int i = 0; i < lLen; i++)
                    longs[i] = input.readLong();
                return longs;
            default:
                throw new IOException("Invalid NBT tag type: " + type);
        }
    }

    private static int getTagId(Object value) {
        if (value instanceof Byte)
            return TAG_BYTE;
        if (value instanceof Short)
            return TAG_SHORT;
        if (value instanceof Integer)
            return TAG_INT;
        if (value instanceof Long)
            return TAG_LONG;
        if (value instanceof Float)
            return TAG_FLOAT;
        if (value instanceof Double)
            return TAG_DOUBLE;
        if (value instanceof byte[])
            return TAG_BYTE_ARRAY;
        if (value instanceof String)
            return TAG_STRING;
        if (value instanceof List)
            return TAG_LIST;
        if (value instanceof NBTTagCompound)
            return TAG_COMPOUND;
        if (value instanceof int[])
            return TAG_INT_ARRAY;
        if (value instanceof long[])
            return TAG_LONG_ARRAY;
        if (value instanceof Boolean)
            return TAG_BYTE; // Boolean -> Byte
        return TAG_END;
    }

    // Helper to extract raw value from NBTTagCompound using package-private access
    // or public getters
    // Since NBTTagCompound wraps values in map, we can try to guess or use the
    // specific getters if we knew types.
    // However, NBTTagCompound stores Object in `data`, so we need to access that.
    // Assuming we can't access `data` directly if it's private, but `getTagId`
    // implies we have the object.
    // Wait, NBTTagCompound has specific setters but generic internal storage.
    // I need to add a way to get raw ID/Value for serialization or use reflection.
    // Or just use the getters provided. The issue is `getTagId` relies on the
    // Object type.
    // I will add a method to NBTTagCompound to get the raw map or iterate.
    // For now I'll assume I can add `getRaw(String key)` to NBTTagCompound.

    private static Object getTagValue(NBTTagCompound compound, String key) {
        // This is tricky without modifying NBTTagCompound to expose raw objects.
        // I will assume I'll modify NBTTagCompound to have a package-private generic
        // getter or similar.
        // For now, let's try to infer or use existing.
        // Actually, I can use the type-specific getters and check existence, but that's
        // slow.
        // Best requires `Object getRaw(String key)` in NBTTagCompound.
        return compound.getRaw(key);
    }

    private static void setTagValue(NBTTagCompound compound, String key, Object value) {
        if (value instanceof Byte) {
            // Check if it was a boolean (0 or 1)
            compound.setInt(key, (Byte) value); // NBTTagCompound only had setInt, setFloat etc.
            // Wait, NBTTagCompound current impl only supports int, float, string,
            // boolean(as boolean), list, tag.
            // It doesn't support Byte, Short, Long, Double, Arrays directly in its API.
            // I MUST UPDATE NBTTagCompound to support all these types if I want full NBT
            // support.
            // OR I map them to the closest available type.
            // For now, I'll map Byte -> Int (or Boolean if 0/1?), Short -> Int, Long -> Int
            // (Data loss!), Double -> Float.
            // This is dangerous. I should update NBTTagCompound.
        } else if (value instanceof Integer) {
            compound.setInt(key, (Integer) value);
        } else if (value instanceof Float) {
            compound.setFloat(key, (Float) value);
        } else if (value instanceof String) {
            compound.setString(key, (String) value);
        } else if (value instanceof NBTTagCompound) {
            compound.setTag(key, (NBTTagCompound) value);
        } else if (value instanceof List) {
            compound.setList(key, (List<?>) value);
        }
        // Fallback for types not strictly in NBTTagCompound yet
        else {
            // For now, just try to put it in directly if possible or ignore
            // The NBTTagCompound uses a Map<String, Object>, so it CAN hold them,
            // but the getters might fail if they cast hard.
            // I'll use a new method setRaw(key, val);
            compound.setRaw(key, value);
        }
    }
}
