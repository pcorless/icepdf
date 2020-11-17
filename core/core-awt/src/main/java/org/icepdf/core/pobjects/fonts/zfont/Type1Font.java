package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.util.Library;

import java.util.HashMap;

public class Type1Font extends SimpleFont {

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library Libaray of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public Type1Font(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        super.init();
        inited = true;
    }
}
