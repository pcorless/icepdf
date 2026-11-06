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
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PInfo;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A named term is meant to be gone from the whole file, so the verifier has to look where a scan of
 * the file's bytes can't: compressed streams and hex strings. It also mustn't cry wolf over binary
 * streams, where a short term can turn up by chance.
 */
public class HiddenTermVerificationTest {

    private static final String TERM = "bravo";

    @DisplayName("a term left in a compressed appearance stream is a failure")
    @Test
    public void compressedAppearance() throws Exception {
        assertFound(" /AP << /N 7 0 R >>", compressed(FORM, "BT (" + TERM + ") Tj ET"));
    }

    @DisplayName("a term in a hex string inside a compressed appearance stream is a failure")
    @Test
    public void hexStringInCompressedAppearance() throws Exception {
        assertFound(" /AP << /N 7 0 R >>", compressed(FORM, "BT <" + hex(TERM) + "> Tj ET"));
    }

    @DisplayName("a term in a compressed stream value is a failure")
    @Test
    public void compressedStreamValue() throws Exception {
        assertFound(" /RV 7 0 R", compressed("", TERM));
    }

    @DisplayName("a UTF-16 term in a compressed stream is a failure")
    @Test
    public void utf16CompressedStream() throws Exception {
        assertFound(" /RV 7 0 R", compressed("", "þÿ" + latin1Of(TERM.getBytes(StandardCharsets.UTF_16BE))));
    }

    @DisplayName("a UTF-8 term with an accent in a compressed stream is a failure")
    @Test
    public void utf8CompressedStream() throws Exception {
        String accented = "bravó";
        assertEquals(RedactionConfidence.FAILED, confidence(accented, " /RV 7 0 R",
                compressed("", latin1Of(("<name>" + accented + "</name>").getBytes(StandardCharsets.UTF_8)))));
    }

    @DisplayName("a term in a hex string is a failure")
    @Test
    public void hexString() throws Exception {
        assertFound(" /TU <" + hex(TERM) + ">", "<< >>");
    }

    @DisplayName("a UTF-16 term in a hex string is a failure")
    @Test
    public void utf16HexString() throws Exception {
        assertFound(" /TU <FEFF" + hexOf(TERM.getBytes(StandardCharsets.UTF_16BE)) + ">", "<< >>");
    }

    @DisplayName("a term in compressed XFA data is a failure")
    @Test
    public void compressedXfa() throws Exception {
        assertFound(" /RV 7 0 R", compressed("", "<xfa:data><name>" + TERM + "</name></xfa:data>"));
    }

    @DisplayName("a term in a stream typed as an embedded file is a failure")
    @Test
    public void embeddedFileType() throws Exception {
        assertFound(" /RV 7 0 R", compressed("/Type /EmbeddedFile", "<xfa:data><name>" + TERM + "</name></xfa:data>"));
    }

    @DisplayName("a term in a stream of no known kind is a failure")
    @Test
    public void streamOfNoKnownKind() throws Exception {
        assertFound(" /X << /Data 7 0 R >>", compressed("", "(" + TERM + ") text"));
    }

    @DisplayName("a term in a hex string in page content is a failure")
    @Test
    public void hexStringInPageContent() throws Exception {
        assertEquals(RedactionConfidence.FAILED, pageContentConfidence("<FEFF"
                + hexOf(TERM.getBytes(StandardCharsets.UTF_16BE)) + ">"));
    }

    @DisplayName("a UTF-16 term at an odd offset of a hex string in page content is a failure")
    @Test
    public void misalignedHexStringInPageContent() throws Exception {
        assertEquals(RedactionConfidence.FAILED, pageContentConfidence("<00"
                + hexOf(TERM.getBytes(StandardCharsets.UTF_16BE)) + ">"));
    }

    @DisplayName("a hex string in page content without the term verifies")
    @Test
    public void otherHexStringInPageContent() throws Exception {
        assertEquals(RedactionConfidence.VERIFIED, pageContentConfidence("<FEFF"
                + hexOf("other".getBytes(StandardCharsets.UTF_16BE)) + ">"));
    }

    @DisplayName("a term nowhere in the file verifies")
    @Test
    public void absentTerm() throws Exception {
        assertClean(" /AP << /N 7 0 R >>", compressed(FORM, "BT (other) Tj ET"));
    }

    @DisplayName("image data containing the term by chance isn't a leak")
    @Test
    public void imageBytes() throws Exception {
        assertClean(" /X 7 0 R", compressed("/Type /XObject /Subtype /Image /Width 5 /Height 1 "
                + "/BitsPerComponent 8 /ColorSpace /DeviceGray", TERM));
    }

    @DisplayName("a font program containing the term by chance isn't a leak")
    @Test
    public void fontProgram() throws Exception {
        assertClean(" /X 7 0 R", compressed("/Length1 5", TERM));
    }

    @DisplayName("a compact font containing the term by chance isn't a leak")
    @Test
    public void compactFont() throws Exception {
        assertClean(" /X 7 0 R", compressed("/Subtype /Type1C", TERM));
    }

