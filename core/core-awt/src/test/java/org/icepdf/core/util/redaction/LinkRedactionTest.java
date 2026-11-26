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
import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.actions.Action;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.structure.CrossReference;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GH-691: the address a URI action opens. A link to a person's page or a {@code mailto:} address
 * repeats the very thing being redacted, and the action can sit in many places.
 */
public class LinkRedactionTest {

    private static final String TERM = "bravo";
    private static final String URI = "mailto:" + TERM + "@example.com";
    private static final String ACTION = "<< /S /URI /URI (" + URI + ") >>";
    private static final Name ADDITIONAL_ACTIONS = new Name("AA");
    private static final Name PAGE_OPEN = new Name("O");

    @DisplayName("a link's action")
    @Test
    public void linkAction() throws Exception {
        assertMasked(redact(fixture("/A " + ACTION, "")), 1);
    }

    @DisplayName("an action in an object of its own")
    @Test
    public void indirectAction() throws Exception {
        assertMasked(redact(fixture("/A 8 0 R", "", ACTION)), 1);
    }

    @DisplayName("a link's /PA, the address it was captured from")
    @Test
    public void previousAction() throws Exception {
        assertMasked(redact(fixture("/A << /S /GoTo /D [4 0 R /Fit] >> /PA " + ACTION, "")), 1);
    }

    @DisplayName("an action later in a /Next chain")
    @Test
    public void nextChain() throws Exception {
        assertMasked(redact(fixture("/A << /S /GoTo /D [4 0 R /Fit] /Next [" + ACTION + "] >>", "")), 1);
    }

    @DisplayName("an annotation's additional actions")
    @Test
    public void additionalActions() throws Exception {
        assertMasked(redact(fixture("/AA << /E " + ACTION + " >>", "")), 1);
    }

    @DisplayName("the document's open action and a bookmark")
    @Test
    public void openActionAndBookmark() throws Exception {
        assertMasked(redact(fixture("", "/OpenAction " + ACTION + " /Outlines 8 0 R",
                "<< /Type /Outlines /First 9 0 R /Last 9 0 R /Count 1 >>",
                "<< /Title (Contact) /Parent 8 0 R /A " + ACTION + " >>")), 2);
    }

