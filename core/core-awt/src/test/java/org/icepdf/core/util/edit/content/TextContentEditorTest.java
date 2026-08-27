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
package org.icepdf.core.util.edit.content;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.redaction.RedactionFixtures;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterisation tests for in-place text editing.
 * <p>
 * The editor shares {@code StringObjectWriter} with redaction, so it is the thing most at risk when
 * that writer changes, and nothing covered it. These pin the observable behaviour - what the text
 * says afterwards, and that the surrounding text is untouched - rather than the exact bytes, so the
 * writer can be reworked underneath them.
 */
public class TextContentEditorTest {

    private static final String FIXTURE =
            Paths.get("src/test/resources/redaction/simple_tj.pdf").toString();

    @DisplayName("a word can be replaced with one of the same length")
    @Test
    public void replaceWithSameLength() throws Exception {
        byte[] edited = edit("bravo", "BRAVO");
        String text = RedactionFixtures.extractedText(edited);

        assertTrue(text.contains("BRAVO"), "replacement should be present, got: " + text);
        assertFalse(text.contains("bravo"), "original should be gone, got: " + text);
        assertTrue(text.contains("alpha") && text.contains("charlie"),
                "surrounding words should survive, got: " + text);
    }

    @DisplayName("a word can be replaced with a shorter one")
    @Test
    public void replaceWithShorter() throws Exception {
        byte[] edited = edit("charlie", "cat");
        String text = RedactionFixtures.extractedText(edited);

        assertTrue(text.contains("cat"), "replacement should be present, got: " + text);
        assertFalse(text.contains("charlie"), "original should be gone, got: " + text);
        assertTrue(text.contains("alpha") && text.contains("bravo"),
                "surrounding words should survive, got: " + text);
    }

    @DisplayName("editing leaves the stream parseable and the other words in place")
    @Test
    public void editedStreamStaysWellFormed() throws Exception {
        byte[] edited = edit("bravo", "BRAVO");
        String stream = RedactionFixtures.contentStreams(edited, false);

        assertFalse(stream.contains("bravo"), "the replaced text should not remain: " + stream);
        assertTrue(stream.contains("alpha"), "untouched text should remain: " + stream);
        // Parsing the reopened document is what extractedText does; if the stream were malformed
        // the surrounding words would not come back.
        assertTrue(RedactionFixtures.extractedText(edited).contains("alpha"),
                "stream should still parse");
    }

    /**
     * An edit whose bounds reach both the page's own text and text inside a form XObject. The
     * replacement belongs to the edit, not to a content stream, so it must appear once however many
     * streams the selection happens to span.
     */
    @DisplayName("an edit spanning a form writes the replacement once")
    @Test
    public void editSpanningAFormWritesTheReplacementOnce() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/form_xobject.pdf").toString());
        byte[] edited;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            // The whole page: "page level text" is in the page stream, "alpha bravo charlie" in the
            // form's, so both callbacks see flagged glyphs.
            TextContentEditor.updateText(page, "text", new Rectangle(0, 0, 300, 200), "ZZZ");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            edited = out.toByteArray();
        } finally {
            document.dispose();
        }

        String streams = RedactionFixtures.contentStreams(edited, false);
        assertEquals(1, RedactionFixtures.countOccurrences(streams, "ZZZ"),
                "the replacement should appear once across all streams, got:\n" + streams);
    }


    /**
     * Editing text on a page that also carries an inline image must leave the image alone.
     * <p>
     * The callback used to advance past an inline image without writing its bytes, which told the
     * copy-through machinery they had been dealt with when they had been skipped. What came out was
     * not a page missing an image - it was a {@code BI} with no {@code ID} and no {@code EI}, a
     * content stream a strict reader is entitled to reject.
     */
    @DisplayName("editing a page leaves an inline image on it intact")
    @Test
    public void inlineImageSurvivesAnEdit() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/inline_image.pdf").toString());
        byte[] edited;
        String before;
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            before = pageStreams(page);

            // "alpha" is on the upper line; the image sits lower down and is not being edited.
            List<Rectangle> bounds = RedactionFixtures.wordBounds(page, Collections.singletonList("alpha"));
            assertEquals(1, bounds.size(), "fixture should contain 'alpha' exactly once");
            TextContentEditor.updateText(page, "alpha", bounds.get(0), "ZZZZZ");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            edited = out.toByteArray();
        } finally {
            document.dispose();
        }

        String after = RedactionFixtures.contentStreams(edited, false);
        assertTrue(after.contains("ZZZZZ"), "the edit should have been applied:\n" + after);
        assertEquals(RedactionFixtures.countOccurrences(before, "ID "),
                RedactionFixtures.countOccurrences(after, "ID "),
                "the inline image's data marker should survive:\n" + after);
        assertEquals(RedactionFixtures.countOccurrences(before, "EI"),
                RedactionFixtures.countOccurrences(after, "EI"),
                "and its terminator:\n" + after);
    }

    /** The page's content streams as they stand, for a before-and-after comparison. */
    private String pageStreams(Page page) throws Exception {
        StringBuilder streams = new StringBuilder();
        for (org.icepdf.core.pobjects.Stream stream : page.getContentStreams()) {
            streams.append(new String(stream.getDecodedStreamBytes(),
                    java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        return streams.toString();
    }

    // -- helpers ---------------------------------------------------------------------------------

    private byte[] edit(String target, String replacement) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();

            List<Rectangle> bounds = RedactionFixtures.wordBounds(page,
                    Collections.singletonList(target));
            assertEquals(1, bounds.size(), "fixture should contain '" + target + "' exactly once");

            TextContentEditor.updateText(page, target, bounds.get(0), replacement);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }
}
