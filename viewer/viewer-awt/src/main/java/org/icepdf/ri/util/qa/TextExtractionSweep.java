/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.ri.util.qa;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.util.FontPropertiesManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Corpus text-extraction signature sweep, for A/B regression checking of the text-extraction pipeline
 * (word/space detection, letter-spacing collapse, rotated/vertical grouping, reading order).
 * <p>
 * Unlike the {@link org.icepdf.qa} JavaFX framework - which compares the rendered <em>output</em> of two icepdf jar
 * <em>versions</em> loaded through separate class loaders - this tool runs in-process against the current build and
 * emits a compact per-document signature.  Run it against two builds (an "old" baseline and the changed build) and
 * diff the two output files: any document whose signature changed had its extracted text altered.
 * <p>
 * For each PDF it extracts the first {@code -DmaxPages} pages (default 5) and writes one tab-separated line:
 * <pre>&lt;relative-path&gt;\t&lt;rawChars&gt;\t&lt;nonSpaceChars&gt;\t&lt;sha1-of-sorted-non-space-chars&gt;</pre>
 * The final column is the key signal: the sorted, whitespace-free multiset of glyphs.  Single-space normalisation,
 * the letter-spacing collapse and vertical reordering all <em>preserve</em> that multiset, so a change in it means
 * real text was lost or gained - the regression to investigate - while raw/non-space char counts distinguish the
 * magnitude and whether spacing merely changed.  A per-document watchdog ({@code -DperDocMs}, default 20s) skips
 * pathological content streams that spin in the parser so one bad document cannot stall the whole sweep.
 * <p>
 * Usage:
 * <pre>
 *   # signature sweep of a corpus to a file
 *   java ... org.icepdf.ri.util.qa.TextExtractionSweep &lt;corpus-dir&gt; [out.tsv]
 *   # dump the extracted text of a single document (relative path) for eyeballing a diff
 *   java ... -Ddump=path/to/doc.pdf org.icepdf.ri.util.qa.TextExtractionSweep &lt;corpus-dir&gt;
 * </pre>
 */
public class TextExtractionSweep {

    private static final int MAX_PAGES = Integer.getInteger("maxPages", 5);
    private static final long PER_DOC_MS = Long.getLong("perDocMs", 20_000);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: TextExtractionSweep <corpus-dir> [out.tsv]   (-Ddump=<rel-path> to dump one doc)");
            return;
        }
        // quiet the loggers and serialise decoding so the signature is deterministic across runs.
        java.util.logging.LogManager.getLogManager().reset();
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);
        org.icepdf.core.util.Library.setThreadPoolSizes(1, 1);
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();

        File root = new File(args[0]);
        String dump = System.getProperty("dump");
        List<File> pdfs = new ArrayList<>();
        collect(root, pdfs);
        Collections.sort(pdfs);
        PrintStream out = args.length > 1 ? new PrintStream(new FileOutputStream(args[1])) : System.out;
        if (dump == null) {
            System.err.println("text-sweeping " + pdfs.size() + " docs under " + root + ", maxPages=" + MAX_PAGES);
        }

        ExecutorService exec = newWorker();
        int done = 0;
        long t0 = System.currentTimeMillis();
        for (File pdf : pdfs) {
            String rel = root.toURI().relativize(pdf.toURI()).getPath();
            if (dump != null && !rel.equals(dump)) {
                continue;
            }
            Future<String> future = exec.submit(() -> extract(pdf));
            try {
                String text = future.get(PER_DOC_MS, TimeUnit.MILLISECONDS);
                if (dump != null) {
                    System.out.print(text);
                    return;
                }
                out.println(rel + "\t" + text.length() + "\t" + signature(text));
            } catch (TimeoutException te) {
                // the worker may be stuck in a parser loop that ignores interrupts - replace the executor.
                future.cancel(true);
                exec.shutdownNow();
                exec = newWorker();
                if (dump != null) {
                    System.out.println("TIMEOUT");
                    return;
                }
                out.println(rel + "\tTIMEOUT\ttimeout");
            } catch (Exception e) {
                if (dump != null) {
                    System.out.println("ERROR " + e);
                    return;
                }
                out.println(rel + "\tERR\t" + e.getClass().getSimpleName());
            }
            if (++done % 250 == 0) {
                System.err.println("... " + done + "/" + pdfs.size() + " (" + (System.currentTimeMillis() - t0) / 1000 + "s)");
                out.flush();
            }
        }
        exec.shutdownNow();
        out.flush();
        System.err.println("done " + done + " docs in " + (System.currentTimeMillis() - t0) / 1000 + "s");
    }

    /** Extracts the text of the first {@link #MAX_PAGES} pages, one line per {@link LineText}. */
    private static String extract(File pdf) throws Exception {
        Document document = new Document();
        try {
            document.setFile(pdf.getAbsolutePath());
            int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
            StringBuilder text = new StringBuilder();
            for (int p = 0; p < pages; p++) {
                PageText pageText = document.getPageText(p);
                if (pageText == null) {
                    continue;
                }
                for (LineText line : pageText.getPageLines()) {
                    for (WordText word : line.getWords()) {
                        text.append(word.getText());
                    }
                    text.append('\n');
                }
            }
            return text.toString();
        } finally {
            document.dispose();
        }
    }

    /**
     * {@code nonSpaceChars\tsha1(sortedNonSpaceChars)} - the order- and whitespace-independent glyph multiset that
     * only changes when real text is lost or gained.
     */
    private static String signature(String text) throws Exception {
        char[] ns = text.replaceAll("\\s", "").toCharArray();
        Arrays.sort(ns);
        return ns.length + "\t" + sha1(new String(ns));
    }

    private static ExecutorService newWorker() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "text-sweep-worker");
            t.setDaemon(true);
            return t;
        });
    }

    private static void collect(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.getName().toLowerCase().endsWith(".pdf")) {
                out.add(f);
            }
        }
    }

    private static String sha1(String s) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
