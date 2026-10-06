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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared scaffolding for the redaction and text-editing tests.
 * <p>
 * The pieces here were each copied into three or four test classes before being collected: building
 * a usable {@link RedactionAnnotation}, and reading back what a saved document says. The annotation
 * builder in particular carries real knowledge - an annotation only redacts anything once its markup
 * path <em>and</em> its bbox are set - which is exactly the sort of thing that gets updated in one
 * copy and not the others.
 */
public final class RedactionFixtures {

    private RedactionFixtures() {
    }

    /**
     * A redaction annotation covering the given page-space bounds, ready to be burned.
     *
     * @param document document the annotation belongs to
     * @param bounds   area to redact, in page space
     * @return the annotation, not yet added to a page
     */
    /**
     * A redaction in a colour of its own, for the cases where the colour is what is being tested.
     */
    public static RedactionAnnotation redactionOver(Document document, Rectangle bounds, Color colour) {
        RedactionAnnotation annotation = redactionOver(document, bounds);
        annotation.setColor(colour);
        return annotation;
    }

    public static RedactionAnnotation redactionOver(Document document, Rectangle bounds) {
        Library library = document.getPageTree().getLibrary();
        RedactionAnnotation annotation = (RedactionAnnotation) AnnotationFactory.buildAnnotation(
                library, Annotation.SUBTYPE_REDACT, bounds);
        if (annotation == null) {
            throw new IllegalStateException("could not build a redaction annotation over " + bounds);
        }
        List<Shape> markupBounds = new ArrayList<>();
        markupBounds.add(bounds);
        annotation.setColor(Color.BLACK);
        annotation.setMarkupBounds((ArrayList<Shape>) markupBounds);
        annotation.setMarkupPath(new GeneralPath(bounds));
        annotation.setBBox(bounds);
        annotation.resetAppearanceStream(new AffineTransform());
        return annotation;
    }

    /**
     * Bounds of every whole word on page 0 whose text matches one of {@code terms}.
     *
     * @param page  page to search, already initialised
     * @param terms words to look for
     * @return page-space bounds, in reading order
     */
    public static List<Rectangle> wordBounds(Page page, List<String> terms) throws InterruptedException {
        List<Rectangle> bounds = new ArrayList<>();
        for (LineText lineText : page.getViewText().getPageLines()) {
            for (WordText wordText : lineText.getWords()) {
                if (terms.contains(wordText.getText().trim())) {
                    bounds.add(wordText.getBounds().getBounds());
                }
            }
        }
        return bounds;
    }

    /**
     * Bounds of the first word on page 0 of at least {@code minimumLength} letters, for tests that
     * need some word to redact and do not care which.
     *
     * @param page          page to search, already initialised
     * @param minimumLength shortest acceptable word
     * @return the word and its page-space bounds
     */
    public static WordText firstLongWord(Page page, int minimumLength) throws InterruptedException {
        for (LineText lineText : page.getViewText().getPageLines()) {
            for (WordText wordText : lineText.getWords()) {
                String text = wordText.getText().trim();
                if (text.length() >= minimumLength && text.chars().allMatch(Character::isLetter)) {
                    return wordText;
                }
            }
        }
        throw new IllegalStateException("page has no word of " + minimumLength + " or more letters");
    }

    /**
     * Text of page 0 of a saved document.
     *
     * @param pdf saved document bytes
     * @return extracted text
     */
    public static String extractedText(byte[] pdf) throws Exception {
        Document document = open(pdf);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            return page.getViewText().toString();
        } finally {
            document.dispose();
        }
    }

    /**
     * Decompressed content streams of page 0, including those of any form XObject it draws.
     * <p>
     * The forms matter: text redacted inside one is rewritten in the form's own stream, so a check
     * that reads only the page stream is blind to everything a form draws.
     *
     * @param pdf       saved document bytes
     * @param normalise true to strip blank lines and indentation, for comparing against a golden
     * @return the streams, concatenated
     */
    public static String contentStreams(byte[] pdf, boolean normalise) throws Exception {
        Document document = open(pdf);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            StringBuilder text = new StringBuilder();
            for (Stream stream : page.getContentStreams()) {
                text.append(new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
                text.append('\n');
            }
            for (Stream stream : formStreams(page)) {
                text.append("% form ").append(stream.getPObjectReference()).append('\n');
                text.append(new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
                text.append('\n');
            }
            return normalise ? normalise(text.toString()) : text.toString();
        } finally {
            document.dispose();
        }
    }

    /**
     * @return how many times {@code needle} occurs in {@code haystack}, overlapping matches included
     */
    public static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }

    /**
     * Form XObjects reachable from a page's resources, in a stable order.
     */
    private static List<Stream> formStreams(Page page) {
        List<Stream> forms = new ArrayList<>();
        if (page.getResources() == null) {
            return forms;
        }
        Library library = page.getLibrary();
        DictionaryEntries xObjects = library.getDictionary(page.getResources().getEntries(),
                Resources.XOBJECT_KEY);
        if (xObjects == null) {
            return forms;
        }
        List<Object> names = new ArrayList<>(xObjects.keySet());
        names.sort(Comparator.comparing(Object::toString));
        for (Object name : names) {
            Object xObject = library.getObject(xObjects, (Name) name);
            if (xObject instanceof Form) {
                forms.add((Form) xObject);
            }
        }
        return forms;
    }

    private static Document open(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "test");
        return document;
    }

    private static String normalise(String streams) {
        StringBuilder normalised = new StringBuilder();
        for (String line : streams.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                normalised.append(trimmed).append('\n');
            }
        }
        return normalised.toString();
    }
}
