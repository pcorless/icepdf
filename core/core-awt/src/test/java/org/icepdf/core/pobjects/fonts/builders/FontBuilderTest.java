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
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.util.Library;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private String cmapText(Library library, Reference reference) throws Exception {
        Object object = library.getObject(reference);
        if (object == null && library.getStateManager().getTempChange(reference) != null) {
            object = library.getStateManager().getTempChange(reference).getObject();
        }
        assertTrue(object instanceof Stream, "/ToUnicode should resolve to a stream, got: " + object);
        return new String(((Stream) object).getDecodedStreamBytes(), StandardCharsets.ISO_8859_1);
    }
}
