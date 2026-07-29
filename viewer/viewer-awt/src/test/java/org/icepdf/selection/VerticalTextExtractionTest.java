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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 verification for rotated/vertical text extraction (org.icepdf.core.views.page.text.detectVerticalText).
 * Vertical text (glyphs advancing along the y axis) must group into contiguous, searchable words rather than being
 * shattered one glyph per line.
 */
public class VerticalTextExtractionTest {

    private static boolean previousFlag;

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
        previousFlag = WordText.detectVerticalText;
        WordText.detectVerticalText = true;
    }

    @AfterAll
    public static void tearDown() {
        WordText.detectVerticalText = previousFlag;
    }

    /** All line texts concatenated with spaces removed, for contiguous-substring assertions. */
    private static String flatten(PageText pt) {
        StringBuilder sb = new StringBuilder();
        for (LineText line : pt.getPageLines()) {
            for (WordText word : line.getWords()) {
                sb.append(word.getText());
            }
        }
        return sb.toString().replace(" ", "");
    }

    @Test
    public void xr650VerticalHeadingIsContiguous() throws Exception {
        Document d = new Document();
        try {
            d.setFile(VerticalTextExtractionTest.class.getResource("/redact/xr_650.pdf").getFile());
            String text = flatten(d.getPageText(4));
            assertTrue(text.contains("SPECIFICATIONS"),
                    "vertical heading should extract as one contiguous word");
        } finally {
            d.dispose();
        }
    }

    /** Raw line texts (spaces preserved) so letter-spacing collapse can be asserted. */
    private static String rawText(PageText pt) {
        StringBuilder sb = new StringBuilder();
        for (LineText line : pt.getPageLines()) {
            for (WordText word : line.getWords()) {
                sb.append(word.getText());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    public void letterSpacedHeadingsCollapse() throws Exception {
        Document d = new Document();
        try {
            d.setFile(VerticalTextExtractionTest.class.getResource("/redact/xr_650.pdf").getFile());
            // vertical letter-spaced heading collapses to a single word (not "S P E C I F I C A T I O N S").
            assertTrue(rawText(d.getPageText(4)).contains("SPECIFICATIONS"),
                    "vertical letter-spaced heading should collapse to one word");
            // horizontal letter-spaced heading collapses, preserving the real word boundary.
            String p1 = rawText(d.getPageText(1));
            assertTrue(p1.contains("PERFORMANCE FIRST"),
                    "horizontal letter-spaced heading should collapse yet keep the word break");
            // ...and ordinary prose on the same line is untouched.
            assertTrue(p1.contains("There are lots of motorcycles"), "prose must not be altered");
            // multi-word heading with a wide gap collapses both words.
            assertTrue(rawText(d.getPageText(5)).contains("ENVIRONMENTAL COMMITMENT"),
                    "spaced heading should collapse both words without dropping the final letter");
        } finally {
            d.dispose();
        }
    }

    @Test
    public void gh263RotatedHeadersAreContiguous() throws Exception {
        File f = new File("/home/pcorless/dev/pdf-qa/support/GH-263.Searching.Issue.pdf");
        org.junit.jupiter.api.Assumptions.assumeTrue(f.exists(), "GH-263 fixture not present");
        Document d = new Document();
        try {
            d.setFile(f.getAbsolutePath());
            String text = flatten(d.getPageText(0));
            assertTrue(text.contains("Sample"), "rotated header 'Sample' should be contiguous");
            assertTrue(text.contains("ProductName"), "rotated header 'Product Name' should be contiguous");
            // two words stacked on one vertical line must read in order (top-to-bottom), not reversed.
            assertTrue(text.contains("HelloTesting"), "stacked words should read Hello then Testing");
        } finally {
            d.dispose();
        }
    }

    @Test
    public void horizontalTextUnaffectedByFlag() throws Exception {
        Document d = new Document();
        try {
            d.setFile(VerticalTextExtractionTest.class.getResource("/redact/xr_650.pdf").getFile());
            String text = flatten(d.getPageText(4));
            // ordinary horizontal body text on the same page must still extract normally.
            assertTrue(text.contains("liquid-cooled"), "horizontal body text should be unaffected");
            assertFalse(text.contains("SPECIFICATIONS".replace("S", "S ")), "sanity: no spaced-out heading");
        } finally {
            d.dispose();
        }
    }
}
