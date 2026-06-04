package com.vanmors.invertedmind.query;

import java.util.List;

/**
 * Proximity query — terms must appear within a specified distance.
 */
public record NearQuery(List<Query> children, int distance) implements Query {
    public NearQuery {
        if (children == null || children.size() < 2) {
            throw new IllegalArgumentException("NEAR requires at least 2 children");
        }
        if (distance < 1) throw new IllegalArgumentException("distance must be >= 1");
        children = List.copyOf(children);
    }
}
