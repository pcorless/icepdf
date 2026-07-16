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
import org.icepdf.core.pobjects.graphics.text.Caret;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the core {@link TextSequence} reading-order primitive (Phase 2 Step 1).
 * Lives in the viewer test module because building the sequence needs page text extraction,
 * which needs the font manager (viewer-side) and the viewer test PDFs.
 */
public class TextSequenceTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    private static PageText pageText(String resource, int page) throws Exception {
        Document document = new Document();
        document.setFile(TextSequenceTest.class.getResource(resource).getFile());
        return document.getPageText(page);
    }

    @DisplayName("reading order + offset/geometry invariants on test_print.pdf")
    @Test
    public void invariants_testPrint() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();

        // canonical reading order
        assertEquals(6, seq.lineCount());
        assertTrue(seq.text().toString().startsWith("Qué es un antipoeta:"),
                "unexpected reading order: " + seq.text());
        assertEquals(seq.text().toString(), seq.text(0, seq.length()));   // select-all identity
        assertEquals(seq.text().length(), seq.length());

        // glyphAt / offsetOf round-trip for every glyph
        for (int off = 0; off < seq.length(); off++) {
            GlyphText g = seq.glyphAt(off);
            if (g != null) {
                int start = seq.offsetOf(g);
                assertTrue(off >= start, "glyphAt(" + off + ") maps to glyph starting after the offset");
                assertSame(g, seq.glyphAt(start), "offsetOf/glyphAt not consistent");
            }
        }

        // glyphsIn(full) covers every glyph exactly once
        assertEquals(seq.glyphCount(), seq.glyphsIn(seq.fullRange()).size());

        // caretAt totality across a grid incl. off-page margins
        Rectangle2D pb = bounds(seq);
        for (int gx = -1; gx <= 11; gx++) {
            for (int gy = -1; gy <= 11; gy++) {
                double x = pb.getMinX() + pb.getWidth() * gx / 10.0;
                double y = pb.getMinY() + pb.getHeight() * gy / 10.0;
                Caret c = seq.caretAt(new Point2D.Double(x, y));
                assertNotNull(c);
                assertTrue(c.getOffset() >= 0 && c.getOffset() <= seq.length());
            }
        }

        // caretAt round-trip: a glyph's centre resolves to a caret on that glyph's span
        for (GlyphText g : seq.glyphsIn(seq.fullRange())) {
            Rectangle2D.Double b = g.getBounds();
            if (b.width <= 0 || b.height <= 0) continue;
            Caret c = seq.caretAt(new Point2D.Double(b.getCenterX(), b.getCenterY()));
            int start = seq.offsetOf(g);
            assertTrue(c.getOffset() >= start && c.getOffset() <= start + g.getUnicode().length(),
                    "caret " + c.getOffset() + " off glyph span for '" + g.getUnicode() + "'");
        }

        // a single word -> exactly one merged line rect
        OffsetRange word = seq.wordRange(0);
        assertEquals(1, seq.rectsFor(word).size());

        // cross-line span -> one rect per touched line (first/last clipped by construction)
        int midA = seq.lineRange(0).getEnd() - 6;                 // near end of line 0
        int line2Start = seq.lineRange(seq.length() / 2).getStart();  // start of some interior line
        List<Rectangle2D.Double> frag = seq.rectsFor(midA, line2Start + 4);
        assertTrue(frag.size() >= 2, "cross-line selection should span multiple line rects");
    }

    @DisplayName("caret navigation primitives (nextBoundary / caretAbove-Below / caretAtLine / caretRect)")
    @Test
    public void navigation() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();

        // glyph boundary: +/- 1
        assertEquals(6, seq.nextBoundary(5, org.icepdf.core.pobjects.graphics.text.BreakType.GLYPH, true));
        assertEquals(4, seq.nextBoundary(5, org.icepdf.core.pobjects.graphics.text.BreakType.GLYPH, false));

        // word boundary at start of the first word "Qué"
        OffsetRange firstWord = seq.wordRange(0);
        assertEquals(firstWord.getEnd(), seq.nextBoundary(0, org.icepdf.core.pobjects.graphics.text.BreakType.WORD, true));

        // line boundary
        OffsetRange line0 = seq.lineRange(3);
        assertEquals(line0.getEnd(), seq.nextBoundary(3, org.icepdf.core.pobjects.graphics.text.BreakType.LINE, true));
        assertEquals(line0.getStart(), seq.nextBoundary(3, org.icepdf.core.pobjects.graphics.text.BreakType.LINE, false));

        // vertical navigation moves one sorted line at the goal column
        int mid = seq.lineRange(seq.length() / 2).getStart() + 3;
        int midLine = seq.lineIndexOf(mid);
        double goalX = seq.caretRect(new Caret(mid, org.icepdf.core.pobjects.graphics.text.Bias.FORWARD)).getX();
        Caret below = seq.caretBelow(new Caret(mid, org.icepdf.core.pobjects.graphics.text.Bias.FORWARD), goalX);
        Caret above = seq.caretAbove(new Caret(mid, org.icepdf.core.pobjects.graphics.text.Bias.FORWARD), goalX);
        assertNotNull(below);
        assertNotNull(above);
        assertEquals(midLine + 1, seq.lineIndexOf(below.getOffset()));
        assertEquals(midLine - 1, seq.lineIndexOf(above.getOffset()));

        // top line has nothing above, last line nothing below
        assertNull(seq.caretAbove(new Caret(seq.lineRange(0).getStart(), org.icepdf.core.pobjects.graphics.text.Bias.FORWARD), goalX));
        assertNull(seq.caretBelow(new Caret(seq.length(), org.icepdf.core.pobjects.graphics.text.Bias.FORWARD), goalX));

        // caretAtLine lands on the requested line; caretRect is a zero-width bar with height
        assertEquals(2, seq.lineIndexOf(seq.caretAtLine(2, goalX).getOffset()));
        Rectangle2D.Double bar = seq.caretRect(new Caret(mid, org.icepdf.core.pobjects.graphics.text.Bias.FORWARD));
        assertEquals(0.0, bar.width);
        assertTrue(bar.height > 0);
    }

    @DisplayName("robustness sweep on dense/table doc pdf_reference_addendum_redaction.pdf")
    @Test
    public void sweep_addendum() throws Exception {
        Document document = new Document();
        document.setFile(TextSequenceTest.class.getResource("/redact/pdf_reference_addendum_redaction.pdf").getFile());
        Random rnd = new Random(42);
        int pagesWithText = 0;
        for (int pi = 0; pi < document.getNumberOfPages(); pi++) {
            PageText pt = document.getPageText(pi);
            if (pt == null) continue;
            TextSequence seq = pt.getTextSequence();
            if (seq.isEmpty()) continue;
            pagesWithText++;

            Rectangle2D pb = bounds(seq);
            for (int gx = -1; gx <= 11; gx++) {
                for (int gy = -1; gy <= 11; gy++) {
                    Caret c = seq.caretAt(new Point2D.Double(
                            pb.getMinX() + pb.getWidth() * gx / 10.0,
                            pb.getMinY() + pb.getHeight() * gy / 10.0));
                    assertTrue(c.getOffset() >= 0 && c.getOffset() <= seq.length());
                }
            }
            for (int t = 0; t < 25; t++) {
                GlyphText g1 = seq.glyphsIn(seq.fullRange()).get(rnd.nextInt(seq.glyphCount()));
                GlyphText g2 = seq.glyphsIn(seq.fullRange()).get(rnd.nextInt(seq.glyphCount()));
                int o1 = seq.caretAt(center(g1)).getOffset();
                int o2 = seq.caretAt(center(g2)).getOffset();
                int lo = Math.min(o1, o2), hi = Math.max(o1, o2);
                assertEquals(seq.text().subSequence(lo, hi).toString(), seq.text(lo, hi),
                        "page " + pi + " selection not a contiguous substring");
                assertTrue(seq.rectsFor(lo, hi).size() <= seq.lineCount());
            }
        }
        assertTrue(pagesWithText > 0);
    }

    private static Rectangle2D bounds(TextSequence seq) {
        Rectangle2D b = null;
        for (GlyphText g : seq.glyphsIn(seq.fullRange())) {
            b = b == null ? (Rectangle2D) g.getBounds().clone() : b.createUnion(g.getBounds());
        }
        return b == null ? new Rectangle2D.Double() : b;
    }

    private static Point2D center(GlyphText g) {
        Rectangle2D.Double b = g.getBounds();
        return new Point2D.Double(b.getCenterX(), b.getCenterY());
    }
}
