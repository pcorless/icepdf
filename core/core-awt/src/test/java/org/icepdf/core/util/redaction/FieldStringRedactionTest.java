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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.PdfBytes;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Everywhere a form field can hold a term besides a plain {@code /V}. The field sits away from any
 * page text, so only the term-driven rewrite can reach it.
 */
public class FieldStringRedactionTest {

    private static final String TERM = "bravo";
    private static final String TEXT_FIELD = "/FT /Tx /T (field)";

    @DisplayName("a value in a text stream is masked, and the stream leaves the file")
    @Test
    public void streamValue() throws Exception {
        Saved saved = redact(TEXT_FIELD + " /V 7 0 R", compressed("", TERM + " report"));

        saved.assertNoTerm();
        assertTrue(saved.text.contains("**** report"), "the rest of the value should stay");
    }

    @DisplayName("a value in a UTF-16 text stream is masked")
    @Test
    public void utf16StreamValue() throws Exception {
        Saved saved = redact(TEXT_FIELD + " /V 7 0 R", compressed("", "þÿ"
                + new String((TERM + " report").getBytes(StandardCharsets.UTF_16BE), StandardCharsets.ISO_8859_1)));

        saved.assertNoTerm();
        assertTrue(saved.text.contains("**** report"), "the rest of the value should stay");
    }

    @DisplayName("each selected value of a multi-select list is masked")
    @Test
    public void arrayValue() throws Exception {
        Saved saved = redact("/FT /Ch /Ff 2097152 /T (multi) /V [(" + TERM + " one) (kept)] /Opt [(kept)]", "<< >>");

        saved.assertNoTerm();
        assertTrue(saved.text.contains("**** one"), saved.text);
        assertTrue(saved.text.contains("kept"), "the other value should stay");
    }

    @DisplayName("rich text is masked")
    @Test
    public void richText() throws Exception {
        Saved saved = redact(TEXT_FIELD + " /Ff 33554432 /V (kept) /RV (<body><p>" + TERM + "</p></body>)", "<< >>");

        saved.assertNoTerm();
        assertTrue(saved.text.contains("<body><p>****</p></body>"), saved.text);
    }

    @DisplayName("rich text in a stream is masked")
    @Test
    public void richTextStream() throws Exception {
        Saved saved = redact(TEXT_FIELD + " /Ff 33554432 /V (kept) /RV 7 0 R", compressed("", "<p>" + TERM + "</p>"));

        saved.assertNoTerm();
    }

    @DisplayName("plain options on a choice field are masked, and the others stay")
    @Test
    public void plainOptions() throws Exception {
        Saved saved = redact("/FT /Ch /Ff 131072 /T (combo) /V (" + TERM + ") /Opt [(" + TERM + ") (other)]", "<< >>");

        saved.assertNoTerm();
        assertTrue(saved.text.contains("(other)"), saved.text);
    }

    @DisplayName("both halves of an option pair are masked")
    @Test
    public void optionPairs() throws Exception {
        Saved saved = redact("/FT /Ch /Ff 131072 /T (pair) /V (e1) /Opt [[(" + TERM + " e) (" + TERM + " d)] [(e2) (other)]]",
                "<< >>");

        saved.assertNoTerm();
        assertTrue(saved.text.contains("(other)"), saved.text);
    }

    @DisplayName("a combo box redrawn after masking doesn't draw the old option")
    @Test
    public void choiceAppearance() throws Exception {
        Saved saved = redact("/FT /Ch /Ff 131072 /T (combo) /V (" + TERM + ") /Opt [(" + TERM + ") (other)]"
                + " /AP << /N 7 0 R >>", appearance(TERM));

        saved.assertNoTerm();
    }

    @DisplayName("a list box redrawn after masking doesn't draw the old option")
    @Test
    public void listAppearance() throws Exception {
        Saved saved = redact("/FT /Ch /T (list) /V (other) /Opt [(" + TERM + ") (other)] /AP << /N 7 0 R >>",
                appearance(TERM));

        saved.assertNoTerm();
    }

