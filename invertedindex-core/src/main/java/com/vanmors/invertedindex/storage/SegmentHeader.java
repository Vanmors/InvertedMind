package com.vanmors.invertedindex.storage;

import java.nio.ByteBuffer;

public record SegmentHeader(
        int formatVersion,
        int termCount,
        int documentCount,
        long totalTokens,
        long dictSize,
        long postingsSize,
        long normsSize
) {
    public static final int MAGIC = 0x494E564D;
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
