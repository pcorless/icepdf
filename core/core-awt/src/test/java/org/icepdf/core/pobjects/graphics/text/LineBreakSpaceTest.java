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
package org.icepdf.core.pobjects.graphics.text;

import org.icepdf.core.pobjects.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GH-676: the space a line break adds between the last glyph of one line and the first of the next.
 * It was placed by the gap between the two, which for a glyph back at the start of the next line put
 * it inside the line that ended, so a space was doubled or a word split once the line was sorted.
 */
public class LineBreakSpaceTest {

    private static final String LINES = "BT /F1 12 Tf 14 TL 72 720 Td\n"
            + "(Patient intake form) Tj T*\n"
            + "(E-mail: jane.doe@example.com) Tj T*\n"
            + "(Phone: \\(555\\) 010-0199) Tj T*\n"
            + "(Card on file: 4111 1111 1111 1111) Tj T*\n"
            + "ET";

    @DisplayName("lines read as written, with no space added or doubled")
    @Test
    public void linesReadAsWritten() throws Exception {
        assertEquals("Patient intake form\n"
                        + "E-mail: jane.doe@example.com\n"
                        + "Phone: (555) 010-0199\n"
                        + "Card on file: 4111 1111 1111 1111",
                text(LINES));
    }

    @DisplayName("each line moved with Td reads the same")
    @Test
    public void linesMovedWithTd() throws Exception {
        assertEquals("first line of text\nsecond line here\nthird",
                text("BT /F1 12 Tf 72 720 Td (first line of text) Tj 0 -14 Td (second line here) Tj "
                        + "0 -14 Td (third) Tj ET"));
    }

    @DisplayName("a line that continues to the right on another baseline is still separated")
    @Test
    public void continuesOnAnotherBaseline() throws Exception {
        // a raised run within half a line's height stays on the line, after a space
        assertEquals("before raised",
                text("BT /F1 12 Tf 72 720 Td (before) Tj 6 Ts (raised) Tj ET"));
    }

    private static String text(String content) throws Exception {
        Document document = new Document();
        byte[] pdf = pdf(content);
        document.setInputStream(new ByteArrayInputStream(pdf), "line-break.pdf");
        try {
            return document.getPageText(0).getTextSequence().text().toString();
        } finally {
            document.dispose();
        }
    }

    /** One page in non-embedded Helvetica, the case the issue was found on. */
    private static byte[] pdf(String content) {
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> "
                        + "/Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
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
