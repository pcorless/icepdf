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
package org.icepdf.core.pobjects.fonts.builders;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The font dictionaries generated for annotation appearances - a FreeText's text, a signature's.
 * <p>
 * There are two builders: one embeds a TrueType subset, the other is the fallback for a face that
 * cannot be embedded. The fallback is the one nothing exercised, and it kept a bug the embedding path
 * had already had fixed - which is why the CMap is now built in one place for both.
 */
public class FontBuilderTest {

    @DisplayName("a generated Type 1 font is a font, and its text can be extracted")
    @Test
    public void type1FallbackIsWellFormed() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/simple_tj.pdf").toString());
        try {
            Library library = document.getCatalog().getLibrary();
            SimpleFont font = new Type1FontBuilder(library, "Helvetica").Build();

            assertEquals(Font.TYPE, font.getEntries().get(Font.TYPE_KEY),
                    "/Type must be /Font; it used to be the name \"Subtype\"");

            Object toUnicode = font.getEntries().get(SimpleFont.TO_UNICODE_KEY);
            assertTrue(toUnicode instanceof Reference,
                    "/ToUnicode takes a CMap stream, not the name /Identity, got: " + toUnicode);

            String cmap = cmapText(library, (Reference) toUnicode);
            assertTrue(cmap.contains("beginbfchar"), "should be a bfchar CMap:\n" + cmap);
            // WinAnsiEncoding is Windows-1252, where 0x93 is a left double quotation mark. Latin-1
            // leaves that position undefined, so a table built from the wrong encoding gets it wrong.
            assertTrue(cmap.contains("<93> <201C>"),
                    "0x93 should map to U+201C under WinAnsiEncoding:\n" + cmap);
            assertTrue(cmap.contains("<41> <0041>"), "and 0x41 to A:\n" + cmap);
        } finally {
            document.dispose();
        }
    }

    /**
     * WinAnsiEncoding is Windows-1252, so a character code is a byte and it is not the Unicode value:
     * U+201C is code 0x93.  The subset is collected as Unicode, and every consumer of it treated
     * those numbers as codes.  For ASCII the two agree, which is the whole reason this survived.
     */
    @DisplayName("a character code is not its Unicode value")
    @Test
    public void winAnsiCodesAreNotUnicode() {
        assertEquals(0x93, WinAnsiEncoding.codeOf(0x201C), "left double quote is code 0x93");
        assertEquals(0x201C, WinAnsiEncoding.unicodeOf(0x93), "and back again");
        assertEquals(0xE9, WinAnsiEncoding.codeOf(0xE9), "Latin-1 range agrees with Unicode");
        assertEquals(0x41, WinAnsiEncoding.codeOf(0x41), "as does ASCII");
        // 0x81 0x8D 0x8F 0x90 0x9D are undefined in Windows-1252
        assertEquals(-1, WinAnsiEncoding.unicodeOf(0x81), "an undefined position means nothing");
        assertEquals(-1, WinAnsiEncoding.codeOf(0x4E2D),
                "a character the encoding cannot show has no code");
    }

    /**
     * A simple font's codes run 0 to 255, so /FirstChar, /LastChar and the length of /Widths are
     * bounded by that.  Indexed by Unicode instead, a subset containing a left double quote produced
     * /LastChar 8220 and an 8156-entry array - a font dictionary no reader can use.
     */
    @DisplayName("/Widths is indexed by character code, so it fits in a simple font")
    @Test
    public void widthsAreIndexedByCharacterCode() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/simple_tj.pdf").toString());
        try {
            Library library = document.getCatalog().getLibrary();
            // "A" and a left double quote: one in the range where code and Unicode agree, one not
            SimpleFont font = embeddedSubset(library, "A\u201C");

            int firstChar = ((Number) font.getEntries().get(new Name("FirstChar"))).intValue();
            int lastChar = ((Number) font.getEntries().get(new Name("LastChar"))).intValue();
            List<?> widths = (List<?>) font.getEntries().get(new Name("Widths"));

            assertEquals(0x41, firstChar, "A is code 0x41");
            assertEquals(0x93, lastChar, "the left double quote is code 0x93, not 8220");
            assertEquals(lastChar - firstChar + 1, widths.size(),
                    "/Widths runs FirstChar to LastChar");
            assertTrue(widths.size() <= 256, "a simple font cannot have more than 256 codes");
            assertTrue(((Number) widths.get(0)).intValue() > 0, "A should have a width");
            assertTrue(((Number) widths.get(widths.size() - 1)).intValue() > 0,
                    "and so should the quote, at the code it is actually shown by");

            String cmap = cmapText(library, (Reference) font.getEntries().get(SimpleFont.TO_UNICODE_KEY));
            assertTrue(cmap.contains("<93> <201C>"),
                    "the subset's /ToUnicode should map the code, not the Unicode value:\n" + cmap);
        } finally {
            document.dispose();
        }
    }

    private SimpleFont embeddedSubset(Library library, String text) throws Exception {
        TrueTypeFontEmbedder embedder = new TrueTypeFontEmbedder(
                FontManager.getInstance().initialize().getInstance("Helvetica", 0));
        for (int i = 0; i < text.length(); i++) {
            embedder.addToSubset(text.charAt(i));
        }
        return new TrueTypeFontBuilder(library, embedder).build();
    }

    /**
     * A simple font dictionary must carry /Widths, /FirstChar, /LastChar and a /FontDescriptor unless
     * it is one of the standard 14, which every reader knows the metrics of (PDF 32000-1, 9.6.2.1).
     * Helvetica is one of the fourteen, so the right thing is to write none of them.
     */
    @DisplayName("a standard 14 font is written without widths, because the reader has them")
    @Test
    public void core14FontOmitsMetrics() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/simple_tj.pdf").toString());
        try {
            Library library = document.getCatalog().getLibrary();
            SimpleFont font = new Type1FontBuilder(library, "Helvetica").Build();

            // /FirstChar 32 and /LastChar 255 used to be written with no /Widths at all, telling a
            // reader the font covers 224 codes and giving it the metrics of none of them.
            assertNull(font.getEntries().get(new Name("FirstChar")), "no /FirstChar without /Widths");
            assertNull(font.getEntries().get(new Name("LastChar")), "nor /LastChar");
            assertNull(font.getEntries().get(new Name("Widths")), "the reader has the metrics");
            assertNull(font.getEntries().get(new Name("FontDescriptor")), "and the descriptor");
        } finally {
            document.dispose();
        }
    }

    /**
     * Anything outside the fourteen has to bring its own metrics, measured from whichever face will
     * actually draw the text.
     */
    @DisplayName("a font outside the standard 14 brings its own widths and descriptor")
    @Test
    public void nonCore14FontCarriesMetrics() throws Exception {
        Document document = new Document();
        document.setFile(Paths.get("src/test/resources/redaction/simple_tj.pdf").toString());
        try {
            Library library = document.getCatalog().getLibrary();
            FontFile face = FontManager.getInstance().initialize().getInstance("Arial", 0);
            SimpleFont font = new Type1FontBuilder(library, "Arial-Not-A-Core-Font", face).Build();

            assertEquals(32, ((Number) font.getEntries().get(new Name("FirstChar"))).intValue());
            assertEquals(255, ((Number) font.getEntries().get(new Name("LastChar"))).intValue());
            List<?> widths = (List<?>) font.getEntries().get(new Name("Widths"));
            assertEquals(224, widths.size(), "one width per code from /FirstChar to /LastChar");

            // Space is code 0x20, the first entry, and no face makes it zero-width.  Checking a real
            // number rather than only the array's length: an array of 224 zeros is the same size.
            assertTrue(((Number) widths.get(0)).intValue() > 0, "space should have a width");
            assertNotNull(font.getEntries().get(new Name("FontDescriptor")),
                    "a font outside the fourteen needs a descriptor");
        } finally {
            document.dispose();
        }
    }

    private String cmapText(Library library, Reference reference) throws Exception {
        Object object = library.getObject(reference);
        if (object == null && library.getStateManager().getTempChange(reference) != null) {
            object = library.getStateManager().getTempChange(reference).getObject();
        }
        assertTrue(object instanceof Stream, "/ToUnicode should resolve to a stream, got: " + object);
        return new String(((Stream) object).getDecodedStreamBytes(), StandardCharsets.ISO_8859_1);
    }
}
