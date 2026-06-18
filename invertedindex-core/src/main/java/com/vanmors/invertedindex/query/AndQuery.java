package com.vanmors.invertedindex.query;

import java.util.List;

public record AndQuery(List<Query> children) implements Query {
    public AndQuery {
        if (children == null || children.size() < 2) {
            throw new IllegalArgumentException("AND requires at least 2 children");
        }
        children = List.copyOf(children);
    }
}
