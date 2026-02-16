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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.fonts.zfont.Type1Font;
import org.icepdf.core.util.Library;

import static org.icepdf.core.pobjects.fonts.Font.TYPE;
import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_TYPE_1;
import static org.icepdf.core.pobjects.fonts.zfont.SimpleFont.TO_UNICODE_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory.IDENTITY_NAME;

public class Type1FontBuilder {

    private Library library;
    private String fontName;

    public Type1FontBuilder(Library library, String fontName) {
        this.library = library;
        this.fontName = fontName;
    }

    public Type1Font Build() {

        DictionaryEntries fontDictionary = new DictionaryEntries();
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY, TYPE);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, FONT_SUBTYPE_TYPE_1);

        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY, new Name("WinAnsiEncoding"));
        fontDictionary.put(TO_UNICODE_KEY, IDENTITY_NAME);

        // build out minima core14 properties.

        Type1Font font = new Type1Font(library, fontDictionary);
        font.setPObjectReference(library.getStateManager().getNewReferenceNumber());
        library.getStateManager().addTempChange(new PObject(font, font.getPObjectReference()));
        return font;
    }
}
