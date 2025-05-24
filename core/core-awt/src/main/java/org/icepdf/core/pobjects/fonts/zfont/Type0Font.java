package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.cmap.CMap;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory;
import org.icepdf.core.util.Library;

import java.util.List;
import java.util.logging.Logger;

public class Type0Font extends SimpleFont {

    private static final Logger logger =
            Logger.getLogger(SimpleFont.class.toString());

    public static final Name DESCENDANT_FONTS_KEY = new Name("DescendantFonts");

    private CMap cMap;

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library Library of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public Type0Font(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @Override
    public synchronized void init() {
        if (inited) {
            return;
        }

        findFontIfNotEmbedded();
        parseToUnicode();
        parseEncoding();
        parseDescendantFont();

        inited = true;
    }

    protected void parseEncoding() {
        Name name = library.getName(entries, ENCODING_KEY);
        if (name != null) {
            cMap = CMapFactory.getPredefinedCMap(name);
            Encoding encoding = Encoding.getInstance((name).getName());
            font = font.deriveFont(encoding, toUnicodeCMap != null ? toUnicodeCMap : font.getToUnicode());
            return;
        }
        Object object = library.getObject(entries, ENCODING_KEY);
        if (object instanceof Stream) {
            Stream gidMap = (Stream) object;
            Name cmapName = library.getName(gidMap.getEntries(), new Name("CMapName"));
            // update font with oneByte information from the cmap, so far I've only
            // scene this on a handful of CID font but fix encoding issue in each case.
            if (cmapName.equals("OneByteIdentityH")) {
                subTypeFormat = SIMPLE_FORMAT;
            }
            return;
        }
    }

    private void parseDescendantFont() {
        if (entries.containsKey(DESCENDANT_FONTS_KEY)) {
            Object descendant = library.getObject(entries, DESCENDANT_FONTS_KEY);
            if (descendant instanceof List) {
                List descendantFonts = (List) descendant;
                CompositeFont descendantFont = null;
                Object descendantFontObject = descendantFonts.get(0);
                if (descendantFontObject instanceof Reference) {
                    Reference descendantFontReference = (Reference) descendantFontObject;
                    descendantFontObject = library.getObject(descendantFontReference);
                }

                if (descendantFontObject instanceof CompositeFont) {
                    descendantFont = (CompositeFont) descendantFontObject;
                }
                // strange malformed PDFe where the descendant font is a font descriptor,
                else if (descendantFontObject instanceof FontDescriptor) {
                    fontDescriptor = (FontDescriptor) descendantFontObject;
                    parseFontDescriptor();
                }

                if (descendantFont != null) {
                    descendantFont.init();
                    font = descendantFont.getFont();
                    font = font.deriveFont(encoding, cMap);
                    isFontSubstitution = descendantFont.isFontSubstitution() && font != null;
                }
            }
        }
    }

}
