/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.benchmark;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PDimension;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.util.GraphicsRenderingHints;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Phase 0 baseline harness for the object/content parser performance work.
 * <p>
 * This is intentionally <b>not</b> a unit test - it is a standalone runner so it can be driven under a profiler
 * (async-profiler / JFR) with whatever JVM flags are needed.  It measures three numbers per document so we can
 * target real contention instead of guessing:
 * <ul>
 *     <li><b>open</b>   - {@link Document#setFile(String)}: header + cross-reference + trailer parse (cold object path).</li>
 *     <li><b>serial</b> - a single thread initialising every page in turn ({@link Page#init()}).  Pure parse
 *         throughput with no pool contention.</li>
 *     <li><b>parallel</b> - every page initialised concurrently on a fixed pool.  This is the path that exercises
 *         the shared file {@code ByteBuffer} lock and the {@code ObjectLoader} monitor; the serial/parallel ratio
 *         is our contention signal.</li>
 * </ul>
 * Each measured iteration uses a <i>fresh</i> {@link Document} so the {@code Library} object cache is cold and the
 * numbers reflect a real open-and-render, not a warm re-parse.
 *
 * <h2>Usage</h2>
 * <pre>
 *   ./gradlew :core:core-awt:parsingBenchmark \
 *       -Picepdf.benchmark.dir=/path/to/corpus \
 *       -Picepdf.benchmark.threads=8 \
 *       -Picepdf.benchmark.jfr=build/parse.jfr
 * </pre>
 * or directly:
 * <pre>
 *   java -cp &lt;test-runtime-cp&gt; org.icepdf.core.benchmark.ParsingBenchmark /path/to/corpus
 * </pre>
 *
 * <h2>Properties</h2>
 * <ul>
 *     <li>{@code icepdf.benchmark.dir}     - directory (recursively scanned for *.pdf) or single file. Falls back to args.</li>
 *     <li>{@code icepdf.benchmark.warmup}  - warmup iterations per document (default 1).</li>
 *     <li>{@code icepdf.benchmark.iters}   - measured iterations per document (default 3).</li>
 *     <li>{@code icepdf.benchmark.threads} - pool size for the parallel sweep (default availableProcessors).</li>
 *     <li>{@code icepdf.benchmark.mode}    - open | serial | parallel | all (default all).</li>
 *     <li>{@code icepdf.benchmark.paint}   - also rasterise each page after init (default false).</li>
 *     <li>{@code icepdf.benchmark.maxPages}- cap pages per document, 0 = no cap (default 0).</li>
 *     <li>{@code icepdf.benchmark.jfr}     - if set, record a JFR with lock events to this path.</li>
 * </ul>
 */
public class ParsingBenchmark {

    private static final int WARMUP = intProp("icepdf.benchmark.warmup", 1);
    private static final int ITERS = intProp("icepdf.benchmark.iters", 3);
    private static final int THREADS = intProp("icepdf.benchmark.threads", Runtime.getRuntime().availableProcessors());
    private static final int MAX_PAGES = intProp("icepdf.benchmark.maxPages", 0);
    private static final String MODE = System.getProperty("icepdf.benchmark.mode", "all");
    private static final boolean PAINT = Boolean.getBoolean("icepdf.benchmark.paint");
    private static final boolean CHILD = Boolean.getBoolean("icepdf.benchmark.child");
    private static final float PAINT_ZOOM = 1.0f;

    public static void main(String[] args) throws Exception {
        quietLogging();
        List<Path> corpus = resolveCorpus(args);
        if (corpus.isEmpty()) {
            System.err.println("No PDFs found. Pass a file/dir as an argument or set -Dicepdf.benchmark.dir=...");
            System.exit(2);
        }
        // To keep numbers trustworthy, each file runs in its own JVM by default: running several large files in one
        // JVM lets heap garbage from earlier files distort the timing of later ones (observed swings of 0.4x-2.7x on
        // the same file). The parent forks one child per file; disable with -Dicepdf.benchmark.fork=false.
        boolean fork = !Boolean.FALSE.toString().equals(System.getProperty("icepdf.benchmark.fork"));
        if (fork && corpus.size() > 1) {
            runForked(corpus);
            return;
        }

        System.out.printf("ICEpdf parsing benchmark | files=%d warmup=%d iters=%d threads=%d mode=%s paint=%b%n",
                corpus.size(), WARMUP, ITERS, THREADS, MODE, PAINT);
        if (!CHILD) System.out.println(header());

        JfrRecorder jfr = JfrRecorder.startIfRequested();
        List<Result> results = new ArrayList<>();
        try {
            for (Path pdf : corpus) {
                try {
                    Result r = benchmark(pdf);
                    results.add(r);
                    System.out.println(r);
                } catch (Throwable t) {
                    System.out.printf("%-40s  FAILED: %s%n", trim(pdf.getFileName().toString(), 40), t);
                }
            }
        } finally {
            if (jfr != null) jfr.stopAndDump();
        }
        if (!CHILD) printSummary(results);
    }

    /**
     * Run each file in a fresh child JVM so heap state never leaks between files. The parent prints the header and
     * launches a child per file (inheriting IO so each child prints its own row); per-file rows are the trustworthy
     * output, so no cross-file aggregate is printed here.
     */
    private static void runForked(List<Path> corpus) throws Exception {
        System.out.printf("ICEpdf parsing benchmark | files=%d warmup=%d iters=%d threads=%d mode=%s paint=%b"
                        + " | forked one JVM per file%n",
                corpus.size(), WARMUP, ITERS, THREADS, MODE, PAINT);
        System.out.println(header());

        String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin"
                + java.io.File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String heap = System.getProperty("icepdf.benchmark.heap");
        String jfrBase = System.getProperty("icepdf.benchmark.jfr");

        for (Path pdf : corpus) {
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBin);
            if (heap != null && !heap.isEmpty()) cmd.add("-Xmx" + heap);
            cmd.add("-cp");
            cmd.add(classpath);
            // forward the knobs that shape a single-file run
            forwardProp(cmd, "icepdf.benchmark.warmup");
            forwardProp(cmd, "icepdf.benchmark.iters");
            forwardProp(cmd, "icepdf.benchmark.threads");
            forwardProp(cmd, "icepdf.benchmark.mode");
            forwardProp(cmd, "icepdf.benchmark.paint");
            forwardProp(cmd, "icepdf.benchmark.maxPages");
            forwardProp(cmd, "icepdf.benchmark.verboseLogging");
            if (jfrBase != null && !jfrBase.isEmpty()) {
                cmd.add("-Dicepdf.benchmark.jfr=" + jfrBase + "." + pdf.getFileName());
            }
            cmd.add("-Dicepdf.benchmark.fork=false");
            cmd.add("-Dicepdf.benchmark.child=true");
            cmd.add(ParsingBenchmark.class.getName());
            cmd.add(pdf.toString());

            Process p = new ProcessBuilder(cmd).inheritIO().start();
            int exit = p.waitFor();
            if (exit != 0) {
                System.out.printf("%-40s  child JVM exited %d%n", trim(pdf.getFileName().toString(), 40), exit);
            }
        }
    }

    private static void forwardProp(List<String> cmd, String key) {
        String v = System.getProperty(key);
        if (v != null && !v.isEmpty()) cmd.add("-D" + key + "=" + v);
    }

    private static Result benchmark(Path pdf) throws Exception {
        // warmup - not recorded, lets the JIT settle.
        for (int i = 0; i < WARMUP; i++) {
            runOnce(pdf, false);
        }
        long openBest = Long.MAX_VALUE;
        long serialBest = Long.MAX_VALUE;
        long parallelBest = Long.MAX_VALUE;
        int pages = 0;
        for (int i = 0; i < ITERS; i++) {
            Sample s = runOnce(pdf, true);
            pages = s.pages;
            openBest = Math.min(openBest, s.openNanos);
            if (s.serialNanos > 0) serialBest = Math.min(serialBest, s.serialNanos);
            if (s.parallelNanos > 0) parallelBest = Math.min(parallelBest, s.parallelNanos);
        }
        return new Result(pdf.getFileName().toString(), pages, sizeKb(pdf),
                openBest, serialBest == Long.MAX_VALUE ? 0 : serialBest,
                parallelBest == Long.MAX_VALUE ? 0 : parallelBest);
    }

    /**
     * One measured pass.  Uses a fresh Document per sweep so the object cache is cold for each measurement.
     */
    private static Sample runOnce(Path pdf, boolean measured) throws Exception {
        Sample sample = new Sample();
        boolean wantSerial = MODE.equals("all") || MODE.equals("serial");
        boolean wantParallel = MODE.equals("all") || MODE.equals("parallel");
        boolean wantOpenOnly = MODE.equals("open");

        // open timing (always - it is the cross-reference / object parse cold path)
        {
            Document doc = new Document();
            long t0 = System.nanoTime();
            doc.setFile(pdf.toString());
            sample.openNanos = System.nanoTime() - t0;
            sample.pages = pageCount(doc);
            doc.dispose();
        }
        if (wantOpenOnly) return sample;

        if (wantSerial) {
            Document doc = new Document();
            doc.setFile(pdf.toString());
            int n = pageCount(doc);
            PageTree tree = doc.getCatalog().getPageTree();
            long t0 = System.nanoTime();
            for (int p = 0; p < n; p++) {
                initPage(tree.getPage(p));
            }
            sample.serialNanos = System.nanoTime() - t0;
            doc.dispose();
        }

        if (wantParallel) {
            Document doc = new Document();
            doc.setFile(pdf.toString());
            int n = pageCount(doc);
            PageTree tree = doc.getCatalog().getPageTree();
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            List<Callable<Void>> tasks = new ArrayList<>(n);
            for (int p = 0; p < n; p++) {
                Page page = tree.getPage(p);
                tasks.add(() -> {
                    initPage(page);
                    return null;
                });
            }
            long t0 = System.nanoTime();
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    // record nothing - a single bad page should not abort the sweep timing.
                }
            }
            sample.parallelNanos = System.nanoTime() - t0;
            pool.shutdownNow();
            doc.dispose();
        }
        return sample;
    }

    private static void initPage(Page page) throws InterruptedException {
        page.resetInitializedState();
        page.init();
        if (PAINT) {
            PDimension bounds = page.getSize(Page.BOUNDARY_CROPBOX, 0, PAINT_ZOOM);
            int w = Math.max(1, (int) bounds.getWidth());
            int h = Math.max(1, (int) bounds.getHeight());
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            page.paint(g2d, GraphicsRenderingHints.SCREEN, Page.BOUNDARY_CROPBOX, 0, PAINT_ZOOM);
            g2d.dispose();
            image.flush();
        }
    }

    private static int pageCount(Document doc) {
        int n = doc.getNumberOfPages();
        return MAX_PAGES > 0 ? Math.min(n, MAX_PAGES) : n;
    }

    // ---- corpus resolution -------------------------------------------------

    private static List<Path> resolveCorpus(String[] args) throws IOException {
        List<Path> roots = new ArrayList<>();
        String prop = System.getProperty("icepdf.benchmark.dir");
        if (prop != null && !prop.isEmpty()) roots.add(Paths.get(prop));
        for (String a : args) roots.add(Paths.get(a));

        List<Path> pdfs = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isRegularFile(root)) {
                pdfs.add(root);
            } else if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    pdfs.addAll(walk.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                            .collect(Collectors.toList()));
                }
            }
        }
        pdfs.sort(Comparator.comparing(Path::toString));
        return pdfs;
    }

    // ---- reporting ---------------------------------------------------------

    private static String header() {
        return String.format("%-40s %6s %8s %10s %10s %10s %8s",
                "file", "pages", "sizeKB", "open(ms)", "serial(ms)", "par(ms)", "speedup");
    }

    private static void printSummary(List<Result> results) {
        if (results.isEmpty()) return;
        double openSum = 0, serialSum = 0, parallelSum = 0;
        double speedupSum = 0;
        int speedupCount = 0;
        for (Result r : results) {
            openSum += ms(r.openNanos);
            serialSum += ms(r.serialNanos);
            parallelSum += ms(r.parallelNanos);
            if (r.serialNanos > 0 && r.parallelNanos > 0) {
                speedupSum += (double) r.serialNanos / r.parallelNanos;
                speedupCount++;
            }
        }
        System.out.println("-".repeat(header().length()));
        System.out.printf("%-40s %6s %8s %10.1f %10.1f %10.1f %8s%n",
                "TOTAL (" + results.size() + " files)", "", "",
                openSum, serialSum, parallelSum, "");
        if (speedupCount > 0) {
            System.out.printf("mean parallel speedup (serial/par) across %d files: %.2fx with %d threads%n",
                    speedupCount, speedupSum / speedupCount, THREADS);
            System.out.printf("(linear would be ~%dx; the gap is the serialization tax we are hunting)%n", THREADS);
        }
    }

    // ---- small helpers -----------------------------------------------------

    /**
     * Parsing malformed/unsupported font tables logs a flood of WARNINGs.  java.util.logging handlers are
     * synchronized, so under the parallel sweep those log calls serialize the worker threads and pollute the
     * timing (and the report).  Silence the noisy loggers unless the caller wants them.
     */
    private static void quietLogging() {
        if (Boolean.getBoolean("icepdf.benchmark.verboseLogging")) return;
        java.util.logging.Logger.getLogger("org.icepdf").setLevel(java.util.logging.Level.SEVERE);
        java.util.logging.Logger.getLogger("org.apache.fontbox").setLevel(java.util.logging.Level.SEVERE);
        java.util.logging.Logger.getLogger("org.apache.pdfbox").setLevel(java.util.logging.Level.SEVERE);
    }

    private static int intProp(String key, int def) {
        String v = System.getProperty(key);
        if (v == null || v.isEmpty()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long sizeKb(Path p) {
        try {
            return Files.size(p) / 1024;
        } catch (IOException e) {
            return -1;
        }
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static final class Sample {
        int pages;
        long openNanos;
        long serialNanos;
        long parallelNanos;
    }

    private static final class Result {
        final String file;
        final int pages;
        final long sizeKb;
        final long openNanos;
        final long serialNanos;
        final long parallelNanos;

        Result(String file, int pages, long sizeKb, long openNanos, long serialNanos, long parallelNanos) {
            this.file = file;
            this.pages = pages;
            this.sizeKb = sizeKb;
            this.openNanos = openNanos;
            this.serialNanos = serialNanos;
            this.parallelNanos = parallelNanos;
        }

        @Override
        public String toString() {
            String speedup = (serialNanos > 0 && parallelNanos > 0)
                    ? String.format("%.2fx", (double) serialNanos / parallelNanos) : "-";
            return String.format("%-40s %6d %8d %10.1f %10.1f %10.1f %8s",
                    trim(file, 40), pages, sizeKb, ms(openNanos),
                    serialNanos > 0 ? ms(serialNanos) : 0.0,
                    parallelNanos > 0 ? ms(parallelNanos) : 0.0,
                    speedup);
        }
    }

    /**
     * Wrapper over jdk.jfr that records CPU, allocation, GC and lock contention together so we can find the real
     * bottleneck without a preconception.  Built on the JDK's "profile" configuration, with the monitor/park events
     * forced on at a low threshold.  Inspect with JDK Mission Control or e.g.
     * {@code jfr print --events jdk.JavaMonitorEnter,jdk.ObjectAllocationSample,jdk.GCPhasePause parse.jfr}.
     */
    private static final class JfrRecorder {
        private final jdk.jfr.Recording recording;

        private JfrRecorder(jdk.jfr.Recording recording) {
            this.recording = recording;
        }

        static JfrRecorder startIfRequested() {
            String path = System.getProperty("icepdf.benchmark.jfr");
            if (path == null || path.isEmpty()) return null;
            try {
                jdk.jfr.Recording r;
                try {
                    r = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
                } catch (Exception noConfig) {
                    r = new jdk.jfr.Recording();
                }
                // make sure lock contention is captured regardless of the base config thresholds
                r.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace();
                r.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(1)).withStackTrace();
                r.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1)).withStackTrace();
                r.setDestination(Paths.get(path));
                r.start();
                System.out.println("JFR recording (cpu+alloc+gc+locks) -> " + path);
                return new JfrRecorder(r);
            } catch (Exception e) {
                System.err.println("Could not start JFR recording: " + e);
                return null;
            }
        }

        void stopAndDump() {
            try {
                recording.stop();
                recording.close();
                System.out.println("JFR recording written.");
            } catch (Exception e) {
                System.err.println("Could not finalise JFR recording: " + e);
            }
        }
    }
}