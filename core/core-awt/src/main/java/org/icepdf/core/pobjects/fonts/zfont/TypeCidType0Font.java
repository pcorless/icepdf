package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType0;
import org.icepdf.core.util.Library;

import java.util.HashMap;
import java.util.logging.Logger;

public class TypeCidType0Font extends CompositeFont {

    private static final Logger logger =
            Logger.getLogger(TypeCidType0Font.class.toString());

    public TypeCidType0Font(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public synchronized void init() {
        super.init();
        inited = true;
    }

    protected void parseWidths() {
        super.parseWidths();
        if (font instanceof ZFontType0) {
            if (widths != null || defaultWidth > -1) {
                font = ((ZFontType0) font).deriveFont(defaultWidth, widths);
            } else {
                font = ((ZFontType0) font).deriveFont(1000, null);
            }
        } else {
            // something bad happened font couldn't be loaded.
            logger.warning("Could not derive with because of null Type0CID font.");
        }
    }

    protected void parseCidToGidMap() {
        Object gidMap = library.getObject(entries, CID_TO_GID_MAP_KEY);
        if (gidMap != null) {
            throw new IllegalStateException("CIDToGIDMap should not exist for TypeCidType0Font");
        }
    }
}
