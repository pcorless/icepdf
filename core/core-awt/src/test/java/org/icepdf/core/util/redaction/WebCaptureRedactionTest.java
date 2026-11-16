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
import org.icepdf.core.pobjects.Names;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
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
 * GH-691: Web Capture data, which records the address and title of each page captured from the web.
 * A page's title usually repeats its heading, so it stays in the file after the heading is burned.
 */
public class WebCaptureRedactionTest {

    private static final String TERM = "bravo";
    private static final Name SPIDER_INFO = new Name("SpiderInfo");
    private static final Name URLS = new Name("URLS");
    private static final String ADDRESS = "http://" + TERM + ".example.com/";
    private static final String SPIDER_INFO_ENTRY = "/SpiderInfo << /V 1.0 /C [<< /URL (" + ADDRESS + ") /L 1 >>] >>";
    private static final String NAME_TREES = "/IDS << /Names [<0123456789ABCDEF0123456789ABCDEF> 8 0 R] >> "
            + "/URLS << /Names [(" + ADDRESS + ") 8 0 R] >>";
    private static final String CONTENT_SET = "<< /Type /SpiderContentSet /S /SPS "
            + "/ID <0123456789ABCDEF0123456789ABCDEF> /T (Page about " + TERM + ") /O [4 0 R] "
            + "/SI << /AU (" + ADDRESS + ") /TS (D:20200101000000Z) >> >>";

    @DisplayName("the catalog's /SpiderInfo and the /IDS and /URLS name trees leave the file")
    @Test
    public void leavesTheFile() throws Exception {
        Saved saved = redact(fixture(SPIDER_INFO_ENTRY + " /Names 7 0 R", "<< " + NAME_TREES + " >>", CONTENT_SET));

        saved.assertNoWebCapture();
        assertEquals(RedactionConfidence.VERIFIED_WITH_WARNINGS, saved.report.getConfidence(),
                saved.report.toString());
    }

    @DisplayName("a name dictionary written inline in the catalog")
    @Test
    public void inlineNames() throws Exception {
        Saved saved = redact(fixture(SPIDER_INFO_ENTRY + " /Names << " + NAME_TREES + " >>", "<< >>", CONTENT_SET));

        saved.assertNoWebCapture();
    }

    @DisplayName("other name trees stay")
    @Test
    public void otherNamesStay() throws Exception {
        Saved saved = redact(fixture("/Names 7 0 R",
                "<< " + NAME_TREES + " /Dests << /Names [(top) [4 0 R /Fit]] >> >>", CONTENT_SET));

        saved.assertNoWebCapture();
        Document document = open(saved.pdf);
        try {
            Names names = document.getCatalog().getNames();
            assertNotNull(names.getDestsNameTree(), "the destinations should still be there");
            assertNotNull(names.getDestsNameTree().searchName("top"));
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a burn with no terms removes it too")
    @Test
    public void annotationsAloneRemoveIt() throws Exception {
        Document document = open(fixture(SPIDER_INFO_ENTRY + " /Names 7 0 R", "<< " + NAME_TREES + " >>", CONTENT_SET));
        Page page = document.getPageTree().getPage(0);
        page.init();
        page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(70, 715, 100, 20)));

        save(document, RedactionRequest.ofAnnotations()).assertNoWebCapture();
    }

    @DisplayName("the report counts it and warns once")
    @Test
    public void reportSaysSo() throws Exception {
        Saved saved = redact(fixture(SPIDER_INFO_ENTRY + " /Names 7 0 R", "<< " + NAME_TREES + " >>", CONTENT_SET));

        assertEquals(1, saved.report.getCountsByTarget().get(RedactionTarget.APPLICATION_DATA));
        assertEquals(1, saved.report.getWarnings().size(), saved.report.getWarnings().toString());
        assertTrue(saved.report.getWarnings().get(0).toString().contains("Web Capture"));
    }

    @DisplayName("it can be kept by leaving it out of scope, and then the term fails verification")
    @Test
    public void canBeKept() throws Exception {
        Saved saved = save(open(fixture(SPIDER_INFO_ENTRY + " /Names 7 0 R", "<< " + NAME_TREES + " >>", CONTENT_SET)),
                RedactionRequest.ofTerms(terms()).with(RedactionOptions.defaults()
                        .targets(EnumSet.complementOf(EnumSet.of(RedactionTarget.APPLICATION_DATA)))));

        assertTrue(saved.allText.contains(TERM), "it was out of scope");
        assertEquals(RedactionConfidence.FAILED, saved.report.getConfidence());
    }

    @DisplayName("a document without any has nothing to report")
    @Test
    public void nothingToRemove() throws Exception {
        Saved saved = redact(fixture("/Names 7 0 R", "<< /Dests << /Names [(top) [4 0 R /Fit]] >> >>"));

        assertNull(saved.report.getCountsByTarget().get(RedactionTarget.APPLICATION_DATA));
        assertEquals(Collections.emptyList(), saved.report.getWarnings());
        assertEquals(RedactionConfidence.VERIFIED, saved.report.getConfidence(), saved.report.toString());
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

        void assertNoWebCapture() throws Exception {
            assertFalse(allText.contains(TERM), "Web Capture data is still in the file");
            assertFalse(allText.contains("SpiderContentSet"), "the content set is still in the file");
            Document document = open(pdf);
            try {
                assertFalse(document.getCatalog().getEntries().containsKey(SPIDER_INFO));
                Names names = document.getCatalog().getNames();
                if (names != null) {
                    assertFalse(names.getEntries().containsKey(Names.IDS_KEY));
                    assertFalse(names.getEntries().containsKey(URLS));
                }
                assertEquals(1, document.getNumberOfPages());
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
        document.setInputStream(new ByteArrayInputStream(pdf), "web-capture.pdf");
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

    /**
     * One page, object 4. Object 7 is given, and extra objects start at 8.
     *
     * @param catalogEntries entries added to the catalog
     */
    private static byte[] fixture(String catalogEntries, String seventh, String... extra) {
        String pageContent = "BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET";
        List<String> objects = new ArrayList<>(List.of(
                "<< /Type /Catalog /Pages 2 0 R " + catalogEntries + " >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                "<< >>",
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
