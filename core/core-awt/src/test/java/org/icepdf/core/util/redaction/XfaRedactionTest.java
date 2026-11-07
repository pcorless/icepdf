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

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFA form data, a second copy of a form's contents in XML beside its AcroForm fields. There is no
 * XFA support to mask it with, so it leaves the file, and the AcroForm fields stay.
 */
public class XfaRedactionTest {

    private static final String TERM = "bravo";
    private static final Name XFA = new Name("XFA");
    private static final Name NEEDS_RENDERING = new Name("NeedsRendering");
    private static final String DATASETS = "<xfa:datasets><xfa:data><name>" + TERM + " in xfa</name></xfa:data></xfa:datasets>";
    private static final String TEMPLATE = "<template><field name=\"" + TERM + " template\"/></template>";

    @DisplayName("a single XFA stream leaves the file")
    @Test
    public void singleStream() throws Exception {
        Saved saved = redact(fixture("/XFA 8 0 R", "", compressed("", DATASETS)));

        saved.assertNoXfa();
    }

    @DisplayName("every packet of an XFA array leaves the file")
    @Test
    public void arrayOfPackets() throws Exception {
        Saved saved = redact(fixture("/XFA [(template) 8 0 R (datasets) 9 0 R]", "",
                compressed("", TEMPLATE), compressed("", DATASETS)));

        saved.assertNoXfa();
    }

    @DisplayName("an XFA packet typed as an embedded file leaves the file")
    @Test
    public void typedAsEmbeddedFile() throws Exception {
        Saved saved = redact(fixture("/XFA [(datasets) 8 0 R]", "", compressed("/Type /EmbeddedFile", DATASETS)));

        saved.assertNoXfa();
    }

    @DisplayName("XFA on an AcroForm written inline in the catalog leaves the file")
    @Test
    public void inlineAcroForm() throws Exception {
        Saved saved = redact(inlineFixture(compressed("", DATASETS)));

        saved.assertNoXfa();
    }

