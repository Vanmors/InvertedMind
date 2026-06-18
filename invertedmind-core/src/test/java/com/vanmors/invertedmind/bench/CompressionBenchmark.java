package com.vanmors.invertedmind.bench;

import com.vanmors.invertedmind.core.*;
import com.vanmors.invertedmind.index.IndexBuilder;
import com.vanmors.invertedmind.util.VarIntUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CompressionBenchmark {

    // df bucket boundaries: [0,10) [10,100) [100,1000) [1000,∞)
    private static final int[] BUCKET_LIMITS  = {10, 100, 1000, Integer.MAX_VALUE};
    private static final String[] BUCKET_LABELS = {"df < 10", "10 ≤ df < 100", "100 ≤ df < 1000", "df ≥ 1000"};

    public static void main(String[] args) throws IOException {
        Path dataDir = args.length > 0 ? Path.of(args[0]) : Path.of("data/msmarco");
        int numDocs  = args.length > 1 ? Integer.parseInt(args[1]) : 50_000;

        System.out.printf("Loading %,d MS MARCO passages...%n", numDocs);
        List<String> passages = MsMarcoLoader.loadPassages(dataDir, numDocs);

        IndexConfig config = IndexConfig.builder(Path.of("/tmp/bench-compression")).build();
        IndexBuilder builder = new IndexBuilder(config);

        System.out.println("Building index...");
        long t0 = System.currentTimeMillis();
        for (String passage : passages) builder.addDocument(passage);
        Map<String, PostingList> postingLists = builder.buildPostingLists();
        System.out.printf("Done in %,d ms — %,d unique terms%n%n",
                System.currentTimeMillis() - t0, postingLists.size());

        // ── Accumulators ──────────────────────────────────────────────────────
        long rawDocIds = 0,    compDocIds = 0;
        long rawFreqs  = 0,    compFreqs  = 0;
        long rawPos    = 0,    compPos    = 0;
        long skipBytes = 0;

        long[] rawDocIdBucket  = new long[BUCKET_LIMITS.length];
        long[] compDocIdBucket = new long[BUCKET_LIMITS.length];
        long[] termsBucket     = new long[BUCKET_LIMITS.length];

        for (PostingList pl : postingLists.values()) {
            int  df  = pl.documentFrequency();
            long ttf = pl.totalTermFrequency();

            long rDid  = (long) df  * Integer.BYTES;
            long cDid  = pl.compressedDocIds().remaining();
            long rFreq = (long) df  * Integer.BYTES;
            long cFreq = pl.compressedFreqs().remaining();
            long rPos  = ttf * Integer.BYTES;
            long cPos  = pl.compressedPositions().remaining();

            rawDocIds  += rDid;   compDocIds  += cDid;
            rawFreqs   += rFreq;  compFreqs   += cFreq;
            rawPos     += rPos;   compPos     += cPos;

            // Skip list metadata written as VarInts in SegmentWriter
            // 5 VarInts per skip entry + 1 VarInt for the count
            skipBytes  += estimateSkipBytes(pl);

            int bucket = bucket(df);
            rawDocIdBucket[bucket]  += rDid;
            compDocIdBucket[bucket] += cDid;
            termsBucket[bucket]++;
        }

        long rawTotal  = rawDocIds + rawFreqs + rawPos;
        long compTotal = compDocIds + compFreqs + compPos + skipBytes;

        // ── Report ────────────────────────────────────────────────────────────
        p("── Compression Efficiency — MS MARCO %dK docs ──────────────────────", numDocs / 1000);
        p("");
        p("  %-12s  %9s  %11s  %7s  %-25s",
                "Component", "Raw", "Compressed", "Ratio", "Method");
        p("  " + "─".repeat(68));
        printRow("docIds",    rawDocIds, compDocIds, "delta-encode → PForDelta (128-val blocks)");
        printRow("freqs",     rawFreqs,  compFreqs,  "VarInt");
        printRow("positions", rawPos,    compPos,    "delta-encode → VarInt");
        p("  %-12s  %9s  %11s", "(skip meta)", "", mb(skipBytes));
        p("  " + "─".repeat(68));
        printRow("TOTAL",     rawTotal, compTotal, "");
        p("");

        p("── DocId Compression by Posting List Length (df) ──────────────────────");
        p("");
        p("  %-20s  %8s  %9s  %11s  %7s",
                "Bucket", "Terms", "Raw", "Compressed", "Ratio");
        p("  " + "─".repeat(60));
        for (int b = 0; b < BUCKET_LIMITS.length; b++) {
            if (termsBucket[b] == 0) continue;
            p("  %-20s  %,8d  %9s  %11s  %6.2f\u00d7",
                    BUCKET_LABELS[b],
                    termsBucket[b],
                    mb(rawDocIdBucket[b]),
                    mb(compDocIdBucket[b]),
                    ratio(rawDocIdBucket[b], compDocIdBucket[b]));
        }
        p("");

        p("── Per-term Average (across all %d terms) ────────────────────────", postingLists.size());
        p("");
        long terms = postingLists.size();
        p("  Avg raw docId bytes/term:   %6.0f B", (double) rawDocIds  / terms);
        p("  Avg comp docId bytes/term:  %6.0f B  (%.2f\u00d7)",
                (double) compDocIds / terms, ratio(rawDocIds, compDocIds));
        p("  Avg raw freq bytes/term:    %6.0f B", (double) rawFreqs   / terms);
        p("  Avg comp freq bytes/term:   %6.0f B  (%.2f\u00d7)",
                (double) compFreqs  / terms, ratio(rawFreqs,  compFreqs));
        p("  Avg raw pos bytes/term:     %6.0f B", (double) rawPos     / terms);
        p("  Avg comp pos bytes/term:    %6.0f B  (%.2f\u00d7)",
                (double) compPos    / terms, ratio(rawPos,    compPos));
        p("");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    
    private static long estimateSkipBytes(PostingList pl) {
        int skipCount = pl.skipDocIds().length;
        long bytes = varIntSize(skipCount);
        for (int i = 0; i < skipCount; i++) {
            bytes += varIntSize(pl.skipDocIds()[i]);
            bytes += varIntSize(pl.skipDocIdOffsets()[i]);
            bytes += varIntSize(pl.skipFreqOffsets()[i]);
            bytes += varIntSize(pl.skipPosOffsets()[i]);
            bytes += varIntSize(pl.skipCumulativeCounts()[i]);
        }
        return bytes;
    }

    
    private static int varIntSize(int v) {
        if (v < 0x80)       return 1;
        if (v < 0x4000)     return 2;
        if (v < 0x200000)   return 3;
        if (v < 0x10000000) return 4;
        return 5;
    }

    private static int bucket(int df) {
        for (int b = 0; b < BUCKET_LIMITS.length; b++) {
            if (df < BUCKET_LIMITS[b]) return b;
        }
        return BUCKET_LIMITS.length - 1;
    }

    private static void printRow(String name, long raw, long comp, String method) {
        p("  %-12s  %9s  %11s  %6.2f\u00d7  %-25s",
                name, mb(raw), mb(comp), ratio(raw, comp), method);
    }

    
    private static void p(String fmt, Object... args) {
        System.out.printf(Locale.ROOT, fmt + "%n", args);
    }

    private static String mb(long bytes) {
        if (bytes >= 1_000_000) return String.format(Locale.ROOT, "%.2f MB", bytes / 1_000_000.0);
        if (bytes >= 1_000)     return String.format(Locale.ROOT, "%.1f KB", bytes / 1_000.0);
        return bytes + " B";
    }

    private static double ratio(long raw, long comp) {
        return comp == 0 ? 0.0 : (double) raw / comp;
    }
}
