package com.vanmors.invertedmind.util;

import java.nio.ByteBuffer;

/**
 * Variable-byte integer encoding/decoding.
 * <p>
 * Each byte uses 7 bits for data and the high bit as a continuation flag:
 * high bit = 1 means more bytes follow, high bit = 0 means this is the last byte.
 * Values are encoded in little-endian order (least significant group first).
 */
public final class VarIntUtil {

    private VarIntUtil() {}

    /**
     * Encodes a non-negative int into the buffer using variable-byte encoding.
     * @return number of bytes written (1-5)
     */
    public static int writeVInt(ByteBuffer buf, int value) {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0: " + value);
        int bytesWritten = 0;
        while (value > 0x7F) {
            buf.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
            bytesWritten++;
        }
        buf.put((byte) value);
        return bytesWritten + 1;
    }

    /**
     * Decodes a variable-byte encoded int from the buffer.
     */
    public static int readVInt(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            result |= (b & 0x7F) << shift;
            shift += 7;
            if (shift > 35) throw new IllegalStateException("VInt too long (corrupt data?)");
        } while ((b & 0x80) != 0);
        return result;
    }

    /**
     * Encodes a non-negative long into the buffer using variable-byte encoding.
     * @return number of bytes written (1-9)
     */
    public static int writeVLong(ByteBuffer buf, long value) {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0: " + value);
        int bytesWritten = 0;
        while (value > 0x7FL) {
            buf.put((byte) ((value & 0x7FL) | 0x80L));
            value >>>= 7;
            bytesWritten++;
        }
        buf.put((byte) value);
        return bytesWritten + 1;
    }

    /**
     * Decodes a variable-byte encoded long from the buffer.
     */
    public static long readVLong(ByteBuffer buf) {
        long result = 0;
        int shift = 0;
        byte b;
        do {
            b = buf.get();
            result |= (long) (b & 0x7F) << shift;
            shift += 7;
            if (shift > 63) throw new IllegalStateException("VLong too long (corrupt data?)");
        } while ((b & 0x80) != 0);
        return result;
    }

    /**
     * Returns the number of bytes needed to encode the given value.
     */
    public static int vIntSize(int value) {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
        int size = 1;
        while (value > 0x7F) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    /**
     * Encodes an array of non-negative ints using variable-byte encoding.
     * @return total bytes written
     */
    public static int  writeVInts(ByteBuffer buf, int[] values, int offset, int length) {
        int totalBytes = 0;
        for (int i = offset; i < offset + length; i++) {
            totalBytes += writeVInt(buf, values[i]);
        }
        return totalBytes;
    }

    /**
     * Decodes multiple variable-byte encoded ints from the buffer.
     * @return number of values decoded
     */
    public static int readVInts(ByteBuffer buf, int[] out, int offset, int count) {
        for (int i = 0; i < count; i++) {
            out[offset + i] = readVInt(buf);
        }
        return count;
    }
}
