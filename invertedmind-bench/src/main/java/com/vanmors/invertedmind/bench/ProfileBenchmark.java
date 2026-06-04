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
 * Benchmarks intended for async-profiler flamegraph generation.
 * Two operations are profiled in isolation:
 * <ol>
 *   <li>{@link #addDocument} — adds one MS MARCO passage to an IndexBuilder
 *       that already holds 50 000 documents (realistic TreeMap depth, real text).</li>
 *   <li>{@link #searchAnd} — executes an AND query over a 50 000-document index
 *       built from real MS MARCO passages.</li>
 * </ol>
 *
 * Run with async-profiler:
 * <pre>
 *   PROFILER="-prof async:libPath=/opt/homebrew/lib/libasyncProfiler.dylib;output=flamegraph;file=docs/charts/flamegraph_%s.html"
 *   java -jar benchmarks.jar ProfileBenchmark.addDocument -prof "${PROFILER/\%s/add_document}" -wi 3 -i 5 -f 1
 *   java -jar benchmarks.jar ProfileBenchmark.searchAnd   -prof "${PROFILER/\%s/search_and}"   -wi 3 -i 5 -f 1
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
public class ProfileBenchmark {

    // ── addDocument state ────────────────────────────────────────────────────
    private IndexBuilder growingBuilder;
    private String extraPassage;

    // ── searchAnd state ──────────────────────────────────────────────────────
    private InvertedIndex index;
    private String andQuery;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path dataDir = Path.of(System.getProperty("msmarco.dir", "data/msmarco"));
        List<String> passages = MsMarcoLoader.loadPassages(dataDir, 50_001);

        IndexConfig config = IndexConfig.builder(Path.of("/tmp/bench")).build();

        // Pre-populate builder with 50 000 docs (addDocument target)
        growingBuilder = new IndexBuilder(config);
        for (int i = 0; i < 50_000; i++) {
            growingBuilder.addDocument(passages.get(i));
        }
        extraPassage = passages.get(50_000);

        // Build a separate index for query profiling
        IndexBuilder queryBuilder = new IndexBuilder(config);
        for (int i = 0; i < 50_000; i++) {
            queryBuilder.addDocument(passages.get(i));
        }
        Map<String, PostingList> postingLists = queryBuilder.buildPostingLists();
        index = new InvertedIndex(postingLists, queryBuilder.buildStatistics(),
                                  queryBuilder.getDocLengths());

        // Select two real terms with moderate document frequency
        String t0 = null, t1 = null;
        for (Map.Entry<String, PostingList> e : postingLists.entrySet()) {
            int df = e.getValue().documentFrequency();
            if (df >= 100 && df <= 2000) {
                if (t0 == null) t0 = e.getKey();
                else { t1 = e.getKey(); break; }
            }
        }
        andQuery = t0 + " AND " + t1;
        System.out.printf("%nAND query: [%s] (df=%d) AND [%s] (df=%d)%n",
            t0, postingLists.get(t0).documentFrequency(),
            t1, postingLists.get(t1).documentFrequency());
    }

    /**
     * Adds one passage to an already-large IndexBuilder.
     * Profiles: Analyzer → tokenize → TreeMap.computeIfAbsent → ArrayList.add.
     */
    @Benchmark
    public int addDocument(Blackhole bh) {
        int docId = growingBuilder.addDocument(extraPassage);
        bh.consume(docId);
        return docId;
    }

    /**
     * Executes an AND query over the 50K-doc index.
     * Profiles: QueryParser → QueryPlanner → AndIterator → TermPostingIterator
     *           (PForDelta decode, skip list advance) → BM25Scorer → top-K heap.
     */
    @Benchmark
    public QueryResult searchAnd(Blackhole bh) {
        QueryResult result = index.search(andQuery, 10);
        bh.consume(result);
        return result;
    }
}
