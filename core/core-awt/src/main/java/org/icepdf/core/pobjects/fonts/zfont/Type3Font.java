package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType3;
import org.icepdf.core.util.Library;

public class Type3Font extends SimpleFont {

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library Library of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public Type3Font(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @Override
    public synchronized void init() {
        if (inited) {
            return;
        }
        font = new ZFontType3(library, entries);
        ((ZFontType3) font).setParentResource(parentResource);
        super.init();
        inited = true;
    }
}
