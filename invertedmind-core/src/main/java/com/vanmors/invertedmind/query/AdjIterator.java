package com.vanmors.invertedmind.query;

import java.util.Arrays;
import java.util.Comparator;

public final class AdjIterator implements PostingListIterator {

    private final PostingListIterator[] iterators;
    private int currentDocId = -1;
    private int[] matchPositions = new int[0]; // positions of the first term in matching adjacencies

    public AdjIterator(PostingListIterator... iterators) {
        if (iterators.length < 2) throw new IllegalArgumentException("ADJ requires at least 2 children");
        this.iterators = iterators;
    }

    @Override
    public int docId() {
        return currentDocId == -1 ? NO_MORE_DOCS : currentDocId;
    }

    @Override
    public int next() {
        // Start from the first iterator
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
            // Align all iterators to the same doc
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

            if (allMatch && checkAdjacent()) {
                currentDocId = target;
                return currentDocId;
            }

            if (allMatch) {
                // Positions didn't match; try next doc
                target = iterators[0].next();
            }
        }
        currentDocId = NO_MORE_DOCS;
        return NO_MORE_DOCS;
    }

    private boolean checkAdjacent() {
        // Get positions from all children
        int[][] allPos = new int[iterators.length][];
        for (int i = 0; i < iterators.length; i++) {
            allPos[i] = iterators[i].positions();
            if (allPos[i].length == 0) return false;
        }

        // Find positions in allPos[0] where allPos[0][j] + 1 is in allPos[1],
        // allPos[0][j] + 2 is in allPos[2], etc.
        // We do this with a merge-scan approach.
        for (int startPos : allPos[0]) {
            boolean match = true;
            for (int k = 1; k < allPos.length; k++) {
                int expectedPos = startPos + k;
                if (Arrays.binarySearch(allPos[k], expectedPos) < 0) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matchPositions = new int[]{startPos};
                return true;
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
