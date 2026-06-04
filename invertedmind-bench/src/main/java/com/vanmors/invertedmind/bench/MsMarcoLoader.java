package com.vanmors.invertedmind.bench;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Loads the MS MARCO Passages dataset for benchmarking.
 * <p>
 * Dataset format: TSV with columns [passageId, passageText].
 * Full collection: ~8.8M passages, ~1GB text.
 * <p>
 * Usage:
 * <pre>
 *   // Load first 100K passages (downloads if needed)
 *   List&lt;String&gt; passages = MsMarcoLoader.loadPassages(Path.of("data"), 100_000);
 *
 *   // Load all passages
 *   List&lt;String&gt; all = MsMarcoLoader.loadPassages(Path.of("data"), -1);
 *
 *   // Load queries
 *   List&lt;String&gt; queries = MsMarcoLoader.loadQueries(Path.of("data"));
 * </pre>
 */
public final class MsMarcoLoader {

    private static final String COLLECTION_URL =
            "https://msmarco.z22.web.core.windows.net/msmarcoranking/collection.tar.gz";
    private static final String QUERIES_URL =
            "https://msmarco.z22.web.core.windows.net/msmarcoranking/queries.tar.gz";

    private static final String COLLECTION_FILE = "collection.tsv";
    private static final String QUERIES_FILE = "queries.dev.tsv";

    private MsMarcoLoader() {}

    /**
     * Loads passages from MS MARCO. Downloads and extracts if not cached.
     *
     * @param dataDir directory to cache downloaded files
     * @param limit   max passages to load, or -1 for all
     * @return list of passage texts
     */
    public static List<String> loadPassages(Path dataDir, int limit) throws IOException {
        Path tsvFile = dataDir.resolve(COLLECTION_FILE);
        if (!Files.exists(tsvFile)) {
            System.out.println("Downloading MS MARCO collection...");
            downloadAndExtractTarGz(COLLECTION_URL, dataDir);
        }

        System.out.printf("Loading passages from %s (limit=%s)...%n",
                tsvFile.getFileName(), limit < 0 ? "all" : limit);

        List<String> passages = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(tsvFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                passages.add(line.substring(tab + 1));
                if (limit > 0 && passages.size() >= limit) break;
            }
        }

        System.out.printf("Loaded %,d passages%n", passages.size());
        return passages;
    }

    /**
     * Streams passages one by one to avoid loading everything into memory.
     *
     * @param dataDir  directory with cached files
     * @param limit    max passages, or -1 for all
     * @param consumer callback for each passage text
     */
    public static int streamPassages(Path dataDir, int limit, Consumer<String> consumer) throws IOException {
        Path tsvFile = dataDir.resolve(COLLECTION_FILE);
        if (!Files.exists(tsvFile)) {
            System.out.println("Downloading MS MARCO collection...");
            downloadAndExtractTarGz(COLLECTION_URL, dataDir);
        }

        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(tsvFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                consumer.accept(line.substring(tab + 1));
                count++;
                if (limit > 0 && count >= limit) break;
            }
        }
        return count;
    }

    /**
     * Loads dev queries from MS MARCO.
     *
     * @param dataDir directory to cache downloaded files
     * @return list of query strings
     */
    public static List<String> loadQueries(Path dataDir) throws IOException {
        Path tsvFile = dataDir.resolve(QUERIES_FILE);
        if (!Files.exists(tsvFile)) {
            System.out.println("Downloading MS MARCO queries...");
            downloadAndExtractTarGz(QUERIES_URL, dataDir);
        }

        List<String> queries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(tsvFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                queries.add(line.substring(tab + 1));
            }
        }

        System.out.printf("Loaded %,d queries%n", queries.size());
        return queries;
    }

    private static void downloadAndExtractTarGz(String url, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
            }

            try (InputStream body = response.body();
                 GZIPInputStream gzip = new GZIPInputStream(body, 65536)) {
                extractTar(gzip, outputDir);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }

        System.out.println("Extracted to " + outputDir);
    }

    /**
     * Minimal tar extractor — handles only regular files (enough for MS MARCO archives).
     */
    private static void extractTar(InputStream in, Path outputDir) throws IOException {
        byte[] header = new byte[512];
        while (true) {
            int read = in.readNBytes(header, 0, 512);
            if (read < 512) break;

            // Check for end-of-archive (two zero blocks)
            boolean allZero = true;
            for (int i = 0; i < 512; i++) {
                if (header[i] != 0) { allZero = false; break; }
            }
            if (allZero) break;

            // Parse filename (bytes 0-99)
            String name = new String(header, 0, 100, StandardCharsets.US_ASCII).trim();
            // Remove null bytes
            int nullIdx = name.indexOf('\0');
            if (nullIdx >= 0) name = name.substring(0, nullIdx);

            // Parse size (bytes 124-135, octal)
            String sizeStr = new String(header, 124, 11, StandardCharsets.US_ASCII).trim();
            long size = sizeStr.isEmpty() ? 0 : Long.parseLong(sizeStr, 8);

            // Parse type (byte 156): '0' or '\0' = regular file, '5' = directory
            byte typeFlag = header[156];

            if (typeFlag == '0' || typeFlag == 0) {
                // Regular file
                Path outFile = outputDir.resolve(name);
                Files.createDirectories(outFile.getParent());
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outFile))) {
                    long remaining = size;
                    byte[] buf = new byte[8192];
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        int n = in.readNBytes(buf, 0, toRead);
                        if (n <= 0) break;
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                }
                System.out.printf("  Extracted: %s (%,d bytes)%n", name, size);
            } else {
                // Skip non-file entries
                long remaining = size;
                while (remaining > 0) {
                    long skipped = in.skip(remaining);
                    if (skipped <= 0) break;
                    remaining -= skipped;
                }
            }

            // Tar blocks are 512-byte aligned
            long padding = (512 - (size % 512)) % 512;
            in.skip(padding);
        }
    }

    /**
     * CLI: downloads and indexes MS MARCO, runs sample queries.
     */
    public static void main(String[] args) throws IOException {
        Path dataDir = args.length > 0 ? Path.of(args[0]) : Path.of("data/msmarco");
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;

        List<String> passages = loadPassages(dataDir, limit);
        System.out.printf("First passage: %.80s...%n", passages.get(0));
        System.out.printf("Last passage:  %.80s...%n", passages.get(passages.size() - 1));
    }
}