    @DisplayName("a burn with no terms removes it too")
    @Test
    public void annotationsAloneRemoveIt() throws Exception {
        Document document = open(fixture("/XFA 8 0 R", "", compressed("", DATASETS)));
        Page page = document.getPageTree().getPage(0);
        page.init();
        page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(70, 715, 100, 20)));

        save(document, RedactionRequest.ofAnnotations()).assertNoXfa();
    }

    @DisplayName("the flag asking a reader to build pages from XFA goes with it")
    @Test
    public void needsRenderingGoes() throws Exception {
        Saved saved = redact(fixture("/XFA 8 0 R", "/NeedsRendering true", compressed("", DATASETS)));

        saved.assertNoXfa();
        Document document = open(saved.pdf);
        try {
            assertFalse(document.getCatalog().getEntries().containsKey(NEEDS_RENDERING));
            assertEquals(1, document.getNumberOfPages());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the AcroForm stays, with its fields masked as usual")
    @Test
    public void acroFormStays() throws Exception {
        Saved saved = redact(fixture("/XFA 8 0 R", "", compressed("", DATASETS)));

        Document document = open(saved.pdf);
        try {
            InteractiveForm form = document.getCatalog().getInteractiveForm();
            assertNotNull(form, "the form should still be there");
            assertEquals(1, form.getFields().size());
        } finally {
            document.dispose();
        }
        assertFalse(saved.allText.contains(TERM + " field"), "the field value should be masked");
        assertTrue(saved.allText.contains("**** field"), saved.allText);
        assertEquals(RedactionConfidence.VERIFIED_WITH_WARNINGS, saved.report.getConfidence());
    }

    @DisplayName("the report counts it and warns about usage rights and signatures")
    @Test
    public void reportSaysSo() throws Exception {
        Saved saved = redact(fixture("/XFA 8 0 R", "", compressed("", DATASETS)));

        assertEquals(1, saved.report.getCountsByTarget().get(RedactionTarget.APPLICATION_DATA));
        assertEquals(1, saved.report.getWarnings().size(), saved.report.getWarnings().toString());
        String warning = saved.report.getWarnings().get(0).toString();
        assertTrue(warning.contains("XFA"), warning);
        assertTrue(warning.contains("usage rights"), warning);
        assertTrue(warning.contains("signatures"), warning);
    }

    @DisplayName("it can be kept by leaving it out of scope, and then the term fails verification")
    @Test
    public void canBeKept() throws Exception {
        Saved saved = save(open(fixture("/XFA 8 0 R", "", compressed("", DATASETS))),
                RedactionRequest.ofTerms(terms()).with(RedactionOptions.defaults()
                        .targets(EnumSet.of(RedactionTarget.FORM_VALUES))));

        assertTrue(saved.allText.contains(DATASETS), "it was out of scope");
        assertEquals(RedactionConfidence.FAILED, saved.report.getConfidence());
    }

    @DisplayName("a form without XFA has nothing to report")
    @Test
    public void nothingToRemove() throws Exception {
        Saved saved = redact(fixture("", ""));

        assertNull(saved.report.getCountsByTarget().get(RedactionTarget.APPLICATION_DATA));
        assertEquals(Collections.emptyList(), saved.report.getWarnings());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static final class Saved {
        final byte[] pdf;
        final RedactionReport report;
        /** The file's own bytes and every stream in it, decoded. */
        final String allText;

        Saved(byte[] pdf, RedactionReport report, String allText) {
            this.pdf = pdf;
            this.report = report;
            this.allText = allText;
        }

        void assertNoXfa() throws Exception {
            assertFalse(allText.contains("xfa:data"), "XFA data is still in the file");
            assertFalse(allText.contains("<template"), "XFA template is still in the file");
            Document document = open(pdf);
            try {
                assertFalse(document.getCatalog().getInteractiveForm().getEntries().containsKey(XFA));
            } finally {
                document.dispose();
            }
        }
    }

    private static List<SearchTerm> terms() {
        return Collections.singletonList(new SearchTerm(TERM, false, false, false));
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "xfa.pdf");
        return document;
    }

    private Saved redact(byte[] pdf) throws Exception {
        return save(open(pdf), RedactionRequest.ofTerms(terms()));
    }

    private Saved save(Document document, RedactionRequest request) throws Exception {
        try {
            Redactor.configure(document, request);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeToOutputStream(out, WriteMode.FULL_UPDATE);
            byte[] pdf = out.toByteArray();
            return new Saved(pdf, document.getRedactionReport(), allText(pdf));
        } finally {
            document.dispose();
        }
    }

    private static String allText(byte[] pdf) throws Exception {
        StringBuilder text = new StringBuilder(new String(pdf, StandardCharsets.ISO_8859_1));
        Document document = open(pdf);
        try {
            Library library = document.getCatalog().getLibrary();
            Set<Reference> references = new HashSet<>();
            for (CrossReference crossReference : library.getCrossReferenceRoot().getCrossReferences()) {
                references.addAll(crossReference.getEntries().keySet());
            }
            for (Reference reference : references) {
                Object object = library.getObject(reference);
                if (object instanceof Stream) {
                    byte[] bytes = ((Stream) object).getDecodedStreamBytes();
                    if (bytes != null) {
                        text.append('\n').append(new String(bytes, StandardCharsets.ISO_8859_1));
                    }
                }
            }
            return text.toString();
        } finally {
            document.dispose();
        }
    }

    private static String compressed(String entries, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) {
            deflater.write(content.getBytes(StandardCharsets.ISO_8859_1));
        }
        return "<< " + entries + " /Filter /FlateDecode /Length " + bytes.size() + " >>\nstream\n"
                + new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1) + "\nendstream";
    }

    /** An indirect AcroForm: object 7. Extra objects start at 8. */
    private static byte[] fixture(String acroFormEntries, String catalogEntries, String... extra) {
        return build("/AcroForm 7 0 R " + catalogEntries,
                "<< /Fields [6 0 R] /DA (/Helv 12 Tf 0 g) /DR << /Font << /Helv 3 0 R >> >> " + acroFormEntries + " >>", extra);
    }

    /** The AcroForm inline in the catalog. Object 7 is then the XFA stream. */
    private static byte[] inlineFixture(String xfa) {
        return build("/AcroForm << /Fields [6 0 R] /DA (/Helv 12 Tf 0 g) /DR << /Font << /Helv 3 0 R >> >> /XFA 7 0 R >>", xfa);
    }

    private static byte[] build(String catalogEntries, String seventh, String... extra) {
        String pageContent = "BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET";
        List<String> objects = new ArrayList<>(List.of(
                "<< /Type /Catalog /Pages 2 0 R " + catalogEntries + " >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R /Annots [6 0 R] >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                "<< /Type /Annot /Subtype /Widget /FT /Tx /T (field) /V (" + TERM + " field) /Rect [72 400 300 420] /F 4 "
                        + "/P 4 0 R /DA (/Helv 12 Tf 0 g) >>",
                seventh));
        objects.addAll(List.of(extra));
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
