package com.vanmors.invertedmind.query;

import com.vanmors.invertedmind.core.PostingList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class QueryPlanner {

    private final Function<String, PostingList> postingListProvider;

    
    public QueryPlanner(Function<String, PostingList> postingListProvider) {
        this.postingListProvider = postingListProvider;
    }

    
    public PostingListIterator plan(Query query) {
        if (query instanceof TermQuery tq) {
            return planTerm(tq);
        } else if (query instanceof AndQuery and) {
            return planAnd(and);
        } else if (query instanceof OrQuery or) {
            return planOr(or);
        } else if (query instanceof NotQuery) {
            throw new QueryParseException("NOT cannot appear at the root level");
        } else if (query instanceof AdjQuery adj) {
            return planAdj(adj);
        } else if (query instanceof NearQuery near) {
            return planNear(near);
        }
        throw new IllegalStateException("Unknown query type: " + query.getClass());
    }

    private PostingListIterator planTerm(TermQuery tq) {
        PostingList pl = postingListProvider.apply(tq.term());
        if (pl == null || pl.documentFrequency() == 0) {
            return new EmptyIterator();
        }
        return new TermPostingIterator(pl);
    }

    private PostingListIterator planAnd(AndQuery and) {
        List<PostingListIterator> positive = new ArrayList<>();
        List<PostingListIterator> negative = new ArrayList<>();

        for (Query child : and.children()) {
            if (child instanceof NotQuery not) {
                PostingListIterator negIt = plan(not.child());
                if (negIt != null) {
                    negative.add(negIt);
                }
            } else {
                PostingListIterator it = plan(child);
                if (it instanceof EmptyIterator) {
                    return new EmptyIterator(); // AND with empty = empty
                }
                positive.add(it);
            }
        }

        if (positive.isEmpty()) {
            return new EmptyIterator();
        }

        PostingListIterator result;
        if (positive.size() == 1) {
            result = positive.get(0);
        } else {
            result = new AndIterator(positive.toArray(new PostingListIterator[0]));
        }

        // Wrap with NOT filters
        for (PostingListIterator neg : negative) {
            result = new NotIterator(result, neg);
        }

        return result;
    }

    private PostingListIterator planOr(OrQuery or) {
        List<PostingListIterator> iterators = new ArrayList<>();
        for (Query child : or.children()) {
            PostingListIterator it = plan(child);
            if (!(it instanceof EmptyIterator)) {
                iterators.add(it);
            }
        }

        if (iterators.isEmpty()) return new EmptyIterator();
        if (iterators.size() == 1) return iterators.get(0);

        return new OrIterator(iterators.toArray(new PostingListIterator[0]));
    }

    private PostingListIterator planAdj(AdjQuery adj) {
        List<PostingListIterator> iterators = new ArrayList<>();
        for (Query child : adj.children()) {
            PostingListIterator it = plan(child);
            if (it instanceof EmptyIterator) return new EmptyIterator();
            iterators.add(it);
        }
        return new AdjIterator(iterators.toArray(new PostingListIterator[0]));
    }

    private PostingListIterator planNear(NearQuery near) {
        List<PostingListIterator> iterators = new ArrayList<>();
        for (Query child : near.children()) {
            PostingListIterator it = plan(child);
            if (it instanceof EmptyIterator) return new EmptyIterator();
            iterators.add(it);
        }
        return new NearIterator(near.distance(), iterators.toArray(new PostingListIterator[0]));
    }
}
