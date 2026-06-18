package com.vanmors.invertedindex.codec;

public final class DeltaTransform {

    private DeltaTransform() {}

    
    public static void encode(int[] values, int offset, int length) {
        for (int i = offset + length - 1; i > offset; i--) {
            values[i] -= values[i - 1];
        }
    }

    
    public static void decode(int[] values, int offset, int length) {
        for (int i = offset + 1; i < offset + length; i++) {
            values[i] += values[i - 1];
        }
    }
}
