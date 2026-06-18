package com.vanmors.invertedindex.scoring;

import com.vanmors.invertedindex.core.CollectionStatistics;

public final class BM25Scorer {

    private final float k1;
    private final float b;

    public BM25Scorer() {
        this(1.2f, 0.75f);
    }

    public BM25Scorer(float k1, float b) {
        this.k1 = k1;
        this.b = b;
    }

    public float score(int termFreq, int docLength, int docFreq, CollectionStatistics stats) {
        double n = stats.totalDocuments();
        double avgDl = stats.averageDocumentLength();

        double idf = Math.log(1.0 + (n - docFreq + 0.5) / (docFreq + 0.5));
        double tfNorm = (termFreq * (k1 + 1.0))
                / (termFreq + k1 * (1.0 - b + b * docLength / avgDl));

        return (float) (idf * tfNorm);
    }
}
