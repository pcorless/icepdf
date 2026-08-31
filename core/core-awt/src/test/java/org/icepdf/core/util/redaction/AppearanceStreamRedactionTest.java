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
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redacting text that is drawn by an annotation rather than by the page.
 * <p>
 * An appearance stream is painted onto the page but is not part of its content, and its text never
 * reaches the page's text either. So a search cannot find it, a redaction driven by search will not
 * have covered it, and the text-extraction check that catches most leaks is blind to it: the only
 * way to know is to look in the stream.
 */
public class AppearanceStreamRedactionTest {

    private static final String FIXTURE = "annotation_appearance.pdf";

    /**
     * The annotation's appearance draws "secret annotation text" at 20,140 to 200,180. The page
     * itself says something else, so anything found afterwards came from the appearance.
     */
    @DisplayName("a redaction over an annotation removes the text its appearance draws")
    @Test
    public void redactionRemovesAppearanceText() throws Exception {
        byte[] redacted = redact(new Rectangle(20, 140, 180, 40), RedactionOptions.defaults());

        String streams = allStreams(redacted);
        assertFalse(streams.contains("secret annotation text"),
                "the appearance should have been redacted:\n" + streams);
        assertTrue(streams.contains("page says alpha"),
                "and the page's own text left alone:\n" + streams);
    }

    @DisplayName("an annotation clear of the redaction is untouched")
    @Test
    public void annotationOutsideTheRedactionSurvives() throws Exception {
        // Bottom-left corner, nowhere near the annotation at y 140..180.
        byte[] redacted = redact(new Rectangle(0, 0, 30, 30), RedactionOptions.defaults());

        assertTrue(allStreams(redacted).contains("secret annotation text"),
                "nothing covered it, so it should still be there");
    }

    @DisplayName("appearance streams can be taken out of scope")
    @Test
    public void appearancesCanBeExcluded() throws Exception {
        byte[] redacted = redact(new Rectangle(20, 140, 180, 40),
                RedactionOptions.defaults().targets(EnumSet.of(RedactionTarget.PAGE_CONTENT)));

        assertTrue(allStreams(redacted).contains("secret annotation text"),
                "appearances were out of scope, so the text should remain");
    }

    /**
     * The redaction's own appearance is the black rectangle marking where content was removed. It is
     * drawn under itself, so a burner that did not know the difference would redact its own marker.
     */
    @DisplayName("the redaction's own appearance is left alone")
    @Test
    public void redactionMarkerSurvives() throws Exception {
        byte[] redacted = redact(new Rectangle(20, 140, 180, 40), RedactionOptions.defaults());

        Document document = new Document();
        document.setByteArray(redacted, 0, redacted.length, "redacted");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            assertEquals(2, page.getAnnotations().size(),
                    "the comment and the redaction marker should both still be there");
        } finally {
            document.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private byte[] redact(Rectangle area, RedactionOptions options) throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/" + FIXTURE).toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            page.addAnnotation(RedactionFixtures.redactionOver(document, area), true);
            Redactor.configure(document, RedactionRequest.ofAnnotations().with(options));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    /**
     * The page's content stream and the decoded appearance stream of every annotation on it.
     * <p>
     * Deliberately not the page's text: an appearance stream's text never appears there, so the
     * usual extraction check would call this document clean whatever happened. Deliberately not the
     * raw file bytes either - that reads correctly today only because the writer happens not to
     * compress these streams, and would start passing for the wrong reason the day it does.
     */
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
            for (Annotation annotation : page.getAnnotations()) {
                Object appearance = document.getCatalog().getLibrary().getObject(
                        annotation.getEntries(), Annotation.APPEARANCE_STREAM_KEY);
                if (appearance instanceof DictionaryEntries) {
                    appearance = document.getCatalog().getLibrary().getObject(
                            (DictionaryEntries) appearance, Annotation.APPEARANCE_STREAM_NORMAL_KEY);
                }
                if (appearance instanceof Stream) {
                    streams.append(new String(((Stream) appearance).getDecodedStreamBytes(),
                            StandardCharsets.ISO_8859_1));
                }
            }
            return streams.toString();
        } finally {
            document.dispose();
        }
    }
}
