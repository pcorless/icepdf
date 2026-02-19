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
            return new TrueTypeFontBuilder(library, fontFileSubSetter).build();
        }
        // fall back on simple Type1 font, if embedding is not available
        else {
            return new Type1FontBuilder(library, fontName).Build();
        }
    }
}


