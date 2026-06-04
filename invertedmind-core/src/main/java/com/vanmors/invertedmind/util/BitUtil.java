package com.vanmors.invertedmind.util;

import java.nio.ByteBuffer;

/**
 * Bit-level packing and unpacking utilities for integer arrays.
 */
public final class BitUtil {

    private BitUtil() {}

    /**
     * Returns the minimum number of bits needed to represent the given value.
     * Returns 0 for value 0 (but callers should use at least 1 bit).
     */
    public static int bitsRequired(int value) {
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
        return 32 - Integer.numberOfLeadingZeros(value);
    }

    /**
     * Returns the maximum value in the array, or 0 if the array is empty.
     */
    public static int maxValue(int[] values, int offset, int length) {
        int max = 0;
        for (int i = offset; i < offset + length; i++) {
            if (values[i] > max) max = values[i];
        }
        return max;
    }

    /**
     * Packs an array of integers using the specified number of bits per value.
     * Values are packed into bytes in little-endian bit order.
     *
     * @param values       source array
     * @param offset       start offset in source
     * @param length       number of values to pack
     * @param bitsPerValue bits allocated per value (1-32)
     * @param out          output buffer
     * @return number of bytes written
     */
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

    /**
     * Unpacks integers from a bit-packed byte stream.
     *
     * @param in           input buffer
     * @param out          output array
     * @param offset       start offset in output
     * @param length       number of values to unpack
     * @param bitsPerValue bits per value used during packing
     * @return number of values unpacked
     */
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

    /**
     * Returns the number of bytes needed to pack the given number of values at the given bit width.
     */
    public static int packedSize(int count, int bitsPerValue) {
        return (count * bitsPerValue + 7) / 8;
    }
}
