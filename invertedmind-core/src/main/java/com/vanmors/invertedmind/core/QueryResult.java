package com.vanmors.invertedmind.core;

import java.util.List;

/**
 * Search result: a ranked list of scored documents.
 */
public record QueryResult(List<ScoredDoc> results, int totalHits) {

    public QueryResult {
        results = List.copyOf(results);
    }
}
