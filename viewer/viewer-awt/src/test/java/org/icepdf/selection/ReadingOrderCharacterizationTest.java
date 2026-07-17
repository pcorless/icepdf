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

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Characterization (golden-snapshot) harness that pins the current page reading order produced by
 * {@link PageText#getPageLines()} across a small corpus that spans the hard cases: a single-column
 * page drawn out of plot order (xr_650 p6), vertical-plus-horizontal mixed text (2005CAT p1), a
 * plain single column (test_print), a dense reference layout, and a couple of general documents.
 * <p>
 * This is a safety net, not a correctness assertion: the golden file records what the extractor does
 * <em>today</em>.  It exists so that a future reading-order change (e.g. an XY-cut / projection-profile
 * column detector replacing the {@code preserveColumns} plot-order heuristic) produces a reviewable,
 * line-by-line diff over real pages before anything ships &mdash; the same discipline the selection and
 * search work has used throughout, and doubly important because {@code getPageLines()} feeds selection,
 * search, and redaction.
 * <p>
 * To regenerate after an intentional change:
 * <pre>./gradlew :viewer:viewer-awt:test --tests '*ReadingOrderCharacterizationTest' -Dupdate.reading.order.golden=true</pre>
 * then review the diff to {@code src/test/resources/selection/reading-order-golden.txt} and commit it.
 */
public class ReadingOrderCharacterizationTest {

    /** {resource, zero-based page index, short label}. */
    private static final String[][] FIXTURES = {
            {"/redact/xr_650.pdf", "5", "xr_650 p6 - single column drawn out of order (Environmental/Programs/legal)"},
            {"/redact/2005CAT.pdf", "0", "2005CAT p1 - vertical + horizontal mixed"},
            {"/redact/test_print.pdf", "0", "test_print p1 - plain single column"},
            {"/redact/pdf_reference_addendum_redaction.pdf", "0", "reference addendum p1 - dense reference layout"},
            {"/redact/libre-test.pdf", "0", "libre-test p1"},
            {"/redact/potato_out.pdf", "0", "potato_out p1"},
    };

    private static final String GOLDEN_RESOURCE = "/selection/reading-order-golden.txt";
    private static final String GOLDEN_SOURCE_PATH = "src/test/resources/selection/reading-order-golden.txt";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("reading order snapshot is stable across the corpus")
    @Test
    public void readingOrderSnapshot() throws Exception {
        String actual = buildSnapshot();

        boolean update = Boolean.getBoolean("update.reading.order.golden");
        String golden = readGolden();

        if (update || golden == null) {
            writeGolden(actual);
            if (golden == null && !update) {
                fail("No golden snapshot found; generated " + GOLDEN_SOURCE_PATH
                        + ".  Review it and re-run to lock the baseline.");
            }
            return; // regenerated on request
        }
        assertEquals(golden, actual,
                "Reading order drifted from the golden snapshot.  If the change is intentional, "
                        + "regenerate with -Dupdate.reading.order.golden=true and review the diff.");
    }

    /** Normalized, human-readable dump of the reading order for every fixture. */
    private static String buildSnapshot() throws Exception {
        StringBuilder out = new StringBuilder();
        for (String[] fx : FIXTURES) {
            String resource = fx[0];
            int page = Integer.parseInt(fx[1]);
            out.append("=== ").append(fx[2]).append(" [").append(resource)
                    .append(" p").append(page).append("] ===\n");
            dumpPage(resource, page, out);
            out.append('\n');
        }
        return out.toString();
    }

    private static void dumpPage(String resource, int page, StringBuilder out) throws Exception {
        Document document = new Document();
        try {
            document.setFile(ReadingOrderCharacterizationTest.class.getResource(resource).getFile());
            PageText pt = document.getPageText(page);
            if (pt == null) {
                out.append("  <no page text>\n");
                return;
            }
            List<LineText> lines = pt.getPageLines();
            int i = 0;
            for (LineText line : lines) {
                String text = collapse(lineText(line));
                if (text.isEmpty()) continue;
                Rectangle2D.Double b = line.getBounds();
                out.append(String.format("  L%03d y=%7.1f x=%7.1f..%-7.1f  %s%n",
                        i++, b.y, b.x, b.x + b.width, truncate(text, 60)));
            }
        } finally {
            document.dispose();
        }
    }

    private static String lineText(LineText line) {
        StringBuilder sb = new StringBuilder();
        for (WordText w : line.getWords()) {
            sb.append(w.getText());
        }
        return sb.toString();
    }

    private static String collapse(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String readGolden() throws IOException {
        try (InputStream in = ReadingOrderCharacterizationTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void writeGolden(String content) throws IOException {
        Path path = Path.of(GOLDEN_SOURCE_PATH);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        System.out.println("Wrote reading-order golden snapshot to " + path.toAbsolutePath());
    }
}
