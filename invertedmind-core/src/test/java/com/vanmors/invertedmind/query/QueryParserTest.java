package com.vanmors.invertedmind.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QueryParserTest {

    @Test
    void parseSingleTerm() {
        Query q = QueryParser.parse("cat");
        assertThat(q).isInstanceOf(TermQuery.class);
        assertThat(((TermQuery) q).term()).isEqualTo("cat");
    }

    @Test
    void parseAnd() {
        Query q = QueryParser.parse("cat AND dog");
        assertThat(q).isInstanceOf(AndQuery.class);
        AndQuery and = (AndQuery) q;
        assertThat(and.children()).hasSize(2);
        assertThat(((TermQuery) and.children().get(0)).term()).isEqualTo("cat");
        assertThat(((TermQuery) and.children().get(1)).term()).isEqualTo("dog");
    }

    @Test
    void parseOr() {
        Query q = QueryParser.parse("cat OR dog");
        assertThat(q).isInstanceOf(OrQuery.class);
    }

    @Test
    void parseAndNot() {
        Query q = QueryParser.parse("cat AND NOT dog");
        assertThat(q).isInstanceOf(AndQuery.class);
        AndQuery and = (AndQuery) q;
        assertThat(and.children()).hasSize(2);
        assertThat(and.children().get(1)).isInstanceOf(NotQuery.class);
    }

    @Test
    void parsePhrase() {
        Query q = QueryParser.parse("\"quick brown fox\"");
        assertThat(q).isInstanceOf(AdjQuery.class);
        AdjQuery adj = (AdjQuery) q;
        assertThat(adj.children()).hasSize(3);
    }

    @Test
    void parseAdj() {
        Query q = QueryParser.parse("quick ADJ brown");
        assertThat(q).isInstanceOf(AdjQuery.class);
    }

    @Test
    void parseNear() {
        Query q = QueryParser.parse("cat NEAR/3 dog");
        assertThat(q).isInstanceOf(NearQuery.class);
        NearQuery near = (NearQuery) q;
        assertThat(near.distance()).isEqualTo(3);
        assertThat(near.children()).hasSize(2);
    }

    @Test
    void parseParentheses() {
        Query q = QueryParser.parse("(cat OR dog) AND fish");
        assertThat(q).isInstanceOf(AndQuery.class);
        AndQuery and = (AndQuery) q;
        assertThat(and.children().get(0)).isInstanceOf(OrQuery.class);
        assertThat(and.children().get(1)).isInstanceOf(TermQuery.class);
    }

    @Test
    void parseComplex() {
        Query q = QueryParser.parse("(cat AND dog) OR (fish AND NOT bird)");
        assertThat(q).isInstanceOf(OrQuery.class);
    }

    @Test
    void topLevelNotRejected() {
        assertThatThrownBy(() -> QueryParser.parse("NOT cat"))
                .isInstanceOf(QueryParseException.class)
                .hasMessageContaining("NOT");
    }

    @Test
    void emptyQueryRejected() {
        assertThatThrownBy(() -> QueryParser.parse(""))
                .isInstanceOf(QueryParseException.class);
    }

    @Test
    void termLowercased() {
        Query q = QueryParser.parse("CaT");
        assertThat(((TermQuery) q).term()).isEqualTo("cat");
    }
}
