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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Collections;
import java.util.List;

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
            "form_drawn_twice.pdf, repeated",
            "quote_operators.pdf, charlie",
            "text_state.pdf, bravo",
    })
    public void redactionMatchesGolden(String fixture, String term) throws Exception {
        assertRedactionMatchesGolden(fixture, term);
    }

    /**
     * Fixtures the current writer gets wrong. Each was run and its failure recorded, so this is a
     * checklist rather than a suspicion - see REDACTION-REVIEW-PLAN.md for the findings:
     * <ul>
     * <li>{@code multi_stream} - the original string stays in the file. Its operand is in one
     *     content stream and its Tj in the next, so the first stream is copied out verbatim and the
     *     replacement appended after it. The orphaned string has no operator so it is never shown,
     *     which is why only the byte-level assertion catches it.</li>
     * </ul>
     */
    @DisplayName("known-failing fixtures (GH-525)")
    @Disabled("GH-525: a show operator split across two content streams leaks its string")
    @ParameterizedTest(name = "{0} redacting \"{1}\"")
    @CsvSource({
            "multi_stream.pdf, charlie",
    })
    public void knownFailingFixtures(String fixture, String term) throws Exception {
        assertRedactionMatchesGolden(fixture, term);
    }

    /**
     * A redaction rectangle drawn snugly over a word - the realistic case when a user drags a box,
     * or when a search hit's bounds are tightened - does not fully contain the glyph bounds, which
     * carry ascender, descender and side-bearing slack. Under a containment test the glyphs stayed
     * in the content stream with the annotation merely painted over them, which is the classic
     * redaction that isn't.
     */
    @DisplayName("a redaction box that only grazes the glyphs still removes them")
    @Test
    public void snugRedactionBoxStillRemovesGlyphs() throws Exception {
        Document document = new Document();
        document.setFile(FIXTURES.resolve("simple_tj.pdf").toString());
        byte[] redacted;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();

            Rectangle2D wordBounds = null;
            for (LineText lineText : page.getViewText().getPageLines()) {
                for (WordText wordText : lineText.getWords()) {
                    if (wordText.getText().trim().equals("bravo")) {
                        wordBounds = wordText.getBounds();
                    }
                }
            }
            assertTrue(wordBounds != null, "fixture should contain 'bravo'");

            // Inset vertically so the glyphs' full height pokes out of the redaction region: the
            // box intersects every glyph but contains none of them.
            Rectangle snug = new Rectangle(
                    (int) wordBounds.getX(), (int) (wordBounds.getY() + 2),
                    (int) wordBounds.getWidth(), (int) (wordBounds.getHeight() - 4));
            assertFalse(new GeneralPath(snug).contains(wordBounds),
                    "the test box must not contain the word bounds, or it proves nothing");

            RedactionAnnotation annotation = (RedactionAnnotation) AnnotationFactory.buildAnnotation(
                    document.getPageTree().getLibrary(), Annotation.SUBTYPE_REDACT, snug);
            ArrayList<Shape> markupBounds = new ArrayList<>();
            markupBounds.add(snug);
            annotation.setColor(Color.BLACK);
            annotation.setMarkupBounds(markupBounds);
            annotation.setMarkupPath(new GeneralPath(snug));
            annotation.setBBox(snug);
            annotation.resetAppearanceStream(new AffineTransform());
            page.addAnnotation(annotation, true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            redacted = out.toByteArray();
        } finally {
            document.dispose();
        }

        assertFalse(RedactionFixtures.extractedText(redacted).contains("bravo"),
                "'bravo' survived a redaction box drawn snugly over it");
        assertFalse(RedactionFixtures.contentStreams(redacted, true).contains("bravo"),
                "'bravo' is still in the content stream after a snug redaction box");
    }

    /**
     * Characterises the cost of flagging on any intersection: a redaction sized to a word can reach
     * glyphs on a neighbouring line when leading is tight enough that their bounds overlap.
     * <p>
     * This is a measurement, not a pass/fail on a requirement - it records what the current
     * predicate does so the trade is visible if it ever bites. If over-reach becomes a problem the
     * answer is the coverage threshold from decision 1 (flag when the intersected fraction of a
     * glyph exceeds some value) rather than a return to containment, which leaks.
     */
    @DisplayName("intersection flagging can reach a neighbouring line when leading is tight")
    @Test
    public void tightLeadingShowsTheCostOfIntersectionFlagging() throws Exception {
        byte[] redacted = redact("tight_leading.pdf", "bravo");
        String remaining = RedactionFixtures.extractedText(redacted);

        assertFalse(remaining.contains("bravo"), "the targeted word must go");
        // What the neighbours look like afterwards is the measurement. Recorded as an assertion so
        // a change in the predicate shows up here rather than silently.
        // Measured: the only collateral is a space glyph on the line above, whose bounds overlap
        // the redaction region. No word on either neighbouring line is lost. Pinned exactly so a
        // change of predicate shows up as a diff here rather than silently widening the blast
        // radius.
        assertEquals("above line  text | middle   word | below line text",
                remaining.trim().replace("\n", " | "),
                "intersection flagging reached further than the measured baseline");
    }

    /**
     * An inline image is written out by the redaction callback rather than copied by the generic
     * token path, and that write used to sit inside the loop over annotations - so the image was
     * emitted once per annotation. With one intersecting and one non-intersecting redaction that
     * meant emitting the burned image <em>and</em> the untouched original.
     */
    @DisplayName("an inline image is written exactly once regardless of annotation count")
    @Test
    public void inlineImageIsWrittenOnce() throws Exception {
        byte[] redacted = redact("inline_image.pdf", "alpha|over");
        String streams = RedactionFixtures.contentStreams(redacted, true);

        assertEquals(1, RedactionFixtures.countOccurrences(streams, "ID "),
                "expected exactly one inline image data marker in:\n" + streams);
        assertEquals(1, RedactionFixtures.countOccurrences(streams, "EI"),
                "expected exactly one inline image terminator in:\n" + streams);
        assertFalse(streams.contains("alpha") || streams.contains("over"),
                "the redacted words should be gone in:\n" + streams);
    }


    private void assertRedactionMatchesGolden(String fixture, String term) throws Exception {
        List<Placement> originsBefore = glyphOrigins(Files.readAllBytes(FIXTURES.resolve(fixture)));

        byte[] redacted = redact(fixture, term);
        String actual = RedactionFixtures.contentStreams(redacted, true);

        String extracted = RedactionFixtures.extractedText(redacted);
        for (String single : term.split("\\|")) {
            assertFalse(extracted.contains(single),
                    "'" + single + "' is still extractable from " + fixture);
        // Extraction is not enough on its own: a string left in the stream without its operator is
        // never shown, so it cannot be extracted, but its bytes are still in the file for anyone
        // who opens it in an editor. Redaction has to remove the bytes, not just the rendering.
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
            List<Rectangle> targets = RedactionFixtures.wordBounds(page, terms);
            // A term may legitimately appear more than once - a form drawn twice shows its text at
            // each placement - so require every term to be found, not a particular count.
            for (String single : terms) {
                assertFalse(RedactionFixtures.wordBounds(page, Collections.singletonList(single)).isEmpty(),
                        "fixture " + fixture + " should contain '" + single + "'");
            }

            for (Rectangle bounds : targets) {
                page.addAnnotation(RedactionFixtures.redactionOver(document, bounds), true);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
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
