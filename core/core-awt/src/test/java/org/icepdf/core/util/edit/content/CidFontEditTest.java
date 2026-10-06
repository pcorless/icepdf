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
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.redaction.RedactionFixtures;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    private static final String INDIRECT_RESOURCES_FIXTURE =
            Paths.get("src/test/resources/redaction/cid_subset_indirect_resources.pdf").toString();

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
            assertTrue(!TextEditCapability.requiresSubstitution(page, bounds, "oral"),
                    "and of oral, so no substitute is needed");
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

            assertEquals("type3", TextEditCapability.unsupportedReason(page, bounds),
                    "should refuse Type 3, and say which reason it is");
            assertTrue(!TextEditCapability.canEdit(page, bounds), "and not offer the edit");
        } finally {
            document.dispose();
        }
    }


    /**
     * "brave" needs an "e", which the subset has no code for. Rather than refuse the correction -
     * and correcting a scanning error is the reason to edit at all - the replacement is written in a
     * substitute font, embedded into the document for the purpose.
     */
    @DisplayName("a character the subset lacks is written in a substitute font")
    @Test
    public void substitutesWhenTheSubsetCannotWriteIt() throws Exception {
        byte[] edited = edit("bravo", "brave");

        String text = RedactionFixtures.extractedText(edited);
        assertTrue(text.contains("brave"), "the correction should be readable, got: " + text);
        assertTrue(text.contains("alpha"), "and the untouched word left alone, got: " + text);

        String raw = new String(edited, StandardCharsets.ISO_8859_1);
        assertTrue(raw.contains("FontFile2"),
                "the substitute should be embedded, not merely referenced");
        String streams = RedactionFixtures.contentStreams(edited, false);
        assertTrue(streams.contains("Tf"), "the substitute has to be selected:\n" + streams);
        assertFalse(streams.contains("0000"),
                "and nothing written as notdef:\n" + streams);
    }

    /**
     * The control. Substituting when the font can do the job would change the look of an edit for no
     * reason, and would add a font to every document edited.
     */
    @DisplayName("no substitute is added when the font can write the text itself")
    @Test
    public void doesNotSubstituteWhenTheFontSuffices() throws Exception {
        byte[] edited = edit("bravo", "loop");

        assertFalse(new String(edited, StandardCharsets.ISO_8859_1).contains("FontFile2"),
                "the subset can write loop, so nothing should have been embedded");
    }

    /**
     * The substitute is subsetted to the replacement, and a character WinAnsiEncoding cannot reach
     * makes it a composite font - whose codes are two bytes. Written as one byte each the substitute
     * drew the wrong glyphs at exactly the characters it was added for, and it is added for nothing
     * else.
     */
    @DisplayName("a substitute that has to be composite is written with two-byte codes")
    @Test
    public void compositeSubstituteIsWrittenAsCids() throws Exception {
        // Greek alpha: outside WinAnsiEncoding, so the substitute cannot be a simple font
        byte[] edited = edit("bravo", "\u03B1\u03B2");

        String raw = new String(edited, StandardCharsets.ISO_8859_1);
        assumeTrue(raw.contains("FontFile2"),
                "no host font could be embedded for the substitute; nothing to check");
        assertTrue(raw.contains("/Type0"),
                "a substitute for text outside WinAnsiEncoding has to be a composite font");
        String streams = RedactionFixtures.contentStreams(edited, false);
        assertTrue(streams.contains("Tf"), "the substitute has to be selected:\n" + streams);
        // a two-byte code cannot be written in a literal string, so the run has to be hex
        assertTrue(streams.matches("(?s).*Tf\\s*\\[?\\s*<[0-9A-Fa-f]{4,}>.*"),
                "the replacement should be shown as hex CIDs:\n" + streams);
    }

    /**
     * Most documents keep /Resources as an object of its own rather than writing it into the page,
     * and a font added for an edit has to end up somewhere the reopened page resolves it from. A
     * content stream that selects a font the file does not define is one no reader can draw, and
     * nothing about the bytes says so - the name is in the stream either way.
     */
    @DisplayName("a substitute reaches the file when /Resources is an indirect object")
    @Test
    public void substituteIsWrittenWhenResourcesAreIndirect() throws Exception {
        // Saved as an incremental update, which writes only what was registered as changed; a full
        // rewrite walks the whole document and would carry the addition however it was recorded.
        byte[] edited = edit(INDIRECT_RESOURCES_FIXTURE, "bravo", "brave", WriteMode.INCREMENT_UPDATE);

        String raw = new String(edited, StandardCharsets.ISO_8859_1);
        assumeTrue(raw.contains("FontFile2"),
                "no host font could be embedded for the substitute; nothing to check");

        String streams = RedactionFixtures.contentStreams(edited, false);
        Matcher selected = Pattern.compile("/(IcePdfEdit\\d+)\\s").matcher(streams);
        assertTrue(selected.find(), "the substitute should be selected by name:\n" + streams);

        // Read back rather than searched for in the bytes: the name appears in the content stream
        // whether or not the resources that define it were written, so only resolving it through the
        // reopened page's resources answers the question.
        Name fontName = new Name(selected.group(1));
        Document written = new Document();
        try {
            written.setByteArray(edited, 0, edited.length, null);
            Page page = written.getPageTree().getPage(0);
            page.init();
            assertNotNull(page.getResources().getFont(fontName),
                    "the page's resources have to define " + fontName + ", or no reader can draw"
                            + " the edit");
        } finally {
            written.dispose();
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
        return edit(FIXTURE, target, replacement);
    }

    private byte[] edit(String fixture, String target, String replacement) throws Exception {
        return edit(fixture, target, replacement, WriteMode.FULL_UPDATE);
    }

    private byte[] edit(String fixture, String target, String replacement, WriteMode writeMode)
            throws Exception {
        Document document = new Document();
        document.setFile(fixture);
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            TextContentEditor.updateText(page, wordBounds(page, target), replacement);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.saveToOutputStream(out, writeMode);
            return out.toByteArray();
        } finally {
            document.dispose();
        }
    }
}
