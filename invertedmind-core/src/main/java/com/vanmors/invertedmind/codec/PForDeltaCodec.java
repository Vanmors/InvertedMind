package com.vanmors.invertedmind.codec;

import com.vanmors.invertedmind.util.BitUtil;
import com.vanmors.invertedmind.util.VarIntUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Patched Frame-of-Reference (PForDelta) codec.
 * <p>
 * Operates on blocks of {@link #BLOCK_SIZE} integers. For each block:
 * <ol>
 *     <li>Finds the optimal bit-width that covers ~90% of values</li>
 *     <li>Packs the base values using that bit-width</li>
 *     <li>Stores exceptions (values that don't fit) as patches</li>
 * </ol>
 * <p>
 * Block layout:
 * <pre>
 *   [1 byte: bitsPerValue]
 *   [1 byte: exceptionCount]
 *   [packed base values: bitsPerValue * blockSize / 8 bytes]
 *   [exceptions: for each exception: 1 byte index + VByte overflow value]
 * </pre>
 * <p>
 * Excellent compression ratio for posting lists with mostly small gaps
 * and occasional large ones (common in real-world inverted indexes).
 */
public final class PForDeltaCodec {

    public static final int BLOCK_SIZE = 128;
    private static final double EXCEPTION_RATIO = 0.10; // allow up to 10% exceptions

    public int encode(int[] values, int offset, int length, ByteBuffer out) {
        int startPos = out.position();
        int remaining = length;
        int pos = offset;

        while (remaining > 0) {
            int blockLen = Math.min(remaining, BLOCK_SIZE);
            encodeBlock(values, pos, blockLen, out);
            pos += blockLen;
            remaining -= blockLen;
        }

        return out.position() - startPos;
    }

    private void encodeBlock(int[] values, int offset, int blockLen, ByteBuffer out) {
        // Find optimal bit-width: smallest width that covers >= 90% of values
        int[] sorted = new int[blockLen];
        System.arraycopy(values, offset, sorted, 0, blockLen);
        Arrays.sort(sorted);

        int threshold = (int) Math.ceil(blockLen * (1.0 - EXCEPTION_RATIO));
        int thresholdValue = sorted[Math.min(threshold, blockLen) - 1];
        int bits = Math.max(1, BitUtil.bitsRequired(thresholdValue));
        int mask = (bits == 32) ? -1 : (1 << bits) - 1;

        // Identify exceptions
        int exceptionCount = 0;
        int[] exceptionIndices = new int[blockLen];
        int[] exceptionValues = new int[blockLen];
        int[] baseValues = new int[blockLen];

        for (int i = 0; i < blockLen; i++) {
            int v = values[offset + i];
            if (v > mask) {
                exceptionIndices[exceptionCount] = i;
                exceptionValues[exceptionCount] = v >>> bits; // overflow portion
                baseValues[i] = v & mask; // keep only the low bits
                exceptionCount++;
            } else {
                baseValues[i] = v;
            }
        }

        // Write block header
        out.put((byte) bits);
        out.put((byte) exceptionCount);
        if (blockLen < BLOCK_SIZE) {
            VarIntUtil.writeVInt(out, blockLen);
        }

        // Write packed base values
        BitUtil.pack(baseValues, 0, blockLen, bits, out);

        // Write exceptions
        for (int i = 0; i < exceptionCount; i++) {
            out.put((byte) exceptionIndices[i]);
            VarIntUtil.writeVInt(out, exceptionValues[i]);
        }
    }

    public int decode(ByteBuffer in, int[] out, int offset, int count) {
        int remaining = count;
        int pos = offset;

        while (remaining > 0) {
            int bits = in.get() & 0xFF;
            int exceptionCount = in.get() & 0xFF;
            int blockLen;
            if (remaining < BLOCK_SIZE) {
                blockLen = VarIntUtil.readVInt(in);
            } else {
                blockLen = BLOCK_SIZE;
            }

            // Unpack base values
            BitUtil.unpack(in, out, pos, blockLen, bits);

            // Apply exception patches
            for (int i = 0; i < exceptionCount; i++) {
                int idx = in.get() & 0xFF;
                int overflow = VarIntUtil.readVInt(in);
                out[pos + idx] |= (overflow << bits);
            }

            pos += blockLen;
            remaining -= blockLen;
        }

        return count;
    }

    public int maxCompressedSize(int count) {
        int fullBlocks = count / BLOCK_SIZE;
        int partial = count % BLOCK_SIZE;
        // Worst case per block: 2 header + blockSize*4 packed + blockSize*(1+5) exceptions
        int perBlock = 2 + BLOCK_SIZE * 4 + BLOCK_SIZE * 6;
        int size = fullBlocks * perBlock;
        if (partial > 0) {
            size += 2 + 5 + partial * 4 + partial * 6;
        }
        return size;
    }
}
