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
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The verification pass: what it can establish about a redacted document, and what it refuses to
 * claim.
 */
public class RedactionVerificationTest {

    @DisplayName("a clean redaction verifies")
    @Test
    public void cleanRedactionVerifies() throws Exception {
        RedactionReport report = redact("simple_tj.pdf", "bravo", RedactionOptions.defaults());

        assertEquals(RedactionConfidence.VERIFIED, report.getConfidence(),
                "nothing found, nothing unverifiable, nothing degraded: " + report.getUnverifiableRegions());
        assertEquals(1f, report.getScore(), 0.001f);
        assertEquals(0, report.getRawByteMatchesAfter(), "no trace should remain in the bytes");
        assertTrue(report.getHitsAfterByTerm().values().stream().allMatch(hits -> hits == 0),
                "nothing should still be findable: " + report.getHitsAfterByTerm());
    }

    @DisplayName("the before count says what was there to start with")
    @Test
    public void beforeCountsWhatWasThere() throws Exception {
        Document document = open("positionless_text.pdf");
        RedactionReport report;
        try {
            annotate(document, "bravo");
            Redactor.configure(document, RedactionRequest.ofAnnotationsAndTerms(terms("bravo")));
            save(document);
            report = document.getRedactionReport();
        } finally {
            document.dispose();
        }

        assertTrue(report.getHitsBeforeByTerm().get("bravo") > 0,
                "the term was in the document before: " + report.getHitsBeforeByTerm());
        assertEquals(0, (int) report.getHitsAfterByTerm().get("bravo"), "and not after");
        assertEquals(RedactionConfidence.VERIFIED, report.getConfidence());
    }

    /**
     * The pass asks whether the term is still in the document, not whether the configured subset of
     * targets was processed. Narrowing the scope and leaving the word on a page is therefore a
     * failure, and deliberately so: the question a reader of this report is asking is "is it safe to
     * release this", and "the parts you asked for went" is not an answer to it.
     */
    @DisplayName("scope is not an excuse: a term left on a page still fails")
    @Test
    public void narrowedScopeStillFailsIfTheTermSurvives() throws Exception {
        RedactionReport report = redactTerms("positionless_text.pdf", "bravo",
                RedactionOptions.defaults());

        assertEquals(RedactionConfidence.FAILED, report.getConfidence(),
                "a term-only request never touches page content, so the word is still on the page");
        assertTrue(report.getHitsAfterByTerm().get("bravo") > 0,
                "and the report should say it is still findable");
    }

    /**
     * The check that gives the rest of the report its weight. An image burn is deliberately left in
     * scope while the text is not, so the word survives on the page: the pass must call that a
     * failure rather than reporting the counts it collected and moving on.
     */
    @DisplayName("a surviving word is a failure, whatever else the report says")
    @Test
    public void survivingTextFails() throws Exception {
        Document document = open("simple_tj.pdf");
        RedactionReport report;
        try {
            annotate(document, "bravo");
            // Images only: the annotation covers text, which is now out of scope, so nothing is
            // removed and "bravo" is still on the page afterwards.
            Redactor.configure(document, RedactionRequest.ofAnnotationsAndTerms(terms("bravo"))
                    .with(RedactionOptions.defaults().targets(EnumSet.of(RedactionTarget.IMAGES))));
            save(document);
            report = document.getRedactionReport();
        } finally {
            document.dispose();
        }

        assertEquals(RedactionConfidence.FAILED, report.getConfidence(),
                "the term is still in the document, so this cannot be anything but a failure");
        assertEquals(0f, report.getScore(), 0.001f, "a failure scores zero");
        assertTrue(report.getHitsAfterByTerm().get("bravo") > 0,
                "and the report should say where it was found");
    }

    /**
     * Extraction and byte scanning catch different things, so the failure has to be reachable from
     * either. Here the text is gone from the page but its bytes are still in the file.
     */
    @DisplayName("a word that is unreadable but still in the bytes is a failure")
    @Test
    public void bytesAloneCanFail() throws Exception {
        RedactionReport report = redactTerms("positionless_text.pdf", "bravo",
                RedactionOptions.defaults().targets(EnumSet.of(RedactionTarget.OUTLINE)));

        // Only the bookmark was in scope, so the comment and the metadata still carry the word -
        // in the file, whether or not any page shows it.
        assertEquals(RedactionConfidence.FAILED, report.getConfidence(),
                "the word is still in the file: " + report);
        assertTrue(report.getRawByteMatchesAfter() > 0,
                "and it was the byte scan that found it");
    }

