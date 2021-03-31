/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.fonts;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.zfont.*;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.*;
import org.icepdf.core.util.Library;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.icepdf.core.pobjects.Dictionary.SUBTYPE_KEY;

/**
 * Simple Factory for loading of font library if present.
 */
public class FontFactory {

    private static final Logger logger =
            Logger.getLogger(FontFactory.class.toString());

    public static final int FONT_OPEN_TYPE = 5;
    public static final int FONT_TRUE_TYPE = java.awt.Font.TRUETYPE_FONT;
    public static final int FONT_TYPE_0 = 6;
    public static final int FONT_TYPE_1 = java.awt.Font.TYPE1_FONT;
    public static final int FONT_TYPE_1C = 7;
    public static final int FONT_TYPE_3 = 8;
    public static final int FONT_CID_TYPE_1C = 9;
    public static final int FONT_CID_TYPE_0 = 10;
    public static final int FONT_CID_TYPE_0C = 11;
    public static final int FONT_CID_TYPE_2 = 12;

    // Singleton instance of class
    private static FontFactory fontFactory;

    public static final Name FONT_SUBTYPE_TYPE_0 = new Name("Type0");
    public static final Name FONT_SUBTYPE_TYPE_1 = new Name("Type1");
    public static final Name FONT_SUBTYPE_MM_TYPE_1 = new Name("MMType1");
    public static final Name FONT_SUBTYPE_TYPE_3 = new Name("Type3");
    public static final Name FONT_SUBTYPE_TRUE_TYPE = new Name("TrueType");
    public static final Name FONT_SUBTYPE_CID_FONT_TYPE_0 = new Name("CIDFontType0");
    public static final Name FONT_SUBTYPE_CID_FONT_TYPE_2 = new Name("CIDFontType2");

    /**
     * <p>Returns a static instance of the FontManager class.</p>
     *
     * @return instance of the FontManager.
     */
    public static FontFactory getInstance() {
        // make sure we have initialized the manager
        if (fontFactory == null) {
            fontFactory = new FontFactory();
        }
        return fontFactory;
    }


    private FontFactory() {
    }

    public Font getFont(Library library, HashMap entries) {

        Font font = null;

        Name subtype = library.getName(entries, SUBTYPE_KEY);

        // each type will have a specific instance but it's the dictionary that makes the factory
        // call to build any embedded fonts.

        // simple fonts
        if (FONT_SUBTYPE_TYPE_1.equals(subtype) || FONT_SUBTYPE_MM_TYPE_1.equals(subtype)) {
            // treating type1 and type1c the same for now
            font = new Type1Font(library, entries);
        } else if (FONT_SUBTYPE_TRUE_TYPE.equals(subtype)) {
            font = new TrueTypeFont(library, entries);
        } else if (FONT_SUBTYPE_TYPE_0.equals(subtype)) {
            font = new Type0Font(library, entries);
        } else if (FONT_SUBTYPE_TYPE_3.equals(subtype)) {
            font = new Type3Font(library, entries);
        }
        // composite fonts
        else if (FONT_SUBTYPE_CID_FONT_TYPE_0.equals(subtype)) {
            font = new TypeCidType0Font(library, entries);
        } else if (FONT_SUBTYPE_CID_FONT_TYPE_2.equals(subtype)) {
            font = new TypeCidType2Font(library, entries);
        }
        if (font == null) {
            // create OFont implementation. 
            font = new org.icepdf.core.pobjects.fonts.ofont.Font(library, entries);
        }
        return font;
    }

    public FontFile createFontFile(Stream fontStream, int fontType, Name fontSubType) {
        FontFile fontFile = null;
        try {
            if (FONT_OPEN_TYPE == fontType) {
                fontFile = new ZFontOpenType(fontStream);
            } else if (FONT_TRUE_TYPE == fontType) {
                fontFile = new ZFontTrueType(fontStream);
            } else if (FONT_TYPE_1 == fontType) {
                fontFile = new ZFontType1(fontStream);
            } else if (FONT_TYPE_1C == fontType) {
                fontFile = new ZFontType1C(fontStream);
            } else if (FONT_CID_TYPE_0 == fontType) {
                fontFile = new ZFontType0(fontStream);
            } else if (FONT_CID_TYPE_0C == fontType || FONT_CID_TYPE_1C == fontType) {
                fontFile = new ZFontType0(fontStream);
            } else if (FONT_CID_TYPE_2 == fontType) {
                fontFile = new ZFontType2(fontStream);
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Error reading font file type " + FONT_OPEN_TYPE, e);
        }
        return fontFile;
    }

    public FontFile createFontFile(File file, int fontType, String fontSubType) {
        try {
            return createFontFile(file.toURI().toURL(), fontType, fontSubType);
        } catch (Throwable e) {
            logger.log(Level.FINE, "Could not create instance of font file " + fontType, e);
        }
        return null;
    }

    public FontFile createFontFile(URL url, int fontType, String fontSubType) {
        FontFile fontFile = null;
        try {
            if (FONT_TRUE_TYPE == fontType || FONT_OPEN_TYPE == fontType) {
                fontFile = new ZFontTrueType(url);
            } else if (FONT_TYPE_1 == fontType) {
                fontFile = new ZFontType1(url);
            }
        } catch (Throwable e) {
            // logging and error handling needs to be addressed
            e.printStackTrace();
        }
        return fontFile;
    }

    private String fontTypeToString(int fontType) {

        if (fontType == FONT_OPEN_TYPE) {
            return "Open Type Font";
        } else if (fontType == FONT_TRUE_TYPE) {
            return "True Type Font";
        } else if (fontType == FONT_TYPE_0) {
            return "Type 0 Font";
        } else if (fontType == FONT_TYPE_1) {
            return "Type 1 Font";
        } else if (fontType == FONT_TYPE_3) {
            return "Type 3 Font";
        } else {
            return "unknown font type: " + fontType;
        }
    }

}
