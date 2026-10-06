/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.selection;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Characterization (golden-snapshot) harness that pins the word/space segmentation produced by the text extractor -
 * the surface changed by single-space insertion, the letter-spacing collapse, and rotated/vertical text grouping
 * (see {@code TEXT-SELECTION-PLAN.md} and the notes in the spaceFraction memory).  It complements
 * {@link ReadingOrderCharacterizationTest} (which pins reading order) by recording the full, untruncated text of each
 * line with word boundaries made visible, so any drift - an over/under-collapsed heading, a swallowed leader dot, a
 * scrambled row, a lost space - is caught.
 * <p>
 * The {@code xr_650.pdf} fixture is used because a handful of its pages exercise every relevant case at once:
 * <ul>
 *   <li>p1 - a horizontal letter-spaced heading ("PERFORMANCE FIRST") leading ordinary prose (guards over-collapse
 *       of the heading and scrambling/over-collapse of the following body text)</li>
 *   <li>p4 - a vertical (upright-stacked) letter-spaced heading ("SPECIFICATIONS") plus tabular labels</li>
 *   <li>p5 - all-caps letter-spaced headings drawn as one line ("PROGRAMS THAT PERFORM", "ENVIRONMENTAL COMMITMENT")
 *       and a single out-of-order column of body text</li>
 * </ul>
 * <p>
 * Golden regeneration (review the diff before committing):
 * <pre>./gradlew :viewer:viewer-awt:test --tests '*WordSegmentationCharacterizationTest' -Dupdate.word.segmentation.golden=true</pre>
 */
public class WordSegmentationCharacterizationTest {

    /** {resource, zero-based page index, label}. */
    private static final String[][] FIXTURES = {
            {"/redact/xr_650.pdf", "1", "xr_650 p2 - horizontal letter-spaced heading + prose"},
            {"/redact/xr_650.pdf", "4", "xr_650 p5 - vertical SPECIFICATIONS heading + labels"},
            {"/redact/xr_650.pdf", "5", "xr_650 p6 - all-caps letter-spaced headings + single column"},
            {"/redact/test_print.pdf", "0", "test_print p1 - plain single column (control)"},
    };

    private static final String GOLDEN_RESOURCE = "/selection/word-segmentation-golden.txt";
    private static final String GOLDEN_SOURCE_PATH = "src/test/resources/selection/word-segmentation-golden.txt";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("word/space segmentation is stable across the feature corpus")
    @Test
    public void segmentationSnapshot() throws Exception {
        String actual = buildSnapshot();
        boolean update = Boolean.getBoolean("update.word.segmentation.golden");
        String golden = readGolden();
        if (update || golden == null) {
            writeGolden(actual);
            if (golden == null && !update) {
                fail("No word-segmentation golden found; generated " + GOLDEN_SOURCE_PATH + ".  Review and re-run.");
            }
            return;
        }
        assertEquals(golden, actual,
                "Word/space segmentation drifted.  If intentional, regenerate with "
                        + "-Dupdate.word.segmentation.golden=true and review the diff.");
    }

    private static String buildSnapshot() throws Exception {
        StringBuilder out = new StringBuilder();
        for (String[] fx : FIXTURES) {
            out.append("=== ").append(fx[2]).append(" ===\n");
            Document document = new Document();
            try {
                document.setFile(WordSegmentationCharacterizationTest.class.getResource(fx[0]).getFile());
                PageText pt = document.getPageText(Integer.parseInt(fx[1]));
                for (LineText line : pt.getPageLines()) {
                    // Faithful, untruncated line text: every word (letters, punctuation and spaces) concatenated in
                    // reading order.  This captures word/space segmentation (a collapsed heading vs a spaced-out one),
                    // preserved leader dots, and word order - so any regression in those shows up in the diff.  Runs
                    // of blank space are normalised to a single space so the pinned value does not depend on the exact
                    // synthetic-space count, and trailing blanks are trimmed.
                    StringBuilder raw = new StringBuilder();
                    for (WordText w : line.getWords()) {
                        raw.append(w.getText());
                    }
                    String text = raw.toString().replaceAll("[ \\t]+", " ").replaceAll("\\s+$", "");
                    if (!text.isEmpty()) {
                        out.append("  ").append(text).append('\n');
                    }
                }
            } finally {
                document.dispose();
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String readGolden() throws IOException {
        try (InputStream in = WordSegmentationCharacterizationTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeGolden(String content) throws IOException {
        Path path = Path.of(GOLDEN_SOURCE_PATH);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        System.out.println("Wrote word-segmentation golden to " + path.toAbsolutePath());
    }
}
