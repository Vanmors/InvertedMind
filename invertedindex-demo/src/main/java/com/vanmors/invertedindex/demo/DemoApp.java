package com.vanmors.invertedindex.demo;

import com.vanmors.invertedindex.core.*;
import com.vanmors.invertedindex.index.IndexBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class DemoApp {

    public static void main(String[] args) throws IOException {
        String msmarcoDir = System.getProperty("msmarco.dir", "data/msmarco");
        Path dataDir = Path.of(msmarcoDir);
        int numDocs  = Integer.parseInt(System.getProperty("msmarco.limit", "50000"));

        System.out.println("=== InvertedIndex Demo ===");
        System.out.println();

        List<String> corpus = loadPassages(dataDir, numDocs);

        // Build index
        Path tempDir = Files.createTempDirectory("invertedindex-demo");
        IndexConfig config = IndexConfig.builder(tempDir).build();
        IndexBuilder builder = new IndexBuilder(config);

        System.out.printf("Indexing %,d documents...%n", corpus.size());
        long t0 = System.currentTimeMillis();
        for (String passage : corpus) {
            builder.addDocument(passage);
        }
        Map<String, PostingList> postingLists = builder.buildPostingLists();
        CollectionStatistics stats = builder.buildStatistics();
        int[] docLengths = builder.getDocLengths();
        long elapsed = System.currentTimeMillis() - t0;

        System.out.printf("Index ready: %,d terms | %,d docs | %.1f avg tokens | %,d ms%n%n",
                postingLists.size(), stats.totalDocuments(),
                stats.averageDocumentLength(), elapsed);

        // Warm up JIT so first interactive query is fast
        InvertedIndex index = new InvertedIndex(postingLists, stats, docLengths);
        warmup(index, corpus);

        printHelp();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("query> ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) break;

            try {
                long startNs = System.nanoTime();
                QueryResult result = index.search(line, 10);
                long elapsedUs = (System.nanoTime() - startNs) / 1_000;

                System.out.printf("%,d hits  (%d us)%n", result.totalHits(), elapsedUs);
                for (ScoredDoc doc : result.results()) {
                    String text = corpus.get(doc.docId());
                    String snippet = text.length() > 120 ? text.substring(0, 117) + "..." : text;
                    System.out.printf("  [%6d] %.4f | %s%n", doc.docId(), doc.score(), snippet);
                }
                System.out.println();
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                System.out.println();
            }
        }

        try (var ds = Files.list(tempDir)) {
            ds.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
        Files.deleteIfExists(tempDir);
    }


    private static List<String> loadPassages(Path dataDir, int limit) throws IOException {
        Path tsvFile = dataDir.resolve("collection.tsv");
        if (!Files.exists(tsvFile)) {
            throw new IOException(
                    "collection.tsv not found in: " + dataDir.toAbsolutePath() + "\n" +
                    "Run the MS MARCO benchmark first to download the dataset:\n" +
                    "  mvn -pl invertedindex-core exec:exec " +
                    "-Dexec.args=\"-classpath %classpath " +
                    "com.vanmors.invertedindex.bench.MsMarcoBenchmark " + dataDir + " 1000\"");
        }

        System.out.printf("Loading %,d passages from %s...%n", limit, tsvFile.toAbsolutePath());
        List<String> passages = new ArrayList<>(limit > 0 ? limit : 1_000_000);
        try (BufferedReader reader = Files.newBufferedReader(tsvFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab >= 0) passages.add(line.substring(tab + 1));
                if (limit > 0 && passages.size() >= limit) break;
            }
        }
        System.out.printf("Loaded %,d passages%n", passages.size());
        return passages;
    }

    
    private static void warmup(InvertedIndex index, List<String> corpus) {
        String[] probes = {"the", "and", "of", "in", "to", "the AND of", "in OR to"};
        for (int i = 0; i < 2; i++) {
            for (String q : probes) {
                try { index.search(q, 1); } catch (Exception ignored) {}
            }
        }
    }

    private static void printHelp() {
        System.out.println("Query syntax:");
        System.out.println("  term                     single term");
        System.out.println("  t1 AND t2                AND");
        System.out.println("  t1 OR t2                 OR");
        System.out.println("  t1 AND NOT t2            AND NOT");
        System.out.println("  \"term1 term2\"             phrase (adjacent)");
        System.out.println("  t1 NEAR/N t2             proximity (within N positions)");
        System.out.println("  (t1 OR t2) AND t3        grouping");
        System.out.println("  quit / exit              exit");
        System.out.println();
    }
}
