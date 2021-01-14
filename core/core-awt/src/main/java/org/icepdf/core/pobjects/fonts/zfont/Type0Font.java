package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.fonts.ofont.CMap;
import org.icepdf.core.util.Library;

import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

// todo
public class Type0Font extends org.icepdf.core.pobjects.fonts.Font {

    private static final Logger logger =
            Logger.getLogger(SimpleFont.class.toString());

    public static final Name DESCENDANT_FONTS_KEY = new Name("DescendantFonts");

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
        parseEncoding();

        inited = true;
    }

    private void parseEncoding() {
        Object encoding = library.getName(entries, ENCODING_KEY);
        if (encoding instanceof Name) {
            cMap = CMap.getInstance((Name) encoding);
            // todo clean up encoding and fix fon substitution
            font = font.deriveFont(null, cMap);
        }
        if (cMap != null) {
            isCMapPredefined = true;
        }
    }

    private void parseDescendantFont() {
        if (entries.containsKey(DESCENDANT_FONTS_KEY)) {
            Object descendant = library.getObject(entries, DESCENDANT_FONTS_KEY);
            if (descendant != null && descendant instanceof List) {
                List descendantFonts = (List) descendant;
                CompositeFont descendantFont = null;
                Object descendantFontObject = descendantFonts.get(0);
                if (descendantFontObject instanceof Reference) {
                    Reference descendantFontReference = (Reference) descendantFontObject;
                    descendantFont = (CompositeFont) library.getObject(descendantFontReference);
                } else if (descendantFontObject instanceof CompositeFont) {
                    descendantFont = (CompositeFont) descendantFontObject;
                }

                if (descendantFont != null) {
//                    if (toUnicodeCMap != null) {
//                        descendantFont.toUnicodeCMap = toUnicodeCMap;
//                    }
                    descendantFont.init();
                    font = descendantFont.getFont();
//                    toUnicodeCMap = descendantFont.toUnicodeCMap;
////                        charset = descendantFont.charset;
//                    // we have a type0 cid font  which we need to setup some
//                    // special mapping
//                    if (descendantFont.isFontSubstitution &&
//                            toUnicodeCMap != null &&
//                            font instanceof ZFontTrueType) {
//                        // get the encoding mapping
//                        Object cmap = library.getObject(entries, ENCODING_KEY);
//                        // try and load the cmap from the international jar.
//                        if (cmap != null && cmap instanceof Name) {
//                            CMap encodingCMap = CMap.getInstance(cmap.toString());
//                            ((ZFontTrueType) font).applyCidCMap(encodingCMap);
//                        }
//                    }
//                    if (!descendantFont.isFontSubstitution) {
//                        isEmbedded = true;
//                    }
                }
            }
        }
    }

}
