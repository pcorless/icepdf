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
import org.icepdf.core.pobjects.graphics.text.BreakType;
import org.icepdf.core.pobjects.graphics.text.Caret;
import org.icepdf.core.pobjects.graphics.text.ColumnBlock;
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

        // canonical reading order (default xycut orders the top page-number line first, then the
        // verse; the verse text is present and intact regardless of where the page number sorts).
        assertEquals(6, seq.lineCount());
        assertTrue(seq.text().toString().contains("Qué es un antipoeta:"),
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

    @DisplayName("caretAt hits the clicked glyph on a multi-column / vertical-text page (2005CAT.pdf)")
    @Test
    public void caretAtMultiColumn() throws Exception {
        // Layout with vertical text and columns: a y-only line lookup selects the wrong word.
        // Every glyph's own centre must resolve to a caret on that glyph.
        TextSequence seq = pageText("/redact/2005CAT.pdf", 0).getTextSequence();
        int total = 0, wrong = 0;
        for (GlyphText g : seq.glyphsIn(seq.fullRange())) {
            Rectangle2D.Double b = g.getBounds();
            if (b.width <= 0 || b.height <= 0) continue;
            total++;
            int off = seq.caretAt(new Point2D.Double(b.getCenterX(), b.getCenterY())).getOffset();
            int start = seq.offsetOf(g);
            if (!(off >= start && off <= start + g.getUnicode().length())) wrong++;
        }
        assertTrue(total > 0);
        assertEquals(0, wrong, wrong + "/" + total + " glyph centres resolved to the wrong caret");
    }

    @DisplayName("caret navigation primitives (nextBoundary / caretAbove-Below / caretAtLine / caretRect)")
    @Test
    public void navigation() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();

        // base assertions at the verse start ("Qué"), which under the default xycut order is not
        // necessarily offset 0 (the top page-number line sorts first).
        int q = seq.text().toString().indexOf("Qué");
        assertTrue(q >= 0, "verse start not found");

        // glyph boundary: +/- 1
        assertEquals(q + 6, seq.nextBoundary(q + 5, org.icepdf.core.pobjects.graphics.text.BreakType.GLYPH, true));
        assertEquals(q + 4, seq.nextBoundary(q + 5, org.icepdf.core.pobjects.graphics.text.BreakType.GLYPH, false));

        // word boundary at start of the word "Qué"
        OffsetRange firstWord = seq.wordRange(q);
        assertEquals(firstWord.getEnd(), seq.nextBoundary(q, org.icepdf.core.pobjects.graphics.text.BreakType.WORD, true));

        // word navigation must not double-stop on the space between words: one forward step from a
        // word end lands on the *next* word end, not on the intervening whitespace (GH-513).
        BreakType word = org.icepdf.core.pobjects.graphics.text.BreakType.WORD;
        int afterQue = seq.nextBoundary(q, word, true);
        int afterEs = seq.nextBoundary(afterQue, word, true);
        assertEquals("Qué", seq.text(q, afterQue));
        assertEquals("Qué es", seq.text(q, afterEs), "forward word step skipped straight over the space");
        // backward mirrors forward: from inside "es" one step reaches the start of "Qué" in a
        // single move, not stopping on the lone space between them.
        assertEquals(q, seq.nextBoundary(afterQue + 1, word, false));

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

    @DisplayName("shift-down onto a shorter next line still advances (goalX overshoots the short line)")
    @Test
    public void caretBelowOntoShorterLine() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();
        org.icepdf.core.pobjects.graphics.text.Bias back = org.icepdf.core.pobjects.graphics.text.Bias.BACKWARD;
        // find a line immediately followed by a shorter one (the classic end-of-paragraph case)
        int longLine = -1;
        for (int i = 0; i < seq.lineCount() - 1; i++) {
            double rightThis = lineRightX(seq, i);
            double rightNext = lineRightX(seq, i + 1);
            if (rightThis > rightNext + 2) {
                longLine = i;
                break;
            }
        }
        assertTrue(longLine >= 0, "test PDF should have a line followed by a shorter one");
        // caret at end of the long line, goalX at its right edge (past the shorter line's end)
        int endOfLong = seq.caretAtLine(longLine, Double.MAX_VALUE).getOffset();
        int endOfNext = seq.caretAtLine(longLine + 1, Double.MAX_VALUE).getOffset();
        double goalX = seq.caretRect(new Caret(endOfLong, back)).getX();
        Caret below = seq.caretBelow(new Caret(endOfLong, back), goalX);
        assertNotNull(below);
        // must land on the shorter next line, not snap back to the long line, clamped to its end
        assertEquals(longLine + 1, seq.lineIndexOf(below.getOffset()));
        assertEquals(endOfNext, below.getOffset());
    }

    private static double lineRightX(TextSequence seq, int lineIndex) {
        int end = seq.caretAtLine(lineIndex, Double.MAX_VALUE).getOffset();
        return seq.caretRect(new Caret(end, org.icepdf.core.pobjects.graphics.text.Bias.BACKWARD)).getX();
    }

    @DisplayName("search corpus: whitespace collapsed, maps back to canonical range/words")
    @Test
    public void searchCorpus() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();
        String corpus = seq.searchText();

        // collapsed: no double spaces, no newlines, not longer than canonical
        assertFalse(corpus.contains("  "), "runs of whitespace should collapse");
        assertFalse(corpus.contains("\n"), "newlines should collapse to spaces");
        assertTrue(corpus.length() <= seq.length());
        assertTrue(corpus.contains("todo el mundo"), "collapsed corpus should contain the phrase");

        // a match in the corpus maps back to a canonical range covering the phrase words
        int start = corpus.indexOf("todo el mundo");
        OffsetRange canonical = seq.searchToCanonicalRange(start, start + "todo el mundo".length());
        String mapped = seq.text(canonical);
        assertTrue(mapped.contains("todo") && mapped.contains("mundo"), "mapped canonical text: " + mapped);
        StringBuilder words = new StringBuilder();
        seq.wordsIn(canonical).forEach(w -> words.append(w.getText()));
        assertTrue(words.toString().replace(" ", "").contains("todoelmundo"),
                "wordsIn should cover the phrase: " + words);
    }

    @DisplayName("folded search corpus: accents removed, maps back to accented canonical text")
    @Test
    public void foldedCorpus() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();

        assertEquals("ataudes", TextSequence.foldDiacritics("ataúdes"));
        assertEquals("PDF", TextSequence.foldDiacritics("PDF"));   // ASCII unchanged

        String folded = seq.foldedSearchText();
        assertTrue(folded.contains("ataudes"), "folded corpus should drop accents: " + folded.substring(0, 40));
        assertFalse(folded.contains("ataúdes"), "folded corpus should not retain accented form");

        // an unaccented match maps back to the accented canonical text
        int start = folded.indexOf("ataudes");
        OffsetRange canonical = seq.foldedToCanonicalRange(start, start + "ataudes".length());
        assertEquals("ataúdes", seq.text(canonical));
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

    // ------------------------------------------------------------------
    // column detection + column-aware selection (Appendix E / D4)
    // ------------------------------------------------------------------

    @DisplayName("windriver p2: two body columns detected, left reads before right, ranges disjoint")
    @Test
    public void columns_twoColumnBody() throws Exception {
        TextSequence seq = pageText("/redact/windrivercasestudy1n3d2m8km0r.pdf", 1).getTextSequence();
        List<ColumnBlock> cols = seq.columns();
        assertEquals(2, cols.size(), "expected two body columns");

        ColumnBlock left = cols.get(0), right = cols.get(1);
        // ordered left-to-right
        assertTrue(left.getBounds().getMinX() < right.getBounds().getMinX());
        // columns don't overlap horizontally (a real gutter separates them)
        assertTrue(left.getBounds().getMaxX() <= right.getBounds().getMinX(),
                "columns overlap in x: " + left.getBounds() + " / " + right.getBounds());
        // reading order: the whole left column precedes the whole right column, no offset overlap
        assertTrue(left.getRange().getEnd() <= right.getRange().getStart(),
                "column offset ranges overlap: " + left.getRange() + " / " + right.getRange());
        // every column offset resolves back to that column
        assertSame(left, seq.columnAt(left.getRange().getStart()));
        assertSame(right, seq.columnAt(right.getRange().getStart()));
    }

    @DisplayName("column-constrained caret keeps a gutter-crossing drag inside the anchor column")
    @Test
    public void columns_constrainedCaretDoesNotJumpTheGutter() throws Exception {
        TextSequence seq = pageText("/redact/windrivercasestudy1n3d2m8km0r.pdf", 1).getTextSequence();
        ColumnBlock left = seq.columns().get(0), right = seq.columns().get(1);
        OffsetRange leftRange = left.getRange();

        // a y within both columns' vertical span, so an unconstrained lookup can reach either.
        double y = (Math.max(left.getBounds().getMinY(), right.getBounds().getMinY())
                + Math.min(left.getBounds().getMaxY(), right.getBounds().getMaxY())) / 2.0;
        double leftX = left.getBounds().getCenterX();
        double rightX = right.getBounds().getCenterX();

        // A point in the GUTTER (over no glyph), anchored to the left column, resolves to the left
        // column: a sideways drift while dragging down the left column must not jump to the right.
        double gutterX = (left.getBounds().getMaxX() + right.getBounds().getMinX()) / 2.0;
        int gutter = seq.caretAt(new Point2D.Double(gutterX, y), left).getOffset();
        assertFalse(right.getRange().contains(gutter),
                "gutter drift jumped to the right column: " + gutter);

        // But a point genuinely OVER the right column's text still crosses (direct glyph hit) — that
        // is how an intentional cross-column drag selects the rest of the left column plus the head
        // of the right.
        int overRight = seq.caretAt(new Point2D.Double(rightX, y), left).getOffset();
        assertFalse(leftRange.contains(overRight),
                "a point over the right column's text should cross to it, not clamp: " + overRight);

        // and a point genuinely in the left column is unaffected by the constraint.
        assertEquals(seq.caretAt(new Point2D.Double(leftX, y)).getOffset(),
                seq.caretAt(new Point2D.Double(leftX, y), left).getOffset());
    }

    @DisplayName("Steinfeld p1: three columns detected despite column-spanning headers; constraint holds")
    @Test
    public void columns_threeColumnWithSpanningHeaders() throws Exception {
        TextSequence seq = pageText("/selection/Steinfeld-88.pdf", 0).getTextSequence();
        List<ColumnBlock> cols = seq.columns();
        assertEquals(3, cols.size(), "expected three body columns");

        // x-bands are ordered and disjoint (the spanning title/abstract lines didn't bridge a gutter).
        for (int i = 1; i < cols.size(); i++) {
            assertTrue(cols.get(i - 1).getBounds().getMaxX() <= cols.get(i).getBounds().getMinX(),
                    "columns overlap in x at " + i + ": " + cols.get(i - 1).getBounds()
                            + " / " + cols.get(i).getBounds());
        }

        // a drag anchored in the middle column, drifting into either gutter at a y where all three
        // columns have body text, stays in the middle column (no accidental jump).
        ColumnBlock mid = cols.get(1);
        Rectangle2D.Double mb = mid.getBounds();
        // y in the vertical overlap of all three columns (near the top of the bodies).
        double y = Math.max(cols.get(0).getBounds().getMinY(),
                Math.max(cols.get(1).getBounds().getMinY(), cols.get(2).getBounds().getMinY())) + 5;
        double leftGutter = (cols.get(0).getBounds().getMaxX() + mb.getMinX()) / 2.0;
        double rightGutter = (mb.getMaxX() + cols.get(2).getBounds().getMinX()) / 2.0;
        for (double gx : new double[]{leftGutter, rightGutter}) {
            int off = seq.caretAt(new Point2D.Double(gx, y), mid).getOffset();
            GlyphText g = seq.glyphAt(off);
            if (g != null) {
                double cx = g.getBounds().getCenterX();
                assertTrue(cx >= mb.getMinX() && cx <= mb.getMaxX(),
                        "gutter point jumped out of the middle column at x=" + gx + ": glyph x=" + cx);
            }
        }
        // but a point genuinely over an outer column's text crosses to it (direct glyph hit).
        int overLeft = seq.caretAt(new Point2D.Double(cols.get(0).getBounds().getCenterX(), y), mid).getOffset();
        GlyphText gl = seq.glyphAt(overLeft);
        if (gl != null) {
            assertTrue(gl.getBounds().getCenterX() < mb.getMinX(),
                    "a point over the left column's text should cross to it, not clamp to middle");
        }
    }

    @DisplayName("java_embedded p1: a wide body over a narrow list is one column; a straight drag never reverses")
    @Test
    public void columns_bridgedGutterDoesNotSplitTheBody() throws Exception {
        // This page changes layout down the page: a wide single-column body (x 24..437) over a
        // three-lane category list (x 24..385), plus a right sidebar (x 448..586).  The body's ragged
        // short lines sit inside the list's first lane, so treating the lane boundary as a gutter
        // split the body in two and scattered its short lines to the head of the reading order —
        // which made a straight downward drag flip the selection backwards at every inter-line gap.
        TextSequence seq = pageText("/selection/java_embedded.pdf", 0).getTextSequence();
        List<ColumnBlock> cols = seq.columns();
        assertEquals(2, cols.size(), "body + sidebar, not the list's lanes: " + cols);
        assertTrue(cols.get(0).getBounds().getMaxX() < 440, "left column swallowed the sidebar: " + cols.get(0));

        // the body reads top-to-bottom: the headline before the paragraph that follows it.
        String text = seq.text().toString();
        assertTrue(text.indexOf("Reach 25,000 embedded") < text.indexOf("Extension Media is pleased"),
                "body paragraphs are out of order");
        assertTrue(text.indexOf("Extension Media is pleased") < text.indexOf("purchasing decisions."),
                "a short ragged body line was hoisted out of its paragraph");

        // drag invariant: sweeping the pointer straight down the body never moves the caret
        // backwards, at any x, whether the sample lands on a glyph or in an inter-line gap.
        for (double x : new double[]{40, 60, 100, 200, 300, 400}) {
            int previous = -1;
            for (double y = 700; y >= 380; y -= 2) {
                Point2D.Double p = new Point2D.Double(x, y);
                ColumnBlock column = seq.columnAt(p);
                int offset = seq.caretAt(p, column).getOffset();
                assertTrue(offset >= previous,
                        "caret moved backwards dragging down x=" + x + " at y=" + y
                                + " (" + previous + " -> " + offset + ")");
                previous = offset;
            }
        }
    }

    @DisplayName("R&D-05-Carbon: a gutter-spanning deck stays whole and the running header reads first")
    @Test
    public void columns_gutterSpanningLinesReadInPlace() throws Exception {
        // p1's deck paragraph spans the gutter, so each of its lines is centred within a couple of
        // points of the gutter centre.  Overlap and edge distance both flip between sibling lines over
        // that much jitter, which tore the paragraph in half and scattered it across the reading order.
        String deck = pageText("/selection/R-D-05-Carbon.pdf", 0).getTextSequence().text().toString();
        int line1 = deck.indexOf("So you want to take a little width");
        int line2 = deck.indexOf("handlebars? You cut down");
        int line3 = deck.indexOf("no problem");
        assertTrue(line1 >= 0 && line2 >= 0 && line3 >= 0, "deck paragraph text missing");
        assertTrue(line1 < line2 && line2 < line3, "the deck paragraph's lines are out of order");
        assertTrue(deck.indexOf("your seatpost.") > line3, "deck paragraph split from its tail");

        // p2's running header spans the gutter above everything else; it must read first rather than
        // sorting into whichever column it happens to be centred over (i.e. after the left column).
        TextSequence p2 = pageText("/selection/R-D-05-Carbon.pdf", 1).getTextSequence();
        String header = "TECHNOLOGY REPORT";
        assertTrue(p2.text().toString().startsWith(header),
                "running header should lead the page, got: " + p2.text().subSequence(0, 40));

        // and a straight drag down the deck and the columns below it never runs backwards.  x values
        // are inside a column: a drag held in the gutter itself is inherently ambiguous once the
        // spanning lines end, and is resolved a level up by the anchor-column stickiness in
        // TextSelection, which these page-level primitives don't model.
        TextSequence seq = pageText("/selection/R-D-05-Carbon.pdf", 0).getTextSequence();
        for (double x : new double[]{60, 100, 150, 250, 300, 340}) {
            int previous = -1;
            for (double y = 620; y >= 260; y -= 2) {
                Point2D.Double p = new Point2D.Double(x, y);
                int offset = seq.caretAt(p, seq.columnAt(p)).getOffset();
                assertTrue(offset >= previous,
                        "caret moved backwards dragging down x=" + x + " at y=" + y
                                + " (" + previous + " -> " + offset + ")");
                previous = offset;
            }
        }
    }

    @DisplayName("extractText inserts paragraph breaks between paragraphs, LF endings, no over-segmentation")
    @Test
    public void extractText_paragraphs() throws Exception {
        // multi-paragraph body page: paragraphs separated by a blank line, no stacked blanks, LF only.
        TextSequence multi = pageText("/selection/Steinfeld-88.pdf", 2).getTextSequence();
        String x = multi.extractText();
        assertTrue(x.contains("\n\n"), "expected blank-line paragraph breaks in extracted text");
        assertFalse(x.contains("\n\n\n"), "paragraph breaks must not stack into multiple blank lines");
        assertTrue(x.indexOf('\r') < 0, "default line ending is LF");
        assertEquals("\n", multi.extractSeparator());

        // a sub-range extracts a subset (and never more than the whole page).
        OffsetRange half = OffsetRange.of(0, multi.length() / 2);
        assertTrue(multi.extractText(half).length() <= x.length());

        // single-paragraph prose (poem) does not get a break inserted mid-paragraph: the verse from
        // "Qué" to "mundo" is one paragraph, so no blank line appears within it (the page-number line
        // may be its own paragraph, but that is outside the verse span).
        TextSequence poem = pageText("/redact/test_print.pdf", 0).getTextSequence();
        String p = poem.extractText();
        int qi = p.indexOf("Qué");
        int mi = p.indexOf("mundo");
        assertTrue(qi >= 0 && mi > qi, "verse text not found");
        assertFalse(p.substring(qi, mi).contains("\n\n"),
                "poem verse should read as one paragraph, not split at every wrapped line");
    }

    @DisplayName("single-column page yields one whole-page column; constraint is a no-op")
    @Test
    public void columns_singleColumnInvariant() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();
        List<ColumnBlock> cols = seq.columns();
        assertEquals(1, cols.size());
        assertEquals(seq.fullRange(), cols.get(0).getRange());

        // caretAt(p, wholePageColumn) == caretAt(p) across a grid: no behaviour change on single-column.
        ColumnBlock whole = cols.get(0);
        Rectangle2D pb = bounds(seq);
        for (int gx = 0; gx <= 10; gx++) {
            for (int gy = 0; gy <= 10; gy++) {
                double x = pb.getMinX() + pb.getWidth() * gx / 10.0;
                double y = pb.getMinY() + pb.getHeight() * gy / 10.0;
                Point2D pt = new Point2D.Double(x, y);
                assertEquals(seq.caretAt(pt).getOffset(), seq.caretAt(pt, whole).getOffset(),
                        "constraint changed the caret at (" + x + "," + y + ")");
            }
        }
    }
}
