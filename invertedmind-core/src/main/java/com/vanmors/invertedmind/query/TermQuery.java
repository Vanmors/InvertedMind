package com.vanmors.invertedmind.query;

public record TermQuery(String term) implements Query {
    public TermQuery {
        if (term == null || term.isEmpty()) throw new IllegalArgumentException("term must not be empty");
    }
}
