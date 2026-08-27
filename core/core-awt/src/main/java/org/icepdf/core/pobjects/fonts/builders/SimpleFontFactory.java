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

import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.icepdf.core.util.Library;

import java.util.Collection;

/**
 * Factory for creating SimpleFont instances, which may be either TrueType or Type1 fonts depending on the availability
 * of embedded font files and the configuration of the FontFactory.
 */
public class SimpleFontFactory {

    public static SimpleFont createFont(Library library, String fontName, TrueTypeFontEmbedder fontFileSubSetter) {
        // get the font file
        FontFile fontFile = fontFileSubSetter.getFontFile();
        // if embedding is support use TrueType font
        if (fontFile instanceof ZFontTrueType && FontFactory.useEmbeddedFonts && fontFileSubSetter.isFontEmbeddable()) {
            if (requiresCompositeFont(fontFileSubSetter.getSubsetCodePoints())) {
                return new TrueTypeCIDFontBuilder(library, fontFileSubSetter).build();
            }
            return new TrueTypeFontBuilder(library, fontFileSubSetter).build();
        }
        // fall back on simple Type1 font, if embedding is not available
        else {
            return new Type1FontBuilder(library, fontName, fontFile).Build();
        }
    }

    /**
     * Whether this text needs a composite font to be shown at all.
     * <p>
     * A simple font's character codes are one byte, and the encoding written here is WinAnsiEncoding,
     * so the most it can ever show is what Windows-1252 defines - no CJK, no Greek, no Cyrillic. One
     * character outside that decides the whole run: the alternative is a font that silently cannot
     * draw part of its own text.
     * <p>
     * The text-writing side has to reach the same conclusion, and does so from the same subset, so
     * that the codes written into the content stream are the ones the font dictionary describes.
     *
     * @param codePoints the Unicode code points the text uses
     * @return true if a Type 0 font is needed
     */
    public static boolean requiresCompositeFont(Collection<Integer> codePoints) {
        for (int codePoint : codePoints) {
            if (!WinAnsiEncoding.canShow(codePoint)) {
                return true;
            }
        }
        return false;
    }
}
