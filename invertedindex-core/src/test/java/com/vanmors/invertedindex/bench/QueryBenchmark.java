package com.vanmors.invertedindex.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import com.vanmors.invertedindex.core.*;
import com.vanmors.invertedindex.index.IndexBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx2g"})
public class QueryBenchmark {

    private static final int RARE_MIN = 50, RARE_MAX = 100;

    private static final int MED_MIN = 500, MED_MAX = 2500;

    private static final int FREQ_MIN = 5000, FREQ_MAX = 10_000;

    private static final long TERM_PICK_SEED = 42L;

    private InvertedIndex index;

    // Rare queries
    private String rare_single, rare_and, rare_or, rare_andNot, rare_phrase, rare_near, rare_complex;

    // Medium queries
    private String med_single, med_and, med_or, med_andNot, med_phrase, med_near, med_complex;

    // Frequent queries
    private String freq_single, freq_and, freq_or, freq_andNot, freq_phrase, freq_near, freq_complex;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path dataDir = Path.of(System.getProperty("msmarco.dir", "data/msmarco"));

        List<String> passages = MsMarcoLoader.loadPassages(dataDir, 100_000);
        IndexConfig config = IndexConfig.builder(Path.of("/tmp/bench")).build();
        IndexBuilder builder = new IndexBuilder(config);
        for (String passage : passages) {
            builder.addDocument(passage);
        }
        Map<String, PostingList> postingLists = builder.buildPostingLists();
        index = new InvertedIndex(postingLists, builder.buildStatistics(), builder.getDocLengths());

        String[] rare = selectTerms(postingLists, RARE_MIN, RARE_MAX, 4);
        String[] med = selectTerms(postingLists, MED_MIN, MED_MAX, 4);
        String[] freq = selectTerms(postingLists, FREQ_MIN, FREQ_MAX, 4);

        rare_single = rare[0];
        rare_and = rare[0] + " AND " + rare[1];
        rare_or = rare[0] + " OR " + rare[1];
        rare_andNot = rare[0] + " AND NOT " + rare[1];
        rare_phrase = "\"" + rare[0] + " " + rare[1] + "\"";
        rare_near = rare[0] + " NEAR/5 " + rare[1];
        rare_complex = "(" + rare[0] + " OR " + rare[1] + ") AND " + rare[2] + " AND NOT " + rare[3];

        med_single = med[0];
        med_and = med[0] + " AND " + med[1];
        med_or = med[0] + " OR " + med[1];
        med_andNot = med[0] + " AND NOT " + med[1];
        med_phrase = "\"" + med[0] + " " + med[1] + "\"";
        med_near = med[0] + " NEAR/5 " + med[1];
        med_complex = "(" + med[0] + " OR " + med[1] + ") AND " + med[2] + " AND NOT " + med[3];

        freq_single = freq[0];
        freq_and = freq[0] + " AND " + freq[1];
        freq_or = freq[0] + " OR " + freq[1];
        freq_andNot = freq[0] + " AND NOT " + freq[1];
        freq_phrase = "\"" + freq[0] + " " + freq[1] + "\"";
        freq_near = freq[0] + " NEAR/5 " + freq[1];
        freq_complex = "(" + freq[0] + " OR " + freq[1] + ") AND " + freq[2] + " AND NOT " + freq[3];

        System.out.printf("%nRare:     %s%n", describe(postingLists, rare));
        System.out.printf("Medium:   %s%n", describe(postingLists, med));
        System.out.printf("Frequent: %s%n", describe(postingLists, freq));
    }

    private static String[] selectTerms(Map<String, PostingList> postingLists,
                                        int minDf, int maxDf, int k) {
        List<String> candidates = new ArrayList<>();
        for (var entry : postingLists.entrySet()) {
            int df = entry.getValue().documentFrequency();
            if (df < minDf || df > maxDf) {
                continue;
            }
            String term = entry.getKey();
            if (term.length() < 4) {
                continue;
            }
            if (!isLowerAscii(term)) {
                continue;
            }
            candidates.add(term);
        }
        if (candidates.size() < k) {
            throw new IllegalStateException(
                    "Not enough terms in df range [" + minDf + ", " + maxDf + "]: "
                            + candidates.size() + " < " + k);
        }
        Collections.sort(candidates);
        Collections.shuffle(candidates, new Random(TERM_PICK_SEED));
        return candidates.subList(0, k).toArray(new String[0]);
    }

    private static boolean isLowerAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return true;
    }

    private static String describe(Map<String, PostingList> postingLists, String[] terms) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < terms.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(terms[i]).append("(df=")
                    .append(postingLists.get(terms[i]).documentFrequency()).append(')');
        }
        return sb.toString();
    }

    // Rare
    @Benchmark
    public void rare_singleTerm(Blackhole bh) {
        bh.consume(index.search(rare_single, 10));
    }

    @Benchmark
    public void rare_and(Blackhole bh) {
        bh.consume(index.search(rare_and, 10));
    }

    @Benchmark
    public void rare_or(Blackhole bh) {
        bh.consume(index.search(rare_or, 10));
    }

    @Benchmark
    public void rare_andNot(Blackhole bh) {
        bh.consume(index.search(rare_andNot, 10));
    }

    @Benchmark
    public void rare_phrase(Blackhole bh) {
        bh.consume(index.search(rare_phrase, 10));
    }

    @Benchmark
    public void rare_near(Blackhole bh) {
        bh.consume(index.search(rare_near, 10));
    }

    @Benchmark
    public void rare_complex(Blackhole bh) {
        bh.consume(index.search(rare_complex, 10));
    }

    // Medium
    @Benchmark
    public void med_singleTerm(Blackhole bh) {
        bh.consume(index.search(med_single, 10));
    }

    @Benchmark
    public void med_and(Blackhole bh) {
        bh.consume(index.search(med_and, 10));
    }

    @Benchmark
    public void med_or(Blackhole bh) {
        bh.consume(index.search(med_or, 10));
    }

    @Benchmark
    public void med_andNot(Blackhole bh) {
        bh.consume(index.search(med_andNot, 10));
    }

    @Benchmark
    public void med_phrase(Blackhole bh) {
        bh.consume(index.search(med_phrase, 10));
    }

    @Benchmark
    public void med_near(Blackhole bh) {
        bh.consume(index.search(med_near, 10));
    }

    @Benchmark
    public void med_complex(Blackhole bh) {
        bh.consume(index.search(med_complex, 10));
    }

    // Frequent
    @Benchmark
    public void freq_singleTerm(Blackhole bh) {
        bh.consume(index.search(freq_single, 10));
    }

    @Benchmark
    public void freq_and(Blackhole bh) {
        bh.consume(index.search(freq_and, 10));
    }

    @Benchmark
    public void freq_or(Blackhole bh) {
        bh.consume(index.search(freq_or, 10));
    }

    @Benchmark
    public void freq_andNot(Blackhole bh) {
        bh.consume(index.search(freq_andNot, 10));
    }

    @Benchmark
    public void freq_phrase(Blackhole bh) {
        bh.consume(index.search(freq_phrase, 10));
    }

    @Benchmark
    public void freq_near(Blackhole bh) {
        bh.consume(index.search(freq_near, 10));
    }

    @Benchmark
    public void freq_complex(Blackhole bh) {
        bh.consume(index.search(freq_complex, 10));
    }
}
