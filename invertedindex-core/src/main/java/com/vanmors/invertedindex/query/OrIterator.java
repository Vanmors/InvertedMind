package com.vanmors.invertedindex.query;

import java.util.*;

public final class OrIterator implements PostingListIterator {

    private final PostingListIterator[] allIterators;
    private final PriorityQueue<PostingListIterator> heap;
    private int currentDocId = -1;

    public OrIterator(PostingListIterator... iterators) {
        if (iterators.length < 2) throw new IllegalArgumentException("OR requires at least 2 children");
        this.allIterators = iterators.clone();
        this.heap = new PriorityQueue<>(iterators.length, Comparator.comparingInt(PostingListIterator::docId));

        // Initialize: advance all iterators to their first doc
        for (PostingListIterator it : this.allIterators) {
            int doc = it.next();
            if (doc != NO_MORE_DOCS) {
                heap.add(it);
            }
        }
    }

    @Override
    public int docId() {
        return currentDocId == -1 ? NO_MORE_DOCS : currentDocId;
    }

    @Override
    public int next() {
        if (heap.isEmpty()) {
            currentDocId = NO_MORE_DOCS;
            return NO_MORE_DOCS;
        }

        // All iterators at the current docId need to be advanced
        // Pop the minimum, that's our next doc
        PostingListIterator top = heap.poll();
        currentDocId = top.docId();

        // Advance this iterator
        int nextDoc = top.next();
        if (nextDoc != NO_MORE_DOCS) {
            heap.add(top);
        }

        // Also advance any other iterators that are at the same docId
        while (!heap.isEmpty() && heap.peek().docId() == currentDocId) {
            PostingListIterator same = heap.poll();
            int nd = same.next();
            if (nd != NO_MORE_DOCS) {
                heap.add(same);
            }
        }

        return currentDocId;
    }

    @Override
    public int advance(int targetDocId) {
        // Advance all iterators in the heap to >= targetDocId
        List<PostingListIterator> toReinsert = new ArrayList<>();
        while (!heap.isEmpty()) {
            PostingListIterator it = heap.poll();
            if (it.docId() < targetDocId) {
                int doc = it.advance(targetDocId);
                if (doc != NO_MORE_DOCS) {
                    toReinsert.add(it);
                }
            } else {
                toReinsert.add(it);
            }
        }
        heap.addAll(toReinsert);

        if (heap.isEmpty()) {
            currentDocId = NO_MORE_DOCS;
            return NO_MORE_DOCS;
        }

        PostingListIterator top = heap.peek();
        currentDocId = top.docId();
        return currentDocId;
    }

    @Override
    public int termFrequency() {
        // Sum TF from all children at current doc
        int sum = 0;
        for (PostingListIterator it : allIterators) {
            if (it.docId() == currentDocId) {
                sum += it.termFrequency();
            }
        }
        return sum;
    }

    @Override
    public int[] positions() {
        return new int[0]; // OR doesn't have meaningful merged positions
    }

    @Override
    public long cost() {
        long total = 0;
        for (PostingListIterator it : allIterators) {
            total += it.cost();
        }
        return total;
    }
}
