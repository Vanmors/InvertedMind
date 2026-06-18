package com.vanmors.invertedindex.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import com.vanmors.invertedindex.core.*;
import com.vanmors.invertedindex.index.IndexBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
public class ProfileBenchmark {

    // addDocument state
    private IndexBuilder growingBuilder;
    private String extraPassage;

    // searchAnd state
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

        andQuery = "blood AND pain";
        System.out.printf("%nAND query: [blood] (df=%d) AND [pain] (df=%d)%n",
            postingLists.get("blood").documentFrequency(),
            postingLists.get("pain").documentFrequency());
    }

    
    @Benchmark
    public int addDocument(Blackhole bh) {
        int docId = growingBuilder.addDocument(extraPassage);
        bh.consume(docId);
        return docId;
    }

    
    @Benchmark
    public QueryResult searchAnd(Blackhole bh) {
        QueryResult result = index.search(andQuery, 10);
        bh.consume(result);
        return result;
    }
}
