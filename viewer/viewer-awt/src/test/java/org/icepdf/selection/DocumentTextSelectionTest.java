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
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.OffsetRange;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.TextSequence;
import org.icepdf.ri.common.tools.TextSelectionSupport;
import org.icepdf.ri.common.views.DocumentTextSelection;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the viewer-side selection model and the core&#8596;viewer bridge (Phase 2 Step 1):
 * {@link DocumentTextSelection}, {@link TextSelectionSupport#rangeForPage},
 * {@link TextSelectionSupport#applySelectionToFlags}, and {@link TextSelectionSupport#selectedText}.
 */
public class DocumentTextSelectionTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("DocumentTextSelection anchor/focus normalization")
    @Test
    public void selectionModel() {
        DocumentTextSelection sel = new DocumentTextSelection();
        assertTrue(sel.isEmpty());

        sel.collapseTo(2, 40);
        assertFalse(sel.isEmpty());
        assertTrue(sel.isCollapsed());
        assertEquals(2, sel.startPage());
        assertEquals(40, sel.startOffset());

        // forward extend
        sel.extendTo(3, 10);
        assertFalse(sel.isCollapsed());
        assertTrue(sel.isForward());
        assertEquals(2, sel.startPage());
        assertEquals(40, sel.startOffset());
        assertEquals(3, sel.endPage());
        assertEquals(10, sel.endOffset());

        // backward extend on the same page -> start/end swap
        sel.collapseTo(1, 50);
        sel.extendTo(1, 20);
        assertFalse(sel.isForward());
        assertEquals(20, sel.startOffset());
        assertEquals(50, sel.endOffset());

        sel.clear();
        assertTrue(sel.isEmpty());
    }

    @DisplayName("rangeForPage: first page anchor->end, middle full, last start->focus")
    @Test
    public void rangeForPage() throws Exception {
        TextSequence seq = pageText("/redact/test_print.pdf", 0).getTextSequence();
        int len = seq.length();

        // selection spanning pages 1..3
        DocumentTextSelection sel = new DocumentTextSelection();
        sel.set(1, 30, 3, 12);

        assertNull(TextSelectionSupport.rangeForPage(sel, 0, seq), "page before selection");
        assertEquals(OffsetRange.of(30, len), TextSelectionSupport.rangeForPage(sel, 1, seq)); // first: anchor->end
        assertEquals(OffsetRange.of(0, len), TextSelectionSupport.rangeForPage(sel, 2, seq));  // middle: full
        assertEquals(OffsetRange.of(0, 12), TextSelectionSupport.rangeForPage(sel, 3, seq));   // last: start->focus
        assertNull(TextSelectionSupport.rangeForPage(sel, 4, seq), "page after selection");

        // single-page selection
        DocumentTextSelection single = new DocumentTextSelection();
        single.set(0, 5, 0, 11);
        assertEquals(OffsetRange.of(5, 11), TextSelectionSupport.rangeForPage(single, 0, seq));

        // empty selection -> null everywhere
        assertNull(TextSelectionSupport.rangeForPage(new DocumentTextSelection(), 0, seq));
    }

    @DisplayName("applySelectionToFlags marks exactly the covered glyphs")
    @Test
    public void applySelectionToFlags() throws Exception {
        PageText pageText = pageText("/redact/test_print.pdf", 0);
        TextSequence seq = pageText.getTextSequence();
        OffsetRange range = OffsetRange.of(10, 40);

        TextSelectionSupport.applySelectionToFlags(pageText, range);

        for (GlyphText g : seq.glyphsIn(seq.fullRange())) {
            int start = seq.offsetOf(g);
            boolean inRange = start >= range.getStart() && start < range.getEnd();
            assertEquals(inRange, g.isSelected(),
                    "glyph at offset " + start + " selected=" + g.isSelected() + " expected " + inRange);
        }

        // clearing resets everything
        TextSelectionSupport.applySelectionToFlags(pageText, null);
        for (GlyphText g : seq.glyphsIn(seq.fullRange())) {
            assertFalse(g.isSelected());
        }
    }

    @DisplayName("selectedText walks the sequence, independent of node flags")
    @Test
    public void selectedText() throws Exception {
        Document document = new Document();
        document.setFile(DocumentTextSelectionTest.class.getResource("/redact/test_print.pdf").getFile());
        TextSequence seq = document.getPageText(0).getTextSequence();

        DocumentTextSelection sel = new DocumentTextSelection();
        sel.set(0, 0, 0, 20);
        assertEquals(seq.text(0, 20), TextSelectionSupport.selectedText(sel, document));

        // reversed anchor/focus yields the same normalized text
        DocumentTextSelection rev = new DocumentTextSelection();
        rev.set(0, 20, 0, 0);
        assertEquals(seq.text(0, 20), TextSelectionSupport.selectedText(rev, document));
    }

    @DisplayName("drag simulation: point -> caret -> range -> flags matches the model text")
    @Test
    public void dragSimulation() throws Exception {
        PageText pageText = pageText("/redact/test_print.pdf", 0);
        TextSequence seq = pageText.getTextSequence();

        // two glyph centres on the first line (single-line selection, no newline)
        GlyphText g1 = seq.glyphsIn(seq.fullRange()).get(5);
        GlyphText g2 = seq.glyphsIn(seq.fullRange()).get(40);
        int o1 = seq.caretAt(new java.awt.geom.Point2D.Double(g1.getBounds().getCenterX(), g1.getBounds().getCenterY())).getOffset();
        int o2 = seq.caretAt(new java.awt.geom.Point2D.Double(g2.getBounds().getCenterX(), g2.getBounds().getCenterY())).getOffset();

        // mimic selectionStart/selection: collapseTo then extendTo, then write-through
        DocumentTextSelection sel = new DocumentTextSelection();
        sel.collapseTo(0, o1);
        sel.extendTo(0, o2);
        OffsetRange range = TextSelectionSupport.rangeForPage(sel, 0, seq);
        TextSelectionSupport.applySelectionToFlags(pageText, range);

        // the flag-based extraction (used by redaction / highlight / edit) matches the model text
        // (getSelected() appends a trailing newline per selected line).
        String flagText = pageText.getSelected().toString();
        if (flagText.endsWith("\n")) flagText = flagText.substring(0, flagText.length() - 1);
        assertEquals(seq.text(range), flagText);
        // and the model-based extraction agrees
        assertEquals(seq.text(range), TextSelectionSupport.selectedText(sel, singlePageDoc()));
    }

    private static Document singlePageDoc() throws Exception {
        Document document = new Document();
        document.setFile(DocumentTextSelectionTest.class.getResource("/redact/test_print.pdf").getFile());
        return document;
    }

    private static PageText pageText(String resource, int page) throws Exception {
        Document document = new Document();
        document.setFile(DocumentTextSelectionTest.class.getResource(resource).getFile());
        return document.getPageText(page);
    }
}
