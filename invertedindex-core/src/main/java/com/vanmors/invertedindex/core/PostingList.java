package com.vanmors.invertedindex.core;

import com.vanmors.invertedindex.codec.DeltaTransform;
import com.vanmors.invertedindex.codec.PForDeltaCodec;
import com.vanmors.invertedindex.util.VarIntUtil;

import java.nio.ByteBuffer;

public final class PostingList {

    private final int documentFrequency;
    private final long totalTermFrequency;

    // Skip list
    private final int[] skipDocIds;       // sampled docIds at skip points
    private final int[] skipDocIdOffsets;  // byte offset into compressedDocIds
    private final int[] skipFreqOffsets;   // byte offset into compressedFreqs
    private final int[] skipPosOffsets;    // byte offset into compressedPositions
    private final int[] skipCumulativeCounts; // cumulative doc count at each skip point

    // Compressed data
    private final ByteBuffer compressedDocIds;
    private final ByteBuffer compressedFreqs;
    private final ByteBuffer compressedPositions;

    private final PForDeltaCodec codec;

    public PostingList(int documentFrequency, long totalTermFrequency,
                       int[] skipDocIds, int[] skipDocIdOffsets,
                       int[] skipFreqOffsets, int[] skipPosOffsets,
                       int[] skipCumulativeCounts,
                       ByteBuffer compressedDocIds,
                       ByteBuffer compressedFreqs,
                       ByteBuffer compressedPositions,
                       PForDeltaCodec codec) {
        this.documentFrequency = documentFrequency;
        this.totalTermFrequency = totalTermFrequency;
        this.skipDocIds = skipDocIds;
        this.skipDocIdOffsets = skipDocIdOffsets;
        this.skipFreqOffsets = skipFreqOffsets;
        this.skipPosOffsets = skipPosOffsets;
        this.skipCumulativeCounts = skipCumulativeCounts;
        this.compressedDocIds = compressedDocIds;
        this.compressedFreqs = compressedFreqs;
        this.compressedPositions = compressedPositions;
        this.codec = codec;
    }

    public int documentFrequency() { return documentFrequency; }
    public long totalTermFrequency() { return totalTermFrequency; }

    public int[] skipDocIds() { return skipDocIds; }
    public int[] skipDocIdOffsets() { return skipDocIdOffsets; }
    public int[] skipFreqOffsets() { return skipFreqOffsets; }
    public int[] skipPosOffsets() { return skipPosOffsets; }
    public int[] skipCumulativeCounts() { return skipCumulativeCounts; }

    public ByteBuffer compressedDocIds() { return compressedDocIds.duplicate(); }
    public ByteBuffer compressedFreqs() { return compressedFreqs.duplicate(); }
    public ByteBuffer compressedPositions() { return compressedPositions.duplicate(); }

