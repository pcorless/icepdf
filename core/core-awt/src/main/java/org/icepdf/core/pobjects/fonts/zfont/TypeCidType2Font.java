package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.ofont.CMap;
import org.icepdf.core.pobjects.fonts.ofont.CMapIdentityH;
import org.icepdf.core.pobjects.fonts.ofont.CMapReverse;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType2;
import org.icepdf.core.util.Library;

import java.util.HashMap;
import java.util.logging.Logger;

public class TypeCidType2Font extends CompositeFont {

    private static final Logger logger =
            Logger.getLogger(TypeCidType2Font.class.toString());

    public TypeCidType2Font(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public synchronized void init() {
        super.init();
        if (!(font instanceof ZFontType2)) {
            font = new ZFontType2((ZFontTrueType) font);
        }
        parseCidToGidMap();
        inited = true;
    }

    protected void parseWidths() {
        super.parseWidths();
        if (font instanceof ZFontType2) {
            if (widths != null || defaultWidth > -1) {
                font = ((ZFontType2) font).deriveFont(defaultWidth, widths);
            } else {
                font = ((ZFontType2) font).deriveFont(1000, null);
            }
        } else {
            // something bad happened font couldn't be loaded.
        }
    }

    protected void parseCidToGidMap() {
        Object gidMap = library.getObject(entries, CID_TO_GID_MAP_KEY);

        // ordering != null && ordering.startsWith("Identity")) || ((gidMap != null || !isFontSubstitution)
//        if (!isFontSubstitution) {
//            CMap subfontToUnicodeCMap = toUnicodeCMap != null ? toUnicodeCMap : CMap.IDENTITY;
        if (gidMap == null) {
//                throw new Exception("null CID_TO_GID_MAP_KEY " + gidMap);
//                font = ((ZFontTrueType) font).deriveFont(CMap.IDENTITY, null);// subfontToUnicodeCMap);
        }
        if (gidMap instanceof Name) {
            String mappingName = null;
            mappingName = gidMap.toString();
            if (toUnicodeCMap instanceof CMapIdentityH) {
                mappingName = toUnicodeCMap.toString();
            }
            // mapping name will be null only in a few corner cases, but
            // identity will be applied otherwise.
            if (mappingName == null || mappingName.equals("Identity")) {
                // subfontToUnicodeCMap
                font = ((ZFontType2) font).deriveFont(CMap.IDENTITY, toUnicodeCMap);
            }
        } else if (gidMap instanceof Stream) {
            int[] cidToGidMap = CMap.parseCidToGidMap((Stream) gidMap);
            CMap cidGidMap = new CMapReverse(cidToGidMap);
            if (font instanceof ZFontType2) {
                font = ((ZFontType2) font).deriveFont(cidGidMap, toUnicodeCMap);
            }
        }
//        }
    }
}