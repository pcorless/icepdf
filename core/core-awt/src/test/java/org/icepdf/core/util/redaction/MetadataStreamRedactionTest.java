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
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-691: XMP metadata on objects other than the catalog. Any stream or dictionary can carry its own
 * {@code /Metadata} (PDF 32000-2 14.3.2), and a marked-content property list did, repeating a term
 * the catalog's XMP no longer held.
 */
public class MetadataStreamRedactionTest {

    private static final String TERM = "bravo";
    private static final String XMP = "<?xpacket begin=\"\"?><x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
            + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description "
            + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:creator>" + TERM + " Smith</dc:creator>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";

    @DisplayName("a marked-content property list's XMP")
    @Test
    public void propertyList() throws Exception {
        Saved saved = redact(fixture("/Properties << /MC0 6 0 R >>", "", false));
        assertMasked(saved, 1);
    }

    @DisplayName("a page's own XMP, compressed")
    @Test
    public void pageCompressed() throws Exception {
        Saved saved = redact(fixture("", "/Metadata 7 0 R", true));
        assertMasked(saved, 1);
    }

    @DisplayName("the rest of the XMP stays")
    @Test
    public void restStays() throws Exception {
        Saved saved = redact(fixture("/Properties << /MC0 6 0 R >>", "", false));
        assertTrue(saved.allText.contains("<dc:creator>**** Smith</dc:creator>"), saved.allText);
    }

    @DisplayName("left out of scope, the XMP stays and verification fails")
    @Test
    public void outOfScope() throws Exception {
        Saved saved = save(open(fixture("/Properties << /MC0 6 0 R >>", "", false)), RedactionRequest.ofTerms(terms())
                .with(RedactionOptions.defaults().targets(EnumSet.complementOf(EnumSet.of(RedactionTarget.METADATA)))));
        assertTrue(saved.allText.contains(TERM + " Smith"));
        assertEquals(RedactionConfidence.FAILED, saved.report.getConfidence());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static void assertMasked(Saved saved, int count) {
        assertFalse(saved.allText.contains(TERM), "the term is still in the file");
        assertEquals(count, saved.report.getCountsByTarget().get(RedactionTarget.METADATA));
        assertEquals(RedactionConfidence.VERIFIED, saved.report.getConfidence(), saved.report.toString());
    }

    private static final class Saved {
        final RedactionReport report;
        /** The file's own bytes and every stream in it, decoded. */
        final String allText;

        Saved(RedactionReport report, String allText) {
            this.report = report;
            this.allText = allText;
        }
    }

    private static List<SearchTerm> terms() {
        return Collections.singletonList(new SearchTerm(TERM, false, false, false));
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "metadata.pdf");
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
            return new Saved(document.getRedactionReport(), allText(pdf));
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
     * A page drawing a marked-content span tagged with property list 6, whose /Metadata is object 7.
     * The catalog has no XMP of its own.
     *
     * @param resourceEntries entries added to the page's resources
     * @param pageEntries     entries added to the page
     * @param compress        whether the XMP stream is Flate-compressed
     */
    private static byte[] fixture(String resourceEntries, String pageEntries, boolean compress) {
        String pageContent = "/OC /MC0 BDC BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET EMC";
        byte[] xmp = XMP.getBytes(StandardCharsets.UTF_8);
        String filter = "";
        if (compress) {
            Deflater deflater = new Deflater();
            deflater.setInput(xmp);
            deflater.finish();
            byte[] buffer = new byte[4096];
            int length = deflater.deflate(buffer);
            deflater.end();
            xmp = java.util.Arrays.copyOf(buffer, length);
            filter = " /Filter /FlateDecode";
        }
        List<byte[]> objects = new ArrayList<>(List.of(
                bytes("<< /Type /Catalog /Pages 2 0 R >>"),
                bytes("<< /Type /Pages /Kids [4 0 R] /Count 1 >>"),
                bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"),
                bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> "
                        + resourceEntries + " >> /Contents 5 0 R " + pageEntries + " >>"),
                bytes("<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream"),
                bytes("<< /Type /OCG /Name (Layer) /Metadata 7 0 R >>"),
                concat(bytes("<< /Type /Metadata /Subtype /XML /Length " + xmp.length + filter + " >>\nstream\n"),
                        xmp, bytes("\nendstream"))));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(bytes("%PDF-1.7\n"));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.writeBytes(bytes((i + 1) + " 0 obj\n"));
            out.writeBytes(objects.get(i));
            out.writeBytes(bytes("\nendobj\n"));
        }
        int xref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            table.append(String.format("%010d 00000 n \n", offset));
        }
        out.writeBytes(bytes(table + "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n"));
        return out.toByteArray();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