    @DisplayName("a hex string address")
    @Test
    public void hexString() throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : URI.getBytes(StandardCharsets.ISO_8859_1)) {
            hex.append(String.format("%02X", b));
        }
        assertMasked(redact(fixture("/A << /S /URI /URI <" + hex + "> >>", "")), 1);
    }

    @DisplayName("where a form is submitted, as a URL string")
    @Test
    public void submitFormUrl() throws Exception {
        assertMasked(redact(fixture("/A << /S /SubmitForm /F (https://example.com/" + TERM + "/submit) >>", "")), 1);
    }

    @DisplayName("where a form is submitted, as a URL file specification")
    @Test
    public void submitFormFileSpecification() throws Exception {
        assertMasked(redact(fixture(
                "/A << /S /SubmitForm /F << /FS /URL /F (https://example.com/" + TERM + ") >> >>", "")), 1);
    }

    @DisplayName("the file a launch opens, and a Windows launch's parameters")
    @Test
    public void launch() throws Exception {
        assertMasked(redact(fixture("/A << /S /Launch /F (" + TERM + ".txt) /Win << /F (notepad.exe) "
                + "/D (C:\\\\Users\\\\" + TERM + ") /P (" + TERM + ".txt) >> >>", "")), 3);
    }

    @DisplayName("a remote go-to's file, every name the file has, and its named destination")
    @Test
    public void remoteGoTo() throws Exception {
        assertMasked(redact(fixture("/A << /S /GoToR /F << /Type /Filespec /F (" + TERM + ".pdf) /UF ("
                + TERM + ".pdf) >> /D (" + TERM + "-section) >>", "")), 3);
    }

    @DisplayName("a file specification in an object of its own")
    @Test
    public void indirectFileSpecification() throws Exception {
        assertMasked(redact(fixture("/A << /S /GoToR /F 8 0 R /D [0 /Fit] >>", "",
                "<< /Type /Filespec /F (reports/" + TERM + ".pdf) >>")), 1);
    }

    @DisplayName("an embedded go-to, import-data and thread action")
    @Test
    public void otherFileActions() throws Exception {
        assertMasked(redact(fixture("/A << /S /GoToE /F (" + TERM + "-1.pdf) /D [0 /Fit] /T << /R /C /N (a) >> "
                + "/Next [<< /S /ImportData /F (" + TERM + ".fdf) >> << /S /Thread /F (" + TERM
                + "-2.pdf) /D 0 >>] >>", "")), 3);
    }

    @DisplayName("a file specification outside an action is left to its own target")
    @Test
    public void fileSpecificationOutsideAction() throws Exception {
        Saved saved = redact(fixture("", "", "<< /Type /Filespec /F (" + TERM + ".pdf) >>"));

        assertNull(saved.report.getCountsByTarget().get(RedactionTarget.LINKS));
    }

    @DisplayName("the rest of the address stays, and the link still has its action")
    @Test
    public void restStays() throws Exception {
        Saved saved = redact(fixture("/A " + ACTION, ""));

        assertTrue(saved.allText.contains("mailto:****@example.com"), saved.allText);
        Document document = open(saved.pdf);
        try {
            Annotation link = document.getPageTree().getPage(0).getAnnotations().get(0);
            assertNotNull(link.getAction());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("other actions are left alone")
    @Test
    public void otherActionsUntouched() throws Exception {
        Saved saved = redact(fixture("/A << /S /GoTo /D [4 0 R /Fit] >>", ""));

        assertNull(saved.report.getCountsByTarget().get(RedactionTarget.LINKS));
        Document document = open(saved.pdf);
        try {
            Annotation link = document.getPageTree().getPage(0).getAnnotations().get(0);
            assertTrue(link.getAction() instanceof GoToAction);
        } finally {
            document.dispose();
        }
    }

    @DisplayName("an encrypted document, where the masked address is written encrypted")
    @ParameterizedTest
    @ValueSource(strings = {"encrypted_objstm_aes128.pdf", "encrypted_objstm_aes256.pdf", "encrypted_objstm_rc4.pdf"})
    public void encrypted(String fixture) throws Exception {
        Document document = new Document();
        try (InputStream stream = getClass().getResourceAsStream("/updater/" + fixture)) {
            document.setInputStream(stream, fixture);
        }
        Page page = document.getPageTree().getPage(0);
        Reference pageReference = page.getPObjectReference();
        DictionaryEntries action = new DictionaryEntries();
        action.put(Action.ACTION_TYPE_KEY, Action.ACTION_TYPE_URI);
        action.put(URIAction.URI_KEY, new LiteralStringObject(URI, pageReference));
        DictionaryEntries additionalActions = new DictionaryEntries();
        additionalActions.put(PAGE_OPEN, action);
        page.getEntries().put(ADDITIONAL_ACTIONS, additionalActions);
        document.getCatalog().getLibrary().getStateManager().addChange(new PObject(page, pageReference));

        Saved saved = save(document, RedactionRequest.ofTerms(terms()));

        assertEquals(1, saved.report.getCountsByTarget().get(RedactionTarget.LINKS));
        Document reopened = open(saved.pdf);
        try {
            Library library = reopened.getCatalog().getLibrary();
            Page written = reopened.getPageTree().getPage(0);
            DictionaryEntries writtenActions = library.getDictionary(written.getEntries(), ADDITIONAL_ACTIONS);
            DictionaryEntries writtenAction = library.getDictionary(writtenActions, PAGE_OPEN);
            StringObject uri = (StringObject) writtenAction.get(URIAction.URI_KEY);
            assertEquals("mailto:****@example.com", TermMasker.decodeString(library, uri));
        } finally {
            reopened.dispose();
        }
    }

    @DisplayName("left out of scope, the address stays and verification fails")
    @Test
    public void outOfScope() throws Exception {
        Saved saved = save(open(fixture("/A " + ACTION, "")), RedactionRequest.ofTerms(terms())
                .with(RedactionOptions.defaults().targets(EnumSet.complementOf(EnumSet.of(RedactionTarget.LINKS)))));

        assertTrue(saved.allText.contains(URI));
        assertEquals(RedactionConfidence.FAILED, saved.report.getConfidence());
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static void assertMasked(Saved saved, int count) {
        assertFalse(saved.allText.contains(TERM), "the address is still in the file");
        assertEquals(count, saved.report.getCountsByTarget().get(RedactionTarget.LINKS));
        assertEquals(RedactionConfidence.VERIFIED, saved.report.getConfidence(), saved.report.toString());
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

    private static List<SearchTerm> terms() {
        return Collections.singletonList(new SearchTerm(TERM, false, false, false));
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "links.pdf");
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
     * A page with one link, object 6. Extra objects start at 8; object 7 is unused.
     *
     * @param linkEntries    entries added to the link
     * @param catalogEntries entries added to the catalog
     */
    private static byte[] fixture(String linkEntries, String catalogEntries, String... extra) {
        String pageContent = "BT /F1 12 Tf 72 720 Td (unrelated words) Tj ET";
        List<String> objects = new ArrayList<>(List.of(
                "<< /Type /Catalog /Pages 2 0 R " + catalogEntries + " >>",
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> "
                        + "/Contents 5 0 R /Annots [6 0 R] >>",
                "<< /Length " + pageContent.length() + " >>\nstream\n" + pageContent + "\nendstream",
                "<< /Type /Annot /Subtype /Link /Rect [72 400 300 420] /Border [0 0 0] " + linkEntries + " >>",
                "<< >>"));
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
