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
package org.icepdf.utils;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.pobjects.graphics.text.LineText;
import org.icepdf.core.pobjects.graphics.text.PageText;
import org.icepdf.core.pobjects.graphics.text.WordText;
import org.icepdf.ri.util.FontPropertiesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GH-521: a composite font's character codes are split by the encoding CMap's codespace ranges
 * (PDF 32000-1 9.7.6.2), never by whether the font happens to carry a width for the resulting CID.
 * <p>
 * The fixture is an Identity-H CIDFontType0 whose {@code /W} array covers only a few dozen CIDs, the
 * rest falling back to {@code /DW}.  Deriving the code width from the width table declared every
 * Japanese CID unusable, so the decoder refused each two-byte code, advanced a single byte, and
 * desynchronised the rest of the string - every glyph after the first became CID 0 and rendered as a
 * .notdef box.
 */
public class CompositeFontCodeTest {

    @BeforeAll
    public static void init() {
        FontPropertiesManager.getInstance().loadOrReadSystemFonts();
    }

    @DisplayName("Identity-H CIDs outside the /W table still decode as two-byte codes")
    @Test
    public void identityHCodesAreTwoBytes() throws Exception {
        Document document = new Document();
        document.setFile(CompositeFontCodeTest.class.getResource(
                "/fonts/gh-521-identity-h-cid.pdf").getFile());
        PageText pageText = document.getPageText(0);

        int glyphs = 0, notdef = 0, beyondWidths = 0;
        for (LineText line : pageText.getPageLines()) {
            for (WordText word : line.getWords()) {
                for (GlyphText glyph : word.getGlyphs()) {
                    glyphs++;
                    if (glyph.getCid() == 0) notdef++;
                    // CIDs past the /W table are exactly the ones the old width-based split threw
                    // away; the page is mostly Japanese, so there must be plenty of them.
                    if (glyph.getCid() > 400) beyondWidths++;
                }
            }
        }
        document.dispose();

        assertTrue(glyphs > 500, "expected the page's text, found " + glyphs + " glyphs");
        assertTrue(beyondWidths > 200,
                "expected many CIDs beyond the /W table (the Japanese text), found " + beyondWidths);
        assertTrue(notdef < glyphs / 10,
                "too many notdef CIDs (" + notdef + " of " + glyphs
                        + "): the code stream is desynchronised");
    }
}
