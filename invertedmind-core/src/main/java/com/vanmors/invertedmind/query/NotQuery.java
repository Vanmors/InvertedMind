package com.vanmors.invertedmind.query;

/**
 * NOT query — only valid as a child of an AND query.
 */
public record NotQuery(Query child) implements Query {
    public NotQuery {
        if (child == null) throw new IllegalArgumentException("NOT child must not be null");
    }
}
