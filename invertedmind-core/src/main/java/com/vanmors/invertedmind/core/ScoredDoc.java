package com.vanmors.invertedmind.core;

/**
 * A document with its relevance score.
 */
public record ScoredDoc(int docId, float score) {
}
