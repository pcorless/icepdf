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
