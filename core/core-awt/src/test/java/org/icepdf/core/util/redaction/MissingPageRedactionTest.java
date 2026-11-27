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
 * GH-702: a page tree whose /Count is more than the pages it holds. The missing page comes back null,
 * which redaction used to dereference, so nothing was written at all.
 */
public class MissingPageRedactionTest {

    private static final String TERM = "bravo";

    @DisplayName("a page counted but missing is skipped and reported, and the rest is redacted")
    @Test
    public void missingPageReported() throws Exception {
        Saved saved = redact(fixture(2));

        assertFalse(saved.text.contains(TERM), "the page that is there is redacted");
        assertTrue(saved.report.getWarnings().stream().anyMatch(w -> w.getKind() == RedactionWarning.Kind.STREAM_NOT_PARSED
                && w.toString().contains("Page 1")), "the missing page is reported: " + saved.report.getWarnings());
        assertNotEquals(RedactionConfidence.VERIFIED, saved.report.getConfidence(),
                "a page nobody read can't be called verified");
    }

    @DisplayName("control: a page tree whose count is right is redacted without a warning")
    @Test
    public void countRight() throws Exception {
        Saved saved = redact(fixture(1));

        assertFalse(saved.text.contains(TERM));
        assertTrue(saved.report.getWarnings().isEmpty(), saved.report.getWarnings().toString());
        assertEquals(RedactionConfidence.VERIFIED, saved.report.getConfidence(), saved.report.toString());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static final class Saved {
        final RedactionReport report;
        final String text;

        Saved(RedactionReport report, String text) {
            this.report = report;
            this.text = text;
        }
    }

    private static Saved redact(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "missing-page.pdf");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            List<Rectangle> bounds = RedactionFixtures.wordBounds(page, Collections.singletonList(TERM));
            assertEquals(1, bounds.size(), "fixture should draw the term");
            page.addAnnotation(RedactionFixtures.redactionOver(document, bounds.get(0)));
            Redactor.configure(document, RedactionRequest.ofTerms(
                    Collections.singletonList(new SearchTerm(TERM, false, false, false))));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeToOutputStream(out, WriteMode.FULL_UPDATE);
            return new Saved(document.getRedactionReport(), PdfBytes.text(out.toByteArray()));
        } finally {
            document.dispose();
        }
    }

    /** One page drawing the term, in a page tree that says it has {@code count}. */
    private static byte[] fixture(int count) {
        String content = "BT /F1 12 Tf 72 720 Td (" + TERM + " report) Tj ET";
        List<String> objects = new ArrayList<>(List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [4 0 R] /Count " + count + " >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding "
                        + "/FirstChar 32 /LastChar 126 /Widths [" + "500 ".repeat(95) + "] >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, "%PDF-1.7\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            write(out, (i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n");
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        write(out, table + "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref
                + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }
}
