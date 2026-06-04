package com.vanmors.invertedmind.index;

import com.vanmors.invertedmind.core.Token;

import java.util.List;

public interface TokenFilter {
    List<Token> filter(List<Token> tokens);
}