    @DisplayName("the tooltip is masked")
    @Test
    public void tooltip() throws Exception {
        redact(TEXT_FIELD + " /V (kept) /TU (" + TERM + ")", "<< >>").assertNoTerm();
    }

    @DisplayName("a hex tooltip is masked")
    @Test
    public void hexTooltip() throws Exception {
        redact(TEXT_FIELD + " /V (kept) /TU <FEFF" + hex(TERM.getBytes(StandardCharsets.UTF_16BE)) + ">", "<< >>")
                .assertNoTerm();
    }

    @DisplayName("the field name and the export name are masked")
    @Test
    public void fieldNames() throws Exception {
        redact("/FT /Tx /T (" + TERM + " name) /TM (" + TERM + " export) /V (kept)", "<< >>").assertNoTerm();
    }

    @DisplayName("a widget's own contents are masked")
    @Test
    public void widgetContents() throws Exception {
        redact(TEXT_FIELD + " /V (kept) /Contents <" + hex(TERM.getBytes(StandardCharsets.ISO_8859_1)) + ">", "<< >>")
                .assertNoTerm();
    }

    @DisplayName("an appearance drawing the term is redrawn, though the value doesn't hold it")
    @Test
    public void appearanceDiffersFromValue() throws Exception {
        Saved saved = redact(TEXT_FIELD + " /V (kept) /AP << /N 7 0 R >>", appearance(TERM));

        saved.assertNoTerm();
        assertTrue(saved.streams.contains("kept") || saved.streams.contains(hex("kept".getBytes(StandardCharsets.ISO_8859_1))),
                "the appearance should draw the value now");
    }

    @DisplayName("an appearance drawing the term as a hex string is redrawn too")
    @Test
    public void hexAppearanceDiffersFromValue() throws Exception {
        redact(TEXT_FIELD + " /V (kept) /AP << /N 7 0 R >>",
                compressed(FORM, "/Tx BMC q BT /Helv 12 Tf 2 5 Td <" + hex(TERM.getBytes(StandardCharsets.ISO_8859_1))
                        + "> Tj ET Q EMC")).assertNoTerm();
    }

    @DisplayName("a parent's options reach the kid that draws them")
    @Test
    public void inheritedOptions() throws Exception {
        Saved saved = redact("/T (kid) /Parent 8 0 R /AP << /N 7 0 R >>", appearance(TERM),
                "<< /FT /Ch /T (parent) /V (" + TERM + ") /Opt [(" + TERM + ") (other)] /Kids [6 0 R] >>", 8);

        saved.assertNoTerm();
    }

    @DisplayName("a field without the term isn't touched")
    @Test
    public void noMatch() throws Exception {
        String appearance = "/Tx BMC q BT /Helv 12 Tf 2 5 Td (other) Tj ET Q EMC";
        Saved saved = redact("/FT /Ch /Ff 131072 /T (combo) /TU (tip) /V (other) /Opt [(other) (more)] /AP << /N 7 0 R >>",
                compressed(FORM, appearance));

        assertNull(saved.report.getCountsByTarget().get(RedactionTarget.FORM_VALUES));
        assertTrue(saved.streams.contains(appearance), "the appearance should be as it was");
    }

    @DisplayName("out of scope, the strings stay")
    @Test
    public void outOfScope() throws Exception {
        Saved saved = save(fixture(TEXT_FIELD + " /V (kept) /TU (" + TERM + ")", "<< >>", null, 0),
                RedactionOptions.defaults().targets(java.util.EnumSet.of(RedactionTarget.PAGE_CONTENT)));

        assertTrue(saved.text.contains("/TU (" + TERM + ")") || saved.text.contains(TERM), saved.text);
    }

