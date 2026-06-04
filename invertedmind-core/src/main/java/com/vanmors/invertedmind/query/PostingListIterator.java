package com.vanmors.invertedmind.query;

/**
 * Iterator over a posting list or compound query result.
 * <p>
 * All query operators implement this interface, enabling uniform composition.
 * The iterator is positioned before the first posting initially; call {@link #next()}
 * or {@link #advance(int)} to move to the first (or a specific) document.
 */
public interface PostingListIterator {

    /** Sentinel value indicating exhaustion. */
    int NO_MORE_DOCS = Integer.MAX_VALUE;

    /**
     * Returns the current document ID, or {@link #NO_MORE_DOCS} if exhausted.
     * Must not be called before the first {@link #next()} or {@link #advance(int)}.
     */
    int docId();

    /**
     * Advances to the next document. Returns the new docId or {@link #NO_MORE_DOCS}.
     */
    int next();

    /**
     * Advances to the first document ID >= targetDocId using skip lists.
     * Returns the new docId or {@link #NO_MORE_DOCS}.
     *
     * @param targetDocId must be > current docId
     */
    int advance(int targetDocId);

    /**
     * Term frequency in the current document.
     * For compound iterators, returns the sum of child frequencies.
     */
    int termFrequency();

    /**
     * Positions of the term(s) in the current document.
     * Returns a sorted int array. The caller must not retain the array (it may be reused).
     * For non-positional iterators, returns an empty array.
     */
    int[] positions();

    /**
     * Estimated cost (number of postings). Used for query optimization.
     */
    long cost();
}
