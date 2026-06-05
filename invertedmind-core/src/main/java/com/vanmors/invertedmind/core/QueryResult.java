package com.vanmors.invertedmind.core;

import java.util.List;

public record QueryResult(List<ScoredDoc> results, int totalHits) {

    public QueryResult {
        results = List.copyOf(results);
    }
}
