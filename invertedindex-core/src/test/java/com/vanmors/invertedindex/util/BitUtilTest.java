package com.vanmors.invertedindex.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class BitUtilTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "2, 2",
            "3, 2",
            "4, 3",
            "7, 3",
            "127, 7",
            "128, 8",
            "255, 8",
            "256, 9",
            "2147483647, 31"  // Integer.MAX_VALUE
    })
    void bitsRequired(int value, int expectedBits) {
        assertThat(BitUtil.bitsRequired(value)).isEqualTo(expectedBits);
    }

    @Test
    void packUnpackRoundtrip() {
        int[] values = {0, 1, 2, 3, 4, 5, 6, 7};
        int bits = 3;

        ByteBuffer buf = ByteBuffer.allocate(BitUtil.packedSize(values.length, bits));
        BitUtil.pack(values, 0, values.length, bits, buf);
        buf.flip();

        int[] decoded = new int[values.length];
        BitUtil.unpack(buf, decoded, 0, values.length, bits);
        assertThat(decoded).isEqualTo(values);
    }

    @Test
    void packUnpackLargeValues() {
        int[] values = {100, 200, 300, 400, 1000, 2000, 0, 1};
        int bits = BitUtil.bitsRequired(BitUtil.maxValue(values, 0, values.length));

        ByteBuffer buf = ByteBuffer.allocate(BitUtil.packedSize(values.length, bits));
        BitUtil.pack(values, 0, values.length, bits, buf);
        buf.flip();

        int[] decoded = new int[values.length];
        BitUtil.unpack(buf, decoded, 0, values.length, bits);
        assertThat(decoded).isEqualTo(values);
    }

    @Test
    void packUnpack32Bits() {
        int[] values = {Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 42};
        int bits = 32;

        ByteBuffer buf = ByteBuffer.allocate(BitUtil.packedSize(values.length, bits));
        BitUtil.pack(values, 0, values.length, bits, buf);
        buf.flip();

        int[] decoded = new int[values.length];
        BitUtil.unpack(buf, decoded, 0, values.length, bits);
        assertThat(decoded).isEqualTo(values);
    }

    @Test
    void packedSizeCalculation() {
        assertThat(BitUtil.packedSize(8, 3)).isEqualTo(3); // 8*3=24 bits = 3 bytes
        assertThat(BitUtil.packedSize(8, 1)).isEqualTo(1); // 8*1=8 bits = 1 byte
        assertThat(BitUtil.packedSize(128, 8)).isEqualTo(128); // 128*8=1024 bits = 128 bytes
    }

    @Test
    void maxValue() {
        assertThat(BitUtil.maxValue(new int[]{1, 5, 3, 2}, 0, 4)).isEqualTo(5);
        assertThat(BitUtil.maxValue(new int[]{0, 0, 0}, 0, 3)).isEqualTo(0);
        assertThat(BitUtil.maxValue(new int[]{10, 20, 30}, 1, 2)).isEqualTo(30);
    }
}
