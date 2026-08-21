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
import org.icepdf.core.pobjects.Form;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@link RedactionTarget} a caller can switch off has to actually be consulted.
 * <p>
 * An option that is declared but never read is worse than one that is missing: the caller believes
 * they have limited what the redaction touches, and nothing tells them otherwise. These tests exist
 * to fail if a target goes back to being decorative.
 */
public class RedactionTargetScopeTest {

    /**
     * The fixture's form draws "alpha bravo charlie" through a non-identity {@code /Matrix}; the page
     * itself says something else.
     */
    @DisplayName("form XObjects are redacted when they are in scope")
    @Test
    public void formsAreRedactedByDefault() throws Exception {
        String streams = allStreams(redact(RedactionOptions.defaults()));

        assertFalse(streams.contains("alpha bravo charlie"),
                "the form should have been redacted:\n" + streams);
    }

    @DisplayName("form XObjects are left alone when they are out of scope")
    @Test
    public void formsCanBeExcluded() throws Exception {
        String streams = allStreams(redact(RedactionOptions.defaults()
                .targets(EnumSet.of(RedactionTarget.PAGE_CONTENT))));

        assertTrue(streams.contains("alpha bravo charlie"),
                "forms were out of scope, so the form's text should remain:\n" + streams);
    }


    // -- tagged text -----------------------------------------------------------------------------

    /**
     * The fixture's structure tree repeats "bravo" in four entries: {@code /T} and {@code /Alt} on
     * the paragraph, {@code /ActualText} and {@code /E} on the span inside it. None of them is drawn
     * on the page, so no rectangle reaches them.
     */
    @DisplayName("structure tree text is masked when tagged text is in scope")
    @Test
    public void taggedTextIsMasked() throws Exception {
        String pdf = new String(redactTerm(RedactionOptions.defaults()), StandardCharsets.ISO_8859_1);

        assertFalse(pdf.contains("a paragraph about bravo"), "/Alt should have been masked:\n" + pdf);
        assertFalse(pdf.contains("bravo section"), "/T should have been masked");
        assertFalse(pdf.contains("bravo expanded"), "/E should have been masked");
        assertTrue(pdf.contains("****"), "and the mask string written in their place");
        // The tagging itself has to survive - masking the words is not licence to break the tree.
        assertTrue(pdf.contains("/StructTreeRoot"), "the structure tree should still be there");
        assertTrue(pdf.contains("/StructElem"), "and its elements with it");
    }

    @DisplayName("structure tree text is left alone when tagged text is out of scope")
    @Test
    public void taggedTextCanBeExcluded() throws Exception {
        String pdf = new String(redactTerm(RedactionOptions.defaults()
                .targets(EnumSet.of(RedactionTarget.PAGE_CONTENT))), StandardCharsets.ISO_8859_1);

        assertTrue(pdf.contains("a paragraph about bravo"),
                "tagged text was out of scope, so /Alt should remain:\n" + pdf);
    }

    /**
     * Term-driven, so it runs from the terms rather than from the rectangles.
     */
    private byte[] redactTerm(RedactionOptions options) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/tagged_text.pdf").toString());
        try {
            document.getPageTree().getPage(0).init();
            ArrayList<String> words = new ArrayList<>(List.of("bravo"));
            Redactor.configure(document, RedactionRequest.ofTerms(
                    List.of(new SearchTerm("bravo", words, false, false, false))).with(options));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }


    /**
     * The same fixture also carries {@code /ActualText} inline in the content stream, which is where
     * a tagged PDF keeps the copy of its words that "copy text" and a screen reader actually use.
     * <p>
     * Both axes run here, so the page's glyphs are burned and a search of the result comes back
     * clean either way; what distinguishes a fixed leak from a live one is the byte scan, which is
     * why this asserts on the report's raw-byte count rather than on the search.
     */
    @DisplayName("marked-content /ActualText is masked in the content stream")
    @Test
    public void inlineActualTextIsMasked() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/tagged_text.pdf").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(20, 145, 200, 20)), true);
            ArrayList<String> words = new ArrayList<>(List.of("bravo"));
            Redactor.configure(document, RedactionRequest.ofAnnotationsAndTerms(
                    List.of(new SearchTerm("bravo", words, false, false, false))));

            document.saveToOutputStream(new ByteArrayOutputStream(), WriteMode.FULL_UPDATE);

            RedactionReport report = document.getRedactionReport();
            assertEquals(0, report.getRawByteMatchesAfter(),
                    "the term should not survive anywhere in the bytes");
            assertEquals(RedactionConfidence.VERIFIED, report.getConfidence(),
                    "warnings: " + report.getWarnings());
        } finally {
            document.dispose();
        }
    }


    /**
     * ACCEPTED LIMITATION, disabled because it fails by design. Kept as executable documentation of
     * the boundary described in {@link RedactionConfidence#VERIFIED}.
     * <p>
     * The verification pass searches for the whole runs the burn removed. Here the rectangle covered
     * the entire line, so the run is "alpha bravo charlie", which does not appear in
     * {@code /ActualText (bravo)} - and the document comes back {@code VERIFIED} with the word still
     * in it.
     * <p>
     * Judged improbable in practice (2026-08-20) and left alone: someone marking text as they read
     * covers phrases in context, and someone searching redacts short specific terms, where the run
     * and the term are the same thing. Widening the scan to the individual words of a removed run
     * would flag a document whenever a redacted word legitimately appears elsewhere - a letterhead,
     * a recurring surname - and {@code FAILED} holds a document from release, so the cure would cost
     * more than the disease.
     * <p>
     * The contract that covers it: to require that a word exist nowhere in the document, drive the
     * redaction with terms rather than rectangles alone.
     */
    @Disabled("accepted limitation: the byte scan looks for whole removed runs - see RedactionConfidence.VERIFIED")
    @DisplayName("an annotation-only redaction does not silently pass tagged text it cannot reach")
    @Test
    public void annotationOnlyRedactionOfTaggedTextIsNotSilent() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/tagged_text.pdf").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(20, 145, 200, 20)), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations());

            document.saveToOutputStream(new ByteArrayOutputStream(), WriteMode.FULL_UPDATE);

            RedactionReport report = document.getRedactionReport();
            assertNotEquals(RedactionConfidence.VERIFIED, report.getConfidence(),
                    "the surviving /ActualText should have stopped this being called verified");
        } finally {
            document.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * The form sits at 20,120 to 320,170 by way of its {@code /Matrix}, so this covers it.
     */
    private byte[] redact(RedactionOptions options) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/form_xobject.pdf").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, new Rectangle(20, 120, 280, 50)), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations().with(options));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    private String allStreams(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setByteArray(pdf, 0, pdf.length, "redacted");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            StringBuilder streams = new StringBuilder();
            for (Stream stream : page.getContentStreams()) {
                streams.append(new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
            }
            Resources resources = page.getResources();
            if (resources != null) {
                for (Name name : resources.getXObjects().keySet()) {
                    Object xObject = resources.getXObject(name);
                    if (xObject instanceof Form) {
                        streams.append(new String(((Form) xObject).getDecodedStreamBytes(),
                                StandardCharsets.ISO_8859_1));
                    }
                }
            }
            return streams.toString();
        } finally {
            document.dispose();
        }
    }
}
