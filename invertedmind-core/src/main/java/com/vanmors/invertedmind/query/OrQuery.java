package com.vanmors.invertedmind.query;

import java.util.List;

public record OrQuery(List<Query> children) implements Query {
    public OrQuery {
        if (children == null || children.size() < 2) {
            throw new IllegalArgumentException("OR requires at least 2 children");
        }
        children = List.copyOf(children);
    }
}
