package com.vanmors.invertedindex.core;

public record TermInfo(
        int documentFrequency,
        long totalTermFrequency,
        long postingListOffset
) {
    public TermInfo {
        if (documentFrequency < 0) throw new IllegalArgumentException("documentFrequency must be >= 0");
    }
}
