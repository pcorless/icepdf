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
import org.icepdf.core.pobjects.graphics.text.XYCutReadingOrder;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization + correctness harness for the {@link XYCutReadingOrder} geometry-driven reading
 * order (see {@code READING-ORDER-XYCUT-PLAN.md}).
 * <p>
 * The algorithm is exercised directly on the default (plot-order) extracted lines rather than via the
 * {@code org.icepdf.core.views.page.text.readingOrder=xycut} system property: that property is read
 * once per JVM into a static, so a property-driven test cannot coexist with the default-mode
 * {@link ReadingOrderCharacterizationTest} in the same JVM.  Calling {@code order()} directly is
 * exactly what {@code PageText} does internally in XYCUT mode.
 * <p>
 * Golden regeneration:
 * <pre>./gradlew :viewer:viewer-awt:test --tests '*ReadingOrderXYCutTest' -Dupdate.reading.order.xycut.golden=true</pre>
 */
public class ReadingOrderXYCutTest {

    private static final String[][] FIXTURES = {
            {"/redact/xr_650.pdf", "5", "xr_650 p6 - single column drawn out of order (Environmental/Programs/legal)"},
            {"/redact/windrivercasestudy1n3d2m8km0r.pdf", "1", "windriver p2 - clean two-column body + full-width footer"},
            {"/redact/2005CAT.pdf", "0", "2005CAT p1 - vertical + horizontal mixed"},
            {"/redact/test_print.pdf", "0", "test_print p1 - plain single column"},
            {"/redact/pdf_reference_addendum_redaction.pdf", "0", "reference addendum p1 - dense reference layout"},
            {"/redact/libre-test.pdf", "0", "libre-test p1"},
            {"/redact/potato_out.pdf", "0", "potato_out p1"},
            {"/selection/java_embedded.pdf", "0", "java_embedded p1 - wide body over a narrow multi-lane list + sidebar"},
            {"/selection/R-D-05-Carbon.pdf", "0", "R&D-05-Carbon p1 - gutter-spanning deck over two columns"},
            {"/selection/R-D-05-Carbon.pdf", "1", "R&D-05-Carbon p2 - gutter-spanning running header"},
    };

    private static final String GOLDEN_RESOURCE = "/selection/reading-order-xycut-golden.txt";
    private static final String GOLDEN_SOURCE_PATH = "src/test/resources/selection/reading-order-xycut-golden.txt";

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("XY-cut reading order snapshot is stable across the corpus")
    @Test
    public void xyCutSnapshot() throws Exception {
        StringBuilder out = new StringBuilder();
        for (String[] fx : FIXTURES) {
            out.append("=== ").append(fx[2]).append(" [").append(fx[0]).append(" p").append(fx[1]).append("] ===\n");
            dump(orderedLines(fx[0], Integer.parseInt(fx[1])), out);
            out.append('\n');
        }
        String actual = out.toString();

        boolean update = Boolean.getBoolean("update.reading.order.xycut.golden");
        String golden = readGolden();
        if (update || golden == null) {
            writeGolden(actual);
            if (golden == null && !update) {
                fail("No XY-cut golden found; generated " + GOLDEN_SOURCE_PATH + ".  Review and re-run.");
            }
            return;
        }
        assertEquals(golden, actual,
                "XY-cut reading order drifted.  If intentional, regenerate with "
                        + "-Dupdate.reading.order.xycut.golden=true and review the diff.");
    }

    @DisplayName("Steinfeld p3: three columns read contiguously (col-spanning caption/footer don't interleave)")
    @Test
    public void steinfeldThreeColumnsContiguous() throws Exception {
        List<LineText> ordered = orderedLines("/selection/Steinfeld-88.pdf", 2);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (LineText l : ordered) {
            minX = Math.min(minX, l.getBounds().getMinX());
            maxX = Math.max(maxX, l.getBounds().getMaxX());
        }
        double width = maxX - minX;
        // three body columns with gutters near x=218 and x=396 (see ColumnLayout detection).
        int prev = -1;
        java.util.Set<Integer> closed = new java.util.HashSet<>();
        for (LineText l : ordered) {
            if (l.getBounds().getWidth() >= width * 0.6) continue;   // skip column-spanning lines
            double cx = l.getBounds().getCenterX();
            int col = cx < 218 ? 0 : (cx < 396 ? 1 : 2);
            if (col == prev) continue;
            assertFalse(closed.contains(col),
                    "column " + col + " reappears in the reading order — columns are interleaved");
            if (prev >= 0) closed.add(prev);
            prev = col;
        }
    }

    @DisplayName("xr_650 p6: out-of-order single column reads top-to-bottom (Environmental before Programs)")
    @Test
    public void xr650SingleColumnOrdered() throws Exception {
        List<String> order = texts(orderedLines("/redact/xr_650.pdf", 5));
        assertTrue(indexOfContaining(order, "ENVIRONMENTAL") < indexOfContaining(order, "PROGRAMS"),
                "Environmental Commitment (higher on page) must read before Programs that Perform");
        assertTrue(indexOfContaining(order, "PROGRAMS") < indexOfContaining(order, "Specifications"),
                "Programs block must read before the legal print below it");
    }

