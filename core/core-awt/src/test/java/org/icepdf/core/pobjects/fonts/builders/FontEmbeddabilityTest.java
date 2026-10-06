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

import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontOpenType;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Which fonts the subsetter can actually embed.
 * <p>
 * The subsetter reads outlines out of a {@code glyf} table. An OpenType font with PostScript outlines
 * keeps them in {@code CFF } instead, and asking the subsetter for one fails with
 * {@code UnsupportedOperationException: OTF fonts do not have a glyf table} - raised from inside the
 * subsetter, long after the embedder has been asked whether the font can be embedded and said yes.
 * <p>
 * Nothing reaches that today, because the CJK OpenType collections that would are excluded from the
 * font scan. That exclusion is the reason Noto CJK cannot be used, and this is one of the two things
 * that has to be true before it can be lifted - the other being a CFF subsetter, which does not
 * exist here.
 */
public class FontEmbeddabilityTest {

    /**
     * urw-base35 ships with Ghostscript and is on most Linux machines; any OpenType file would do.
     */
    private static final String CFF_FONT = "/usr/share/fonts/opentype/urw-base35/NimbusRoman-Regular.otf";

    @BeforeAll
    public static void init() {
        FontManager.getInstance().initialize();
    }

    @DisplayName("a font whose outlines are CFF is not embeddable")
    @Test
    public void postScriptOutlinesAreNotEmbeddable() throws Exception {
        File font = new File(CFF_FONT);
        assumeTrue(font.canRead(), CFF_FONT + " is not installed");

        ZFontOpenType openType = new ZFontOpenType(Files.readAllBytes(font.toPath()));
        TrueTypeFontEmbedder embedder = new TrueTypeFontEmbedder(openType);
        embedder.addToSubset('A');

        // Said true before, and then createSubsetFont threw from inside the subsetter.
        assertFalse(embedder.isFontEmbeddable(),
                "the subsetter reads glyf outlines and this font has none");
    }

    /**
     * The control. Without it "not embeddable" is equally well explained by the check having become
     * false for every font, which would quietly stop embedding anything at all.
     */
    @DisplayName("a font with glyf outlines still is")
    @Test
    public void trueTypeOutlinesAreEmbeddable() {
        ZFontTrueType trueType = (ZFontTrueType) FontManager.getInstance().getInstance("Helvetica", 0);
        TrueTypeFontEmbedder embedder = new TrueTypeFontEmbedder(trueType);
        embedder.addToSubset('A');

        assertTrue(embedder.isFontEmbeddable(), "an ordinary TrueType face should still embed");
    }
}
