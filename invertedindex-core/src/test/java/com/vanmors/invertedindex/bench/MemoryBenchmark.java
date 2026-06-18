package com.vanmors.invertedindex.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import com.vanmors.invertedindex.core.*;
import com.vanmors.invertedindex.index.IndexBuilder;
import com.vanmors.invertedindex.storage.Segment;
import com.vanmors.invertedindex.storage.SegmentWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class MemoryBenchmark {

    private Path segmentFile;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path dataDir = Path.of(System.getProperty("msmarco.dir", "data/msmarco"));
        List<String> passages = MsMarcoLoader.loadPassages(dataDir, 10_000);

        Path tmpDir = Files.createTempDirectory("invertedindex-bench");
        IndexConfig config = IndexConfig.builder(tmpDir).build();
        IndexBuilder builder = new IndexBuilder(config);
        for (String passage : passages) {
            builder.addDocument(passage);
        }

        Map<String, PostingList> postingLists = builder.buildPostingLists();
        CollectionStatistics stats = builder.buildStatistics();
        int[] docLengths = builder.getDocLengths();

        segmentFile = tmpDir.resolve("bench.inv");
        SegmentWriter writer = new SegmentWriter(segmentFile, builder.getCodec());
        writer.write(postingLists, docLengths, stats);
    }

    @Benchmark
    public void loadSegment(Blackhole bh) throws IOException {
        long beforeHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        try (Segment segment = new Segment(segmentFile)) {
            long afterHeap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            bh.consume(segment);
            bh.consume(afterHeap - beforeHeap);
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws IOException {
        if (segmentFile != null) {
            Files.deleteIfExists(segmentFile);
            Files.deleteIfExists(segmentFile.getParent());
        }
    }
}
