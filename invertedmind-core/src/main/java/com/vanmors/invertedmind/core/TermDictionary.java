package com.vanmors.invertedmind.core;

import java.util.Arrays;

/**
 * In-memory term dictionary with binary search lookup.
 * <p>
 * Terms are stored sorted. Each term has an associated {@link TermInfo} with
 * document frequency, total term frequency, and posting list location.
 */
public final class TermDictionary {

    private final String[] terms;
    private final TermInfo[] termInfos;

    public TermDictionary(String[] terms, TermInfo[] termInfos) {
        this.terms = terms;
        this.termInfos = termInfos;
    }

    public int lookupTerm(String term) {
        return Arrays.binarySearch(terms, term);
    }

    public TermInfo getTermInfo(String term) {
        int id = lookupTerm(term);
        return id >= 0 ? termInfos[id] : null;
    }

    public int size() {
        return terms.length;
    }

    /**
     * Builds a TermDictionary from a sorted list of (term, df, ttf, offset, length) entries.
     */
    public static Builder builder(int capacity) {
        return new Builder(capacity);
    }

    public static final class Builder {
        private final String[] terms;
        private final TermInfo[] infos;
        private int count = 0;

        private Builder(int capacity) {
            terms = new String[capacity];
            infos = new TermInfo[capacity];
        }

        public Builder add(String term, int df, long ttf, long offset) {
            terms[count] = term;
            infos[count] = new TermInfo(df, ttf, offset);
            count++;
            return this;
        }

        public TermDictionary build() {
            if (count < terms.length) {
                return new TermDictionary(
                        Arrays.copyOf(terms, count),
                        Arrays.copyOf(infos, count)
                );
            }
            return new TermDictionary(terms, infos);
        }
    }
}
