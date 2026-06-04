package com.vanmors.invertedmind.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VarIntUtilTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 127, 128, 255, 256, 16383, 16384, 1_000_000, Integer.MAX_VALUE})
    void roundtripVInt(int value) {
        ByteBuffer buf = ByteBuffer.allocate(5);
        VarIntUtil.writeVInt(buf, value);
        buf.flip();
        assertThat(VarIntUtil.readVInt(buf)).isEqualTo(value);
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void vIntSizeMatchesActualBytes() {
        for (int value : new int[]{0, 1, 127, 128, 16383, 16384, Integer.MAX_VALUE}) {
            ByteBuffer buf = ByteBuffer.allocate(5);
            int written = VarIntUtil.writeVInt(buf, value);
            assertThat(VarIntUtil.vIntSize(value)).isEqualTo(written);
        }
    }

    @Test
    void roundtripVLong() {
        long[] values = {0L, 1L, 127L, 128L, Long.MAX_VALUE, (long) Integer.MAX_VALUE + 1};
        for (long value : values) {
            ByteBuffer buf = ByteBuffer.allocate(9);
            VarIntUtil.writeVLong(buf, value);
            buf.flip();
            assertThat(VarIntUtil.readVLong(buf)).isEqualTo(value);
        }
    }

    @Test
    void roundtripVIntArray() {
        int[] values = {0, 42, 128, 1000, 0, Integer.MAX_VALUE};
        ByteBuffer buf = ByteBuffer.allocate(values.length * 5);
        VarIntUtil.writeVInts(buf, values, 0, values.length);
        buf.flip();

        int[] decoded = new int[values.length];
        VarIntUtil.readVInts(buf, decoded, 0, values.length);
        assertThat(decoded).isEqualTo(values);
    }

    @Test
    void negativeValueThrows() {
        ByteBuffer buf = ByteBuffer.allocate(5);
        assertThatThrownBy(() -> VarIntUtil.writeVInt(buf, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
