package com.vanmors.invertedmind.codec;

/**
 * Delta encoding/decoding for sorted integer arrays.
 * <p>
 * Delta encoding replaces each value with the difference from its predecessor,
 * producing small gaps that compress well. The first value is kept as-is.
 * <p>
 * All operations are in-place to avoid allocations.
 */
public final class DeltaTransform {

    private DeltaTransform() {}

    /**
     * In-place delta encode: values[i] = values[i] - values[i-1].
     * After encoding, values[0] is unchanged, values[1..n-1] are gaps.
     *
     * @param values sorted input array (modified in-place)
     * @param offset start offset
     * @param length number of values to encode
     */
    public static void encode(int[] values, int offset, int length) {
        for (int i = offset + length - 1; i > offset; i--) {
            values[i] -= values[i - 1];
        }
    }

    /**
     * In-place delta decode (prefix sum): values[i] = values[i] + values[i-1].
     * Restores the original sorted values from gaps.
     *
     * @param values delta-encoded input array (modified in-place)
     * @param offset start offset
     * @param length number of values to decode
     */
    public static void decode(int[] values, int offset, int length) {
        for (int i = offset + 1; i < offset + length; i++) {
            values[i] += values[i - 1];
        }
    }
}