    @DisplayName("the report counts each field once")
    @Test
    public void reportCountsFields() throws Exception {
        Saved saved = redact("/FT /Ch /Ff 131072 /T (" + TERM + ") /TU (" + TERM + ") /V (" + TERM + ") /Opt [(" + TERM + ")]",
                "<< >>");

        assertEquals(1, saved.report.getCountsByTarget().get(RedactionTarget.FORM_VALUES));
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static final String FORM = "/Type /XObject /Subtype /Form /BBox [0 0 228 20] "
            + "/Resources << /Font << /Helv 3 0 R >> >>";

    private static final class Saved {
        final RedactionReport report;
        /** The file's own text and its object streams. */
        final String text;
        /** Every stream in the file, decoded. */
        final String streams;

        Saved(byte[] pdf, RedactionReport report) throws Exception {
            this.report = report;
            this.text = PdfBytes.text(pdf);
            this.streams = decodedStreams(pdf);
        }

        void assertNoTerm() {
            assertFalse(streams.contains(TERM), "still in a stream: " + streams);
            assertFalse(streams.contains(hex(TERM.getBytes(StandardCharsets.ISO_8859_1))), "still in a stream as hex");
            assertFalse(text.contains(TERM), "still in the file");
            assertFalse(text.contains(hex(TERM.getBytes(StandardCharsets.UTF_16BE))), "still in a hex string");
            assertFalse(text.contains(hex(TERM.getBytes(StandardCharsets.ISO_8859_1))), "still in a hex string");
        }
    }

    private Saved redact(String fieldEntries, String extraObject) throws Exception {
        return save(fixture(fieldEntries, extraObject, null, 0), RedactionOptions.defaults());
    }

    private Saved redact(String fieldEntries, String extraObject, String parent, int parentNumber) throws Exception {
        return save(fixture(fieldEntries, extraObject, parent, parentNumber), RedactionOptions.defaults());
    }

    private Saved save(byte[] pdf, RedactionOptions options) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "field-strings.pdf");
        try {
            Redactor.configure(document, RedactionRequest.ofTerms(
                    Collections.singletonList(new SearchTerm(TERM, false, false, false))).with(options));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeToOutputStream(out, WriteMode.FULL_UPDATE);
            return new Saved(out.toByteArray(), document.getRedactionReport());
        } finally {
            document.dispose();
        }
    }

    private static String decodedStreams(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "saved.pdf");
        try {
            Library library = document.getCatalog().getLibrary();
            Set<Reference> references = new HashSet<>();
            for (CrossReference crossReference : library.getCrossReferenceRoot().getCrossReferences()) {
                references.addAll(crossReference.getEntries().keySet());
            }
            StringBuilder streams = new StringBuilder();
            for (Reference reference : references) {
                Object object = library.getObject(reference);
                if (object instanceof Stream) {
                    byte[] bytes = ((Stream) object).getDecodedStreamBytes();
                    if (bytes != null) {
                        streams.append(new String(bytes, StandardCharsets.ISO_8859_1)).append('\n');
                    }
                }
            }
            return streams.toString();
        } finally {
            document.dispose();
        }
    }

    private static String appearance(String text) throws IOException {
        return compressed(FORM, "/Tx BMC q BT /Helv 12 Tf 2 5 Td (" + text + ") Tj ET Q EMC");
    }

    private static String compressed(String entries, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) {
            deflater.write(content.getBytes(StandardCharsets.ISO_8859_1));
        }
        return "<< " + entries + " /Filter /FlateDecode /Length " + bytes.size() + " >>\nstream\n"
                + new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1) + "\nendstream";
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    /** The widget is object 6 and the extra object 7; a parent, when there is one, is 8 and the top field. */
    private static byte[] fixture(String fieldEntries, String extraObject, String parent, int parentNumber) {
        int topField = parent != null ? parentNumber : 6;
        String pageContent = "BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET";
        List<String> objects = new ArrayList<>(List.of(
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [" + topField + " 0 R] /DA (/Helv 12 Tf 0 g) "
                        + "/DR << /Font << /Helv 3 0 R >> >> >> >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R /Annots [6 0 R] >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                "<< /Type /Annot /Subtype /Widget /Rect [72 400 300 420] /F 4 /P 4 0 R /DA (/Helv 12 Tf 0 g) "
                        + fieldEntries + " >>",
                extraObject));
        if (parent != null) {
            objects.add(parent);
        }
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
