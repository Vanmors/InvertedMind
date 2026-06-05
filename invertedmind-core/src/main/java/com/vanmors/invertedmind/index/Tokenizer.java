package com.vanmors.invertedmind.index;

import com.vanmors.invertedmind.core.Token;

import java.util.List;

public interface Tokenizer {
    List<Token> tokenize(String text);
}
