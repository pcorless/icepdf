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
package org.icepdf.core.pobjects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-677: input with no PDF structure must fail with an {@link IOException} that says so, not an
 * internal NullPointerException wrapped in an IllegalStateException.  Services rely on that to tell
 * "this isn't a PDF" apart from a damaged PDF and from a bug.
 */
public class NotAPdfTest {

    /** A one-page document with no cross-reference table, so loading it always goes through a reindex. */
    private static final String MINIMAL_BODY =
            "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
            "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>\nendobj\n" +
            "trailer\n<< /Root 1 0 R /Size 4 >>\n%%EOF\n";

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @DisplayName("setByteArray on non-PDF input throws IOException")
    @ParameterizedTest
    @ValueSource(strings = {"hello", "", "%PDF-1.7\ngarbage", "%PDF-"})
    public void byteArrayThrowsIOException(String input) {
        byte[] b = bytes(input);
        IOException e = assertThrows(IOException.class,
                () -> new Document().setByteArray(b, 0, b.length, "x.pdf"));
        assertTrue(e.getMessage().startsWith("Not a"), e.getMessage());
    }

    @DisplayName("setInputStream on non-PDF input throws IOException")
    @Test
    public void inputStreamThrowsIOException() {
        assertThrows(IOException.class,
                () -> new Document().setInputStream(new ByteArrayInputStream(bytes("hello")), "x.pdf"));
    }

    @DisplayName("the message says whether a %PDF- header was present")
    @Test
    public void messageDistinguishesMissingHeader() {
        byte[] noHeader = bytes("hello");
        byte[] header = bytes("%PDF-1.7\ngarbage");
        IOException a = assertThrows(IOException.class,
                () -> new Document().setByteArray(noHeader, 0, noHeader.length, "x.pdf"));
        IOException b = assertThrows(IOException.class,
                () -> new Document().setByteArray(header, 0, header.length, "x.pdf"));
        assertTrue(a.getMessage().contains("no %PDF- header"), a.getMessage());
        assertTrue(b.getMessage().startsWith("Not a readable PDF"), b.getMessage());
    }

    @DisplayName("a trailer whose /Root resolves to nothing throws IOException")
    @Test
    public void missingCatalogThrowsIOException() {
        byte[] b = bytes("%PDF-1.7\n1 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n" +
                "trailer\n<< /Root 9 0 R /Size 2 >>\n%%EOF\n");
        IOException e = assertThrows(IOException.class,
                () -> new Document().setByteArray(b, 0, b.length, "x.pdf"));
        assertTrue(e.getMessage().contains("catalog"), e.getMessage());
    }

    @DisplayName("control: a damaged but real PDF still loads through the reindex")
    @Test
    public void damagedPdfStillLoads() throws Exception {
        byte[] b = bytes("%PDF-1.7\n" + MINIMAL_BODY);
        Document document = new Document();
        document.setByteArray(b, 0, b.length, "ok.pdf");
        assertEquals(1, document.getNumberOfPages());
        document.dispose();
    }

    @DisplayName("control: the word obj in a comment does not abort the reindex")
    @Test
    public void strayObjWordDoesNotAbortReindex() throws Exception {
        byte[] b = bytes("%PDF-1.7\n% the obj word, not an object\n" + MINIMAL_BODY);
        Document document = new Document();
        document.setByteArray(b, 0, b.length, "ok.pdf");
        assertEquals(1, document.getNumberOfPages());
        document.dispose();
    }

    @DisplayName("control: a missing %PDF- header alone does not refuse a recoverable file")
    @Test
    public void headerlessPdfStillLoads() throws Exception {
        // the header line is simply missing; up to GH-677 the header scan sliced away the start of such a file
        byte[] b = bytes("\n" + MINIMAL_BODY);
        Document document = new Document();
        document.setByteArray(b, 0, b.length, "ok.pdf");
        assertEquals(1, document.getNumberOfPages());
        document.dispose();
    }
}
