package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType2;
import org.icepdf.core.util.Library;

import java.util.logging.Logger;

public class TypeCidType2Font extends CompositeFont {

    private static final Logger logger =
            Logger.getLogger(TypeCidType2Font.class.toString());

    public TypeCidType2Font(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @Override
    public synchronized void init() {
        super.init();
        if (!(font instanceof ZFontType2) && font instanceof ZFontTrueType) {
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
        if (gidMap instanceof Stream) {
            int[] cidToGidMap = CMapFactory.parseCidToGidMap((Stream) gidMap);
            if (font instanceof ZFontType2) {
                font = ((ZFontType2) font).deriveFont(cidToGidMap, toUnicodeCMap);
            }
        }
    }
}