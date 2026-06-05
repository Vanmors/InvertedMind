package com.vanmors.invertedmind.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class QueryAstBuilder extends InvertedMindQueryBaseVisitor<Query> {

    @Override
    public Query visitQuery(InvertedMindQueryParser.QueryContext ctx) {
        Query result = visit(ctx.orExpr());
        validate(result);
        return result;
    }

    @Override
    public Query visitOrExpr(InvertedMindQueryParser.OrExprContext ctx) {
        List<InvertedMindQueryParser.AndExprContext> children = ctx.andExpr();
        if (children.size() == 1) {
            return visit(children.get(0));
        }
        List<Query> queries = new ArrayList<>();
        for (var child : children) {
            queries.add(visit(child));
        }
        return new OrQuery(queries);
    }

    @Override
    public Query visitAndExpr(InvertedMindQueryParser.AndExprContext ctx) {
        List<InvertedMindQueryParser.UnaryExprContext> children = ctx.unaryExpr();
        if (children.size() == 1) {
            return visit(children.get(0));
        }
        List<Query> queries = new ArrayList<>();
        for (var child : children) {
            queries.add(visit(child));
        }
        return new AndQuery(queries);
    }

    @Override
    public Query visitNotExpr(InvertedMindQueryParser.NotExprContext ctx) {
        return new NotQuery(visit(ctx.unaryExpr()));
    }

    @Override
    public Query visitPassNear(InvertedMindQueryParser.PassNearContext ctx) {
        return visit(ctx.nearExpr());
    }

    @Override
    public Query visitNearExpr(InvertedMindQueryParser.NearExprContext ctx) {
        List<InvertedMindQueryParser.AdjExprContext> children = ctx.adjExpr();
        if (children.size() == 1) {
            return visit(children.get(0));
        }

        // Parse distance from NEAR/N token
        // There may be multiple NEAR_OP tokens for chained expressions like: a NEAR/3 b NEAR/5 c
        // We use the first NEAR_OP's distance (simplification)
        String nearToken = ctx.NEAR_OP(0).getText(); // "NEAR/3"
        int distance = Integer.parseInt(nearToken.substring(5)); // after "NEAR/"

        List<Query> queries = new ArrayList<>();
        for (var child : children) {
            queries.add(visit(child));
        }
        return new NearQuery(queries, distance);
    }

    @Override
    public Query visitAdjExpr(InvertedMindQueryParser.AdjExprContext ctx) {
        List<InvertedMindQueryParser.PrimaryContext> children = ctx.primary();
        if (children.size() == 1) {
            return visit(children.get(0));
        }
        List<Query> queries = new ArrayList<>();
        for (var child : children) {
            queries.add(visit(child));
        }
        return new AdjQuery(queries);
    }

    @Override
    public Query visitTermPrimary(InvertedMindQueryParser.TermPrimaryContext ctx) {
        return new TermQuery(ctx.TERM().getText().toLowerCase());
    }

    @Override
    public Query visitPhrasePrimary(InvertedMindQueryParser.PhrasePrimaryContext ctx) {
        // "quick brown fox" -> AdjQuery of TermQuerys
        String phrase = ctx.PHRASE().getText();
        // Strip quotes
        String content = phrase.substring(1, phrase.length() - 1).trim();
        String[] words = content.toLowerCase().split("\\s+");

        if (words.length == 1) {
            return new TermQuery(words[0]);
        }

        List<Query> terms = new ArrayList<>();
        for (String word : words) {
            terms.add(new TermQuery(word));
        }
        return new AdjQuery(terms);
    }

    @Override
    public Query visitParenExpr(InvertedMindQueryParser.ParenExprContext ctx) {
        return visit(ctx.orExpr());
    }

    
    private void validate(Query query) {
        if (query instanceof NotQuery) {
            throw new QueryParseException("NOT cannot be used at the top level");
        } else if (query instanceof OrQuery or) {
            for (Query child : or.children()) {
                if (child instanceof NotQuery) {
                    throw new QueryParseException("NOT cannot be a direct child of OR");
                }
                validate(child);
            }
        } else if (query instanceof AndQuery and) {
            boolean hasPositive = false;
            for (Query child : and.children()) {
                if (child instanceof NotQuery not) {
                    validateInner(not.child());
                } else {
                    hasPositive = true;
                    validate(child);
                }
            }
            if (!hasPositive) {
                throw new QueryParseException("AND must have at least one non-NOT child");
            }
        } else if (query instanceof AdjQuery adj) {
            adj.children().forEach(this::validate);
        } else if (query instanceof NearQuery near) {
            near.children().forEach(this::validate);
        }
        // TermQuery is always valid
    }

    private void validateInner(Query query) {
        if (query instanceof NotQuery) {
            throw new QueryParseException("Double NOT is not allowed");
        }
        validate(query);
    }
}
