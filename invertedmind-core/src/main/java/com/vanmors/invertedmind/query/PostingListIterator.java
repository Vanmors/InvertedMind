package com.vanmors.invertedmind.query;

public interface PostingListIterator {

    
    int NO_MORE_DOCS = Integer.MAX_VALUE;

    
    int docId();

    
    int next();

    
    int advance(int targetDocId);

    
    int termFrequency();

    
    int[] positions();

    
    long cost();
}
