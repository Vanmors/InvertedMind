package com.vanmors.invertedmind.core;

import com.vanmors.invertedmind.query.*;
import com.vanmors.invertedmind.scoring.BM25Scorer;

import java.util.*;

public final class InvertedIndex {

    private final Map<String, PostingList> postingLists;
    private final CollectionStatistics stats;
    private final int[] docLengths;
    private final BM25Scorer scorer = new BM25Scorer();

    public InvertedIndex(Map<String, PostingList> postingLists,
                         CollectionStatistics stats,
                         int[] docLengths) {
        this.postingLists = postingLists;
        this.stats = stats;
        this.docLengths = docLengths;
    }

    
    public PostingList getPostingList(String term) {
        return postingLists.get(term);
    }

    public CollectionStatistics statistics() {
        return stats;
    }

    
    public QueryResult search(String queryString, int topK) {
        Query query = QueryParser.parse(queryString);
        return search(query, topK);
    }

    
    public QueryResult search(Query query, int topK) {
        QueryPlanner planner = new QueryPlanner(this::getPostingList);
        PostingListIterator iterator = planner.plan(query);

        if (iterator instanceof EmptyIterator) {
            return new QueryResult(List.of(), 0);
        }

        // Collect all leaf term iterators for scoring
        List<TermScoreInfo> termInfos = collectTermInfos(query);

        // Evaluate: iterate through matching docs, score each
        PriorityQueue<ScoredDoc> topDocs = new PriorityQueue<>(topK + 1,
                Comparator.comparingDouble(ScoredDoc::score));
        int totalHits = 0;

        // Re-plan since iterators are stateful
        planner = new QueryPlanner(this::getPostingList);
        iterator = planner.plan(query);

        int docId;
        while ((docId = iterator.next()) != PostingListIterator.NO_MORE_DOCS) {
            totalHits++;
            float score = scoreDocument(docId, iterator, termInfos);

            if (topDocs.size() < topK || score > topDocs.peek().score()) {
                topDocs.add(new ScoredDoc(docId, score));
                if (topDocs.size() > topK) {
                    topDocs.poll();
                }
            }
        }

        List<ScoredDoc> results = new ArrayList<>(topDocs);
        results.sort(Comparator.comparingDouble(ScoredDoc::score).reversed());
        return new QueryResult(results, totalHits);
    }

    private float scoreDocument(int docId, PostingListIterator iterator, List<TermScoreInfo> termInfos) {
        int docLength = docId < docLengths.length ? docLengths[docId] : 0;

        // For simple term queries, use the iterator's TF directly
        if (termInfos.size() == 1) {
            TermScoreInfo info = termInfos.get(0);
            return scorer.score(iterator.termFrequency(), docLength, info.df, stats);
        }

        // For compound queries, sum BM25 scores across matching terms
        float totalScore = 0;
        for (TermScoreInfo info : termInfos) {
            PostingList pl = postingLists.get(info.term);
            if (pl != null) {
                totalScore += scorer.score(
                        Math.max(1, iterator.termFrequency() / termInfos.size()),
                        docLength, info.df, stats);
            }
        }
        return totalScore;
    }

    private List<TermScoreInfo> collectTermInfos(Query query) {
        List<TermScoreInfo> infos = new ArrayList<>();
        collectTermInfosRecursive(query, infos);
        return infos;
    }

    private void collectTermInfosRecursive(Query query, List<TermScoreInfo> infos) {
        if (query instanceof TermQuery tq) {
            PostingList pl = postingLists.get(tq.term());
            int df = pl != null ? pl.documentFrequency() : 0;
            infos.add(new TermScoreInfo(tq.term(), df));
        } else if (query instanceof AndQuery and) {
            for (Query child : and.children()) collectTermInfosRecursive(child, infos);
        } else if (query instanceof OrQuery or) {
            for (Query child : or.children()) collectTermInfosRecursive(child, infos);
        } else if (query instanceof NotQuery not) {
            // Don't score NOT terms
        } else if (query instanceof AdjQuery adj) {
            for (Query child : adj.children()) collectTermInfosRecursive(child, infos);
        } else if (query instanceof NearQuery near) {
            for (Query child : near.children()) collectTermInfosRecursive(child, infos);
        }
    }

    private record TermScoreInfo(String term, int df) {}

}
