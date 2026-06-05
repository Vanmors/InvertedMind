package com.vanmors.invertedmind.index;

import com.vanmors.invertedmind.core.Token;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StandardTokenizer implements Tokenizer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    @Override
    public List<Token> tokenize(String text) {
        if (text == null || text.isEmpty()) return List.of();

        // Normalize to NFD and strip combining marks (accents)
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String lower = normalized.toLowerCase();

        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(lower);
        int position = 0;
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(), position));
            position++;
        }
        return tokens;
    }
}
