package com.vanmors.invertedindex.index;

import com.vanmors.invertedindex.core.Token;

import java.util.List;

public final class Analyzer {

    private final Tokenizer tokenizer;

    public Analyzer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public List<Token> analyze(String text) {
        return tokenizer.tokenize(text);
    }

    public static Analyzer defaultAnalyzer() {
        return new Analyzer(new StandardTokenizer());
    }
}
