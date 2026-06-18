package com.vanmors.invertedindex.query;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.vanmors.invertedindex.core.*;
import com.vanmors.invertedindex.index.IndexBuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EndToEndQueryTest {

    private static InvertedIndex index;

    @BeforeAll
    static void buildIndex() {
        IndexConfig config = IndexConfig.defaults(Path.of("/tmp"));
        IndexBuilder builder = new IndexBuilder(config);

        // doc 0
        builder.addDocument("the quick brown fox jumps over the lazy dog");
        // doc 1
        builder.addDocument("quick brown dog runs fast");
        // doc 2
        builder.addDocument("the fox and the dog are friends");
        // doc 3
        builder.addDocument("lazy fox sleeps all day long");
        // doc 4
        builder.addDocument("brown cat sits on the mat");

        Map<String, PostingList> postingLists = builder.buildPostingLists();
        CollectionStatistics stats = builder.buildStatistics();
        int[] docLengths = builder.getDocLengths();

        index = new InvertedIndex(postingLists, stats, docLengths);
    }

    @Test
    void singleTermQuery() {
        QueryResult result = index.search("fox", 10);
        assertThat(result.totalHits()).isEqualTo(3); // docs 0, 2, 3
        assertThat(result.results().stream().map(ScoredDoc::docId))
                .containsExactlyInAnyOrder(0, 2, 3);
    }

    @Test
    void andQuery() {
        QueryResult result = index.search("quick AND brown", 10);
        assertThat(result.totalHits()).isEqualTo(2); // docs 0, 1
        assertThat(result.results().stream().map(ScoredDoc::docId))
                .containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void orQuery() {
        QueryResult result = index.search("cat OR fox", 10);
        assertThat(result.totalHits()).isEqualTo(4); // docs 0, 2, 3 (fox) + doc 4 (cat)
        assertThat(result.results().stream().map(ScoredDoc::docId))
                .containsExactlyInAnyOrder(0, 2, 3, 4);
    }

    @Test
    void andNotQuery() {
        QueryResult result = index.search("fox AND NOT lazy", 10);
        assertThat(result.totalHits()).isEqualTo(1); // doc 2 (fox without lazy)
        assertThat(result.results().stream().map(ScoredDoc::docId))
                .containsExactly(2);
    }

    @Test
    void phraseQuery() {
        QueryResult result = index.search("\"quick brown\"", 10);
        assertThat(result.totalHits()).isEqualTo(2); // docs 0, 1
    }

    @Test
    void adjQuery() {
        QueryResult result = index.search("quick ADJ brown", 10);
        assertThat(result.totalHits()).isEqualTo(2); // docs 0, 1
    }

    @Test
    void nearQuery() {
        QueryResult result = index.search("fox NEAR/3 dog", 10);
        // doc 0: "fox" at pos 3, "dog" at pos 8 -> distance 5 > 3
        // doc 2: "fox" at pos 1, "dog" at pos 3 -> distance 2 <= 3
        assertThat(result.totalHits()).isGreaterThanOrEqualTo(1);
        assertThat(result.results().stream().map(ScoredDoc::docId))
                .contains(2);
    }

    @Test
    void complexQuery() {
        QueryResult result = index.search("(fox OR cat) AND brown", 10);
        // fox AND brown: doc 0 (both present)
        // cat AND brown: doc 4 (both present)
        assertThat(result.totalHits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void nonexistentTerm() {
        QueryResult result = index.search("unicorn", 10);
        assertThat(result.results()).isEmpty();
    }

    @Test
    void bm25ScoresArePositive() {
        QueryResult result = index.search("fox", 10);
        for (ScoredDoc doc : result.results()) {
            assertThat(doc.score()).isGreaterThan(0);
        }
    }

    @Test
    void bm25RanksRareTermsHigher() {
        // "cat" appears in 1 doc, "dog" in 3 docs
        // The doc with "cat" should score higher for "cat" than docs score for "dog"
        QueryResult catResult = index.search("cat", 10);
        QueryResult dogResult = index.search("dog", 10);

        float catTopScore = catResult.results().get(0).score();
        float dogTopScore = dogResult.results().get(0).score();

        // cat is rarer -> higher IDF -> higher score
        assertThat(catTopScore).isGreaterThan(dogTopScore);
    }
}
