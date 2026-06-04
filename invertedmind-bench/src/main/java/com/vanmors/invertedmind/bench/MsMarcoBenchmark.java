package com.vanmors.invertedmind.bench;

import com.vanmors.invertedmind.core.*;
import com.vanmors.invertedmind.index.IndexBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * End-to-end benchmark on MS MARCO Passages.
 * <p>
 * Downloads MS MARCO if not cached, indexes passages, writes segment to disk,
 * runs queries, and reports timing.
 * <p>
 * Usage:
 * <pre>
 *   mvn clean package -pl invertedmind-bench -am -DskipTests
 *   java -cp invertedmind-bench/target/benchmarks.jar \
 *        com.vanmors.invertedmind.bench.MsMarcoBenchmark [dataDir] [numPassages]
 * </pre>
 */
public final class MsMarcoBenchmark {

    public static void main(String[] args) throws IOException {
        Path dataDir = args.length > 0 ? Path.of(args[0]) : Path.of("data/msmarco");
        int numPassages = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;

        System.out.println("=== MS MARCO Benchmark ===");
        System.out.printf("Passages to index: %,d%n%n", numPassages);

        // --- 1. Load passages ---
        long t0 = System.currentTimeMillis();
        List<String> passages = MsMarcoLoader.loadPassages(dataDir, numPassages);
        long loadMs = System.currentTimeMillis() - t0;
        System.out.printf("Load time: %,d ms%n%n", loadMs);

        // --- 2. Build index ---
        System.out.println("Building index...");
        IndexConfig config = IndexConfig.defaults(dataDir);
        IndexBuilder builder = new IndexBuilder(config);

        t0 = System.currentTimeMillis();
        for (String passage : passages) {
            builder.addDocument(passage);
        }
        long indexBuildMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        var postingLists = builder.buildPostingLists();
        long compressMs = System.currentTimeMillis() - t0;

        CollectionStatistics stats = builder.buildStatistics();
        int[] docLengths = builder.getDocLengths();

        System.out.printf("Documents:     %,d%n", stats.totalDocuments());
        System.out.printf("Total tokens:  %,d%n", stats.totalTokens());
        System.out.printf("Unique terms:  %,d%n", postingLists.size());
        System.out.printf("Avg doc len:   %.1f tokens%n", stats.averageDocumentLength());
        System.out.printf("Index time:    %,d ms (add docs) + %,d ms (compress)%n%n", indexBuildMs, compressMs);

        // --- 3. Write segment to disk ---
        Path segmentFile = dataDir.resolve("msmarco.inv");
        System.out.println("Writing segment to disk...");
        t0 = System.currentTimeMillis();
        builder.writeSegment(segmentFile);
        long writeMs = System.currentTimeMillis() - t0;
        long fileSize = Files.size(segmentFile);
        long rawSize = stats.totalTokens() * 4; // rough estimate: 4 bytes per token
        System.out.printf("Segment size:  %,d bytes (%.1f MB)%n", fileSize, fileSize / 1_048_576.0);
        System.out.printf("Write time:    %,d ms%n", writeMs);
        System.out.printf("Compression:   %.1fx vs raw ints%n%n", (double) rawSize / fileSize);

        // --- 4. Search ---
        InvertedIndex index = new InvertedIndex(postingLists, stats, docLengths);

        String[] queries = {
                "dog",
                "machine OR learning",
                "dog AND cat",
                "president OR government",
                "\"new york\"",
                "water NEAR/3 temperature",
                "science AND NOT fiction",
                "(cancer OR tumor) AND treatment",
        };

        // JIT warmup: run each query twice, discard results
        System.out.println("Warming up JIT...");
        for (int w = 0; w < 2; w++) {
            for (String q : queries) {
                try { index.search(q, 10); } catch (Exception ignored) {}
            }
        }

        System.out.println("Running queries...");
        System.out.printf("%-40s %8s %8s%n", "Query", "Hits", "Time(us)");
        System.out.println("-".repeat(60));

        for (String q : queries) {
            try {
                t0 = System.nanoTime();
                QueryResult result = index.search(q, 10);
                long elapsedUs = (System.nanoTime() - t0) / 1_000;

                System.out.printf("%-40s %8d %8d%n", q, result.totalHits(), elapsedUs);
            } catch (Exception e) {
                System.out.printf("%-40s ERROR: %s%n", q, e.getMessage());
            }
        }

        // --- 5. Load queries from MS MARCO (if available) ---
        try {
            List<String> msmarcoQueries = MsMarcoLoader.loadQueries(dataDir);
            if (!msmarcoQueries.isEmpty()) {
                System.out.printf("%nRunning %,d MS MARCO dev queries...%n", Math.min(1000, msmarcoQueries.size()));

                int count = 0;
                long totalHits = 0;
                long totalNs = 0;
                int errors = 0;

                for (String q : msmarcoQueries) {
                    if (count >= 1000) break;
                    // Convert free-text to OR query (term1 OR term2 OR ...)
                    String orQuery = String.join(" OR ", q.trim()
                            .toLowerCase()
                            .replaceAll("[^a-z0-9 ]", " ")
                            .trim()
                            .split("\\s+"));
                    try {
                        t0 = System.nanoTime();
                        QueryResult result = index.search(orQuery, 10);
                        totalNs += System.nanoTime() - t0;
                        totalHits += result.totalHits();
                        count++;
                    } catch (Exception e) {
                        errors++;
                    }
                }

                System.out.printf("Queries:       %,d (errors: %d)%n", count, errors);
                System.out.printf("Total hits:    %,d%n", totalHits);
                System.out.printf("Avg latency:   %,d us%n", totalNs / count / 1_000);
                System.out.printf("Throughput:    %.0f qps%n", count * 1_000_000_000.0 / totalNs);
            }
        } catch (Exception e) {
            System.out.println("(Skipping MS MARCO queries: " + e.getMessage() + ")");
        }

        // Cleanup
        Files.deleteIfExists(segmentFile);
        System.out.println("\nDone.");
    }
}
