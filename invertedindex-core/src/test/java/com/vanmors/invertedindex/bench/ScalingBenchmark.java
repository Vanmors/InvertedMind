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
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(value = 1, jvmArgs = {"-Xms4g", "-Xmx4g"})
public class ScalingBenchmark {

    @Param({"10000", "50000", "100000", "250000"})
    int numDocs;

    private InvertedIndex index;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path dataDir = Path.of(System.getProperty("msmarco.dir", "data/msmarco"));
        List<String> passages = MsMarcoLoader.loadPassages(dataDir, numDocs);

        IndexConfig config = IndexConfig.builder(Path.of("/tmp/bench")).build();
        IndexBuilder builder = new IndexBuilder(config);
        for (String passage : passages) {
            builder.addDocument(passage);
        }
        Map<String, PostingList> postingLists = builder.buildPostingLists();
        index = new InvertedIndex(postingLists, builder.buildStatistics(), builder.getDocLengths());

        int dfBlood    = df(postingLists, "blood");
        int dfPressure = df(postingLists, "pressure");
        System.out.printf("%n[N=%,d] blood(df=%d), pressure(df=%d)%n", numDocs, dfBlood, dfPressure);
    }

    private static int df(Map<String, PostingList> pls, String term) {
        PostingList pl = pls.get(term);
        return pl != null ? pl.documentFrequency() : 0;
    }

    @Benchmark public void singleTerm(Blackhole bh) { bh.consume(index.search("blood", 10)); }
    @Benchmark public void andQuery  (Blackhole bh) { bh.consume(index.search("blood AND pressure", 10)); }
    @Benchmark public void orQuery   (Blackhole bh) { bh.consume(index.search("blood OR pressure", 10)); }
    @Benchmark public void phrase    (Blackhole bh) { bh.consume(index.search("\"blood pressure\"", 10)); }
}
