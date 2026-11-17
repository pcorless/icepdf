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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-691: a signature that names a redacted term. The signer's name is in the signature dictionary,
 * in the certificates inside the signature, and usually drawn by the field's appearance. None of
 * that can be masked without the rest disagreeing, so the redaction is refused and nothing written.
 */
public class SignatureRedactionTest {

    private static final String TERM = "bravo";
    private static final Reference FIELD = new Reference(6, 0);
    private static final Name PERMS = new Name("Perms");
    private static final Name DOC_MDP = new Name("DocMDP");

    @DisplayName("a signer's name in /Name is refused")
    @Test
    public void signerName() throws Exception {
        assertRefused(fixture(signature("/Name (" + TERM + ")", "unrelated"), "Signed"), "Signature1");
    }

    @DisplayName("a term only in the certificates inside the signature is refused")
    @Test
    public void certificate() throws Exception {
        assertRefused(fixture(signature("/Name (someone)", "CN=" + TERM), "Signed"), "Signature1");
    }

    @DisplayName("a term only in the appearance's nested form is refused")
    @Test
    public void appearance() throws Exception {
        assertRefused(fixture(signature("/Name (someone)", "unrelated"), "Signed by " + TERM), "Signature1");
    }

    @DisplayName("a term in /Location is refused")
    @Test
    public void location() throws Exception {
        assertRefused(fixture(signature("/Location (" + TERM + " town)", "unrelated"), "Signed"), "Signature1");
    }

    @DisplayName("a signature on a parent field is refused")
    @Test
    public void parentField() throws Exception {
        assertRefused(parentFixture(signature("/Name (" + TERM + ")", "unrelated")), "Signature1");
    }

    @DisplayName("a term in the field's own name is masked in the message")
    @Test
    public void fieldNameMasked() throws Exception {
        byte[] pdf = fixture(signature("/Name (" + TERM + ")", "unrelated"), "Signed");
        String renamed = new String(pdf, StandardCharsets.ISO_8859_1).replace("/T (Signature1)", "/T (" + TERM + " sig1)");
        assertRefused(renamed.getBytes(StandardCharsets.ISO_8859_1), "**** sig1");
    }

    @DisplayName("a signature without a term is left alone and the redaction goes ahead")
    @Test
    public void unrelatedSignatureStays() throws Exception {
        Saved saved = redact(fixture(signature("/Name (someone)", "unrelated"), "Signed"));

        Document document = open(saved.pdf);
        try {
            Library library = document.getCatalog().getLibrary();
            DictionaryEntries field = entries(library, FIELD);
            assertTrue(field.containsKey(FieldDictionary.V_KEY));
            assertTrue(field.containsKey(Annotation.APPEARANCE_STREAM_KEY));
            assertNotNull(library.getDictionary(document.getCatalog().getEntries(), PERMS).get(DOC_MDP));
        } finally {
            document.dispose();
        }
        assertEquals(RedactionConfidence.VERIFIED, saved.report.getConfidence(), saved.report.toString());
    }

