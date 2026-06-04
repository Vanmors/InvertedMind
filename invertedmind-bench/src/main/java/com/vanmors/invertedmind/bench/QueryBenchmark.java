package com.vanmors.invertedmind.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import com.vanmors.invertedmind.core.*;
import com.vanmors.invertedmind.index.IndexBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Measures query latency using a real MS MARCO index (50 000 passages).
 * <p>
 * Operator benchmarks (AND, OR, …) all use the same pair of terms selected
 * from the real corpus so the comparison is fair. searchDevQuery runs actual
 * MS MARCO dev queries converted to OR form.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
public class QueryBenchmark {

    private InvertedIndex index;

    private String singleTermQuery;
    private String andQuery;
    private String orQuery;
    private String andNotQuery;
    private String phraseQuery;
    private String nearQuery;
    private String complexQuery;

    private String[] devQueries;
    private int devQueryIdx;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path dataDir = Path.of(System.getProperty("msmarco.dir", "data/msmarco"));

        // ── Build index from real passages ──────────────────────────────────
        List<String> passages = MsMarcoLoader.loadPassages(dataDir, 50_000);
        IndexConfig config = IndexConfig.builder(Path.of("/tmp/bench")).build();
        IndexBuilder builder = new IndexBuilder(config);
        for (String passage : passages) {
            builder.addDocument(passage);
        }
        Map<String, PostingList> postingLists = builder.buildPostingLists();
        CollectionStatistics stats = builder.buildStatistics();
        int[] docLengths = builder.getDocLengths();
        index = new InvertedIndex(postingLists, stats, docLengths);

        // ── Pick terms with medium df for fair operator comparison ──────────
        // Target: df in [100, 2000] so posting lists aren't trivially small or huge.
        List<String> terms = new ArrayList<>();
        for (Map.Entry<String, PostingList> e : postingLists.entrySet()) {
            int df = e.getValue().documentFrequency();
            if (df >= 100 && df <= 2000) {
                terms.add(e.getKey());
                if (terms.size() == 4) break;
            }
        }
        // Fallback: take most-frequent terms if not enough medium-df terms found
        if (terms.size() < 4) {
            postingLists.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getValue().documentFrequency()))
                .limit(4)
                .forEach(e -> { if (!terms.contains(e.getKey())) terms.add(e.getKey()); });
        }

        String t0 = terms.get(0), t1 = terms.get(1),
               t2 = terms.get(2), t3 = terms.get(3);

        singleTermQuery = t0;
        andQuery        = t0 + " AND " + t1;
        orQuery         = t0 + " OR "  + t1;
        andNotQuery     = t0 + " AND NOT " + t1;
        phraseQuery     = "\"" + t0 + " " + t1 + "\"";
        nearQuery       = t0 + " NEAR/5 " + t1;
        complexQuery    = "(" + t0 + " OR " + t1 + ") AND " + t2 + " AND NOT " + t3;

        System.out.printf(
            "%nQuery terms: t0=%s(df=%d) t1=%s(df=%d) t2=%s(df=%d) t3=%s(df=%d)%n",
            t0, postingLists.get(t0).documentFrequency(),
            t1, postingLists.get(t1).documentFrequency(),
            t2, postingLists.get(t2).documentFrequency(),
            t3, postingLists.get(t3).documentFrequency());

        // ── Load real MS MARCO dev queries, convert to OR form ──────────────
        List<String> rawQueries = MsMarcoLoader.loadQueries(dataDir);
        devQueries = rawQueries.stream()
            .limit(500)
            .map(q -> String.join(" OR ",
                q.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")))
            .toArray(String[]::new);
        devQueryIdx = 0;
    }

    @Benchmark public void searchSingleTerm(Blackhole bh) { bh.consume(index.search(singleTermQuery, 10)); }
    @Benchmark public void searchAnd       (Blackhole bh) { bh.consume(index.search(andQuery,        10)); }
    @Benchmark public void searchOr        (Blackhole bh) { bh.consume(index.search(orQuery,         10)); }
    @Benchmark public void searchAndNot    (Blackhole bh) { bh.consume(index.search(andNotQuery,     10)); }
    @Benchmark public void searchPhrase    (Blackhole bh) { bh.consume(index.search(phraseQuery,     10)); }
    @Benchmark public void searchNear      (Blackhole bh) { bh.consume(index.search(nearQuery,       10)); }
    @Benchmark public void searchComplex   (Blackhole bh) { bh.consume(index.search(complexQuery,    10)); }

    /** Cycles through 500 real MS MARCO dev queries (OR-converted). */
    @Benchmark
    public void searchDevQuery(Blackhole bh) {
        bh.consume(index.search(devQueries[devQueryIdx], 10));
        devQueryIdx = (devQueryIdx + 1) % devQueries.length;
    }
}
