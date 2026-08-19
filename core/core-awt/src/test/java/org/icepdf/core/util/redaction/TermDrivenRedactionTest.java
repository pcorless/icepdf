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

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redacting the copies of a word that no rectangle can cover.
 * <p>
 * The fixture puts "bravo" in five places: the page content, a bookmark title, a comment's contents,
 * and the document title and keywords. Burning the page removes exactly one of them, which is the
 * whole reason this axis exists.
 */
public class TermDrivenRedactionTest {

    private static final String FIXTURE =
            Paths.get("src/test/resources/redaction/positionless_text.pdf").toString();

    @DisplayName("burning the page alone leaves the word everywhere else")
    @Test
    public void burningThePageLeavesTheRest() throws Exception {
        byte[] saved = redact(RedactionRequest.ofAnnotations());

        assertFalse(pageText(saved).contains("bravo"), "the page itself should be redacted");
        // Everything the annotation could not cover is still there. Pinned rather than lamented:
        // this is what a geometry-only redaction means, and it is why the term axis exists.
        assertEquals("bravo section", outlineTitle(saved), "bookmark still names it");
        assertEquals("a note about bravo", annotationContents(saved), "comment still says it");
        assertEquals("bravo report", info(saved).getTitle(), "document title still says it");
    }

    @DisplayName("terms remove the word from bookmark, comment and metadata too")
    @Test
    public void termsReachPositionlessText() throws Exception {
        byte[] saved = redact(RedactionRequest.ofAnnotationsAndTerms(terms("bravo")));

        assertFalse(pageText(saved).contains("bravo"), "page content");
        assertEquals("**** section", outlineTitle(saved), "bookmark title");
        assertEquals("a note about ****", annotationContents(saved), "comment contents");

        PInfo info = info(saved);
        assertEquals("**** report", info.getTitle(), "document title");
        assertEquals("alpha, ****", info.getKeywords(), "keywords");
        assertEquals("nobody", info.getAuthor(), "an entry without the term is left alone");
    }

