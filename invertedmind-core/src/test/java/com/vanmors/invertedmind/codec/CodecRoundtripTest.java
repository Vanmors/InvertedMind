package com.vanmors.invertedmind.codec;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CodecRoundtripTest {

    private final PForDeltaCodec codec = new PForDeltaCodec();

    @Test
    void roundtripSmallValues() {
        assertRoundtrip(new int[]{1, 1, 2, 1, 3, 1, 1, 5, 1, 2});
    }

    @Test
    void roundtripLargeValues() {
        assertRoundtrip(new int[]{1000, 50000, 1, 100000, 42, 7, 999999});
    }

    @Test
    void roundtripSingleValue() {
        assertRoundtrip(new int[]{42});
    }

    @Test
    void roundtripZeros() {
        assertRoundtrip(new int[]{0, 0, 0, 0, 0});
    }

    @Test
    void roundtripExactBlock() {
        // Exactly 128 values (one full block)
        int[] values = new int[128];
        for (int i = 0; i < 128; i++) {
            values[i] = i % 50;
        }
        assertRoundtrip(values);
    }

    @Test
    void roundtripMultipleBlocks() {
        // 300 values — 2 full blocks + partial
        int[] values = new int[300];
        Random rng = new Random(42);
        for (int i = 0; i < 300; i++) {
            values[i] = rng.nextInt(10000);
        }
        assertRoundtrip(values);
    }

    @Test
    void roundtripRealisticDeltaEncodedDocIds() {
        int[] docIds = {5, 103, 257, 300, 489, 1000, 1001, 1500, 2000, 5000};
        DeltaTransform.encode(docIds, 0, docIds.length);
        assertRoundtrip(docIds.clone());
    }

    @Test
    void roundtripWithMixOfSmallAndLargeGaps() {
        int[] values = new int[200];
        Random rng = new Random(123);
        for (int i = 0; i < 200; i++) {
            if (rng.nextInt(10) == 0) {
                values[i] = rng.nextInt(1_000_000);
            } else {
                values[i] = rng.nextInt(100);
            }
        }
        assertRoundtrip(values);
    }

    @Test
    void roundtripMaxIntValues() {
        assertRoundtrip(new int[]{Integer.MAX_VALUE, 0, Integer.MAX_VALUE});
    }

    private void assertRoundtrip(int[] values) {
        ByteBuffer buf = ByteBuffer.allocate(codec.maxCompressedSize(values.length));
        codec.encode(values, 0, values.length, buf);
        buf.flip();

        int[] decoded = new int[values.length];
        codec.decode(buf, decoded, 0, values.length);

        assertThat(decoded)
                .as("PForDelta roundtrip failed")
                .isEqualTo(values);
    }
}
