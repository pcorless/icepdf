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
import org.icepdf.core.pobjects.Resources;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden tests over the content stream a redaction produces.
 * <p>
 * Each fixture is a small generated PDF exercising one text-layout feature
 * (see {@code src/test/resources/redaction/make_redaction_fixtures.py}). One word is redacted and
 * the resulting decompressed content stream is compared to a checked-in golden, so any change to
 * the writer shows up as a reviewable diff rather than a pass/fail.
 * <p>
 * Re-bless with {@code -Dredaction.bless=true} after <em>reading</em> the diff. Goldens were first
 * captured against the pre-fix writer, so some encode known defects; those are called out in the
 * golden's own header line and in REDACTION-REVIEW-PLAN.md.
 * <p>
 * Alongside the golden, every case asserts the two things that must hold whatever the syntax:
 * the redacted word is gone from the extracted text, and every surviving glyph is still within
 * {@link #ORIGIN_TOLERANCE} of where it started. The second is the objective form of "the fix did
 * not break the layout".
 */
public class RedactionGoldenTest {

    private static final Path FIXTURES = Paths.get("src/test/resources/redaction");
    private static final Path GOLDENS = Paths.get("src/test/resources/redaction/golden");

    /**
     * How far a surviving glyph may move, in user space. Redaction rewrites the positioning of the
     * text around what it removed, so exact equality is not the bar; visible drift is.
     */
    private static final double ORIGIN_TOLERANCE = 0.01;

    private static final boolean BLESS = Boolean.getBoolean("redaction.bless");

    @DisplayName("redacted content stream matches its golden")
    @ParameterizedTest(name = "{0} redacting \"{1}\"")
    @CsvSource({
            "simple_tj.pdf, bravo",
            "tj_array.pdf, bravo",
            "rotated_page.pdf, bravo",
            "form_xobject.pdf, bravo",
            // two annotations inside a form: the transform is non-identity, so this is the case
            // that catches normalizeToUserSpace being applied once per annotation instead of once
            // per glyph.
            "form_xobject.pdf, alpha|charlie",
    })
    public void redactionMatchesGolden(String fixture, String term) throws Exception {
        assertRedactionMatchesGolden(fixture, term);
    }

    /**
     * Fixtures the current writer gets wrong. Each was run and its failure recorded, so this is a
     * checklist rather than a suspicion - see REDACTION-REVIEW-PLAN.md for the findings:
     * <ul>
     * <li>{@code quote_operators} - 'charlie' survives. The ' and " operators are not in
     *     isTextLayoutToken, so the original string is copied out verbatim ahead of the
     *     replacement.</li>
     * <li>{@code text_state} - redacting 'bravo' moves 'charlie' from x=59.75 to x=103.25. With
     *     Tz 50 the offset is computed from glyph advances that carry the horizontal scale, but Td
     *     operands are in unscaled text space.</li>
     * <li>{@code form_drawn_twice} - one form, two placements. Form.init() short-circuits on its
     *     inited flag, so only the first placement is parsed with a redaction callback, and the
     *     form is therefore only ever tested against the first placement's transform. Policy is
     *     that a redaction on a shared form applies to every placement (no copy-on-burn for forms),
     *     so the fix is to flag against all placement transforms. Note extraction is not at fault:
     *     both placements report correct distinct bounds, and multiple redactions on a plain page
     *     work.</li>
     * <li>{@code multi_stream} - the original string stays in the file. Its operand is in one
     *     content stream and its Tj in the next, so the first stream is copied out verbatim and the
     *     replacement appended after it. The orphaned string has no operator so it is never shown,
     *     which is why only the byte-level assertion catches it.</li>
     * </ul>
     */
    @DisplayName("known-failing fixtures (GH-525)")
    @Disabled("GH-525: quote operators leak, Tz breaks layout, a show operator split across " +
            "content streams leaks its string, a form drawn twice is only redacted once")
    @ParameterizedTest(name = "{0} redacting \"{1}\"")
    @CsvSource({
            "quote_operators.pdf, charlie",
            "text_state.pdf, bravo",
            "multi_stream.pdf, charlie",
            "form_drawn_twice.pdf, repeated",
    })
    public void knownFailingFixtures(String fixture, String term) throws Exception {
        assertRedactionMatchesGolden(fixture, term);
    }

    private void assertRedactionMatchesGolden(String fixture, String term) throws Exception {
        List<Placement> originsBefore = glyphOrigins(Files.readAllBytes(FIXTURES.resolve(fixture)));

        byte[] redacted = redact(fixture, term);
        String actual = contentStreams(redacted);

        for (String single : term.split("\\|")) {
            assertFalse(extractedText(redacted).contains(single),
                    "'" + single + "' is still extractable from " + fixture);
        }
        // Extraction is not enough on its own: a string left in the stream without its operator is
        // never shown, so it cannot be extracted, but its bytes are still in the file for anyone
        // who opens it in an editor. Redaction has to remove the bytes, not just the rendering.
        for (String single : term.split("\\|")) {
            assertFalse(actual.contains(single),
                    "'" + single + "' is gone from the rendered text of " + fixture + " but its " +
                            "bytes remain in the content stream:\n" + actual);
        }
        assertOnlyTheRedactedTermMoved(originsBefore, glyphOrigins(redacted), term, fixture);

        Path golden = GOLDENS.resolve(fixture.replace(".pdf", "") +
                (term.contains("|") ? "_" + term.replace("|", "_") : "") + ".txt");
        if (BLESS || !Files.exists(golden)) {
            Files.createDirectories(GOLDENS);
            Files.write(golden, actual.getBytes(StandardCharsets.ISO_8859_1));
            if (!BLESS) {
                fail("no golden for " + fixture + "; one has been written to " + golden +
                        " - review it and re-run");
            }
            return;
        }
        assertEquals(new String(Files.readAllBytes(golden), StandardCharsets.ISO_8859_1), actual,
                "content stream for " + fixture + " changed; review the diff, then re-bless with " +
                        "-Dredaction.bless=true if it is correct");
    }

    // -- helpers ---------------------------------------------------------------------------------

    /**
     * Redacts every occurrence of {@code term} on page 0 and returns the saved document.
     */
    private byte[] redact(String fixture, String term) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURES.resolve(fixture).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();

            List<String> terms = Arrays.asList(term.split("\\|"));
            List<Rectangle> targets = new ArrayList<>();
            for (LineText lineText : page.getViewText().getPageLines()) {
                for (WordText wordText : lineText.getWords()) {
                    if (terms.contains(wordText.getText().trim())) {
                        targets.add(wordText.getBounds().getBounds());
                    }
                }
            }
            assertEquals(terms.size(), targets.size(),
                    "fixture " + fixture + " should contain each of " + terms + " exactly once");

            for (Rectangle bounds : targets) {
                RedactionAnnotation annotation = (RedactionAnnotation) AnnotationFactory.buildAnnotation(
                        document.getPageTree().getLibrary(), Annotation.SUBTYPE_REDACT, bounds);
                ArrayList<Shape> markupBounds = new ArrayList<>();
                markupBounds.add(bounds);
                annotation.setColor(Color.BLACK);
                annotation.setMarkupBounds(markupBounds);
                annotation.setMarkupPath(new GeneralPath(bounds));
                annotation.setBBox(bounds);
                annotation.resetAppearanceStream(new AffineTransform());
                page.addAnnotation(annotation, true);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    /**
     * Decompressed content streams of page 0, normalised so a golden is diffable: one operator per
     * line, and the trailing whitespace a writer may or may not emit removed.
     */
    private String contentStreams(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "golden");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            StringBuilder text = new StringBuilder();
            for (Stream stream : page.getContentStreams()) {
                text.append(new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
                text.append('\n');
            }
            // Form XObjects carry their own content streams, and text redacted inside one is
            // rewritten there rather than in the page stream. Without these the golden would show
            // only "/Fm0 Do" and the byte-level leak check would be blind to everything a form
            // draws.
            for (Stream stream : formStreams(page)) {
                text.append("% form ").append(stream.getPObjectReference()).append('\n');
                text.append(new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
                text.append('\n');
            }
            StringBuilder normalised = new StringBuilder();
            for (String line : text.toString().split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    normalised.append(trimmed).append('\n');
                }
            }
            return normalised.toString();
        } finally {
            document.dispose();
        }
    }

    /**
     * Form XObjects reachable from a page's resources, in a stable order.
     */
    private List<Stream> formStreams(Page page) {
        List<Stream> forms = new ArrayList<>();
        if (page.getResources() == null) {
            return forms;
        }
        DictionaryEntries xObjects = page.getLibrary()
                .getDictionary(page.getResources().getEntries(), Resources.XOBJECT_KEY);
        if (xObjects == null) {
            return forms;
        }
        List<Object> names = new ArrayList<>(xObjects.keySet());
        names.sort(Comparator.comparing(Object::toString));
        for (Object name : names) {
            Object xObject = page.getLibrary().getObject(xObjects, (Name) name);
            if (xObject instanceof Form) {
                forms.add((Form) xObject);
            }
        }
        return forms;
    }

    private String extractedText(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "extract");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            return page.getViewText().toString();
        } finally {
            document.dispose();
        }
    }

    /**
     * Every glyph on page 0, as character plus position.
     * <p>
     * Deliberately a flat list and not a map: removing a word regroups the surrounding text into
     * different words, so any identity built from word membership or occurrence index is unstable
     * across a redaction and would report drift that never happened.
     */
    private List<Placement> glyphOrigins(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "origins");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            List<Placement> placements = new ArrayList<>();
            for (LineText lineText : page.getViewText().getPageLines()) {
                for (WordText wordText : lineText.getWords()) {
                    for (GlyphText glyphText : wordText.getGlyphs()) {
                        Rectangle2D bounds = glyphText.getBounds().getBounds2D();
                        placements.add(new Placement(glyphText.getUnicode(),
                                bounds.getX(), bounds.getY()));
                    }
                }
            }
            return placements;
        } finally {
            document.dispose();
        }
    }

    /**
     * Asserts that redaction moved nothing: every glyph still on the page occupies a position that
     * a glyph of the same character occupied before, within {@link #ORIGIN_TOLERANCE}.
     * <p>
     * Matches are consumed, so two glyphs cannot both claim one original position. Glyphs present
     * before and absent after are the redacted ones and are simply left unmatched.
     */
    private void assertOnlyTheRedactedTermMoved(List<Placement> before, List<Placement> after,
                                                String term, String fixture) {
        List<Placement> unclaimed = new ArrayList<>(before);
        List<Placement> moved = new ArrayList<>();
        for (Placement placement : after) {
            boolean matched = false;
            for (int i = 0; i < unclaimed.size(); i++) {
                if (unclaimed.get(i).isSamePlaceAs(placement, ORIGIN_TOLERANCE)) {
                    unclaimed.remove(i);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                moved.add(placement);
            }
        }
        assertTrue(moved.isEmpty(),
                fixture + ": redacting '" + term + "' moved surviving glyphs " + moved +
                        " - no glyph of the same character started at those positions");
        assertFalse(after.isEmpty(),
                fixture + ": nothing survived, so the layout check proved nothing");
    }

    /**
     * One glyph: which character, and where it sits in user space.
     */
    private static final class Placement {
        private final String unicode;
        private final double x;
        private final double y;

        private Placement(String unicode, double x, double y) {
            this.unicode = unicode;
            this.x = x;
            this.y = y;
        }

        private boolean isSamePlaceAs(Placement other, double tolerance) {
            return unicode.equals(other.unicode)
                    && Math.abs(x - other.x) <= tolerance
                    && Math.abs(y - other.y) <= tolerance;
        }

        @Override
        public String toString() {
            return String.format("'%s'@(%.2f,%.2f)", unicode, x, y);
        }
    }
}
