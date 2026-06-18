package com.vanmors.invertedindex.query;

public sealed interface Query permits TermQuery, AndQuery, OrQuery, NotQuery, AdjQuery, NearQuery {
}
