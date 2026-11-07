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
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An application's private copy of a document, kept in {@code /PieceInfo}. Adobe Illustrator stores
 * an editable copy there, so text burned off the page stays readable in the file. Nothing can be
 * masked reliably in a format only its application knows, so the data goes.
 */
public class ApplicationDataRedactionTest {

    private static final String ON_THE_PAGE = "BT /F1 12 Tf 72 720 Td (hello world) Tj ET /Fm0 Do";
    private static final String IN_THE_FORM = "BT /F1 12 Tf 72 600 Td (drawn by the form) Tj ET";
    private static final String[] PRIVATE_COPIES = {"catalog private copy", "page private copy", "form private copy"};
    private static final Name PIECE_INFO = new Name("PieceInfo");

    @DisplayName("private data on the catalog, a page and a form leaves the file")
    @Test
    public void privateDataLeavesTheFile() throws Exception {
        Saved saved = redact(terms("hello"), fixture());

        for (String copy : PRIVATE_COPIES) {
            assertFalse(saved.streams.contains(copy), copy + " is still in the file");
        }
    }

    @DisplayName("a burn with no terms removes it too, as it leaves the same copy behind")
    @Test
    public void annotationsAloneRemoveIt() throws Exception {
        Document document = open(fixture());
        Page page = document.getPageTree().getPage(0);
        page.init();
        page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(70, 715, 100, 20)));
        Saved saved = save(document, RedactionRequest.ofAnnotations());

        for (String copy : PRIVATE_COPIES) {
            assertFalse(saved.streams.contains(copy), copy + " is still in the file");
        }
    }

    @DisplayName("the document still has its page and what the form draws")
    @Test
    public void theRestOfTheDocumentStays() throws Exception {
        Saved saved = redact(terms("hello"), fixture());

        assertTrue(saved.streams.contains(IN_THE_FORM), "the form's own content should be untouched");
        Document document = open(saved.pdf);
        try {
            assertEquals(1, document.getNumberOfPages());
            assertTrue(document.getPageText(0).toString().contains("drawn by the form"),
                    "the page should still draw the form");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the report counts each removal and warns once")
    @Test
    public void reportSaysSo() throws Exception {
        // a term that's nowhere in the file: a terms-only redaction leaves page text, which would fail
        Saved saved = redact(terms("zzqqzzqq"), fixture());

        assertEquals(3, saved.report.getCountsByTarget().get(RedactionTarget.APPLICATION_DATA));
        assertEquals(1, saved.report.getWarnings().size(), saved.report.getWarnings().toString());
        assertEquals(RedactionConfidence.VERIFIED_WITH_WARNINGS, saved.report.getConfidence());
    }

    @DisplayName("it can be kept by leaving it out of scope")
    @Test
    public void canBeKept() throws Exception {
        Saved saved = save(open(fixture()), RedactionRequest.ofTerms(terms("hello"))
                .with(RedactionOptions.defaults().targets(EnumSet.of(RedactionTarget.PAGE_CONTENT))));

        for (String copy : PRIVATE_COPIES) {
            assertTrue(saved.streams.contains(copy), copy + " was out of scope");
        }
    }

    @DisplayName("a document without any has nothing to report")
    @Test
    public void nothingToRemove() throws Exception {
        Saved saved = redact(terms("hello"), withoutPrivateData());

        assertNull(saved.report.getCountsByTarget().get(RedactionTarget.APPLICATION_DATA));
        assertEquals(Collections.emptyList(), saved.report.getWarnings());
    }

    @DisplayName("an encrypted form loses the entry and still decrypts")
    @ParameterizedTest
    @ValueSource(strings = {"encrypted_objstm_aes128.pdf", "encrypted_objstm_aes256.pdf", "encrypted_objstm_rc4.pdf"})
    public void encryptedForm(String fixture) throws Exception {
        Document document = new Document();
        try (InputStream stream = getClass().getResourceAsStream("/updater/" + fixture)) {
            document.setInputStream(stream, fixture);
        }
        Library library = document.getCatalog().getLibrary();
        Reference formReference = firstForm(library);
        assertNotNull(formReference, fixture + " should have a form to test with");
        Form form = (Form) library.getObject(formReference);
        byte[] before = form.getDecodedStreamBytes();
        form.getEntries().put(PIECE_INFO, new DictionaryEntries());
        library.getStateManager().addChange(new PObject(form, formReference));

        Saved saved = save(document, RedactionRequest.ofTerms(terms("zzqqzzqq")));

        Document reopened = open(saved.pdf);
        try {
            Stream written = (Stream) reopened.getCatalog().getLibrary().getObject(formReference);
            assertFalse(written.getEntries().containsKey(PIECE_INFO));
            assertArrayEquals(before, written.getDecodedStreamBytes());
        } finally {
            reopened.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static final class Saved {
        final byte[] pdf;
        final RedactionReport report;
        final String streams;

        Saved(byte[] pdf, RedactionReport report, String streams) {
            this.pdf = pdf;
            this.report = report;
            this.streams = streams;
        }
    }

    private static List<SearchTerm> terms(String word) {
        return Collections.singletonList(new SearchTerm(word, false, false, false));
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "application-data.pdf");
        return document;
    }

    private Saved redact(List<SearchTerm> terms, byte[] pdf) throws Exception {
        return save(open(pdf), RedactionRequest.ofTerms(terms));
    }

    private Saved save(Document document, RedactionRequest request) throws Exception {
        try {
            Redactor.configure(document, request);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeToOutputStream(out, WriteMode.FULL_UPDATE);
            return new Saved(out.toByteArray(), document.getRedactionReport(), decodedStreams(out.toByteArray()));
        } finally {
            document.dispose();
        }
    }

    /** Every stream in the written file, decoded: the private copies are compressed. */
    private static String decodedStreams(byte[] pdf) throws Exception {
        Document document = open(pdf);
        try {
            Library library = document.getCatalog().getLibrary();
            StringBuilder streams = new StringBuilder();
            for (Reference reference : references(library)) {
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

    private static Reference firstForm(Library library) {
        for (Reference reference : references(library)) {
            if (library.getObject(reference) instanceof Form) {
                return reference;
            }
        }
        return null;
    }

    private static Set<Reference> references(Library library) {
        Set<Reference> references = new HashSet<>();
        for (CrossReference crossReference : library.getCrossReferenceRoot().getCrossReferences()) {
            references.addAll(crossReference.getEntries().keySet());
        }
        return references;
    }

    private static String compressed(String entries, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) {
            deflater.write(content.getBytes(StandardCharsets.ISO_8859_1));
        }
        return "<< " + entries + " /Filter /FlateDecode /Length " + bytes.size() + " >>\nstream\n"
                + new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1) + "\nendstream";
    }

    private static byte[] fixture() throws IOException {
        return build("/PieceInfo << /Illustrator << /Private 7 0 R >> >>",
                "/PieceInfo << /Illustrator << /Private 8 0 R >> >>",
                "/PieceInfo << /Illustrator << /Private 9 0 R >> >>",
                compressed("", PRIVATE_COPIES[0]), compressed("", PRIVATE_COPIES[1]), compressed("", PRIVATE_COPIES[2]));
    }

    private static byte[] withoutPrivateData() throws IOException {
        return build("", "", "");
    }

    private static byte[] build(String catalogEntries, String pageEntries, String formEntries, String... extra)
            throws IOException {
        List<String> objects = new ArrayList<>(Arrays.asList(
                "<< /Type /Catalog /Pages 2 0 R " + catalogEntries + " >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R "
                        + "/Resources << /Font << /F1 3 0 R >> /XObject << /Fm0 6 0 R >> >> " + pageEntries + " >>",
                compressed("", ON_THE_PAGE),
                compressed("/Type /XObject /Subtype /Form /BBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + formEntries, IN_THE_FORM)));
        objects.addAll(Arrays.asList(extra));
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
