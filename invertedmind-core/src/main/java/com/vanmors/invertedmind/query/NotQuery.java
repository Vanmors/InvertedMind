package com.vanmors.invertedmind.query;

public record NotQuery(Query child) implements Query {
    public NotQuery {
        if (child == null) throw new IllegalArgumentException("NOT child must not be null");
    }
}
