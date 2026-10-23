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

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.ri.util.qa.PageCapture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Captures the first pages of each document as PNGs, the way the QA app's capture set does
 * ({@link PageCapture}: screen hints, crop box, no rotation, zoom 1), headless.  Run it once per
 * ICEpdf build, each with that build's jars on the class path, then compare the two output folders
 * with {@link Compare}.  See README.md.
 * <p>
 * {@code java CaptureRun <docs> <out dir> [doc threads] [pages per doc]}: {@code docs} is a folder of
 * PDFs (searched recursively) or a text file listing one PDF path per line.  Two threads capture
 * the pages of each document at once, as the QA app does, so threading bugs still show up.  A
 * document that takes longer than 180s is abandoned; failures and timeouts go to
 * {@code <out dir>/log.txt}.
 */
public class CaptureRun {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: CaptureRun <pdf folder | list file> <out dir> [doc threads=4] [pages=3]");
            System.exit(2);
        }
        List<String> docs = documents(Paths.get(args[0]));
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        int pagesPerDoc = args.length > 3 ? Integer.parseInt(args[3]) : 3;

        ExecutorService docPool = Executors.newFixedThreadPool(threads);
        AtomicInteger done = new AtomicInteger();
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        long t0 = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (String doc : docs) {
            futures.add(docPool.submit(() -> {
                String name = Paths.get(doc).getFileName().toString();
                ExecutorService pagePool = Executors.newFixedThreadPool(2);
                PageCapture capture = new PageCapture();
                try {
                    int pages = Math.min(pagesPerDoc, capture.load(Paths.get(doc)));
                    List<Future<?>> pageFutures = new ArrayList<>();
                    for (int p = 0; p < pages; p++) {
                        int page = p;
                        pageFutures.add(pagePool.submit(() -> {
                            BufferedImage image = capture.capture(page, GraphicsRenderingHints.SCREEN,
                                    Page.BOUNDARY_CROPBOX, 0f, 1f);
                            if (image != null) {
                                ImageIO.write(image, "png", out.resolve(name + "_" + page + ".png").toFile());
                            }
                            return null;
                        }));
                    }
                    for (Future<?> f : pageFutures) f.get(180, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    log.add("TIMEOUT " + doc);
                } catch (Throwable e) {
                    log.add("ERROR " + doc + " " + e);
                } finally {
                    pagePool.shutdownNow();
                    try {
                        capture.dispose();
                    } catch (Throwable ignored) {
                        // a failed load leaves nothing to dispose.
                    }
                }
                int n = done.incrementAndGet();
                if (n % 100 == 0) System.out.printf("%d/%d %.0fs%n", n, docs.size(), (System.nanoTime() - t0) / 1e9);
            }));
        }
        for (Future<?> f : futures) f.get();
        System.out.printf("done %d documents in %.0fs, %d failed or timed out (log.txt)%n", done.get(),
                (System.nanoTime() - t0) / 1e9, log.size());
        Collections.sort(log);
        Files.write(out.resolve("log.txt"), log);
        System.exit(0);
    }

    /** The PDFs under a folder, or the paths listed in a file. */
    private static List<String> documents(Path docs) throws Exception {
        if (Files.isDirectory(docs)) {
            try (Stream<Path> walk = Files.walk(docs)) {
                return walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                        .map(Path::toString).sorted().collect(Collectors.toList());
            }
        }
        return Files.readAllLines(docs).stream().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .collect(Collectors.toList());
    }
}
