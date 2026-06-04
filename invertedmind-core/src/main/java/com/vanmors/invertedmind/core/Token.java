package com.vanmors.invertedmind.core;

/**
 * A token produced by the tokenizer: the term text and its position in the document.
 */
public record Token(String term, int position) {

    public Token {
        if (term == null || term.isEmpty()) throw new IllegalArgumentException("term must not be null or empty");
        if (position < 0) throw new IllegalArgumentException("position must be >= 0");
    }
}
