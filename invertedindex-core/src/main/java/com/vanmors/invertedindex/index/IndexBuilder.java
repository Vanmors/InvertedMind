package com.vanmors.invertedindex.index;

import com.vanmors.invertedindex.codec.PForDeltaCodec;
import com.vanmors.invertedindex.core.*;
import com.vanmors.invertedindex.storage.SegmentWriter;

import java.nio.file.Path;
import java.util.*;


public final class IndexBuilder {

    private final IndexConfig config;

    private final Analyzer analyzer;

    private final PForDeltaCodec codec;

    private final Map<String, List<Posting>> invertedLists = new HashMap<>();

    private int nextDocId = 0;

    private final List<Integer> docLengths = new ArrayList<>();

    public IndexBuilder(IndexConfig config) {
        this(config, Analyzer.defaultAnalyzer());
    }

    public IndexBuilder(IndexConfig config, Analyzer analyzer) {
        this.config = config;
        this.analyzer = analyzer;
        this.codec = new PForDeltaCodec();
    }


    public int addDocument(String text) {
        List<Token> tokens = analyzer.analyze(text);
        int docId = nextDocId++;
        docLengths.add(tokens.size());

        // Group by term -> positions
        Map<String, List<Integer>> termPositions = new LinkedHashMap<>();
        for (Token t : tokens) {
            termPositions.computeIfAbsent(t.term(), k -> new ArrayList<>())
                    .add(t.position());
        }

        for (var entry : termPositions.entrySet()) {
            int[] positions = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            invertedLists.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                    .add(new Posting(docId, positions.length, positions));
        }

        return docId;
    }


    public int documentCount() {
        return nextDocId;
    }


    public int[] getDocLengths() {
        return docLengths.stream().mapToInt(Integer::intValue).toArray();
    }


    public Map<String, PostingList> buildPostingLists() {
        final String[] terms = invertedLists.keySet().toArray(new String[0]);
        Arrays.sort(terms);
        Map<String, PostingList> result = new LinkedHashMap<>(terms.length * 2);
        for (var term : terms) {
            result.put(term, PostingList.build(invertedLists.get(term), codec, config.skipInterval()));
        }
        return result;
    }


    public CollectionStatistics buildStatistics() {
        long totalTokens = docLengths.stream().mapToLong(Integer::longValue).sum();
        return CollectionStatistics.compute(nextDocId, totalTokens);
    }


    public void writeSegment(Path segmentFile) throws java.io.IOException {
        Map<String, PostingList> postingLists = buildPostingLists();
        CollectionStatistics stats = buildStatistics();
        int[] docLens = getDocLengths();
        SegmentWriter writer = new SegmentWriter(segmentFile, codec);
        writer.write(postingLists, docLens, stats);
    }

    public PForDeltaCodec getCodec() {
        return codec;
    }
}
