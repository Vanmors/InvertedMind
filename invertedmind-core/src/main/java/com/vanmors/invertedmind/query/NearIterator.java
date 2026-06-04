package com.vanmors.invertedmind.query;

import java.util.Arrays;

/**
 * NEAR iterator: terms must appear within a specified distance in the document.
 * <p>
 * Like ADJ but with a configurable maximum distance between consecutive terms.
 */
public final class NearIterator implements PostingListIterator {

    private final PostingListIterator[] iterators;
    private final int maxDistance;
    private int currentDocId = -1;
    private int[] matchPositions = new int[0];

    public NearIterator(int maxDistance, PostingListIterator... iterators) {
        if (iterators.length < 2) throw new IllegalArgumentException("NEAR requires at least 2 children");
        if (maxDistance < 1) throw new IllegalArgumentException("maxDistance must be >= 1");
        this.iterators = iterators;
        this.maxDistance = maxDistance;
    }

    @Override
    public int docId() {
        return currentDocId == -1 ? NO_MORE_DOCS : currentDocId;
    }

    @Override
    public int next() {
        int target = iterators[0].next();
        return findNextMatch(target);
    }

    @Override
    public int advance(int targetDocId) {
        int target = iterators[0].advance(targetDocId);
        return findNextMatch(target);
    }

    private int findNextMatch(int target) {
        while (target != NO_MORE_DOCS) {
            boolean allMatch = true;
            for (int i = 1; i < iterators.length; i++) {
                int doc = iterators[i].advance(target);
                if (doc == NO_MORE_DOCS) {
                    currentDocId = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                if (doc > target) {
                    target = iterators[0].advance(doc);
                    allMatch = false;
                    break;
                }
            }

            if (allMatch && checkNear()) {
                currentDocId = target;
                return currentDocId;
            }

            if (allMatch) {
                target = iterators[0].next();
            }
        }
        currentDocId = NO_MORE_DOCS;
        return NO_MORE_DOCS;
    }

    private boolean checkNear() {
        int[][] allPos = new int[iterators.length][];
        for (int i = 0; i < iterators.length; i++) {
            allPos[i] = iterators[i].positions();
            if (allPos[i].length == 0) return false;
        }

        // For each position in the first term, check if all subsequent terms
        // have a position within maxDistance of the previous term's position
        for (int startPos : allPos[0]) {
            if (checkNearRecursive(allPos, 1, startPos)) {
                matchPositions = new int[]{startPos};
                return true;
            }
        }
        return false;
    }

    private boolean checkNearRecursive(int[][] allPos, int termIdx, int prevPos) {
        if (termIdx >= allPos.length) return true;

        for (int pos : allPos[termIdx]) {
            int dist = Math.abs(pos - prevPos);
            if (dist >= 1 && dist <= maxDistance) {
                if (checkNearRecursive(allPos, termIdx + 1, pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int termFrequency() {
        return matchPositions.length;
    }

    @Override
    public int[] positions() {
        return matchPositions;
    }

    @Override
    public long cost() {
        long minCost = Long.MAX_VALUE;
        for (PostingListIterator it : iterators) {
            minCost = Math.min(minCost, it.cost());
        }
        return minCost;
    }
}
