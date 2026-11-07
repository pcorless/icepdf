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
package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.PdfBytes;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text strings a PDF isn't supposed to hold, but real files do: UTF-16 little endian, or with no byte
 * order mark. A viewer can read them as text, so a redaction has to as well, or the term stays.
 */
public class LittleEndianRedactionTest {

    private static final String TERM = "bravo";
    private static final String PAGE = "BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET";

    @DisplayName("little endian with no byte order mark is decoded")
    @Test
    public void decodesLittleEndian() {
        assertEquals("bravo report", TermMasker.decodeText(bytes("bravo report", StandardCharsets.UTF_16LE)));
    }

    @DisplayName("little endian with a byte order mark is decoded, past Latin-1 too")
    @Test
    public void decodesLittleEndianMark() {
        byte[] text = bytes("中文 bravo", StandardCharsets.UTF_16LE);
        byte[] marked = new byte[text.length + 2];
        marked[0] = (byte) 0xFF;
        marked[1] = (byte) 0xFE;
        System.arraycopy(text, 0, marked, 2, text.length);
        assertEquals("中文 bravo", TermMasker.decodeText(marked));
    }

    @DisplayName("big endian with no byte order mark is decoded")
    @Test
    public void decodesBigEndianWithoutMark() {
        assertEquals("bravo report", TermMasker.decodeText(bytes("bravo report", StandardCharsets.UTF_16BE)));
    }

    @DisplayName("a conforming text string reads as it always did")
    @Test
    public void conformingStringsAreUnchanged() {
        assertEquals("bravo", TermMasker.decodeText(bytes("bravo", StandardCharsets.ISO_8859_1)));
        byte[] big = bytes("﻿bravo", StandardCharsets.UTF_16BE);
        assertEquals("bravo", TermMasker.decodeText(big));
    }

    @DisplayName("bytes with no pattern to them aren't taken for UTF-16")
    @Test
    public void otherBytesAreLeftAlone() {
        byte[] mixed = {0x41, 0x00, 0x00, 0x42, 0x43, 0x44};
        assertEquals(org.icepdf.core.util.Utils.decodeTextString(mixed), TermMasker.decodeText(mixed));
        byte[] odd = {0x41, 0x00, 0x42};
        assertEquals(org.icepdf.core.util.Utils.decodeTextString(odd), TermMasker.decodeText(odd));
    }

    @DisplayName("a term in a little-endian /Alt is masked on a burned page")
    @Test
    public void markedContentIsMasked() throws Exception {
        String alt = "/Span << /Alt <" + hex(bytes(TERM + " here", StandardCharsets.UTF_16LE)) + "> >> BDC " + PAGE + " EMC";
        Document document = open(fixture(alt, ""));
        Page page = document.getPageTree().getPage(0);
        page.init();
        page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(70, 715, 50, 20)));
        Saved saved = save(document, RedactionRequest.ofAnnotationsAndTerms(terms()));

        assertFalse(saved.content.contains(hex(bytes(TERM, StandardCharsets.UTF_16LE))), "still in the page");
        assertEquals(1, saved.report.getCountsByTarget().get(RedactionTarget.TAGGED_TEXT));
    }

    @DisplayName("a term in a little-endian field value is masked")
    @Test
    public void fieldValueIsMasked() throws Exception {
        Saved saved = save(open(fixture(PAGE, " /V <" + hex(bytes(TERM + " here", StandardCharsets.UTF_16LE)) + ">")),
                RedactionRequest.ofTerms(terms()));

        assertFalse(saved.content.contains(hex(bytes(TERM, StandardCharsets.UTF_16LE))), "still in the field");
        assertEquals(RedactionConfidence.VERIFIED, saved.report.getConfidence());
    }

    @DisplayName("a little-endian term at the very start of a hex string in page content fails verification")
    @Test
    public void verifierReadsLittleEndianAtTheStart() throws Exception {
        // on a page with no burn, so nothing masks it: the term is its first bytes
        String alt = "/Span << /Alt <" + hex(bytes(TERM, StandardCharsets.UTF_16LE)) + "> >> BDC " + PAGE + " EMC";
        Saved saved = save(open(fixture(alt, "")), RedactionRequest.ofTerms(terms()));

        assertEquals(RedactionConfidence.FAILED, saved.report.getConfidence());
    }

    @DisplayName("a little-endian term in a string out of scope fails verification")
    @Test
    public void verifierReadsLittleEndianStrings() throws Exception {
        Saved saved = save(open(fixture(PAGE, " /V <" + hex(bytes(TERM, StandardCharsets.UTF_16LE)) + ">")),
                RedactionRequest.ofTerms(terms()).with(RedactionOptions.defaults()
                        .targets(java.util.EnumSet.of(RedactionTarget.PAGE_CONTENT))));

        assertEquals(RedactionConfidence.FAILED, saved.report.getConfidence());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static final class Saved {
        final RedactionReport report;
        final String content;

        Saved(byte[] pdf, RedactionReport report) {
            this.report = report;
            // upper-case hex and page content, so a test can look for a string however it was written
            this.content = PdfBytes.text(pdf).toUpperCase(java.util.Locale.ROOT);
        }
    }

    private static List<SearchTerm> terms() {
        return Collections.singletonList(new SearchTerm(TERM, false, false, false));
    }

    private static byte[] bytes(String text, java.nio.charset.Charset charset) {
        return text.getBytes(charset);
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "little-endian.pdf");
        return document;
    }

    private static Saved save(Document document, RedactionRequest request) throws Exception {
        try {
            Redactor.configure(document, request);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeToOutputStream(out, WriteMode.FULL_UPDATE);
            return new Saved(out.toByteArray(), document.getRedactionReport());
        } finally {
            document.dispose();
        }
    }

    private static byte[] fixture(String pageContent, String fieldEntries) {
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [6 0 R] /DA (/Helv 12 Tf 0 g) "
                        + "/DR << /Font << /Helv 3 0 R >> >> >> >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R /Annots [6 0 R] >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                "<< /Type /Annot /Subtype /Widget /FT /Tx /T (field) /Rect [72 400 300 420] /F 4 /P 4 0 R "
                        + "/DA (/Helv 12 Tf 0 g)" + fieldEntries + " >>",
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.length + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        write(out, table + "trailer\n<< /Size " + (objects.length + 1) + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}