    @DisplayName("fragments sharing a visual line read left-to-right despite a sub-point y offset")
    @Test
    public void nearTieFragmentsNotReversed() throws Exception {
        // "FLEXenabled" (y=391.2958) and its superscripted continuation "tm software, and hundreds
        // of Fortune" (y=391.3020) share a visual line but differ in y by 0.006pt.  The baseline merge
        // (PageText.mergeLinesByBaseline) now folds them into one line, so the left-to-right requirement
        // becomes an in-line character-order check on the flattened reading order: the left fragment must
        // still precede its continuation regardless of whether they end up on one line or two.
        String order = flatten(texts(orderedLines("/redact/windrivercasestudy1n3d2m8km0r.pdf", 1)));
        assertTrue(order.indexOf("FLEXenabled") < order.indexOf("tmsoftware"),
                "left fragment must read before the superscripted continuation on the same line");

        // same defect class on xr_650 p6: a trademark glyph at the right edge of a body line.
        String xr = flatten(texts(orderedLines("/redact/xr_650.pdf", 5)));
        assertTrue(xr.indexOf("HondaGenuineAccessories") < xr.indexOf("Oilsand"),
                "body text must read before the trademark fragment sharing its line");
    }

    @DisplayName("windriver p2: left column fully precedes right column (no interleaving)")
    @Test
    public void windriverColumnsNotInterleaved() throws Exception {
        List<LineText> lines = orderedLines("/redact/windrivercasestudy1n3d2m8km0r.pdf", 1);
        // last index of a left-column body line must come before the first right-column body line.
        // columns split around x~310; ignore the full-width footer band (y < 60).
        int lastLeft = -1, firstRight = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            Rectangle2D.Double b = lines.get(i).getBounds();
            if (b.getMaxY() < 60) continue; // footer band, not part of the column body
            if (b.getCenterX() < 310) lastLeft = Math.max(lastLeft, i);
            else firstRight = Math.min(firstRight, i);
        }
        assertTrue(lastLeft >= 0 && firstRight < Integer.MAX_VALUE, "expected two columns");
        assertTrue(lastLeft < firstRight,
                "every left-column line must read before every right-column line (got lastLeft="
                        + lastLeft + " firstRight=" + firstRight + ")");
    }

    @DisplayName("2005CAT p1: vertical INTRODUCTION label groups into one contiguous line")
    @Test
    public void verticalLabelPreserved() throws Exception {
        List<LineText> lines = orderedLines("/redact/2005CAT.pdf", 0);
        // with vertical-text detection the skinny left-margin stack (x < 40) is grouped into a single line
        // rather than one glyph per line; assert that line reads as the contiguous "INTRODUCTION" label.
        boolean found = false;
        for (LineText l : lines) {
            if (l.getBounds().getMaxX() < 40) {
                String label = lineText(l).replace(" ", "");
                if (label.contains("INTRODUCTION")) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "vertical INTRODUCTION label should extract as one contiguous left-margin line");
    }

    // ------------------------------------------------------------------

    private static List<LineText> orderedLines(String resource, int page) throws Exception {
        Document document = new Document();
        try {
            document.setFile(ReadingOrderXYCutTest.class.getResource(resource).getFile());
            PageText pt = document.getPageText(page);
            return XYCutReadingOrder.order(pt.getPageLines());
        } finally {
            document.dispose();
        }
    }

    private static List<String> texts(List<LineText> lines) {
        List<String> out = new ArrayList<>();
        for (LineText line : lines) {
            String t = lineText(line);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static int indexOfContaining(List<String> texts, String needle) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).replace(" ", "").contains(needle.replace(" ", ""))) return i;
        }
        return Integer.MAX_VALUE;
    }

    /** Concatenates the reading-order line texts (spaces removed) so in-line character order can be asserted. */
    private static String flatten(List<String> texts) {
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            sb.append(t);
        }
        return sb.toString().replace(" ", "");
    }

    private static void dump(List<LineText> lines, StringBuilder out) {
        int i = 0;
        for (LineText line : lines) {
            String text = lineText(line);
            if (text.isEmpty()) continue;
            Rectangle2D.Double b = line.getBounds();
            out.append(String.format("  L%03d y=%7.1f x=%7.1f..%-7.1f  %s%n",
                    i++, b.y, b.x, b.x + b.width, text.length() > 60 ? text.substring(0, 60) : text));
        }
    }

    private static String lineText(LineText line) {
        StringBuilder sb = new StringBuilder();
        for (WordText w : line.getWords()) sb.append(w.getText());
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String readGolden() throws IOException {
        try (InputStream in = ReadingOrderXYCutTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeGolden(String content) throws IOException {
        Path path = Path.of(GOLDEN_SOURCE_PATH);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        System.out.println("Wrote XY-cut golden snapshot to " + path.toAbsolutePath());
    }
}
