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

        // initialize cMap array with base characters


        // todo pull over ofont width and encoding, so we can start using new object for encoding
        //  and hook up differences correctly

        // todo put setEncoding and setWidth in the base class?

//        font

        // setup encoding

        // setup widths
    }

    /**
     * Utility method for setting the widths for a particular font given the
     * specified encoding.
     */
    private void setWidth() {
        float missingWidth = 0;
        float ascent = 0.0f;
        float descent = 0.0f;
        if (fontDescriptor != null) {
            if (fontDescriptor.getMissingWidth() > 0) {
                missingWidth = fontDescriptor.getMissingWidth() / 1000f;
                ascent = fontDescriptor.getAscent() / 1000f;
                descent = fontDescriptor.getDescent() / 1000f;
            }
        }
        if (widths != null) {
            float[] newWidth = new float[256 - firstchar];
            for (int i = 0, max = widths.size(); i < max; i++) {
                if (widths.get(i) != null) {
                    newWidth[i] = ((Number) widths.get(i)).floatValue() / 1000f;
                }
            }
            // todo creat new encoding out of cMap.
//            font = font.deriveFont(newWidth, firstchar, missingWidth, ascent, descent, cMap);
        }
//        if (isAFMFont) {
//            font = font.deriveFont(afm.getWidths(), firstchar, missingWidth, ascent, descent, cMap);
//        }

    }
}
