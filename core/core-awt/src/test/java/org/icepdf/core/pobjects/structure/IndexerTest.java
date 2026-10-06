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
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @DisplayName("a file with no trailer and its catalog inside an object stream is rebuilt")
    @Test
    public void catalogInObjectStreamIsFound() throws Exception {
        // A compressed file keeps its trailer in the cross-reference stream at the end, so truncation loses
        // it, and usually keeps its catalog in an object stream, which the catalog search didn't look in.
        String catalog = "<< /Type /Catalog /Pages 2 0 R >>";
        String pages = "<< /Type /Pages /Kids [4 0 R] /Count 1 >>";
        String offsets = "1 0 2 " + catalog.length() + " ";
        String objects = offsets + catalog + pages;
        StringBuilder pdf = new StringBuilder("%PDF-1.5\n");
        pdf.append("3 0 obj\n<< /Type /ObjStm /N 2 /First ").append(offsets.length())
                .append(" /Length ").append(objects.length()).append(" >>\nstream\n")
                .append(objects).append("\nendstream\nendobj\n");
        pdf.append("4 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 250 200] >>\nendobj\n");
        // truncated: no xref stream, no trailer

        Document document = new Document();
        document.setByteArray(pdf.toString().getBytes(StandardCharsets.ISO_8859_1), 0, pdf.length(),
                "objstm-catalog.pdf");
        assertEquals(1, document.getNumberOfPages());
        assertEquals(250, document.getPageTree().getPage(0).getMediaBox().getWidth(), 0.01);
    }

    @DisplayName("an encrypted file's object streams are indexed once they can be decrypted")
    @Test
    public void encryptedObjectStreamsAreIndexedAfterDecryption() throws Exception {
        // The open-time rebuild runs before the security handler exists, so it read the object stream as
        // ciphertext, indexed nothing from it, and the page tree inside was lost.
        byte[] id = "0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1);
        byte[] owner = new byte[32];
        Arrays.fill(owner, (byte) 0x41);
        int permissions = -4;
        byte[] fileKey = rc4FileKey(owner, permissions, id);
        byte[] user = rc4(fileKey, PASSWORD_PADDING);

        String pages = "<< /Type /Pages /Kids [4 0 R] /Count 1 >>";
        String page = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 200] >>";
        String offsets = "2 0 4 " + pages.length() + " ";
        byte[] objects = (offsets + pages + page).getBytes(StandardCharsets.ISO_8859_1);
        String encrypted = new String(rc4(objectKey(fileKey, 3), objects), StandardCharsets.ISO_8859_1);

        StringBuilder pdf = new StringBuilder("%PDF-1.5\n");
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        pdf.append("3 0 obj\n<< /Type /ObjStm /N 2 /First ").append(offsets.length())
                .append(" /Length ").append(encrypted.length()).append(" >>\nstream\n")
                .append(encrypted).append("\nendstream\nendobj\n");
        pdf.append("5 0 obj\n<< /Filter /Standard /V 1 /R 2 /P ").append(permissions)
                .append(" /O <").append(hex(owner)).append("> /U <").append(hex(user)).append("> >>\nendobj\n");
        // no xref, and a startxref that points nowhere
        pdf.append("trailer\n<< /Root 1 0 R /Encrypt 5 0 R /Size 6 /ID [<").append(hex(id)).append("><")
                .append(hex(id)).append(">] >>\nstartxref\n99999\n%%EOF\n");

        Document document = new Document();
        document.setByteArray(pdf.toString().getBytes(StandardCharsets.ISO_8859_1), 0, pdf.length(),
                "encrypted-objstm.pdf");
        assertEquals(1, document.getNumberOfPages());
        assertEquals(300, document.getPageTree().getPage(0).getMediaBox().getWidth(), 0.01);
    }

    private static final byte[] PASSWORD_PADDING = {
            0x28, (byte) 0xBF, 0x4E, 0x5E, 0x4E, 0x75, (byte) 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56, (byte) 0xFF,
            (byte) 0xFA, 0x01, 0x08, 0x2E, 0x2E, 0x00, (byte) 0xB6, (byte) 0xD0, 0x68, 0x3E, (byte) 0x80, 0x2F,
            0x0C, (byte) 0xA9, (byte) 0xFE, 0x64, 0x53, 0x69, 0x7A};

    /** Standard security handler R2 file key for the empty user password (ISO 32000-1 algorithm 2). */
    private static byte[] rc4FileKey(byte[] owner, int permissions, byte[] id) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(PASSWORD_PADDING);
        md5.update(owner);
        md5.update(new byte[]{(byte) permissions, (byte) (permissions >> 8), (byte) (permissions >> 16),
                (byte) (permissions >> 24)});
        md5.update(id);
        return Arrays.copyOf(md5.digest(), 5);
    }

    /** Per-object key (ISO 32000-1 algorithm 1), generation 0. */
    private static byte[] objectKey(byte[] fileKey, int objectNumber) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(fileKey);
        md5.update(new byte[]{(byte) objectNumber, (byte) (objectNumber >> 8), (byte) (objectNumber >> 16), 0, 0});
        return Arrays.copyOf(md5.digest(), fileKey.length + 5);
    }

    private static byte[] rc4(byte[] key, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("ARCFOUR");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ARCFOUR"));
        return cipher.doFinal(data);
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }

    @DisplayName("an object lookup made while rebuilding does not start another rebuild")
    @Test
    public void rebuildDoesNotRecurse() throws Exception {
        // No trailer, so a rebuild parses objects to find the catalog; the stream's /Length is indirect,
        // and resolving it goes through the table being replaced.  When that table is stale the lookup
        // failed, which started another rebuild, which parsed the same stream... until StackOverflowError.
        StringBuilder pdf = new StringBuilder("%PDF-1.5\n");
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        pdf.append("2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n");
        pdf.append("3 0 obj\n<< /Length 4 0 R >>\nstream\nBT ET\nendstream\nendobj\n");
        pdf.append("4 0 obj\n5\nendobj\n");
        pdf.append("%%EOF\n");
        String bytes = pdf.toString();

        Document document = new Document();
        document.setByteArray(bytes.getBytes(StandardCharsets.ISO_8859_1), 0, bytes.length(), "norecurse.pdf");
        Library library = document.getCatalog().getLibrary();

        // stand in for a stale xref: every offset lands on an "endobj", which is not an object header
        int garbage = bytes.indexOf("endobj");
        CrossReferenceTable stale = new CrossReferenceTable(library, new DictionaryEntries(), bytes.length());
        for (int i = 1; i <= 5; i++) {
            stale.addEntry(new CrossReferenceUsedEntry(i, 0, garbage));
        }
        CrossReferenceRoot staleRoot = new CrossReferenceRoot(library);
        staleRoot.addCrossReference(stale);
        library.setCrossReferenceRoot(staleRoot);

        // object 5 does not exist; the lookup fails, rebuilds once, and misses
        assertDoesNotThrow(() -> library.getObject(new Reference(5, 0)));
        assertNotNull(library.getObject(new Reference(1, 0)));
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
