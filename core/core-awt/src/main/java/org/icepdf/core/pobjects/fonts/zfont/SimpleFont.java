package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.AFM;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory;
import org.icepdf.core.util.FontUtil;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleFont extends org.icepdf.core.pobjects.fonts.Font {

    protected static final Logger logger =
            Logger.getLogger(SimpleFont.class.toString());

    // get list of all available fonts.
    private static final java.awt.Font[] fonts =
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();

    public static final Name TO_UNICODE_KEY = new Name("ToUnicode");
    public static final Name BASE_ENCODING_KEY = new Name("BaseEncoding");

    public static final Name WIDTHS_KEY = new Name("Widths");
    public static final Name FIRST_CHAR_KEY = new Name("FirstChar");
    public static final Name DIFFERENCES_KEY = new Name("Differences");

    // An array of (LastChar ? FirstChar + 1) widths, each element being the
    // glyph width for the character code that equals FirstChar plus the array index.
    // For character codes outside the range FirstChar to LastChar, the value
    // of MissingWidth from the FontDescriptor entry for this font is used.
    protected List widths;

    // Base 14 AFM fonts
    protected AFM afm;

    // Base character mapping of 256 chars
    protected String[] cMap;

    protected Encoding encoding;

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library library of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public SimpleFont(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        if (inited) {
            return;
        }

        parseFontDescriptor();
        findFontIfNotEmbedded();
        parseToUnicode();
        parseEncoding();
        parseWidth();
    }

    protected void findSystemFont() {
        try {
            font = FontManager.getInstance().initialize().getInstance(basefont, getFontFlags());
            isFontSubstitution = true;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE,
                        "Font loading failure, no font available to substitute: " + basefont);
            }
        }
    }

    protected void parseEncoding() {
        // Find any special encoding information, not used very often
        Object encodingValue = library.getObject(entries, ENCODING_KEY);
        if (encodingValue instanceof DictionaryEntries) {
            DictionaryEntries encodingDictionary = (DictionaryEntries) encodingValue;
            Name baseEncoding = library.getName(encodingDictionary, BASE_ENCODING_KEY);
            setBaseEncoding(baseEncoding);

            // not normally found for TrueType fonts
            List differences = (List) library.getObject(encodingDictionary, DIFFERENCES_KEY);
            if (differences != null) {
                int c = 0;
                for (Object oo : differences) {
                    if (c == cMap.length) {
                        break;
                    }
                    if (oo instanceof Number) {
                        c = ((Number) oo).intValue();
                    } else if (oo instanceof Name) {
                        cMap[c] = oo.toString();
                        c++;
                    }
                }
            }
            encoding = new Encoding(encoding, cMap);
        } else if (encodingValue instanceof Name) {
            setBaseEncoding((Name) encodingValue);
        }
        if (toUnicodeCMap == null) {
            if (encoding != null) {
                toUnicodeCMap = GlyphList.guessToUnicode(encoding);
            } else {
                toUnicodeCMap = CMapFactory.getPredefinedCMap(CMapFactory.IDENTITY_H_NAME);
            }
        }
        font = font.deriveFont(encoding, toUnicodeCMap);
    }

    protected void parseWidth() {
        float missingWidth = 0;
        float ascent = 0.0f;
        float descent = 0.0f;
        Rectangle2D bbox = null;
        if (fontDescriptor != null) {
            missingWidth = fontDescriptor.getMissingWidth() / 1000f;
            ascent = fontDescriptor.getAscent() / 1000f;
            descent = fontDescriptor.getDescent() / 1000f;
            bbox = fontDescriptor.getFontBBox();
        }
        widths = (List) library.getObject(entries, WIDTHS_KEY);
        if (widths != null) {
            float[] newWidth = new float[256 - firstchar];
            float widthValue = 0;
            for (int i = 0, max = widths.size(), max2 = newWidth.length; i < max && i < max2; i++) {
                if (widths.get(i) != null) {
                    Object tmp = widths.get(i);
                    if (tmp instanceof Number) {
                        widthValue = ((Number) tmp).floatValue();
                    }
                    // unusual case where the width is a reference to a number, technically not allowed by spec.
                    // sometimes I wonder if encoders do this on purpose.
                    else if (tmp instanceof Reference) {
                        tmp = library.getObject((Reference) tmp);
                        if (tmp instanceof Number) {
                            newWidth[i] = ((Number) tmp).floatValue();
                        } else {
                            logger.warning("Error reading width value, expected number but found: " + tmp);
                            throw new IllegalStateException("Error reading width value, expected number but found: " + tmp);
                        }
                    }
                    newWidth[i] = widthValue / 1000f;
                }
            }
            font = font.deriveFont(newWidth, firstchar, missingWidth, ascent, descent, bbox, null);
        }
        // currently not using afm, instead using font's width table, seems more reliable
        else if (afm != null && !isFontSubstitution) {
            font = font.deriveFont(afm.getWidths(), firstchar, missingWidth, ascent, descent, bbox, null);
        } else if (bbox != null) {
            font = font.deriveFont(new float[0], firstchar, missingWidth, ascent, descent, bbox, null);
        }
    }

    protected void setBaseEncoding(Name baseEncoding) {

        // todo some of these rules are wrong according the spec
        if (baseEncoding != null) {
            encoding = Encoding.getInstance(baseEncoding.getName());
        } else if (!isFontSubstitution && font.getEncoding() != null) {
            encoding = new Encoding(font.getEncoding());
        } else if (basefont == null) {
            encoding = Encoding.standardEncoding;
        } else if (basefont.startsWith("ZapfD")) {
            encoding = Encoding.zapfDingBats;
        } else if (basefont.startsWith("Symbol")) {
            encoding = Encoding.symbolEncoding;
        }
        if (encoding == null) {
            encoding = Encoding.standardEncoding;
        }
        // initiate encoding cMap.
        cMap = new String[256];
        for (char i = 0; i < 256; i++) {
            cMap[i] = encoding.getName(i);
        }
    }

    protected void findFontIfNotEmbedded() {
        if (font == null) {
            findSystemFont();
        }
    }

    protected void parseToUnicode() {
        Object objectUnicode = library.getObject(entries, TO_UNICODE_KEY);
        if (objectUnicode instanceof Stream) {
            Stream cMapStream = (Stream) objectUnicode;
            try {
                toUnicodeCMap = CMapFactory.parseEmbeddedCMap(cMapStream);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error reading CMap file.", e);
            }
        }
        else if (objectUnicode instanceof Name) {
            Name unicodeName = (Name) objectUnicode;
            logger.warning("found unicodeName " + unicodeName);
            if (CMapFactory.IDENTITY_NAME.equals(unicodeName)) {
                toUnicodeCMap = CMapFactory.getPredefinedCMap(CMapFactory.IDENTITY_H_NAME);
            } else if (CMapFactory.IDENTITY_V_NAME.equals(unicodeName)) {
                toUnicodeCMap = CMapFactory.getPredefinedCMap(CMapFactory.IDENTITY_V_NAME);
            } else if (CMapFactory.IDENTITY_H_NAME.equals(unicodeName)) {
                toUnicodeCMap = CMapFactory.getPredefinedCMap(CMapFactory.IDENTITY_H_NAME);
            }
        }
    }

    protected int getFontFlags() {
        int fontFlags = 0;
        if (fontDescriptor != null) {
            fontFlags = fontDescriptor.getFlags();
        }
        return fontFlags;
    }

    protected String cleanFontName(String fontName) {

        // crystal report encoding specific, this will have to made more
        // robust when more examples are found.
        fontName = FontUtil.removeBaseFontSubset(fontName);

        // strip commas from basefont name and replace with dashes
        if (subtype != null && (subtype.equals("Type0")
                || subtype.equals("Type1")
                || subtype.equals("MMType1")
                || subtype.equals("TrueType"))) {
            if (fontName != null) {
                // normalize so that java.awt.decode will work correctly
                fontName = fontName.replace(',', '-');
            }
        }
        return fontName;
    }

}
