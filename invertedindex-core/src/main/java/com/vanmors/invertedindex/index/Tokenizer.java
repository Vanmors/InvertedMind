package com.vanmors.invertedindex.index;

import com.vanmors.invertedindex.core.Token;

import java.util.List;

public interface Tokenizer {
    List<Token> tokenize(String text);
}
