package com.vanmors.invertedmind.storage;

import com.vanmors.invertedmind.codec.PForDeltaCodec;

import com.vanmors.invertedmind.core.*;
import com.vanmors.invertedmind.util.VarIntUtil;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class MmapSegmentReader implements Closeable {

    private final FileChannel channel;
    private final SegmentHeader header;
    private final MappedByteBuffer dictBuffer;
    private final MappedByteBuffer postingsBuffer;
    private final MappedByteBuffer normsBuffer;
    private final PForDeltaCodec codec;

    public MmapSegmentReader(Path segmentFile) throws IOException {
        this.channel = FileChannel.open(segmentFile, StandardOpenOption.READ);

        // Map and parse header
        MappedByteBuffer headerBuf = channel.map(FileChannel.MapMode.READ_ONLY, 0, SegmentHeader.HEADER_SIZE);
        this.header = SegmentHeader.readFrom(headerBuf);
        this.codec = new PForDeltaCodec();

        // Map sections
        this.dictBuffer = channel.map(FileChannel.MapMode.READ_ONLY, header.dictOffset(), header.dictSize());
        this.postingsBuffer = channel.map(FileChannel.MapMode.READ_ONLY, header.postingsOffset(), header.postingsSize());
        this.normsBuffer = channel.map(FileChannel.MapMode.READ_ONLY, header.normsOffset(), header.normsSize());
    }

    public SegmentHeader header() { return header; }
    public PForDeltaCodec codec() { return codec; }

    
    public TermDictionary loadTermDictionary() {
        ByteBuffer dict = dictBuffer.duplicate();
        TermDictionary.Builder builder = TermDictionary.builder(header.termCount());

        // First pass: read terms + df/ttf from dictionary
        String[] terms = new String[header.termCount()];
        int[] dfs = new int[header.termCount()];
        long[] ttfs = new long[header.termCount()];

        for (int i = 0; i < header.termCount(); i++) {
            int termLen = VarIntUtil.readVInt(dict);
            byte[] termBytes = new byte[termLen];
            dict.get(termBytes);
            terms[i] = new String(termBytes, StandardCharsets.UTF_8);
            dfs[i] = VarIntUtil.readVInt(dict);
            ttfs[i] = VarIntUtil.readVLong(dict);
        }

        // Second pass: scan postings buffer to find offsets/lengths for each term
        ByteBuffer postings = postingsBuffer.duplicate();
        for (int i = 0; i < header.termCount(); i++) {
            long offset = postings.position();

            // Skip over: skip list + compressed data
            int skipCount = VarIntUtil.readVInt(postings);
            for (int s = 0; s < skipCount; s++) {
                VarIntUtil.readVInt(postings); // skipDocId
                VarIntUtil.readVInt(postings); // skipDocIdOffset
                VarIntUtil.readVInt(postings); // skipFreqOffset
                VarIntUtil.readVInt(postings); // skipPosOffset
                VarIntUtil.readVInt(postings); // skipCumulativeCount
            }

            int docIdSize = VarIntUtil.readVInt(postings);
            int freqSize = VarIntUtil.readVInt(postings);
            int posSize = VarIntUtil.readVInt(postings);

            // Skip over compressed data
            postings.position(postings.position() + docIdSize + freqSize + posSize);

            builder.add(terms[i], dfs[i], ttfs[i], offset);
        }

        return builder.build();
    }

    
    public PostingList readPostingList(TermInfo termInfo) {
        ByteBuffer buf = postingsBuffer.duplicate();
        buf.position((int) termInfo.postingListOffset());

        int skipCount = VarIntUtil.readVInt(buf);
        int[] skipDocIds = new int[skipCount];
        int[] skipDocIdOffsets = new int[skipCount];
        int[] skipFreqOffsets = new int[skipCount];
        int[] skipPosOffsets = new int[skipCount];
        int[] skipCumulativeCounts = new int[skipCount];

        for (int i = 0; i < skipCount; i++) {
            skipDocIds[i] = VarIntUtil.readVInt(buf);
            skipDocIdOffsets[i] = VarIntUtil.readVInt(buf);
            skipFreqOffsets[i] = VarIntUtil.readVInt(buf);
            skipPosOffsets[i] = VarIntUtil.readVInt(buf);
            skipCumulativeCounts[i] = VarIntUtil.readVInt(buf);
        }

        int docIdSize = VarIntUtil.readVInt(buf);
        int freqSize = VarIntUtil.readVInt(buf);
        int posSize = VarIntUtil.readVInt(buf);

        int dataStart = buf.position();
        ByteBuffer docIdBuf = sliceBuffer(buf, dataStart, docIdSize);
        ByteBuffer freqBuf = sliceBuffer(buf, dataStart + docIdSize, freqSize);
        ByteBuffer posBuf = sliceBuffer(buf, dataStart + docIdSize + freqSize, posSize);

        return new PostingList(
                termInfo.documentFrequency(), termInfo.totalTermFrequency(),
                skipDocIds, skipDocIdOffsets, skipFreqOffsets, skipPosOffsets, skipCumulativeCounts,
                docIdBuf, freqBuf, posBuf, codec);
    }

    
    public int[] loadDocLengths() {
        ByteBuffer norms = normsBuffer.duplicate();
        int[] lengths = new int[header.documentCount()];
        for (int i = 0; i < header.documentCount(); i++) {
            lengths[i] = VarIntUtil.readVInt(norms);
        }
        return lengths;
    }

    private ByteBuffer sliceBuffer(ByteBuffer source, int offset, int size) {
        ByteBuffer dup = source.duplicate();
        dup.position(offset);
        dup.limit(offset + size);
        return dup.slice().asReadOnlyBuffer();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
