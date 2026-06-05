package com.vanmors.invertedmind.core;

public record CollectionStatistics(int totalDocuments, long totalTokens, double averageDocumentLength) {

    public CollectionStatistics {
        if (totalDocuments < 0) throw new IllegalArgumentException("totalDocuments must be >= 0");
        if (totalTokens < 0) throw new IllegalArgumentException("totalTokens must be >= 0");
    }

    public static CollectionStatistics compute(int totalDocuments, long totalTokens) {
        double avgDl = totalDocuments > 0 ? (double) totalTokens / totalDocuments : 0.0;
        return new CollectionStatistics(totalDocuments, totalTokens, avgDl);
    }
}
