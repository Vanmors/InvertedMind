package com.vanmors.invertedindex.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vanmors.invertedindex.core.*;
import com.vanmors.invertedindex.index.IndexBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentRoundtripTest {

    @TempDir
    Path tempDir;

    @Test
    void writeAndReadSegment() throws IOException {
        IndexConfig config = IndexConfig.defaults(tempDir);
        IndexBuilder builder = new IndexBuilder(config);

        builder.addDocument("the quick brown fox jumps over the lazy dog");
        builder.addDocument("quick brown dog");
        builder.addDocument("the fox and the dog are friends");
        builder.addDocument("lazy fox sleeps all day");

        Map<String, PostingList> postingLists = builder.buildPostingLists();
        CollectionStatistics stats = builder.buildStatistics();
        int[] docLengths = builder.getDocLengths();

        // Write segment
        Path segmentFile = tempDir.resolve("test.inv");
        SegmentWriter writer = new SegmentWriter(segmentFile, builder.getCodec());
        writer.write(postingLists, docLengths, stats);

        // Read segment back
        try (Segment segment = new Segment(segmentFile)) {
            assertThat(segment.documentCount()).isEqualTo(4);
            assertThat(segment.statistics().totalDocuments()).isEqualTo(4);

            // Verify term dictionary
            TermDictionary dict = segment.dictionary();
            assertThat(dict.size()).isEqualTo(postingLists.size());
            assertThat(dict.lookupTerm("quick")).isGreaterThanOrEqualTo(0);
            assertThat(dict.lookupTerm("fox")).isGreaterThanOrEqualTo(0);
            assertThat(dict.lookupTerm("nonexistent")).isLessThan(0);

            // Verify posting list for "quick"
            PostingList quickPl = segment.getPostingList("quick");
            assertThat(quickPl).isNotNull();
            assertThat(quickPl.documentFrequency()).isEqualTo(2); // docs 0 and 1

            // Verify posting list for "fox"
            PostingList foxPl = segment.getPostingList("fox");
            assertThat(foxPl).isNotNull();
            assertThat(foxPl.documentFrequency()).isEqualTo(3); // docs 0, 2, 3

            // Verify doc lengths
            int[] readLengths = segment.docLengths();
            assertThat(readLengths).hasSize(4);
            assertThat(readLengths).isEqualTo(docLengths);

            // Nonexistent term
            assertThat(segment.getPostingList("xyz")).isNull();
        }
    }

    @Test
    void singleDocSegment() throws IOException {
        IndexConfig config = IndexConfig.defaults(tempDir);
        IndexBuilder builder = new IndexBuilder(config);

        builder.addDocument("hello world");

        Map<String, PostingList> postingLists = builder.buildPostingLists();
        CollectionStatistics stats = builder.buildStatistics();
        int[] docLengths = builder.getDocLengths();

        Path segmentFile = tempDir.resolve("single.inv");
        SegmentWriter writer = new SegmentWriter(segmentFile, builder.getCodec());
        writer.write(postingLists, docLengths, stats);

        try (Segment segment = new Segment(segmentFile)) {
            assertThat(segment.documentCount()).isEqualTo(1);
            PostingList helloPl = segment.getPostingList("hello");
            assertThat(helloPl).isNotNull();
            assertThat(helloPl.documentFrequency()).isEqualTo(1);
        }
    }
}
