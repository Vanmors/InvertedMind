package com.vanmors.invertedindex.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import com.vanmors.invertedindex.core.IndexConfig;
import com.vanmors.invertedindex.index.IndexBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
public class IndexingBenchmark {

    @Param({"1000", "10000"})
    int numDocs;

    private List<String> passages;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path dataDir = Path.of(System.getProperty("msmarco.dir", "data/msmarco"));
        passages = MsMarcoLoader.loadPassages(dataDir, numDocs);
    }

    @Benchmark
    public void indexDocuments(Blackhole bh) {
        IndexConfig config = IndexConfig.builder(Path.of("/tmp/bench")).build();
        IndexBuilder builder = new IndexBuilder(config);
        for (String passage : passages) {
            builder.addDocument(passage);
        }
        bh.consume(builder.buildPostingLists());
    }
}
