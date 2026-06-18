package com.vanmors.invertedindex.util;

import java.nio.ByteBuffer;

public final class VarIntUtil {

    private VarIntUtil() {}

    
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

    
    public static int vIntSize(int value) {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
        int size = 1;
        while (value > 0x7F) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    
    public static int  writeVInts(ByteBuffer buf, int[] values, int offset, int length) {
        int totalBytes = 0;
        for (int i = offset; i < offset + length; i++) {
            totalBytes += writeVInt(buf, values[i]);
        }
        return totalBytes;
    }

    
    public static int readVInts(ByteBuffer buf, int[] out, int offset, int count) {
        for (int i = 0; i < count; i++) {
            out[offset + i] = readVInt(buf);
        }
        return count;
    }
}
