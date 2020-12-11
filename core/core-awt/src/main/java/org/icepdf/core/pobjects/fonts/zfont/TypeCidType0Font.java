package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType0;
import org.icepdf.core.util.Library;

import java.util.HashMap;

public class TypeCidType0Font extends CompositeFont {
    public TypeCidType0Font(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        super.init();
        parseCidToGidMap();
        inited = true;
    }

    protected void parseWidths() {
        super.parseWidths();
        if (widths != null || defaultWidth > -1) {
            font = ((ZFontType0) font).deriveFont(defaultWidth, widths);
        } else {
            font = ((ZFontType0) font).deriveFont(1000, null);
        }
    }

    protected void parseCidToGidMap() {
        Object gidMap = library.getObject(entries, CID_TO_GID_MAP_KEY);
        System.out.println();
//        if (subtype.equals("CIDFontType0") && font instanceof ZFontOpenType && (isEmbedded || gidMap != null)) {
//            font = ((ZFontOpenType) font).deriveFont(CMap.IDENTITY, toUnicodeCMap);
//        }
    }
}
