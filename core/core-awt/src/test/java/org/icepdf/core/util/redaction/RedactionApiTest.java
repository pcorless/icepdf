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
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The redaction API as a caller meets it: state what you want, save, read what happened.
 * <p>
 * Redaction runs inside the write - it rewrites every stream that carried the content, which an
 * incremental update cannot do - so these go through an actual save rather than calling the burner
 * directly. That is also the only way to prove the request reaches it: the burn happens on a reopened
 * copy of the document, two instances away from the one configured here.
 */
public class RedactionApiTest {

    private static final String FIXTURE =
            Paths.get("src/test/resources/redaction/simple_tj.pdf").toString();

    @DisplayName("a report comes back describing what was removed")
    @Test
    public void reportDescribesWhatWasRemoved() throws Exception {
        Document document = open();
        try {
            redactWord(document, "bravo");
            save(document, null);

            RedactionReport report = document.getRedactionReport();
            assertNotNull(report, "a full write with redactions should leave a report");
            assertFalse(report.isEmpty(), "something was redacted, so the report should say so");
            assertEquals(5, report.getGlyphsRemoved(), "'bravo' is five glyphs");
            assertEquals(5, (int) report.getCountsByTarget().get(RedactionTarget.PAGE_CONTENT));
            assertEquals(0, report.getImagesBurned(), "the fixture has no images");
            assertTrue(report.getWarnings().isEmpty(),
                    "nothing degraded, got: " + report.getWarnings());
        } finally {
            document.dispose();
        }
    }

    @DisplayName("no report until the document has been written")
    @Test
    public void noReportBeforeAWrite() throws Exception {
        Document document = open();
        try {
            redactWord(document, "bravo");
            assertNull(document.getRedactionReport(),
                    "redaction happens during the write, so there is nothing to report yet");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("an incremental write leaves no report, because it redacts nothing")
    @Test
    public void incrementalWriteLeavesNoReport() throws Exception {
        Document document = open();
        try {
            redactWord(document, "bravo");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.INCREMENT_UPDATE);
            assertNull(document.getRedactionReport(),
                    "an incremental update only appends, so it cannot have redacted anything");
        } finally {
            document.dispose();
        }
    }

    @DisplayName("options reach the burn, two document instances away")
    @Test
    public void optionsReachTheBurn() throws Exception {
        Document document = open();
        byte[] saved;
        try {
            redactWord(document, "bravo");
            // Narrowing the targets to images means the text should be left alone: proof that the
            // request survived the temp round trip the writer does before burning.
            save(document, RedactionRequest.ofAnnotations().with(RedactionOptions.defaults()
                    .targets(EnumSet.of(RedactionTarget.IMAGES))));
            saved = savedBytes;

            RedactionReport report = document.getRedactionReport();
            assertNotNull(report);
            assertTrue(report.isEmpty(), "page content was out of scope, so nothing should have gone");
        } finally {
            document.dispose();
        }
        assertTrue(RedactionFixtures.extractedText(saved).contains("bravo"),
                "text was out of scope for this request and should have survived");
    }

    @DisplayName("the default request redacts, so old callers are unaffected")
    @Test
    public void defaultsStillRedact() throws Exception {
        Document document = open();
        byte[] saved;
        try {
            redactWord(document, "bravo");
            save(document, null);   // no configure() call at all
            saved = savedBytes;
        } finally {
            document.dispose();
        }
        assertFalse(RedactionFixtures.extractedText(saved).contains("bravo"),
                "a document with redaction annotations is redacted on save, configured or not");
    }

    @DisplayName("the report serialises to JSON for writing beside the file")
    @Test
    public void reportSerialisesToJson() throws Exception {
        Document document = open();
        try {
            redactWord(document, "bravo");
            save(document, null);
            String json = document.getRedactionReport().toJson();

            assertTrue(json.startsWith("{") && json.endsWith("}"), "should be a JSON object: " + json);
            assertTrue(json.contains("\"glyphsRemoved\": 5"), "should carry the counts: " + json);
            assertTrue(json.contains("PAGE_CONTENT"), "should break down by target: " + json);
            assertTrue(json.contains("\"warnings\": []"), "should carry an empty warning list: " + json);
        } finally {
            document.dispose();
        }
    }

    @DisplayName("options reject a coverage threshold that is not a fraction")
    @Test
    public void coverageThresholdIsAFraction() {
        assertThrows(IllegalArgumentException.class,
                () -> RedactionOptions.defaults().glyphCoverageThreshold(1.5f));
        assertThrows(IllegalArgumentException.class,
                () -> RedactionOptions.defaults().glyphCoverageThreshold(-1f));
    }

    @DisplayName("a redaction colour other than black is honoured")
    @Test
    public void redactionColourIsConfigurable() {
        RedactionOptions options = RedactionOptions.defaults().redactionColor(Color.RED);
        assertEquals(Color.RED, options.getRedactionColor());
        assertEquals(Color.BLACK, RedactionOptions.defaults().getRedactionColor(),
                "black remains the default");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private byte[] savedBytes;

    private Document open() throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        return document;
    }

    private void redactWord(Document document, String word) throws Exception {
        Page page = document.getPageTree().getPage(0);
        page.init();
        List<Rectangle> bounds = RedactionFixtures.wordBounds(page, Collections.singletonList(word));
        assertEquals(1, bounds.size(), "fixture should contain '" + word + "'");
        page.addAnnotation(RedactionFixtures.redactionOver(document, bounds.get(0)), true);
    }

    private void save(Document document, RedactionRequest request) throws Exception {
        if (request != null) {
            Redactor.configure(document, request);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
        savedBytes = out.toByteArray();
    }
}
