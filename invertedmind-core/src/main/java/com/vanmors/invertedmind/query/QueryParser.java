package com.vanmors.invertedmind.query;

import org.antlr.v4.runtime.*;

/**
 * Parses query strings into Query AST.
 * <p>
 * Syntax examples:
 * <pre>
 *   cat AND dog
 *   cat OR dog
 *   cat AND NOT dog
 *   "quick brown fox"        (phrase = adjacent terms)
 *   quick ADJ brown
 *   cat NEAR/3 dog
 *   (cat OR dog) AND fish
 * </pre>
 */
public final class QueryParser {

    private QueryParser() {}

    /**
     * Parses a query string into a Query AST.
     *
     * @throws QueryParseException if the query is syntactically invalid or violates semantic constraints
     */
    public static Query parse(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            throw new QueryParseException("Query string must not be empty");
        }

        try {
            CharStream input = CharStreams.fromString(queryString);
            InvertedMindQueryLexer lexer = new InvertedMindQueryLexer(input);
            lexer.removeErrorListeners();
            lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            InvertedMindQueryParser parser = new InvertedMindQueryParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(ThrowingErrorListener.INSTANCE);

            InvertedMindQueryParser.QueryContext tree = parser.query();
            return new QueryAstBuilder().visit(tree);
        } catch (QueryParseException e) {
            throw e;
        } catch (Exception e) {
            throw new QueryParseException("Failed to parse query: " + queryString, e);
        }
    }

    private static class ThrowingErrorListener extends BaseErrorListener {
        static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            throw new QueryParseException("Syntax error at position " + charPositionInLine + ": " + msg);
        }
    }
}
