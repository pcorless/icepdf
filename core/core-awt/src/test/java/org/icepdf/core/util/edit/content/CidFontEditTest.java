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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Editing text drawn with a composite (CID) font.
 * <p>
 * A CID font in a real document is a subset: the codes and glyphs the document needed and no others.
 * Writing text back into one means finding the code that draws a character, which is the font's
 * {@code /ToUnicode} map run backwards.
 * <p>
 * The fixture draws "alpha bravo" with a font whose map covers exactly the letters of those two
 * words, which is what a subsetted font looks like.
 */
public class CidFontEditTest {

    private static final String FIXTURE =
            Paths.get("src/test/resources/redaction/cid_subset_text.pdf").toString();

    /**
     * Every letter of the replacement is one the page already draws, so this is an edit the font can
     * express - and the one that used to come out as a row of notdefs.
     */
    @DisplayName("a CID font can be edited with characters it already draws")
    @Test
    public void editUsingCharactersTheSubsetHas() throws Exception {
        byte[] edited = edit("bravo", "loop");

        String text = RedactionFixtures.extractedText(edited);
        assertTrue(text.contains("loop"), "the replacement should be readable, got: " + text);
        assertTrue(text.contains("alpha"), "and the untouched word left alone, got: " + text);
        // CID 0 is notdef. Writing one means the character could not be encoded, which is how every
        // edit to a composite font used to come out.
        assertFalse(RedactionFixtures.contentStreams(edited, false).contains("0000"),
                "no notdef codes should have been written:\n"
                        + RedactionFixtures.contentStreams(edited, false));
    }

    /**
     * "brave" needs an "e", which this document never drew, so the font has no code for it. The check
     * has to say so - the alternative is writing notdef and leaving a blank where a letter should be.
     */
    @DisplayName("characters the subset lacks are reported before the edit")
    @Test
    public void charactersOutsideTheSubsetAreReported() throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            Rectangle bounds = wordBounds(page, "bravo");

            assertEquals(List.of('e'), TextEditCapability.unsupportedCharacters(page, bounds, "brave"),
                    "the subset has no e");
            assertEquals(List.of(), TextEditCapability.unsupportedCharacters(page, bounds, "loop"),
                    "but it has every letter of loop");
            assertTrue(TextEditCapability.canEdit(page, bounds, "oral"), "and of oral");
        } finally {
            document.dispose();
        }
    }


    /**
     * A Type 3 glyph is a content stream the page draws, not a character in a font program, so there
     * is nothing to write a new character as. Refused plainly rather than attempted badly.
     */
    @DisplayName("text in a Type 3 font is refused, with a reason")
    @Test
    public void type3TextIsRefused() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/type3_text.pdf").toString());
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            // The glyphs are drawn from 20,150; cover generously rather than by word, since a Type 3
            // font need not map its glyphs to anything text search would find.
            Rectangle bounds = new Rectangle(15, 145, 60, 20);

            String reason = TextEditCapability.unsupportedReason(page, bounds, "abc");
            assertTrue(reason != null && reason.contains("Type 3"),
                    "should refuse Type 3 by name, got: " + reason);
            assertTrue(!TextEditCapability.canEdit(page, bounds, "abc"), "and not offer the edit");
        } finally {
            document.dispose();
        }
    }

    // -- helpers ---------------------------------------------------------------------------------

    private static void assertFalse(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertFalse(condition, message);
    }

    private Rectangle wordBounds(Page page, String word) throws Exception {
        List<Rectangle> bounds = RedactionFixtures.wordBounds(page, Collections.singletonList(word));
        assertEquals(1, bounds.size(), "fixture should contain '" + word + "' exactly once");
        return bounds.get(0);
    }

    private byte[] edit(String target, String replacement) throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            TextContentEditor.updateText(page, target, wordBounds(page, target), replacement);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, WriteMode.FULL_UPDATE);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }
}
