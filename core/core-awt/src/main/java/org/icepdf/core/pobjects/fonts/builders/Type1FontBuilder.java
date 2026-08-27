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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_TYPE_1;
import static org.icepdf.core.pobjects.fonts.zfont.SimpleFont.TO_UNICODE_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory.IDENTITY_NAME;

public class Type1FontBuilder {

    private static final Logger LOGGER = Logger.getLogger(Type1FontBuilder.class.toString());

    /** WinAnsiEncoding's printable range, which is what this font declares. */
    private static final int FIRST_CHAR = 32;
    private static final int LAST_CHAR = 255;

    private Library library;
    private String fontName;

    public Type1FontBuilder(Library library, String fontName) {
        this.library = library;
        this.fontName = fontName;
    }

    public Type1Font Build() {
        // This font is not embedded, and PDF/A requires that every font is.  The path is taken when
        // the face cannot be embedded - a licence that forbids it, or embedding turned off - so the
        // choice is this or no text at all; but a document that reaches here will not validate, and
        // silence about that is how it gets discovered by a validator instead of by us.
        LOGGER.warning("Falling back to a non-embedded Type 1 font for '" + fontName
                + "'; the document will not conform to PDF/A, which requires embedded fonts");

        DictionaryEntries fontDictionary = new DictionaryEntries();
        // /Type /Font.  This used to put the *name* "Subtype" in as the value of /Type, which is
        // not a type any reader knows.
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY,
                org.icepdf.core.pobjects.fonts.Font.TYPE);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, FONT_SUBTYPE_TYPE_1);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.NAME_KEY, new Name(fontName));
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY, new Name("WinAnsiEncoding"));
        fontDictionary.put(new Name("FirstChar"), FIRST_CHAR);
        fontDictionary.put(new Name("LastChar"), LAST_CHAR);
        List<Integer> codes = new ArrayList<>();
        for (int code = FIRST_CHAR; code <= LAST_CHAR; code++) {
            codes.add(code);
        }
        fontDictionary.put(TO_UNICODE_KEY, ToUnicodeCMap.forCodes(library, codes));

        // build out min core14 properties.
        Type1Font font = new Type1Font(library, fontDictionary);
        font.setPObjectReference(library.getStateManager().getNewReferenceNumber());
        library.getStateManager().addTempChange(new PObject(font, font.getPObjectReference()));
        return font;
    }
}