    @DisplayName("an image burn cannot be verified, and the report says so rather than claiming it is clean")
    @Test
    public void imageBurnIsUnverifiable() throws Exception {
        Document document = open("rotated_image.pdf");
        RedactionReport report;
        try {
            document.getPageTree().getPage(0).init();
            document.getPageTree().getPage(0).addAnnotation(
                    RedactionFixtures.redactionOver(document, new Rectangle(142, 102, 26, 26)), true);
            save(document);
            report = document.getRedactionReport();
        } finally {
            document.dispose();
        }

        assertEquals(1, report.getImagesBurned(), "an image should have been burned");
        assertEquals(RedactionConfidence.UNVERIFIED, report.getConfidence(),
                "pixels can be confirmed changed, but not that the right pixels changed");
        assertTrue(report.getUnverifiableRegions().stream()
                        .anyMatch(region -> region.getReason() == UnverifiableRegion.Reason.RASTER_CONTENT),
                "and the reason should be recorded: " + report.getUnverifiableRegions());
        assertTrue(report.getScore() < 1f, "an unverified result should not score as a clean one");
    }

    @DisplayName("terms can be hashed so the report is safe to keep")
    @Test
    public void termsCanBeHashed() throws Exception {
        RedactionReport report = redactTerms("positionless_text.pdf", "bravo",
                RedactionOptions.defaults().hashTermsInReport(true));

        assertFalse(report.getHitsBeforeByTerm().containsKey("bravo"),
                "the plaintext term should not be in the report: " + report.getHitsBeforeByTerm());
        assertEquals(1, report.getHitsBeforeByTerm().size(), "but the count should still be there");
        assertFalse(report.toJson().contains("bravo"),
                "and it should not appear in the serialised form either");
    }

    @DisplayName("verification can be switched off")
    @Test
    public void verificationCanBeSwitchedOff() throws Exception {
        RedactionReport report = redact("simple_tj.pdf", "bravo",
                RedactionOptions.defaults().verify(false));

        assertNull(report.getConfidence(), "nothing was verified, so nothing should be claimed");
        assertTrue(report.getHitsAfterByTerm().isEmpty());
    }

    @DisplayName("the serialised report carries the verification")
    @Test
    public void jsonCarriesVerification() throws Exception {
        String json = redact("simple_tj.pdf", "bravo", RedactionOptions.defaults()).toJson();

        assertTrue(json.contains("\"confidence\": \"VERIFIED\""), json);
        assertTrue(json.contains("\"score\": 1.0"), json);
        assertTrue(json.contains("\"rawByteMatchesAfter\": 0"), json);
    }

    // -- helpers ---------------------------------------------------------------------------------

    private Document open(String fixture) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + fixture).toString());
        return document;
    }

    /**
     * Redacts a word by annotation, so the verification has only what the burn recorded to go on.
     */
    private RedactionReport redact(String fixture, String word, RedactionOptions options) throws Exception {
        Document document = open(fixture);
        try {
            annotate(document, word);
            Redactor.configure(document, RedactionRequest.ofAnnotations().with(options));
            save(document);
            return document.getRedactionReport();
        } finally {
            document.dispose();
        }
    }

    /**
     * Redacts by term, with no annotation at all.
     */
    private RedactionReport redactTerms(String fixture, String word, RedactionOptions options) throws Exception {
        Document document = open(fixture);
        try {
            Redactor.configure(document, RedactionRequest.ofTerms(terms(word)).with(options));
            save(document);
            return document.getRedactionReport();
        } finally {
            document.dispose();
        }
    }

    private void annotate(Document document, String word) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        List<Rectangle> bounds = RedactionFixtures.wordBounds(page, Collections.singletonList(word));
        assertFalse(bounds.isEmpty(), "fixture should contain '" + word + "'");
        page.addAnnotation(RedactionFixtures.redactionOver(document, bounds.get(0)), true);
    }

    private void save(Document document) throws Exception {
        document.saveToOutputStream(new ByteArrayOutputStream(), WriteMode.FULL_UPDATE);
    }

    private List<SearchTerm> terms(String word) {
        ArrayList<String> words = new ArrayList<>();
        words.add(word);
        return Collections.singletonList(new SearchTerm(word, words, false, false, false));
    }
}
