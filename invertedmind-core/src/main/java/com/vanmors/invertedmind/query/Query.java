package com.vanmors.invertedmind.query;

/**
 * Base sealed interface for query AST nodes.
 */
public sealed interface Query permits TermQuery, AndQuery, OrQuery, NotQuery, AdjQuery, NearQuery {
}
