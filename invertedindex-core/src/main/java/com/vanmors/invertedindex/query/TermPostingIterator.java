package com.vanmors.invertedindex.query;

import com.vanmors.invertedindex.codec.DeltaTransform;
import com.vanmors.invertedindex.codec.PForDeltaCodec;
import com.vanmors.invertedindex.core.PostingList;
import com.vanmors.invertedindex.util.VarIntUtil;

import java.nio.ByteBuffer;

public final class TermPostingIterator implements PostingListIterator {

    private final PostingList postingList;
    private final PForDeltaCodec codec;

    // Skip list
    private final int[] skipDocIds;
    private final int[] skipDocIdOffsets;
    private final int[] skipFreqOffsets;
    private final int[] skipPosOffsets;
    private final int[] skipCumulativeCounts;

    // Compressed data views
    private final ByteBuffer docIdBuf;
    private final ByteBuffer freqBuf;
    private final ByteBuffer posBuf;

    // Decode state
    private int currentDocId = -1;
    private int currentFreq = 0;
    private int docsDecoded = 0;
    private final int totalDocs;

    // Block-based docId gap decoding
    private final int skipInterval;
    private final int[] gapBuffer;
    private int gapCursor;
    private int gapsInBuffer;

    // Position decoding (lazy)
    private int positionsRead = 0; // how many docs' positions have been consumed from posBuf

    public TermPostingIterator(PostingList postingList) {
        this.postingList = postingList;
        this.codec = postingList.codec();
        this.skipDocIds = postingList.skipDocIds();
        this.skipDocIdOffsets = postingList.skipDocIdOffsets();
        this.skipFreqOffsets = postingList.skipFreqOffsets();
        this.skipPosOffsets = postingList.skipPosOffsets();
        this.skipCumulativeCounts = postingList.skipCumulativeCounts();
        this.docIdBuf = postingList.compressedDocIds();
        this.freqBuf = postingList.compressedFreqs();
        this.posBuf = postingList.compressedPositions();
        this.totalDocs = postingList.documentFrequency();

        this.skipInterval = skipCumulativeCounts.length > 0 ? skipCumulativeCounts[0] : totalDocs;
        this.gapBuffer = new int[Math.max(skipInterval, 1)];
        this.gapCursor = 0;
        this.gapsInBuffer = 0;
    }

    @Override
    public int docId() {
        return currentDocId == -1 ? NO_MORE_DOCS : currentDocId;
    }

    @Override
    public int next() {
        if (docsDecoded >= totalDocs) {
            currentDocId = NO_MORE_DOCS;
            return NO_MORE_DOCS;
        }

        // Skip positions of current doc if not yet consumed
        skipCurrentPositions();

        // Decode next chunk of docId gaps if buffer exhausted
        if (gapCursor >= gapsInBuffer) {
            int remaining = totalDocs - docsDecoded;
            int count = Math.min(skipInterval, remaining);
            codec.decode(docIdBuf, gapBuffer, 0, count);
            gapCursor = 0;
            gapsInBuffer = count;
        }

        int gap = gapBuffer[gapCursor++];
        if (currentDocId == -1) {
            currentDocId = gap; // first doc: gap IS the docId
        } else {
            currentDocId += gap;
        }

        // Decode frequency (VarInt encoded)
        currentFreq = VarIntUtil.readVInt(freqBuf);
        docsDecoded++;
        positionsRead = 0; // reset for new doc

        return currentDocId;
    }

    @Override
    public int advance(int targetDocId) {
        if (currentDocId >= targetDocId) {
            return currentDocId;
        }

        // Try skip list
        if (skipDocIds.length > 0) {
            int skipIdx = -1;
            for (int i = skipDocIds.length - 1; i >= 0; i--) {
                if (skipDocIds[i] <= targetDocId && skipCumulativeCounts[i] > docsDecoded) {
                    skipIdx = i;
                    break;
                }
            }

            if (skipIdx >= 0) {
                // Jump to skip point: reposition all buffers
                docIdBuf.position(skipDocIdOffsets[skipIdx]);
                freqBuf.position(skipFreqOffsets[skipIdx]);
                posBuf.position(skipPosOffsets[skipIdx]);

                // Decode the chunk starting at the skip point
                int remaining = totalDocs - skipCumulativeCounts[skipIdx];
                int count = Math.min(skipInterval, remaining);
                codec.decode(docIdBuf, gapBuffer, 0, count);

                // First gap in buffer is for the skip point doc itself;
                // we know its docId from the skip list, so skip past it
                gapCursor = 1;
                gapsInBuffer = count;

                currentDocId = skipDocIds[skipIdx];
                currentFreq = VarIntUtil.readVInt(freqBuf);
                docsDecoded = skipCumulativeCounts[skipIdx] + 1;
                positionsRead = 0;

                if (currentDocId >= targetDocId) {
                    return currentDocId;
                }
            }
        }

        // Sequential scan
        while (currentDocId < targetDocId) {
            int doc = next();
            if (doc == NO_MORE_DOCS) return NO_MORE_DOCS;
        }
        return currentDocId;
    }

    @Override
    public int termFrequency() {
        return currentFreq;
    }

    @Override
    public int[] positions() {
        if (positionsRead > 0) {
            return new int[0]; // already consumed
        }
        positionsRead = 1;

        int count = VarIntUtil.readVInt(posBuf);
        int[] pos = new int[count];
        for (int i = 0; i < count; i++) {
            pos[i] = VarIntUtil.readVInt(posBuf);
        }
        // Delta decode positions
        DeltaTransform.decode(pos, 0, count);
        return pos;
    }

    @Override
    public long cost() {
        return totalDocs;
    }

    
    private void skipCurrentPositions() {
        if (docsDecoded > 0 && positionsRead == 0) {
            int count = VarIntUtil.readVInt(posBuf);
            for (int i = 0; i < count; i++) {
                VarIntUtil.readVInt(posBuf);
            }
            positionsRead = 1;
        }
    }
}
