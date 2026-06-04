package com.vanmors.invertedmind.codec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaTransformTest {

    @Test
    void encodeDecodeSortedArray() {
        int[] original = {3, 7, 11, 20, 25, 100};
        int[] values = original.clone();

        DeltaTransform.encode(values, 0, values.length);
        // After encoding: [3, 4, 4, 9, 5, 75]
        assertThat(values).isEqualTo(new int[]{3, 4, 4, 9, 5, 75});

        DeltaTransform.decode(values, 0, values.length);
        assertThat(values).isEqualTo(original);
    }

    @Test
    void singleElement() {
        int[] values = {42};
        DeltaTransform.encode(values, 0, 1);
        assertThat(values).isEqualTo(new int[]{42});
        DeltaTransform.decode(values, 0, 1);
        assertThat(values).isEqualTo(new int[]{42});
    }

    @Test
    void consecutiveDocIds() {
        int[] original = {0, 1, 2, 3, 4, 5};
        int[] values = original.clone();

        DeltaTransform.encode(values, 0, values.length);
        assertThat(values).isEqualTo(new int[]{0, 1, 1, 1, 1, 1});

        DeltaTransform.decode(values, 0, values.length);
        assertThat(values).isEqualTo(original);
    }

    @Test
    void withOffset() {
        int[] values = {99, 10, 20, 30, 99};
        DeltaTransform.encode(values, 1, 3);
        assertThat(values).isEqualTo(new int[]{99, 10, 10, 10, 99});

        DeltaTransform.decode(values, 1, 3);
        assertThat(values).isEqualTo(new int[]{99, 10, 20, 30, 99});
    }
}
