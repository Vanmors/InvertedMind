package com.vanmors.invertedmind.query;

import java.util.List;

/**
 * Adjacent terms query — terms must appear at consecutive positions.
 */
public record AdjQuery(List<Query> children) implements Query {
    public AdjQuery {
        if (children == null || children.size() < 2) {
            throw new IllegalArgumentException("ADJ requires at least 2 children");
        }
        children = List.copyOf(children);
    }
}
