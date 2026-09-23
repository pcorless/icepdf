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
package org.icepdf.core.pobjects.structure;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests rebuilding a document whose cross-reference table cannot be trusted.
 * <p>
 * The index is the map from object number to file offset.  When it is wrong the document is
 * unreadable even though every object in it may be perfectly intact, and the repair is to ignore
 * the map and find the objects by scanning for them.
 * <p>
 * The three damaged files here are the same document, cut in three different ways, which is what
 * makes them worth having together: a repair that produced <em>some</em> readable output would pass
 * a test that only checked for the absence of an exception, so each is asserted to produce the same
 * text as the others.  Recovering the wrong content is the failure this catches.
 */
public class IndexerTest {

    /** The document all three damaged files are a copy of, as it reads when repaired. */
    private static final String EXPECTED_OPENING = "Brand new generation frame replaces";

    private static Document open(String fixture) throws Exception {
        Document document = new Document();
        InputStream in = IndexerTest.class.getResourceAsStream("/structure/" + fixture);
        assertNotNull(in, "missing fixture " + fixture);
        document.setInputStream(in, fixture);
        return document;
    }

    /**
     * @return page 0's text, whitespace collapsed
     */
    private static String firstPageText(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        return page.getViewText().toString().replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------
    // damage the index can be rebuilt around
    // ------------------------------------------------------------------

    @DisplayName("objects held in an object stream survive a rebuild")
    @Test
    public void objectStreamContentsAreIndexed() throws Exception {
        // The page tree lives only inside an /ObjStm, as in a linearized or compressed file.  Those
        // objects have no "n g obj" keyword, so a scan for keywords alone lost them and the rebuilt
        // document had a catalog with no pages (Editable1.pdf: NPE from getNumberOfPages).
        String pages = "<< /Type /Pages /Kids [4 0 R] /Count 1 >>";
        String page = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>";
        String offsets = "2 0 4 " + pages.length() + " ";
        String objects = offsets + pages + page;
        StringBuilder pdf = new StringBuilder("%PDF-1.5\n");
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        pdf.append("3 0 obj\n<< /Type /ObjStm /N 2 /First ").append(offsets.length())
                .append(" /Length ").append(objects.length()).append(" >>\nstream\n")
                .append(objects).append("\nendstream\nendobj\n");
        // no xref at all, and a startxref that points nowhere
        pdf.append("trailer\n<< /Root 1 0 R /Size 5 >>\nstartxref\n99999\n%%EOF\n");

        Document document = new Document();
        document.setByteArray(pdf.toString().getBytes(StandardCharsets.ISO_8859_1), 0, pdf.length(),
                "objstm.pdf");
        assertEquals(1, document.getNumberOfPages());
        Page first = document.getPageTree().getPage(0);
        assertNotNull(first);
        assertEquals(200, first.getMediaBox().getWidth(), 0.01);
    }

    @DisplayName("a truncated file whose page tree was cut off is rebuilt from its surviving pages")
    @Test
    public void truncatedPageTreeIsRecovered() throws Exception {
        // A linearized file writes its first page up front and its page tree last, so truncation
        // keeps the page and loses the tree.  The surviving trailer's /Prev also points past the
        // end; following it used to throw on every lookup (602695 (2).pdf, a 296MB file cut to 104MB).
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 9 0 R >>\nendobj\n");
        pdf.append("2 0 obj\n<< /Type /Page /Parent 9 0 R /MediaBox [0 0 300 200] >>\nendobj\n");
        pdf.append("3 0 obj\n<< /Type /Page /Parent 9 0 R /MediaBox [0 0 400 200] >>\nendobj\n");
        pdf.append("trailer\n<< /Root 1 0 R /Size 10 /Prev 99999999 >>\nstartxref\n99999998\n%%EOF\n");

        Document document = new Document();
        document.setByteArray(pdf.toString().getBytes(StandardCharsets.ISO_8859_1), 0, pdf.length(),
                "truncated.pdf");
        assertEquals(2, document.getNumberOfPages());
        // file order is kept
        assertEquals(300, document.getPageTree().getPage(0).getMediaBox().getWidth(), 0.01);
        assertEquals(400, document.getPageTree().getPage(1).getMediaBox().getWidth(), 0.01);
    }

    @DisplayName("a file whose xref offsets are all wrong is rebuilt from its objects")
    @Test
    public void badObjectOffsets() throws Exception {
        Document document = open("bad_object_offset.pdf");
        try {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(firstPageText(document).startsWith(EXPECTED_OPENING));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a file cut off before its trailer is rebuilt from its objects")
    @Test
    public void missingTrailerAndStartxref() throws Exception {
        // Truncation takes the trailer first, being the last thing written, and the repair used to
        // be gated on finding one - so the commonest damage of all was the one kind that could not
        // be rebuilt, even with every object intact.  The catalog is now found by reading the
        // objects and the trailer reconstructed around it.
        Document document = open("bad_missing_xref.pdf");
        try {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(firstPageText(document).startsWith(EXPECTED_OPENING));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a file with a startxref pointing past its end is rebuilt from its objects")
    @Test
    public void startxrefPastEndOfFile() throws Exception {
        // This one still claims an offset for its cross-reference table; the offset is beyond the
        // end of the file, and there is no trailer to fall back to.
        Document document = open("bad_partial_xref.pdf");
        try {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(firstPageText(document).startsWith(EXPECTED_OPENING));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the three damaged copies all recover the same document")
    @Test
    public void allThreeRecoverTheSameText() throws Exception {
        // The assertion that makes the three above mean something: a repair that recovered the
        // wrong objects, or stopped part way, would still open a document and still extract text.
        // What it would not do is agree with the other two.
        String offsets;
        String truncated;
        String pastEnd;

        Document document = open("bad_object_offset.pdf");
        try {
            offsets = firstPageText(document);
        } finally {
            document.dispose();
        }
        document = open("bad_missing_xref.pdf");
        try {
            truncated = firstPageText(document);
        } finally {
            document.dispose();
        }
        document = open("bad_partial_xref.pdf");
        try {
            pastEnd = firstPageText(document);
        } finally {
            document.dispose();
        }

        assertTrue(offsets.length() > 200, "the page should hold a paragraph of text, not a word");
        assertEquals(offsets, truncated, "the truncated copy should recover the same text");
        assertEquals(offsets, pastEnd, "the copy with a bad startxref should recover the same text");
    }

    @DisplayName("a rebuilt document finds its catalog and its page tree")
    @Test
    public void rebuiltDocumentHasACatalog() throws Exception {
        // Without a trailer there is no /Root, so the catalog has to be found among the objects;
        // everything else in the document hangs off it.
        Document document = open("bad_missing_xref.pdf");
        try {
            Catalog catalog = document.getCatalog();
            assertNotNull(catalog, "the repair should have found a catalog");
            assertNotNull(catalog.getPageTree(), "and the page tree it points at");
            assertEquals(1, catalog.getPageTree().getNumberOfPages());
        } finally {
            document.dispose();
        }
    }

    // ------------------------------------------------------------------
    // damage nothing can be done about
    // ------------------------------------------------------------------

    @DisplayName("a file that is not a PDF at all is refused")
    @Test
    public void notAPdf() {
        // This fixture is a zip archive, not a damaged PDF: there are no objects to find, so there
        // is nothing to rebuild from and saying so is the right answer.
        assertThrows(IllegalStateException.class, () -> open("bad_encoding.pdf"));
    }
}
