package com.vanmors.invertedmind.query;

import java.util.Arrays;
import java.util.Comparator;

public final class AndIterator implements PostingListIterator {

    private final PostingListIterator[] iterators;
    private int currentDocId = -1;

    public AndIterator(PostingListIterator... iterators) {
        if (iterators.length < 2) throw new IllegalArgumentException("AND requires at least 2 children");
        // Sort by cost ascending — cheapest leads
        this.iterators = iterators.clone();
        Arrays.sort(this.iterators, Comparator.comparingLong(PostingListIterator::cost));
    }

    @Override
    public int docId() {
        return currentDocId == -1 ? NO_MORE_DOCS : currentDocId;
    }

    @Override
    public int next() {
        int target = iterators[0].next();
        return advanceToCommon(target);
    }

    @Override
    public int advance(int targetDocId) {
        int target = iterators[0].advance(targetDocId);
        return advanceToCommon(target);
    }

    private int advanceToCommon(int target) {
        while (target != NO_MORE_DOCS) {
            boolean allMatch = true;
            for (int i = 1; i < iterators.length; i++) {
                int doc = iterators[i].advance(target);
                if (doc == NO_MORE_DOCS) {
                    currentDocId = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                if (doc > target) {
                    // Mismatch: advance lead to this higher doc
                    target = iterators[0].advance(doc);
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                currentDocId = target;
                return currentDocId;
            }
        }
        currentDocId = NO_MORE_DOCS;
        return NO_MORE_DOCS;
    }

    @Override
    public int termFrequency() {
        int sum = 0;
        for (PostingListIterator it : iterators) {
            sum += it.termFrequency();
        }
        return sum;
    }

    @Override
    public int[] positions() {
        // Merge positions from all children
        int totalLen = 0;
        int[][] childPos = new int[iterators.length][];
        for (int i = 0; i < iterators.length; i++) {
            childPos[i] = iterators[i].positions();
            totalLen += childPos[i].length;
        }
        int[] merged = new int[totalLen];
        int off = 0;
        for (int[] cp : childPos) {
            System.arraycopy(cp, 0, merged, off, cp.length);
            off += cp.length;
        }
        Arrays.sort(merged);
        return merged;
    }

    @Override
    public long cost() {
        // Cost is the minimum child cost (the lead iterator)
        return iterators[0].cost();
    }

}
