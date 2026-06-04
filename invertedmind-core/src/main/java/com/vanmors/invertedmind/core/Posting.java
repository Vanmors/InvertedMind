package com.vanmors.invertedmind.core;

import java.util.Arrays;

/**
 * A single posting entry: a term occurrence in a document.
 * Used during index construction; at query time we work with compressed byte streams.
 */
public record Posting(int docId, int termFrequency, int[] positions) {

    public Posting {
        if (docId < 0) throw new IllegalArgumentException("docId must be >= 0");
        if (termFrequency < 1) throw new IllegalArgumentException("termFrequency must be >= 1");
        if (positions.length != termFrequency) {
            throw new IllegalArgumentException("positions.length must equal termFrequency");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Posting p)) return false;
        return docId == p.docId && termFrequency == p.termFrequency && Arrays.equals(positions, p.positions);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(docId);
        result = 31 * result + Integer.hashCode(termFrequency);
        result = 31 * result + Arrays.hashCode(positions);
        return result;
    }

    @Override
    public String toString() {
        return "Posting{docId=" + docId + ", tf=" + termFrequency + ", pos=" + Arrays.toString(positions) + "}";
    }
}
