package com.vanmors.invertedmind.index;

import com.vanmors.invertedmind.core.Token;

import java.util.List;

/**
 * Splits text into a stream of tokens with their positions.
 */
public interface Tokenizer {
    List<Token> tokenize(String text);
}
