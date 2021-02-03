package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.fonts.ofont.CMap;
import org.icepdf.core.util.Library;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

public class Type0Font extends SimpleFont {

    private static final Logger logger =
            Logger.getLogger(SimpleFont.class.toString());

    public static final Name DESCENDANT_FONTS_KEY = new Name("DescendantFonts");

    private CompositeFont descendantFont;
    private CMap cMap;
    private boolean isCMapPredefined;

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library Library of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public Type0Font(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        if (inited) {
            return;
        }

        parseDescendantFont();
        findFontIfNotEmbedded();
        parseEncoding();

        inited = true;
    }

    protected void parseEncoding() {
        Object encoding = library.getName(entries, ENCODING_KEY);
        if (encoding instanceof Name) {
            cMap = CMap.getInstance((Name) encoding);
            // todo clean up encoding and fix font substitution
            font = font.deriveFont(null, cMap);
        }
        if (cMap != null) {
            isCMapPredefined = true;
        }
    }

    private void parseDescendantFont() {
        if (entries.containsKey(DESCENDANT_FONTS_KEY)) {
            Object descendant = library.getObject(entries, DESCENDANT_FONTS_KEY);
            if (descendant instanceof List) {
                List descendantFonts = (List) descendant;
                descendantFont = null;
                Object descendantFontObject = descendantFonts.get(0);
                if (descendantFontObject instanceof Reference) {
                    Reference descendantFontReference = (Reference) descendantFontObject;
                    descendantFont = (CompositeFont) library.getObject(descendantFontReference);
                } else if (descendantFontObject instanceof CompositeFont) {
                    descendantFont = (CompositeFont) descendantFontObject;
                }

                if (descendantFont != null) {
                    descendantFont.init();
                    font = descendantFont.getFont();
                    isFontSubstitution = descendantFont.isFontSubstitution() && font != null;
                }
            }
        }
    }

}
