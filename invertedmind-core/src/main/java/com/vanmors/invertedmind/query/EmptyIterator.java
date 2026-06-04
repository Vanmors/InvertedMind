package com.vanmors.invertedmind.query;

/**
 * An iterator that yields no documents.
 * Returned when a query term is not found in the index.
 */
public final class EmptyIterator implements PostingListIterator {

    @Override
    public int docId() {
        return NO_MORE_DOCS;
    }

    @Override
    public int next() {
        return NO_MORE_DOCS;
    }

    @Override
    public int advance(int targetDocId) {
        return NO_MORE_DOCS;
    }

    @Override
    public int termFrequency() {
        return 0;
    }

    @Override
    public int[] positions() {
        return new int[0];
    }

    @Override
    public long cost() {
        return 0;
    }
}
