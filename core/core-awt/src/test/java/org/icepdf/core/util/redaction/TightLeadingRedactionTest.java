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
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lines set closer than their glyph bounds are tall, as in 2005CAT.pdf (7.2pt apart, 7.9pt bounds):
 * a redaction sized to a word on the middle line reaches a sliver into the lines above and below.
 * Removing every glyph it touched took "ates  t" and "sisted" from the neighbours, and the
 * verification then found that removed text elsewhere in the document and called it a leak.
 */
public class TightLeadingRedactionTest {

    /**
     * Four lines of Helvetica 10 set 12.4pt apart, under the 13.6pt their glyph bounds are tall: the
     * neighbours overlap by 9%, as 2005CAT's do (0.7pt of 7.9pt).
     */
    static byte[] tightLeading() {
        String content = "BT /F1 10 Tf 12.4 TL 20 160 Td (dates to even before) Tj T* (in 1949 when the) Tj "
                + "T* (insisted that their) Tj T* (dates to come) Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] /Contents 4 0 R "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        StringBuilder out = new StringBuilder("%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.length());
            out.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = out.length();
        out.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) out.append(String.format("%010d 00000 n \n", offset));
        out.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return out.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "tight-leading");
        return document;
    }

    private static List<String> lines(Page page) throws Exception {
        List<String> lines = new ArrayList<>();
        for (LineText line : page.getViewText().getPageLines()) {
            lines.add(line.toString().replaceAll("[\\[\\], ]+", " ").trim());
        }
        return lines;
    }

    private static List<SearchTerm> term(String word) {
        return List.of(new SearchTerm(word, false, false, false));
    }

    private static RedactionReport redact1949(Document document, RedactionOptions options, ByteArrayOutputStream out)
            throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        List<Rectangle> bounds = RedactionFixtures.wordBounds(page, List.of("1949"));
        assertEquals(1, bounds.size());
        page.addAnnotation(RedactionFixtures.redactionOver(document, bounds.get(0)));
        Redactor.configure(document, RedactionRequest.ofAnnotationsAndTerms(term("1949")).with(options));
        document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
        return document.getRedactionReport();
    }

    @DisplayName("the fixture's lines overlap: their glyph bounds are taller than the line pitch")
    @Test
    public void fixtureOverlaps() throws Exception {
        Document document = open(tightLeading());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            List<LineText> lines = page.getViewText().getPageLines();
            assertEquals(4, lines.size(), lines.toString());
            assertTrue(lines.get(0).getBounds().intersects(lines.get(1).getBounds()),
                    "line 1 " + lines.get(0).getBounds() + " reaches line 2 " + lines.get(1).getBounds());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a redaction over a word on a tightly set line leaves the lines above and below, and verifies")
    @Test
    public void neighbouringLinesSurvive() throws Exception {
        Document document = open(tightLeading());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RedactionReport report;
        try {
            report = redact1949(document, RedactionOptions.defaults(), out);
        } finally {
            document.dispose();
        }
        assertEquals(RedactionConfidence.VERIFIED, report.getConfidence(), report.toString());
        assertEquals(java.util.Set.of("1949"), report.getRemovedText(), "only the word was taken");

        Document written = open(out.toByteArray());
        try {
            Page page = written.getPageTree().getPage(0);
            page.init();
            assertEquals(List.of("dates to even before", "in when the", "insisted that their", "dates to come"),
                    lines(page));
        } finally {
            written.dispose();
        }
    }

    /**
     * Text a redaction removed is looked for afterwards, but only that copy had to go: the same text
     * elsewhere in the document is not a leak. Any-intersection still clips the neighbours here
     * ("dates to" above), and the other "dates to" further down is no reason to fail.
     */
    @DisplayName("removed text that also appears elsewhere isn't a leak; the old any-touch rule still clips")
    @Test
    public void removedTextElsewhereIsNotALeak() throws Exception {
        Document document = open(tightLeading());
        RedactionReport report;
        try {
            report = redact1949(document,
                    RedactionOptions.defaults().glyphCoverageThreshold(RedactionOptions.ANY_INTERSECTION),
                    new ByteArrayOutputStream());
        } finally {
            document.dispose();
        }
        assertTrue(report.getRemovedText().size() > 1, "any-touch takes more than the word: " + report.getRemovedText());
        assertEquals(RedactionConfidence.VERIFIED, report.getConfidence(), report.toString());
    }
}
