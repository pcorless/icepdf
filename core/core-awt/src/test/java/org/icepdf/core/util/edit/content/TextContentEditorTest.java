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
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

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
        String text = extractedText(edited);

        assertTrue(text.contains("BRAVO"), "replacement should be present, got: " + text);
        assertFalse(text.contains("bravo"), "original should be gone, got: " + text);
        assertTrue(text.contains("alpha") && text.contains("charlie"),
                "surrounding words should survive, got: " + text);
    }

    @DisplayName("a word can be replaced with a shorter one")
    @Test
    public void replaceWithShorter() throws Exception {
        byte[] edited = edit("charlie", "cat");
        String text = extractedText(edited);

        assertFalse(text.contains("charlie"), "original should be gone, got: " + text);
        assertTrue(text.contains("alpha") && text.contains("bravo"),
                "surrounding words should survive, got: " + text);
    }

    @DisplayName("editing leaves the stream parseable and the other words in place")
    @Test
    public void editedStreamStaysWellFormed() throws Exception {
        byte[] edited = edit("bravo", "BRAVO");
        String stream = contentStream(edited);

        assertFalse(stream.contains("bravo"), "the replaced text should not remain: " + stream);
        assertTrue(stream.contains("alpha"), "untouched text should remain: " + stream);
        // Parsing the reopened document is what extractedText does; if the stream were malformed
        // the surrounding words would not come back.
        assertTrue(extractedText(edited).contains("alpha"), "stream should still parse");
    }

    // -- helpers ---------------------------------------------------------------------------------

    private byte[] edit(String target, String replacement) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();

            Rectangle bounds = null;
            for (LineText lineText : page.getViewText().getPageLines()) {
                for (WordText wordText : lineText.getWords()) {
                    if (wordText.getText().trim().equals(target)) {
                        bounds = wordText.getBounds().getBounds();
                    }
                }
            }
            assertNotNull(bounds, "fixture should contain '" + target + "'");

            TextContentEditor.updateText(page, target, bounds, replacement);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }

    private String extractedText(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "edited");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            return page.getViewText().toString();
        } finally {
            document.dispose();
        }
    }

    private String contentStream(byte[] pdf) throws Exception {
        Document document = new Document();
        document.setInputStream(new ByteArrayInputStream(pdf), "edited");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            StringBuilder text = new StringBuilder();
            for (Stream stream : page.getContentStreams()) {
                text.append(new String(stream.getDecodedStreamBytes(), StandardCharsets.ISO_8859_1));
            }
            return text.toString();
        } finally {
            document.dispose();
        }
    }
}
