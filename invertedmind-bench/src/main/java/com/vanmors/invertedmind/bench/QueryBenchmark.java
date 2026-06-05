package com.vanmors.invertedmind.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import com.vanmors.invertedmind.core.*;
import com.vanmors.invertedmind.index.IndexBuilder;

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
public class QueryBenchmark {

    private static final String T0 = "blood";
    private static final String T1 = "pain";
    private static final String T2 = "cancer";
    private static final String T3 = "drug";

    private InvertedIndex index;

    private String singleTermQuery;
    private String andQuery;
    private String orQuery;
    private String andNotQuery;
    private String phraseQuery;
    private String nearQuery;
    private String complexQuery;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path dataDir = Path.of(System.getProperty("msmarco.dir", "data/msmarco"));

        List<String> passages = MsMarcoLoader.loadPassages(dataDir, 50_000);
        IndexConfig config = IndexConfig.builder(Path.of("/tmp/bench")).build();
        IndexBuilder builder = new IndexBuilder(config);
        for (String passage : passages) {
            builder.addDocument(passage);
        }
        Map<String, PostingList> postingLists = builder.buildPostingLists();
        index = new InvertedIndex(postingLists, builder.buildStatistics(), builder.getDocLengths());

        singleTermQuery = T0;
        andQuery        = T0 + " AND " + T1;
        orQuery         = T0 + " OR "  + T1;
        andNotQuery     = T0 + " AND NOT " + T1;
        phraseQuery     = "\"" + T0 + " " + T1 + "\"";
        nearQuery       = T0 + " NEAR/5 " + T1;
        complexQuery    = "(" + T0 + " OR " + T1 + ") AND " + T2 + " AND NOT " + T3;

        System.out.printf("%nTerms: %s(df=%d), %s(df=%d), %s(df=%d), %s(df=%d)%n",
            T0, postingLists.get(T0).documentFrequency(),
            T1, postingLists.get(T1).documentFrequency(),
            T2, postingLists.get(T2).documentFrequency(),
            T3, postingLists.get(T3).documentFrequency());
    }

    @Benchmark public void searchSingleTerm(Blackhole bh) { bh.consume(index.search(singleTermQuery, 10)); }
    @Benchmark public void searchAnd       (Blackhole bh) { bh.consume(index.search(andQuery,        10)); }
    @Benchmark public void searchOr        (Blackhole bh) { bh.consume(index.search(orQuery,         10)); }
    @Benchmark public void searchAndNot    (Blackhole bh) { bh.consume(index.search(andNotQuery,     10)); }
    @Benchmark public void searchPhrase    (Blackhole bh) { bh.consume(index.search(phraseQuery,     10)); }
    @Benchmark public void searchNear      (Blackhole bh) { bh.consume(index.search(nearQuery,       10)); }
    @Benchmark public void searchComplex   (Blackhole bh) { bh.consume(index.search(complexQuery,    10)); }
}
