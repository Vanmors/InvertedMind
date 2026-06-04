package com.vanmors.invertedmind.storage;

import java.nio.ByteBuffer;

/**
 * Binary segment file header (44 bytes).
 * <p>
 * Sections are stored contiguously: [Header][Dict][Postings][Norms].
 * Offsets are derived from sizes at read time.
 */
public record SegmentHeader(
        int formatVersion,
        int termCount,
        int documentCount,
        long totalTokens,
        long dictSize,
        long postingsSize,
        long normsSize
) {
    public static final int MAGIC = 0x494E564D; // "INVM"
    // 4 (magic) + 4 (version) + 4 (termCount) + 4 (docCount) + 8 (totalTokens)
    // + 8 (dictSize) + 8 (postingsSize) + 8 (normsSize) = 48
    public static final int HEADER_SIZE = 48;
    public static final int CURRENT_VERSION = 1;

    public long dictOffset()     { return HEADER_SIZE; }
    public long postingsOffset() { return HEADER_SIZE + dictSize; }
    public long normsOffset()    { return HEADER_SIZE + dictSize + postingsSize; }

    public void writeTo(ByteBuffer buf) {
        buf.putInt(MAGIC);
        buf.putInt(formatVersion);
        buf.putInt(termCount);
        buf.putInt(documentCount);
        buf.putLong(totalTokens);
        buf.putLong(dictSize);
        buf.putLong(postingsSize);
        buf.putLong(normsSize);
    }

    public static SegmentHeader readFrom(ByteBuffer buf) {
        int magic = buf.getInt();
        if (magic != MAGIC) {
            throw new IllegalStateException("Invalid segment file: bad magic 0x" + Integer.toHexString(magic));
        }
        int version = buf.getInt();
        int termCount = buf.getInt();
        int docCount = buf.getInt();
        long totalTokens = buf.getLong();
        long dictSize = buf.getLong();
        long postingsSize = buf.getLong();
        long normsSize = buf.getLong();

        return new SegmentHeader(version, termCount, docCount, totalTokens,
                dictSize, postingsSize, normsSize);
    }
}