    @DisplayName("an ICC profile containing the term by chance isn't a leak")
    @Test
    public void iccProfile() throws Exception {
        assertClean(" /X 7 0 R", compressed("/N 3 /Alternate /DeviceRGB", TERM));
    }

    @DisplayName("a sampled function containing the term by chance isn't a leak")
    @Test
    public void sampledFunction() throws Exception {
        assertClean(" /X 7 0 R", compressed("/FunctionType 0 /Domain [0 1] /Range [0 1] /Size [5] /BitsPerSample 8",
                TERM));
    }

    @DisplayName("a character map naming a font like the term isn't a leak")
    @Test
    public void characterMap() throws Exception {
        assertClean(" /X 7 0 R", compressed("", "/CIDInit /ProcSet findresource begin 12 dict begin begincmap "
                + "/CMapName /" + TERM + " def endcmap"));
    }

    @DisplayName("a term left in a string of an encrypted document is a failure")
    @ParameterizedTest
    @ValueSource(strings = {"encrypted-aes-v2.pdf", "encrypted_objstm_rc4.pdf"})
    public void encryptedString(String fixture) throws Exception {
        assertEquals(RedactionConfidence.FAILED, encryptedConfidence(fixture, TERM));
    }

    @DisplayName("an encrypted document without the term doesn't fail")
    @ParameterizedTest
    @ValueSource(strings = {"encrypted-aes-v2.pdf", "encrypted_objstm_rc4.pdf"})
    public void encryptedWithoutTheTerm(String fixture) throws Exception {
        assertNotEquals(RedactionConfidence.FAILED, encryptedConfidence(fixture, "zzqqzzqq"));
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * An encrypted document with the term in a string of its information dictionary, which stays
     * because metadata is out of the redaction's scope.
     */
    private RedactionConfidence encryptedConfidence(String fixture, String term) throws Exception {
        Document document = new Document();
        try (InputStream stream = getClass().getResourceAsStream("/updater/" + fixture)) {
            document.setInputStream(stream, fixture);
        }
        try {
            PInfo info = document.getInfo();
            info.getEntries().put(new Name("Probe"), new LiteralStringObject(TERM, info.getPObjectReference()));
            document.getCatalog().getLibrary().getStateManager()
                    .addChange(new PObject(info, info.getPObjectReference()));
            Redactor.configure(document, RedactionRequest.ofTerms(
                            Collections.singletonList(new SearchTerm(term, false, false, false)))
                    .with(RedactionOptions.defaults().targets(EnumSet.of(RedactionTarget.PAGE_CONTENT))));
            document.writeToOutputStream(new ByteArrayOutputStream(), WriteMode.FULL_UPDATE);
            return document.getRedactionReport().getConfidence();
        } finally {
            document.dispose();
        }
    }

    private static final String PAGE_CONTENT = "BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET";
    private static final String FORM = "/Type /XObject /Subtype /Form /BBox [0 0 100 20]";

    private void assertFound(String fieldEntries, String extraObject) throws Exception {
        assertEquals(RedactionConfidence.FAILED, confidence(TERM, fieldEntries, extraObject));
    }

    private void assertClean(String fieldEntries, String extraObject) throws Exception {
        RedactionConfidence confidence = confidence(TERM, fieldEntries, extraObject);
        assertTrue(confidence == RedactionConfidence.VERIFIED, "got " + confidence);
    }

    /** The string as a marked-content property, as a tagged document writes alternate text. */
    private RedactionConfidence pageContentConfidence(String hexString) throws Exception {
        return confidence(TERM, fixture("/Span << /Alt " + hexString + " >> BDC " + PAGE_CONTENT + " EMC", "", "<< >>"));
    }

    private RedactionConfidence confidence(String term, String fieldEntries, String extraObject) throws Exception {
        return confidence(term, fixture(PAGE_CONTENT, fieldEntries, extraObject));
    }

    private RedactionConfidence confidence(String term, byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "hidden.pdf");
        try {
            Redactor.configure(document, RedactionRequest.ofTerms(
                    Collections.singletonList(new SearchTerm(term, false, false, false))));
            document.writeToOutputStream(new ByteArrayOutputStream(), WriteMode.FULL_UPDATE);
            return document.getRedactionReport().getConfidence();
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
                + latin1Of(bytes.toByteArray()) + "\nendstream";
    }

    private static String latin1Of(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static String hex(String text) {
        return hexOf(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String hexOf(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }

    /** A field whose entries say where the term is, away from any page text. */
    private static byte[] fixture(String pageContent, String fieldEntries, String extraObject) {
        String[] objects = {
                "<< /Type /Catalog /Pages 2 0 R /AcroForm << /Fields [6 0 R] /DA (/Helv 12 Tf 0 g) "
                        + "/DR << /Font << /Helv 3 0 R >> >> >> >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R /Annots [6 0 R] >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                "<< /Type /Annot /Subtype /Widget /FT /Tx /T (field) /V (kept) /Rect [72 400 300 420] /F 4 /P 4 0 R "
                        + "/DA (/Helv 12 Tf 0 g)" + fieldEntries + " >>",
                extraObject,
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
