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
package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.fonts.AFM;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory;
import org.icepdf.core.util.Library;
public class Type1Font extends SimpleFont {

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library Libaray of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public Type1Font(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @Override
    public synchronized void init() {
        // handle afm
        if (inited) {
            return;
        }
        AFM a = AFM.AFMs.get(basefont.toLowerCase());
        if (a != null && a.getFontName() != null) {
            afm = a;
            if (afm.getWidths() != null) {
                isAFMFont = true;
            }
        }
        super.init();

        if (encoding == null) {
            encoding = Encoding.standardEncoding;
            font = font.deriveFont(encoding, toUnicodeCMap);
            if (toUnicodeCMap == null) {
                if (encoding != null) {
                    toUnicodeCMap = GlyphList.guessToUnicode(encoding);
                } else {
                    toUnicodeCMap = CMapFactory.getPredefinedCMap(CMapFactory.IDENTITY_H_NAME);
                }
            }
        }

        inited = true;
    }
}
