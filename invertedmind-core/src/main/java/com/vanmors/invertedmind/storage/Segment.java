package com.vanmors.invertedmind.storage;

import com.vanmors.invertedmind.core.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A read-only segment backed by a memory-mapped file.
 * Provides access to the term dictionary, posting lists, and document norms.
 */
public final class Segment implements Closeable {

    private final MmapSegmentReader reader;
    private final TermDictionary dictionary;
    private final int[] docLengths;
    private final CollectionStatistics stats;

    public Segment(Path path) throws IOException {
        this.reader = new MmapSegmentReader(path);
        this.dictionary = reader.loadTermDictionary();
        this.docLengths = reader.loadDocLengths();

        SegmentHeader header = reader.header();
        this.stats = CollectionStatistics.compute(header.documentCount(), header.totalTokens());
    }

    /**
     * Returns the posting list for the given term, or null if the term is not in this segment.
     */
    public PostingList getPostingList(String term) {
        TermInfo info = dictionary.getTermInfo(term);
        if (info == null) return null;
        return reader.readPostingList(info);
    }

    public TermDictionary dictionary() { return dictionary; }
    public int[] docLengths() { return docLengths; }
    public CollectionStatistics statistics() { return stats; }

    public int documentCount() { return reader.header().documentCount(); }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