    public PForDeltaCodec codec() { return codec; }

    
    public static PostingList build(java.util.List<Posting> postings, PForDeltaCodec codec, int skipInterval) {
        int df = postings.size();
        if (df == 0) {
            return new PostingList(0, 0,
                    new int[0], new int[0], new int[0], new int[0], new int[0],
                    ByteBuffer.allocate(0), ByteBuffer.allocate(0), ByteBuffer.allocate(0),
                    codec);
        }

        // Compute skip interval: auto = sqrt(df)
        int skip = skipInterval > 0 ? skipInterval : Math.max(1, (int) Math.sqrt(df));

        long totalTf = 0;
        int[] docIds = new int[df];
        int[] freqs = new int[df];

        for (int i = 0; i < df; i++) {
            Posting p = postings.get(i);
            docIds[i] = p.docId();
            freqs[i] = p.termFrequency();
            totalTf += p.termFrequency();
        }

        // Build skip list
        int skipCount = (df - 1) / skip; // number of skip points (not counting the start)
        int[] skipDocIdsArr = new int[skipCount];
        int[] skipCumCounts = new int[skipCount];
        for (int i = 0; i < skipCount; i++) {
            int idx = (i + 1) * skip;
            skipDocIdsArr[i] = docIds[idx];
            skipCumCounts[i] = idx;
        }

        // Delta-encode docIds
        int[] docIdGaps = docIds.clone();
        DeltaTransform.encode(docIdGaps, 0, df);

        // Encode docId gaps in chunks aligned with skip points
        ByteBuffer docIdBuf = ByteBuffer.allocate(codec.maxCompressedSize(df));
        int[] skipDocIdOffs = new int[skipCount];

        int chunkStart = 0;
        int skipIdx = 0;
        for (int i = 0; i < skipCount; i++) {
            int chunkEnd = (i + 1) * skip;
            int chunkLen = chunkEnd - chunkStart;
            codec.encode(docIdGaps, chunkStart, chunkLen, docIdBuf);
            chunkStart = chunkEnd;
            skipDocIdOffs[i] = docIdBuf.position();
        }
        // Encode remaining
        if (chunkStart < df) {
            codec.encode(docIdGaps, chunkStart, df - chunkStart, docIdBuf);
        }
        docIdBuf.flip();

        // Encode freqs in same chunks (VarInt encoding)
        ByteBuffer freqBuf = ByteBuffer.allocate(df * 5); // VByte worst case
        int[] skipFreqOffs = new int[skipCount];

        chunkStart = 0;
        for (int i = 0; i < skipCount; i++) {
            int chunkEnd = (i + 1) * skip;
            int chunkLen = chunkEnd - chunkStart;
            com.vanmors.invertedindex.util.VarIntUtil.writeVInts(freqBuf, freqs, chunkStart, chunkLen);
            chunkStart = chunkEnd;
            skipFreqOffs[i] = freqBuf.position();
        }
        if (chunkStart < df) {
            com.vanmors.invertedindex.util.VarIntUtil.writeVInts(freqBuf, freqs, chunkStart, df - chunkStart);
        }
        freqBuf.flip();

        // Encode positions: for each doc, delta-encode positions, then VByte
        ByteBuffer posBuf = ByteBuffer.allocate((int) (totalTf * 5 + df * 5));
        int[] skipPosOffs = new int[skipCount];

        for (int i = 0; i < df; i++) {
            // Record skip point offsets
            for (int s = 0; s < skipCount; s++) {
                if (skipCumCounts[s] == i && skipPosOffs[s] == 0 && i > 0) {
                    skipPosOffs[s] = posBuf.position();
                }
            }

            Posting p = postings.get(i);
            int[] pos = p.positions().clone();
            // Delta encode positions
            com.vanmors.invertedindex.codec.DeltaTransform.encode(pos, 0, pos.length);
            com.vanmors.invertedindex.util.VarIntUtil.writeVInt(posBuf, pos.length);
            for (int v : pos) {
                com.vanmors.invertedindex.util.VarIntUtil.writeVInt(posBuf, v);
            }
        }
        // Fix skip position offsets for skip points at boundaries
        for (int s = 0; s < skipCount; s++) {
            if (skipPosOffs[s] == 0 && skipCumCounts[s] > 0) {
                // Recalculate by scanning
                int targetDoc = skipCumCounts[s];
                int off = 0;
                ByteBuffer scan = posBuf.duplicate();
                scan.flip();
                for (int d = 0; d < targetDoc; d++) {
                    int cnt = com.vanmors.invertedindex.util.VarIntUtil.readVInt(scan);
                    for (int j = 0; j < cnt; j++) {
                        com.vanmors.invertedindex.util.VarIntUtil.readVInt(scan);
                    }
                }
                skipPosOffs[s] = scan.position();
            }
        }
        posBuf.flip();

        return new PostingList(df, totalTf,
                skipDocIdsArr, skipDocIdOffs, skipFreqOffs, skipPosOffs, skipCumCounts,
                compactBuffer(docIdBuf), compactBuffer(freqBuf), compactBuffer(posBuf),
                codec);
    }

    private static ByteBuffer compactBuffer(ByteBuffer buf) {
        ByteBuffer compact = ByteBuffer.allocate(buf.remaining());
        compact.put(buf);
        compact.flip();
        return compact;
    }
}
