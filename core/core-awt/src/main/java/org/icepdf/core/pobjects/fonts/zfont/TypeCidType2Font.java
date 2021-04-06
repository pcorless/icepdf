package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.CMap;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapIdentityH;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapReverse;
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
            logger.warning("Could not derive with because of null Type2CID font.");
        }
    }

    protected void parseCidToGidMap() {
        Object gidMap = library.getObject(entries, CID_TO_GID_MAP_KEY);
        if (gidMap == null && !isFontSubstitution) {
            CMap subfontToUnicodeCMap = toUnicodeCMap != null ? toUnicodeCMap : org.icepdf.core.pobjects.fonts.zfont.cmap.CMap.IDENTITY;
            font = ((ZFontType2) font).deriveFont(org.icepdf.core.pobjects.fonts.zfont.cmap.CMap.IDENTITY, subfontToUnicodeCMap);
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
                font = ((ZFontType2) font).deriveFont(org.icepdf.core.pobjects.fonts.zfont.cmap.CMap.IDENTITY, toUnicodeCMap);
            }
        } else if (gidMap instanceof Stream) {
            int[] cidToGidMap = org.icepdf.core.pobjects.fonts.zfont.cmap.CMap.parseCidToGidMap((Stream) gidMap);
            CMap cidGidMap = new CMapReverse(cidToGidMap);
            if (font instanceof ZFontType2) {
                font = ((ZFontType2) font).deriveFont(cidGidMap, toUnicodeCMap);
            }
        }
    }
}