package com.vanmors.invertedmind.index;

import com.vanmors.invertedmind.core.Token;

import java.util.List;

/**
 * Combines a tokenizer with a chain of token filters.
 */
public final class Analyzer {

    private final Tokenizer tokenizer;
    private final List<TokenFilter> filters;

    public Analyzer(Tokenizer tokenizer, TokenFilter... filters) {
        this.tokenizer = tokenizer;
        this.filters = List.of(filters);
    }

    public List<Token> analyze(String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        for (TokenFilter filter : filters) {
            tokens = filter.filter(tokens);
        }
        return tokens;
    }

    /**
     * Default analyzer: StandardTokenizer (which already lowercases and strips accents).
     */
    public static Analyzer defaultAnalyzer() {
        return new Analyzer(new StandardTokenizer());
    }

    /**
     * Analyzer with stop word removal.
     */
    public static Analyzer withStopWords() {
        return new Analyzer(new StandardTokenizer(), new StopWordFilter());
    }
}
