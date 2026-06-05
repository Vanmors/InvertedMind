package com.vanmors.invertedmind.query;

public sealed interface Query permits TermQuery, AndQuery, OrQuery, NotQuery, AdjQuery, NearQuery {
}
