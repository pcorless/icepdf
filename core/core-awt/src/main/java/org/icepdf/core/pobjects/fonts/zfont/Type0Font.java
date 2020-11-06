package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Name;

import java.util.logging.Logger;

public class Type0Font {

    private static final Logger logger =
            Logger.getLogger(SimpleFont.class.toString());


    public static final Name DESCENDANT_FONTS_KEY = new Name("DescendantFonts");
    public static final Name CID_SYSTEM_INFO_KEY = new Name("CIDSystemInfo");
    public static final Name CID_TO_GID_MAP_KEY = new Name("CIDToGIDMap");

    public static final Name DW_KEY = new Name("DW");
    public static final Name W_KEY = new Name("W");
}
