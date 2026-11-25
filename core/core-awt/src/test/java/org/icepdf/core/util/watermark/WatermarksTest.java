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
package org.icepdf.core.util.watermark;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.WatermarkAnnotation;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WatermarksTest {

    public static final String TWO_PAGES = "src/test/resources/redaction/shared_content_alike.pdf";
    static final String ROTATED = "src/test/resources/redaction/rotated_page.pdf";

    static final Watermark BLACK_HEADER = Watermark.of("SAMPLE WATERMARK")
            .withPosition(Watermark.Position.HEADER).withColor(Color.BLACK).withOpacity(1f).withFontSize(14);

    @DisplayName("every page gets one watermark, read back after a full rewrite")
    @Test
    public void watermarkIsWrittenAndReadBack() throws Exception {
        byte[] written = watermarked(TWO_PAGES, Watermark.of("Evaluation copy"));
        Document document = open(written);
        try {
            List<WatermarkAnnotation> found = Watermarks.find(document);
            assertEquals(2, found.size(), "one watermark on each of the two pages");
            for (WatermarkAnnotation watermark : found) {
                assertEquals("Evaluation copy", watermark.getText());
                assertTrue(watermark.getFlagPrint(), "printed");
                assertTrue(watermark.getFlagLocked(), "locked");
                assertTrue(watermark.getFlagLockedContents(), "contents locked");
            }
        } finally {
            document.dispose();
        }
    }

    @DisplayName("pages of the same size and rotation share one appearance stream")
    @Test
    public void samePagesShareOneAppearance() throws Exception {
        Document document = open(watermarked(TWO_PAGES, Watermark.of("Shared")));
        try {
            List<WatermarkAnnotation> found = Watermarks.find(document);
            Object first = appearanceReference(found.get(0));
            assertNotNull(first, "the watermark has an appearance stream");
            assertEquals(first, appearanceReference(found.get(1)), "both pages point at the same stream");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("applying again replaces the page's watermark rather than adding a second")
    @Test
    public void applyingAgainReplaces() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get(TWO_PAGES).toString());
        byte[] written;
        try {
            Watermarks.apply(document, Watermark.of("First"));
            Watermarks.apply(document, Watermark.of("Second"));
            written = save(document);
        } finally {
            document.dispose();
        }
        Document reopened = open(written);
        try {
            List<WatermarkAnnotation> found = Watermarks.find(reopened);
            assertEquals(2, found.size(), "still one a page");
            assertEquals("Second", found.get(0).getText());
            assertEquals("Second", found.get(1).getText());
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("remove takes every watermark out")
    @Test
    public void removeTakesThemOut() throws Exception {
        Document document = open(watermarked(TWO_PAGES, Watermark.of("Temporary")));
        byte[] written;
        try {
            assertEquals(2, Watermarks.remove(document));
            written = save(document);
        } finally {
            document.dispose();
        }
        Document reopened = open(written);
        try {
            assertTrue(Watermarks.find(reopened).isEmpty(), "no watermarks left");
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("apply, find and remove read annotations only: no page's content is parsed")
    @Test
    public void pagesAreNotParsed() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get(TWO_PAGES).toString());
        try {
            Watermarks.apply(document, Watermark.of("Unparsed"));
            assertEquals(2, Watermarks.find(document).size());
            for (int i = 0; i < 2; i++) {
                Page page = document.getPageTree().getPage(i);
                assertFalse(page.isInitiated(), "page " + (i + 1) + " parsed");
                // the page is an edit now, so its content is held softly once it is parsed
                assertTrue(document.getStateManager().contains(page.getPObjectReference()));
                page.init();
                assertNotNull(page.getShapes());
            }
            assertEquals(2, Watermarks.remove(document));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a page range is watermarked alone")
    @Test
    public void pageRange() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get(TWO_PAGES).toString());
        try {
            assertEquals(1, Watermarks.apply(document, Watermark.of("Page two"), 1, 1));
            assertTrue(Watermarks.on(document.getPageTree().getPage(0)).isEmpty());
            assertEquals(1, Watermarks.on(document.getPageTree().getPage(1)).size());
            assertThrows(IllegalArgumentException.class, () -> Watermarks.apply(document, Watermark.of("x"), 1, 2));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a header is drawn along the top of the page, a footer along the bottom")
    @Test
    public void headerAndFooterLandOnTheirEdges() throws Exception {
        int[] header = changedRows(TWO_PAGES, 0, BLACK_HEADER);
        assertTrue(header[0] > 0, "the header changed some pixels");
        assertTrue(header[1] < header[3] / 4, "every change in the top quarter, last row " + header[1]);

        int[] footer = changedRows(TWO_PAGES, 0, BLACK_HEADER.withPosition(Watermark.Position.FOOTER));
        assertTrue(footer[0] > 0, "the footer changed some pixels");
        assertTrue(footer[2] > footer[3] * 3 / 4, "every change in the bottom quarter, first row " + footer[2]);
    }

    @DisplayName("on a rotated page the header is still along the top as the page is shown")
    @Test
    public void headerFollowsRotation() throws Exception {
        int[] header = changedRows(ROTATED, 0, BLACK_HEADER);
        assertTrue(header[0] > 0, "the header changed some pixels");
        assertTrue(header[1] < header[3] / 4, "every change in the top quarter of the shown page, last row "
                + header[1] + " of " + header[3]);
    }

    @DisplayName("an overlay crosses the middle of the page")
    @Test
    public void overlayCrossesTheMiddle() throws Exception {
        int[] overlay = changedRows(TWO_PAGES, 0, BLACK_HEADER.withPosition(Watermark.Position.OVERLAY).withFontSize(0));
        assertTrue(overlay[0] > 0, "the overlay changed some pixels");
        assertTrue(overlay[2] < overlay[3] / 2 && overlay[1] > overlay[3] / 2, "spans the middle row");
    }

    @DisplayName("the appearance holds the text once, in the standard font, nothing embedded")
    @Test
    public void appearanceContent() {
        String content = new String(Watermarks.content(Watermark.of("Draft"), 300, 200, 0), StandardCharsets.US_ASCII);
        assertTrue(content.contains("<4472616674> Tj"), content);   // "Draft" in hex
        assertTrue(content.contains("/WmF0 12 Tf"), content);
        assertTrue(content.contains("/WmGS0 gs"), content);
    }

    @DisplayName("characters WinAnsi can't hold become question marks")
    @Test
    public void unencodableCharacters() {
        assertArrayEquals(new byte[]{'A', (byte) 0xE9, '?'}, Watermarks.encode("Aé中"));
    }

    @DisplayName("bad settings are refused")
    @Test
    public void badSettings() {
        assertThrows(IllegalArgumentException.class, () -> Watermark.of("  "));
        assertThrows(IllegalArgumentException.class, () -> Watermark.of("x").withOpacity(1.5f));
        assertThrows(IllegalArgumentException.class, () -> Watermark.of("x").withFontSize(-1));
        assertEquals("two lines", Watermark.of("two\nlines").getText());
    }

    // --- helpers

    public static byte[] watermarked(String file, Watermark watermark) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get(file).toString());
        try {
            Watermarks.apply(document, watermark);
            return save(document);
        } finally {
            document.dispose();
        }
    }

    public static byte[] save(Document document) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
        return out.toByteArray();
    }

    public static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "watermarked");
        return document;
    }

    private static Object appearanceReference(WatermarkAnnotation annotation) {
        Object ap = annotation.getEntries().get(Annotation.APPEARANCE_STREAM_KEY);
        if (ap instanceof org.icepdf.core.pobjects.DictionaryEntries) {
            return ((org.icepdf.core.pobjects.DictionaryEntries) ap).get(Annotation.APPEARANCE_STREAM_NORMAL_KEY);
        }
        ap = annotation.getLibrary().getObject(annotation.getEntries(), Annotation.APPEARANCE_STREAM_KEY);
        return ap instanceof org.icepdf.core.pobjects.DictionaryEntries
                ? ((org.icepdf.core.pobjects.DictionaryEntries) ap).get(Annotation.APPEARANCE_STREAM_NORMAL_KEY) : null;
    }

    public static BufferedImage render(byte[] pdf, int pageIndex) throws Exception {
        Document document = open(pdf);
        try {
            return (BufferedImage) document.getPageImage(pageIndex, GraphicsRenderingHints.PRINT,
                    Page.BOUNDARY_CROPBOX, 0f, 2f);
        } finally {
            document.dispose();
        }
    }

    public static BufferedImage renderFile(String file, int pageIndex) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get(file).toString());
        try {
            return (BufferedImage) document.getPageImage(pageIndex, GraphicsRenderingHints.PRINT,
                    Page.BOUNDARY_CROPBOX, 0f, 2f);
        } finally {
            document.dispose();
        }
    }

    /** {changed pixel count, last changed row, first changed row, image height}, against the unwatermarked page. */
    public static int[] changedRows(String file, int pageIndex, Watermark watermark) throws Exception {
        return difference(renderFile(file, pageIndex), render(watermarked(file, watermark), pageIndex));
    }

    public static int[] difference(BufferedImage before, BufferedImage after) {
        assertEquals(before.getHeight(), after.getHeight(), "same rendered size");
        int count = 0, last = -1, first = -1;
        for (int y = 0; y < after.getHeight(); y++) {
            for (int x = 0; x < after.getWidth(); x++) {
                if (Math.abs(luminance(before.getRGB(x, y)) - luminance(after.getRGB(x, y))) > 60) {
                    count++;
                    last = y;
                    if (first < 0) {
                        first = y;
                    }
                }
            }
        }
        return new int[]{count, last, first, after.getHeight()};
    }

    private static int luminance(int rgb) {
        return (((rgb >> 16) & 0xff) * 3 + ((rgb >> 8) & 0xff) * 6 + (rgb & 0xff)) / 10;
    }
}
