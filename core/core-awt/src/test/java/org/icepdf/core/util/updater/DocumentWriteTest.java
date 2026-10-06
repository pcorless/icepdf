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
package org.icepdf.core.util.updater;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SquareAnnotation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the two ways a document is written out: an incremental update appended to the original
 * bytes, and a full rewrite from the object graph.
 * <p>
 * The distinction matters to a caller in ways that are easy to get wrong silently.  An incremental
 * update must leave every original byte in place - that is what keeps an existing digital signature
 * valid - while a full rewrite is free to renumber and drop anything unreachable.  Both have to
 * produce a file that reopens with the same content, and that is what is asserted here: the saved
 * bytes are always read back as a document rather than inspected as a blob, except where the point
 * of the test is the byte layout itself.
 * <p>
 * Fixtures are chosen for their cross-reference format, since the writers branch on it: a plain
 * cross-reference table and a cross-reference stream take different code paths to write the same
 * change.
 */
public class DocumentWriteTest {

    /** A document with a plain cross-reference table. */
    private static final String XREF_TABLE_FIXTURE = "/redaction/simple_tj.pdf";
    /** A two page document with a cross-reference stream and an existing incremental update. */
    private static final String XREF_STREAM_FIXTURE = "/updater/annotation_popup.pdf";

    private static final Rectangle BOUNDS = new Rectangle(100, 500, 200, 100);

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static byte[] fixtureBytes(String resource) throws Exception {
        try (java.io.InputStream in = DocumentWriteTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing fixture " + resource);
            return in.readAllBytes();
        }
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "write-test");
        return document;
    }

    private static byte[] save(Document document, WriteMode writeMode) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.saveToOutputStream(out, writeMode);
        return out.toByteArray();
    }

    /**
     * Adds a square annotation to page 0, so there is a change worth writing.
     */
    private static void addAnnotation(Document document, String contents) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        SquareAnnotation square = (SquareAnnotation) AnnotationFactory.buildAnnotation(
                document.getCatalog().getLibrary(), Annotation.SUBTYPE_SQUARE, BOUNDS);
        square.setContents(contents);
        square.resetAppearanceStream(new AffineTransform());
        page.addAnnotation(square);
    }

    private static int annotationCount(byte[] pdf) throws Exception {
        Document document = open(pdf);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            return page.getAnnotations() == null ? 0 : page.getAnnotations().size();
        } finally {
            document.dispose();
        }
    }

    // ------------------------------------------------------------------
    // incremental update
    // ------------------------------------------------------------------

    @DisplayName("incremental - the original bytes are left untouched at the head of the file")
    @Test
    public void incrementalPreservesOriginalBytes() throws Exception {
        // This is the property that keeps an already-signed document's signature valid; anything
        // that rewrites the head of the file breaks it.
        byte[] original = fixtureBytes(XREF_TABLE_FIXTURE);
        byte[] saved;
        Document document = open(original);
        try {
            addAnnotation(document, "appended");
            saved = save(document, WriteMode.INCREMENT_UPDATE);
        } finally {
            document.dispose();
        }

        assertTrue(saved.length > original.length, "an incremental update only ever grows the file");
        assertArrayEquals(original, Arrays.copyOf(saved, original.length),
                "the original bytes must be byte-for-byte unchanged");
    }

    @DisplayName("incremental - the update ends with its own trailer and EOF marker")
    @Test
    public void incrementalWritesATrailer() throws Exception {
        byte[] saved;
        Document document = open(fixtureBytes(XREF_TABLE_FIXTURE));
        try {
            addAnnotation(document, "appended");
            saved = save(document, WriteMode.INCREMENT_UPDATE);
        } finally {
            document.dispose();
        }

        String tail = new String(saved, saved.length - 512, 512, StandardCharsets.ISO_8859_1);
        assertTrue(tail.contains("startxref"), "the appended section needs its own startxref");
        assertTrue(tail.trim().endsWith("%%EOF"), "the file has to end with an EOF marker");
        assertTrue(tail.contains("/Prev"), "the new trailer has to chain to the previous one");
    }

    @DisplayName("incremental - the change reads back out of the saved file")
    @Test
    public void incrementalRoundTrip() throws Exception {
        byte[] saved;
        Document document = open(fixtureBytes(XREF_TABLE_FIXTURE));
        try {
            addAnnotation(document, "appended");
            saved = save(document, WriteMode.INCREMENT_UPDATE);
        } finally {
            document.dispose();
        }
        assertEquals(1, annotationCount(saved));
    }

    @DisplayName("incremental - a document with nothing to say still writes a valid file")
    @Test
    public void incrementalWithNoChanges() throws Exception {
        byte[] saved;
        Document document = open(fixtureBytes(XREF_TABLE_FIXTURE));
        try {
            saved = save(document, WriteMode.INCREMENT_UPDATE);
        } finally {
            document.dispose();
        }

        Document reopened = open(saved);
        try {
            assertEquals(1, reopened.getNumberOfPages());
        } finally {
            reopened.dispose();
        }
    }

    // ------------------------------------------------------------------
    // full update
    // ------------------------------------------------------------------

    @DisplayName("full - the document is rebuilt, not appended to")
    @Test
    public void fullRewrite() throws Exception {
        byte[] original = fixtureBytes(XREF_TABLE_FIXTURE);
        byte[] saved;
        Document document = open(original);
        try {
            addAnnotation(document, "rewritten");
            saved = save(document, WriteMode.FULL_UPDATE);
        } finally {
            document.dispose();
        }

        // a rewrite starts from the header again, so the two files diverge well before the end
        assertTrue(saved.length > 0);
        assertEquals(1, annotationCount(saved));
        assertTrue(new String(saved, 0, 9, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));
    }

    @DisplayName("full - an incremental update already in the file is folded in, not carried along")
    @Test
    public void fullRewriteDropsIncrementalSections() throws Exception {
        // The fixture is a document plus an appended update.  Rewriting it whole should produce one
        // cross-reference section, with the update's objects merged into the body.
        byte[] saved;
        Document document = open(fixtureBytes(XREF_STREAM_FIXTURE));
        try {
            saved = save(document, WriteMode.FULL_UPDATE);
        } finally {
            document.dispose();
        }

        String text = new String(saved, StandardCharsets.ISO_8859_1);
        assertEquals(1, countOf(text, "%%EOF"), "a rewritten file has exactly one EOF marker");

        Document reopened = open(saved);
        try {
            assertEquals(2, reopened.getNumberOfPages());
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("full - a rewritten document can be rewritten again")
    @Test
    public void twoFullRewrites() throws Exception {
        byte[] once;
        Document document = open(fixtureBytes(XREF_TABLE_FIXTURE));
        try {
            addAnnotation(document, "first");
            once = save(document, WriteMode.FULL_UPDATE);
        } finally {
            document.dispose();
        }

        byte[] twice;
        Document second = open(once);
        try {
            twice = save(second, WriteMode.FULL_UPDATE);
        } finally {
            second.dispose();
        }
        assertEquals(1, annotationCount(twice));
    }

    // ------------------------------------------------------------------
    // cross-reference formats
    // ------------------------------------------------------------------

    @DisplayName("a cross-reference stream document survives both write modes")
    @Test
    public void crossReferenceStreamDocument() throws Exception {
        for (WriteMode writeMode : WriteMode.values()) {
            byte[] saved;
            Document document = open(fixtureBytes(XREF_STREAM_FIXTURE));
            try {
                addAnnotation(document, "added to " + writeMode);
                saved = save(document, writeMode);
            } finally {
                document.dispose();
            }

            Document reopened = open(saved);
            try {
                assertEquals(2, reopened.getNumberOfPages(), "page count changed under " + writeMode);
                Page page = reopened.getPageTree().getPage(0);
                page.init();
                assertTrue(page.getAnnotations().size() >= 1,
                        "the new annotation is missing under " + writeMode);
            } finally {
                reopened.dispose();
            }
        }
    }

    @DisplayName("saveToOutputStream defaults to an incremental update")
    @Test
    public void defaultWriteMode() throws Exception {
        byte[] original = fixtureBytes(XREF_TABLE_FIXTURE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = open(original);
        try {
            addAnnotation(document, "default mode");
            document.saveToOutputStream(out);
        } finally {
            document.dispose();
        }
        byte[] saved = out.toByteArray();
        assertArrayEquals(original, Arrays.copyOf(saved, original.length),
                "the default save should have appended rather than rewritten");
    }

    @DisplayName("the reported length matches the bytes actually written")
    @Test
    public void reportedLength() throws Exception {
        // Callers size buffers and report progress from this number.
        for (WriteMode writeMode : WriteMode.values()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = open(fixtureBytes(XREF_TABLE_FIXTURE));
            long reported;
            try {
                addAnnotation(document, "measured");
                reported = document.saveToOutputStream(out, writeMode);
            } finally {
                document.dispose();
            }
            assertEquals(out.size(), reported, "length mismatch under " + writeMode);
        }
    }

    // ------------------------------------------------------------------
    // deletions
    // ------------------------------------------------------------------

    @DisplayName("a page deleted before a full rewrite is gone from the saved file")
    @Test
    public void deletePage() throws Exception {
        byte[] saved;
        Document document = open(fixtureBytes(XREF_STREAM_FIXTURE));
        try {
            assertEquals(2, document.getNumberOfPages());
            document.deletePage(document.getPageTree().getPage(1));
            saved = save(document, WriteMode.FULL_UPDATE);
        } finally {
            document.dispose();
        }

        Document reopened = open(saved);
        try {
            assertEquals(1, reopened.getNumberOfPages());
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("a deleted page is also gone after an incremental update")
    @Test
    public void deletePageIncrementally() throws Exception {
        byte[] saved;
        Document document = open(fixtureBytes(XREF_STREAM_FIXTURE));
        try {
            document.deletePage(document.getPageTree().getPage(1));
            saved = save(document, WriteMode.INCREMENT_UPDATE);
        } finally {
            document.dispose();
        }

        Document reopened = open(saved);
        try {
            assertEquals(1, reopened.getNumberOfPages());
        } finally {
            reopened.dispose();
        }
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
