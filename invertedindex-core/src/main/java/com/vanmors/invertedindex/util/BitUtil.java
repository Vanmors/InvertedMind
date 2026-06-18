package com.vanmors.invertedindex.util;

import java.nio.ByteBuffer;

public final class BitUtil {

    private BitUtil() {}

    
    public static int bitsRequired(int value) {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    
    public static int maxValue(int[] values, int offset, int length) {
        int max = 0;
        for (int i = offset; i < offset + length; i++) {
            if (values[i] > max) max = values[i];
        }
        return max;
    }

    
    public static int pack(int[] values, int offset, int length, int bitsPerValue, ByteBuffer out) {
        if (bitsPerValue < 1 || bitsPerValue > 32) {
            throw new IllegalArgumentException("bitsPerValue must be 1-32: " + bitsPerValue);
        }

        int startPos = out.position();
        long buffer = 0;
        int bitsInBuffer = 0;

        for (int i = offset; i < offset + length; i++) {
            buffer |= ((long) values[i] & ((1L << bitsPerValue) - 1)) << bitsInBuffer;
            bitsInBuffer += bitsPerValue;
            while (bitsInBuffer >= 8) {
                out.put((byte) (buffer & 0xFF));
                buffer >>>= 8;
                bitsInBuffer -= 8;
            }
        }
        // Flush remaining bits
        if (bitsInBuffer > 0) {
            out.put((byte) (buffer & 0xFF));
        }

        return out.position() - startPos;
    }

    
    public static int unpack(ByteBuffer in, int[] out, int offset, int length, int bitsPerValue) {
        if (bitsPerValue < 1 || bitsPerValue > 32) {
            throw new IllegalArgumentException("bitsPerValue must be 1-32: " + bitsPerValue);
        }

        long mask = (1L << bitsPerValue) - 1;
        long buffer = 0;
        int bitsInBuffer = 0;

        for (int i = 0; i < length; i++) {
            while (bitsInBuffer < bitsPerValue) {
                buffer |= ((long) (in.get() & 0xFF)) << bitsInBuffer;
                bitsInBuffer += 8;
            }
            out[offset + i] = (int) (buffer & mask);
            buffer >>>= bitsPerValue;
            bitsInBuffer -= bitsPerValue;
        }

        return length;
    }

    
    public static int packedSize(int count, int bitsPerValue) {
        return (count * bitsPerValue + 7) / 8;
    }
}
