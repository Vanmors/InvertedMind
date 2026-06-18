package com.vanmors.invertedindex.query;

public final class NotIterator implements PostingListIterator {

    private final PostingListIterator positive;
    private final PostingListIterator negative;
    private int currentDocId = -1;

    public NotIterator(PostingListIterator positive, PostingListIterator negative) {
        this.positive = positive;
        this.negative = negative;
        // Initialize negative to first doc
        this.negative.next();
    }

    @Override
    public int docId() {
        return currentDocId == -1 ? NO_MORE_DOCS : currentDocId;
    }

    @Override
    public int next() {
        int doc = positive.next();
        return skipExcluded(doc);
    }

    @Override
    public int advance(int targetDocId) {
        int doc = positive.advance(targetDocId);
        return skipExcluded(doc);
    }

    private int skipExcluded(int doc) {
        while (doc != NO_MORE_DOCS) {
            // Advance negative to at least doc
            int negDoc = negative.docId();
            if (negDoc < doc && negDoc != NO_MORE_DOCS) {
                negDoc = negative.advance(doc);
            }

            if (negDoc != doc) {
                // doc is not in the negative set — keep it
                currentDocId = doc;
                return doc;
            }

            // doc is excluded, try next
            doc = positive.next();
        }
        currentDocId = NO_MORE_DOCS;
        return NO_MORE_DOCS;
    }

    @Override
    public int termFrequency() {
        return positive.termFrequency();
    }

    @Override
    public int[] positions() {
        return positive.positions();
    }

    @Override
    public long cost() {
        return positive.cost();
    }
}