    @DisplayName("the report counts what was rewritten, by target")
    @Test
    public void reportCountsRewrites() throws Exception {
        Document document = open();
        try {
            redactWord(document);
            Redactor.configure(document, RedactionRequest.ofAnnotationsAndTerms(terms("bravo")));
            save(document);

            RedactionReport report = document.getRedactionReport();
            assertEquals(4, report.getStringsRewritten(),
                    "bookmark, comment, title and keywords: " + report.getCountsByTarget());
            assertEquals(1, (int) report.getCountsByTarget().get(RedactionTarget.OUTLINE));
            assertEquals(1, (int) report.getCountsByTarget().get(RedactionTarget.ANNOTATION_CONTENTS));
            assertEquals(2, (int) report.getCountsByTarget().get(RedactionTarget.METADATA));
            assertEquals(5, report.getGlyphsRemoved(), "the page glyphs are counted separately");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("the mask is a fixed string, not one character per character removed")
    @Test
    public void maskDoesNotLeakLength() throws Exception {
        byte[] saved = redact(RedactionRequest.ofTerms(terms("bravo"))
                .with(RedactionOptions.defaults()));
        // "bravo" is five characters and the mask is four: a mask sized to what it replaced would
        // tell a reader how long every redacted term was.
        assertEquals("**** section", outlineTitle(saved));
        assertNotEquals("*****", "****", "the mask is not length-matched");
    }

    @DisplayName("the mask is configurable")
    @Test
    public void maskIsConfigurable() throws Exception {
        byte[] saved = redact(RedactionRequest.ofTerms(terms("bravo"))
                .with(RedactionOptions.defaults().maskString("[redacted]")));
        assertEquals("[redacted] section", outlineTitle(saved));
    }

    @DisplayName("a target can be taken out of scope")
    @Test
    public void targetsNarrowWhatIsRewritten() throws Exception {
        byte[] saved = redact(RedactionRequest.ofTerms(terms("bravo"))
                .with(RedactionOptions.defaults().targets(EnumSet.of(RedactionTarget.OUTLINE))));

        assertEquals("**** section", outlineTitle(saved), "outline was in scope");
        assertEquals("a note about bravo", annotationContents(saved), "comment was not");
        assertEquals("bravo report", info(saved).getTitle(), "metadata was not");
    }

    @DisplayName("matching honours the search term's own rules")
    @Test
    public void matchingHonoursSearchFlags() throws Exception {
        // Case-sensitive "Bravo" must not match the lower case text in the fixture.
        byte[] saved = redact(RedactionRequest.ofTerms(
                Collections.singletonList(new SearchTerm("Bravo", terms("Bravo").get(0).getTerms(),
                        true, false, false))));
        assertEquals("bravo section", outlineTitle(saved),
                "a case-sensitive term should not match different case");
    }

    @DisplayName("a form field's value and default value are both masked")
    @Test
    public void formValuesAreMasked() throws Exception {
        byte[] saved = redactIn("form_and_destinations.pdf", RedactionRequest.ofTerms(terms("bravo")));

        Document document = reopen(saved);
        try {
            FieldDictionary field = firstField(document);
            assertEquals("contains ****", stringValue(document, field, FieldDictionary.V_KEY),
                    "the value the field holds");
            // /DV is a separate copy: leaving it would put the word back on a form reset.
            assertEquals("contains ****", stringValue(document, field, FieldDictionary.DV_KEY),
                    "the default value the field resets to");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("a named destination is renamed, and everything pointing at it follows")
    @Test
    public void destinationsAreRenamedWithTheirReferences() throws Exception {
        byte[] saved = redactIn("form_and_destinations.pdf", RedactionRequest.ofTerms(terms("bravo")));

        Document document = reopen(saved);
        try {
            NameTree dests = document.getCatalog().getNames().getDestsNameTree();
            assertNotNull(dests.searchName("**** dest"), "the destination should be renamed");
            assertNull(dests.searchName("bravo dest"), "and the old name should be gone");

            // The name is quoted by whatever jumps to it, so a rename that stopped at the name tree
            // would both leave the word readable in these and break the links.
            OutlineItem bookmark = document.getCatalog().getOutlines().getRootOutlineItem().getSubItem(0);
            assertEquals("**** dest", bookmark.getDest().getNamedDestination(),
                    "the bookmark should point at the new name");
            assertEquals("**** dest", linkDestination(document),
                    "the link annotation should point at the new name");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("names that mask to the same string stay distinct")
    @Test
    public void collidingDestinationNamesAreKeptApart() {
        // "bravo one" and "bravo two" both mask to "**** one"/"**** two" and are fine, but
        // "bravo" and "bravo!" would both become "****". The rename planner appends a counter
        // rather than letting the second overwrite the first and lose a destination.
        TermMasker masker = new TermMasker(terms("bravo"), "****");
        assertEquals("****", masker.mask("bravo"));
        assertEquals("****!", masker.mask("bravo!"));
    }

    // -- helpers ---------------------------------------------------------------------------------

    private List<SearchTerm> terms(String word) {
        ArrayList<String> words = new ArrayList<>();
        words.add(word);
        return Collections.singletonList(new SearchTerm(word, words, false, false, false));
    }

    private Document open() throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        return document;
    }

    /**
     * Redacts the fixture with the given request, always dropping an annotation over "bravo" on the
     * page so the geometric and term axes are exercised together.
     */
    /**
     * Redacts a named fixture with the given request, without adding any annotation: these targets
     * are reached by term, not by geometry.
     */
    private byte[] redactIn(String fixture, RedactionRequest request) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + fixture).toString());
        try {
            Redactor.configure(document, request);
            return save(document);
        } finally {
            document.dispose();
        }
    }

    private FieldDictionary firstField(Document document) {
        Object field = document.getCatalog().getInteractiveForm().getFields().get(0);
        if (field instanceof AbstractWidgetAnnotation) {
            return ((AbstractWidgetAnnotation<?>) field).getFieldDictionary();
        }
        return (FieldDictionary) field;
    }

    private String stringValue(Document document, FieldDictionary field, Name key) {
        Object value = document.getCatalog().getLibrary().getObject(field.getEntries(), key);
        return value instanceof StringObject
                ? Utils.convertStringObject(document.getCatalog().getLibrary(), (StringObject) value)
                : null;
    }

    private String linkDestination(Document document) throws Exception {
        Library library = document.getCatalog().getLibrary();
        Page page = document.getPageTree().getPage(0);
        for (Reference reference : page.getAnnotationReferences()) {
            Object annotation = library.getObject(reference);
            if (annotation instanceof Annotation) {
                org.icepdf.core.pobjects.actions.Action action = ((Annotation) annotation).getAction();
                if (action instanceof GoToAction) {
                    return ((GoToAction) action).getDestination().getNamedDestination();
                }
            }
        }
        return null;
    }

    private byte[] redact(RedactionRequest request) throws Exception {
        Document document = open();
        try {
            redactWord(document);
            Redactor.configure(document, request);
            return save(document);
        } finally {
            document.dispose();
        }
    }

    private void redactWord(Document document) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        List<Rectangle> bounds = RedactionFixtures.wordBounds(page, Collections.singletonList("bravo"));
        assertEquals(1, bounds.size(), "fixture should contain 'bravo' on the page");
        page.addAnnotation(RedactionFixtures.redactionOver(document, bounds.get(0)), true);
    }

    private byte[] save(Document document) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
        return out.toByteArray();
    }

    private String pageText(byte[] pdf) throws Exception {
        return RedactionFixtures.extractedText(pdf);
    }

    private String outlineTitle(byte[] pdf) throws Exception {
        Document document = reopen(pdf);
        try {
            return document.getCatalog().getOutlines().getRootOutlineItem().getSubItem(0).getTitle();
        } finally {
            document.dispose();
        }
    }

    private String annotationContents(byte[] pdf) throws Exception {
        Document document = reopen(pdf);
        try {
            Library library = document.getCatalog().getLibrary();
            Page page = document.getPageTree().getPage(0);
            for (Reference reference : page.getAnnotationReferences()) {
                Object annotation = library.getObject(reference);
                if (annotation instanceof MarkupAnnotation) {
                    String contents = ((MarkupAnnotation) annotation).getContents();
                    if (contents != null && !contents.isEmpty()) {
                        return contents;
                    }
                }
            }
            return null;
        } finally {
            document.dispose();
        }
    }

    private PInfo info(byte[] pdf) throws Exception {
        Document document = reopen(pdf);
        try {
            return document.getInfo();
        } finally {
            document.dispose();
        }
    }

    private Document reopen(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "redacted");
        return document;
    }
}
