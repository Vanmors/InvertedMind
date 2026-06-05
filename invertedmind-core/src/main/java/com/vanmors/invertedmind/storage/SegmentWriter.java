package com.vanmors.invertedmind.storage;

import com.vanmors.invertedmind.codec.PForDeltaCodec;
import com.vanmors.invertedmind.core.CollectionStatistics;
import com.vanmors.invertedmind.core.PostingList;
import com.vanmors.invertedmind.util.VarIntUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class SegmentWriter {

    private final Path outputPath;
    private final PForDeltaCodec codec;

    public SegmentWriter(Path outputPath, PForDeltaCodec codec) {
        this.outputPath = outputPath;
        this.codec = codec;
    }

    
    public void write(Map<String, PostingList> postingLists, int[] docLengths,
                      CollectionStatistics stats) throws IOException {
        try (FileChannel channel = FileChannel.open(outputPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            // Reserve space for header
            channel.position(SegmentHeader.HEADER_SIZE);

            // Write sections contiguously: [Dict][Postings][Norms]
            long dictSize = writeTermDictionary(channel, postingLists);
            long postingsSize = writePostingLists(channel, postingLists);
            long normsSize = writeDocNorms(channel, docLengths);

            // Write header at position 0
            SegmentHeader header = new SegmentHeader(
                    SegmentHeader.CURRENT_VERSION,
                    postingLists.size(),
                    stats.totalDocuments(),
                    stats.totalTokens(),
                    dictSize, postingsSize, normsSize
            );

            ByteBuffer headerBuf = ByteBuffer.allocate(SegmentHeader.HEADER_SIZE);
            header.writeTo(headerBuf);
            headerBuf.flip();
            channel.position(0);
            channel.write(headerBuf);
        }
    }

    private long writeTermDictionary(FileChannel channel, Map<String, PostingList> postingLists)
            throws IOException {
        long startPos = channel.position();

        ByteBuffer buf = ByteBuffer.allocate(8192);

        for (var entry : postingLists.entrySet()) {
            String term = entry.getKey();
            PostingList pl = entry.getValue();
            byte[] termBytes = term.getBytes(StandardCharsets.UTF_8);

            // Ensure buffer capacity
            int needed = 5 + termBytes.length + 5 + 9;
            if (buf.remaining() < needed) {
                buf.flip();
                channel.write(buf);
                buf.clear();
            }

            VarIntUtil.writeVInt(buf, termBytes.length);
            buf.put(termBytes);
            VarIntUtil.writeVInt(buf, pl.documentFrequency());
            VarIntUtil.writeVLong(buf, pl.totalTermFrequency());
        }

        buf.flip();
        if (buf.hasRemaining()) {
            channel.write(buf);
        }

        return channel.position() - startPos;
    }

    private long writePostingLists(FileChannel channel, Map<String, PostingList> postingLists)
            throws IOException {
        long startPos = channel.position();

        for (var entry : postingLists.entrySet()) {
            PostingList pl = entry.getValue();

            // Write skip list metadata
            int skipCount = pl.skipDocIds().length;
            ByteBuffer meta = ByteBuffer.allocate(20 + 25 * skipCount);
            VarIntUtil.writeVInt(meta, skipCount);

            for (int i = 0; i < pl.skipDocIds().length; i++) {
                VarIntUtil.writeVInt(meta, pl.skipDocIds()[i]);
                VarIntUtil.writeVInt(meta, pl.skipDocIdOffsets()[i]);
                VarIntUtil.writeVInt(meta, pl.skipFreqOffsets()[i]);
                VarIntUtil.writeVInt(meta, pl.skipPosOffsets()[i]);
                VarIntUtil.writeVInt(meta, pl.skipCumulativeCounts()[i]);
            }

            // Write compressed data sizes
            ByteBuffer docIds = pl.compressedDocIds();
            ByteBuffer freqs = pl.compressedFreqs();
            ByteBuffer positions = pl.compressedPositions();

            VarIntUtil.writeVInt(meta, docIds.remaining());
            VarIntUtil.writeVInt(meta, freqs.remaining());
            VarIntUtil.writeVInt(meta, positions.remaining());

            meta.flip();
            channel.write(meta);

            // Write compressed data
            channel.write(docIds);
            channel.write(freqs);
            channel.write(positions);
        }

        return channel.position() - startPos;
    }

    private long writeDocNorms(FileChannel channel, int[] docLengths) throws IOException {
        long startPos = channel.position();

        ByteBuffer buf = ByteBuffer.allocate(docLengths.length * 5);
        for (int len : docLengths) {
            VarIntUtil.writeVInt(buf, len);
        }
        buf.flip();
        channel.write(buf);

        return channel.position() - startPos;
    }
}