    @DisplayName("with form values out of scope there's no refusal, and verification fails")
    @Test
    public void outOfScope() throws Exception {
        Saved saved = save(open(fixture(signature("/Name (" + TERM + ")", "unrelated"), "Signed")),
                RedactionRequest.ofTerms(terms()).with(RedactionOptions.defaults()
                        .targets(EnumSet.complementOf(EnumSet.of(RedactionTarget.FORM_VALUES)))));

        assertTrue(saved.allText.contains(TERM));
        assertEquals(RedactionConfidence.FAILED, saved.report.getConfidence());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static void assertRefused(byte[] pdf, String fieldName) throws Exception {
        Document document = open(pdf);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Redactor.configure(document, RedactionRequest.ofTerms(terms()));
            RedactionRefusedException refused = assertThrows(RedactionRefusedException.class,
                    () -> document.writeToOutputStream(out, WriteMode.FULL_UPDATE));
            assertEquals(fieldName, refused.getFieldName());
            assertTrue(refused.getMessage().contains("'" + fieldName + "'"), refused.getMessage());
            assertTrue(refused.getMessage().contains("signature"), refused.getMessage());
            assertFalse(refused.getMessage().contains(TERM), "the message gives the term away");
        } finally {
            document.dispose();
        }
        assertEquals(0, out.size(), "nothing should be written");
    }

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
    }

    private static DictionaryEntries entries(Library library, Reference reference) {
        Object object = library.getObject(reference);
        return object instanceof org.icepdf.core.pobjects.Dictionary
                ? ((org.icepdf.core.pobjects.Dictionary) object).getEntries() : (DictionaryEntries) object;
    }

    private static List<SearchTerm> terms() {
        return Collections.singletonList(new SearchTerm(TERM, false, false, false));
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "signature.pdf");
        return document;
    }

    private static Saved redact(byte[] pdf) throws Exception {
        return save(open(pdf), RedactionRequest.ofTerms(terms()));
    }

    private static Saved save(Document document, RedactionRequest request) throws Exception {
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

    /**
     * A signature dictionary, with a stand-in for the signature in {@code /Contents}: some bytes,
     * then the certificate text given, as DER holds a subject's name.
     */
    private static String signature(String entries, String certificateText) {
        StringBuilder hex = new StringBuilder("3082010A0201");
        for (byte b : certificateText.getBytes(StandardCharsets.ISO_8859_1)) {
            hex.append(String.format("%02X", b));
        }
        return "<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /adbe.pkcs7.detached " + entries
                + " /M (D:20200101000000Z) /ByteRange [0 0 0 0] /Contents <" + hex + "0000> >>";
    }

    private static String stream(String entries, String content) {
        return "<< " + entries + " /Length " + content.length() + " >>\nstream\n" + content + "\nendstream";
    }

    /**
     * A signed field merged with its widget, object 6. The AcroForm is 7, the signature 8, the
     * appearance 9, drawing its text from 10.
     */
    private static byte[] fixture(String signature, String drawnText) {
        return build("<< /Type /Annot /Subtype /Widget /FT /Sig /T (Signature1) /V 8 0 R /Rect [72 400 300 440] "
                        + "/F 4 /P 4 0 R /AP << /N 9 0 R >> /DR << /XObject << /FRM 9 0 R >> >> >>",
                "[6 0 R]", "/Annots [6 0 R]", signature, drawnText);
    }

    /** The signature on a parent field, 6, with a widget kid, 11. */
    private static byte[] parentFixture(String signature) {
        return build("<< /FT /Sig /T (Signature1) /V 8 0 R /Kids [11 0 R] >>", "[6 0 R]", "/Annots [11 0 R]",
                signature, "Signed",
                "<< /Type /Annot /Subtype /Widget /Parent 6 0 R /Rect [72 400 300 440] /F 4 /P 4 0 R "
                        + "/AP << /N 9 0 R >> >>");
    }

    private static byte[] build(String field, String fields, String pageEntries, String signature,
                                String drawnText, String... extra) {
        String pageContent = "BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET";
        String layer = "BT /F1 10 Tf 2 20 Td (" + drawnText + ") Tj ET";
        List<String> objects = new ArrayList<>(List.of(
                "<< /Type /Catalog /Pages 2 0 R /AcroForm 7 0 R /Perms << /DocMDP 8 0 R >> >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R " + pageEntries + " >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                field,
                "<< /Fields " + fields + " /SigFlags 3 /DR << /Font << /Helv 3 0 R >> /XObject << /FRM 9 0 R >> >> >>",
                signature,
                stream("/Type /XObject /Subtype /Form /BBox [0 0 228 40] /Resources << /XObject << /n2 10 0 R >> >>",
                        "/n2 Do"),
                stream("/Type /XObject /Subtype /Form /BBox [0 0 228 40] /Resources << /Font << /F1 3 0 R >> >>",
                        layer)));
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
