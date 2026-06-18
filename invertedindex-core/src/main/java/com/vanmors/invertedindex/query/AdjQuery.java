package com.vanmors.invertedindex.query;

import java.util.List;

public record AdjQuery(List<Query> children) implements Query {
    public AdjQuery {
        if (children == null || children.size() < 2) {
            throw new IllegalArgumentException("ADJ requires at least 2 children");
        }
        children = List.copyOf(children);
    }
}
