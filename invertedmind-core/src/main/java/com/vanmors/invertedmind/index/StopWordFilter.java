package com.vanmors.invertedmind.index;

import com.vanmors.invertedmind.core.Token;

import java.util.List;
import java.util.Set;

/**
 * Removes common stop words from the token stream.
 * Positions are preserved (not renumbered) so proximity queries remain correct.
 */
public final class StopWordFilter implements TokenFilter {

    private static final Set<String> DEFAULT_STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to",
            "for", "of", "with", "by", "from", "is", "was", "are", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "will", "would", "shall", "should", "may", "might", "must", "can",
            "could", "not", "no", "nor", "so", "if", "then", "than", "that",
            "this", "these", "those", "it", "its", "i", "we", "you", "he",
            "she", "they", "me", "us", "him", "her", "them", "my", "our",
            "your", "his", "their", "what", "which", "who", "whom", "how",
            "when", "where", "why", "all", "each", "every", "both", "few",
            "more", "most", "other", "some", "such", "only", "own", "same",
            "as", "up", "out", "about", "into", "over", "after", "also"
    );

    private final Set<String> stopWords;

    public StopWordFilter() {
        this(DEFAULT_STOP_WORDS);
    }

    public StopWordFilter(Set<String> stopWords) {
        this.stopWords = stopWords;
    }

    @Override
    public List<Token> filter(List<Token> tokens) {
        return tokens.stream()
                .filter(t -> !stopWords.contains(t.term()))
                .toList();
    }
}
