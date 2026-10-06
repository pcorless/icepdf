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

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.HorizontalMetricsTable;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.*;


public class TrueTypeFontBuilder extends FontBuilder {

    public TrueTypeFontBuilder(Library library, TrueTypeFontEmbedder fontFileSubSetter) {
        super(library, fontFileSubSetter);
    }

    public SimpleFont build() {
        // double check we have an embedded font available for the font name
        if (!(FontFactory.useEmbeddedFonts || fontFileSubSetter.isFontEmbeddable())) {
            throw new IllegalStateException("Font embedding not supported or font is not embeddable.");
        }

        // generate the subset font,
        try {
            fontFileSubSetter.createSubsetFont();

            String subsetTag = fontFileSubSetter.getSubsetTag();
            String subsetFontName = subsetTag + fontFileSubSetter.getFontFile().getName();

            // build the descriptor
            createFontDescriptor(subsetFontName);

            // build type 0 parent font
            createSimpleFontFile(subsetFontName);

            // descendant CIDFont
            int[] codePoints =
                    fontFileSubSetter.getSubsetCodePoints().stream().sorted().mapToInt(Integer::intValue).toArray();
            setWidths(simpleFont.getEntries(), codePoints);

        } catch (IOException e) {
            throw new RuntimeException("Failed create font subset", e);
        }

        return simpleFont;
    }

    private void setWidths(DictionaryEntries fontDictionary, int[] codePoints) throws IOException {
        if (codePoints.length == 0) {
            return;
        }
        org.apache.fontbox.ttf.TrueTypeFont trueTypeFontFile = fontFileSubSetter.getFontFile().getTrueTypeFont();
        float scaling = 1000f / trueTypeFontFile.getHeader().getUnitsPerEm();
        HorizontalMetricsTable hmtx = trueTypeFontFile.getHorizontalMetrics();

        // codePoints are Unicode; /Widths is indexed by character code, and the font declares
        // WinAnsiEncoding, so the two are only the same below 0x80.  Indexing by the Unicode value
        // gave a left double quote /LastChar 8220 and an 8156-entry array for a font whose codes
        // cannot exceed 255 - invalid for a simple font, and wrong for every code above 0x7F.
        int firstChar = -1;
        int lastChar = -1;
        for (int codePoint : codePoints) {
            int code = WinAnsiEncoding.codeOf(codePoint);
            if (code < 0) {
                continue;
            }
            firstChar = firstChar < 0 ? code : Math.min(firstChar, code);
            lastChar = Math.max(lastChar, code);
        }
        if (firstChar < 0) {
            return;
        }

        List<Integer> widths = new ArrayList<>(lastChar - firstChar + 1);
        for (int i = 0; i <= lastChar - firstChar; i++) {
            widths.add(0);
        }

        CmapLookup cmapLookup = trueTypeFontFile.getUnicodeCmapLookup();
        for (int codePoint : codePoints) {
            int code = WinAnsiEncoding.codeOf(codePoint);
            if (code < 0) {
                continue;
            }
            // the glyph is still found by Unicode - that is what the font's cmap is keyed by
            int gid = cmapLookup.getGlyphId(codePoint);
            widths.set(code - firstChar, Math.round(hmtx.getAdvanceWidth(gid) * scaling));
        }

        fontDictionary.put(FIRST_CHAR_KEY, firstChar);
        fontDictionary.put(LAST_CHAR_KEY, lastChar);
        fontDictionary.put(WIDTHS_KEY, widths);
    }

}
