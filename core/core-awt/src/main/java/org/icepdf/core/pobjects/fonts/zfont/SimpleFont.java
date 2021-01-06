package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.AFM;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.ofont.CMap;
import org.icepdf.core.pobjects.fonts.ofont.OFont;
import org.icepdf.core.util.FontUtil;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

// todo likely will become the base class replacing the abstract pobject.font class
public class SimpleFont extends org.icepdf.core.pobjects.fonts.Font {

    private static final Logger logger =
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
    public SimpleFont(Library library, HashMap entries) {
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
        if (encodingValue instanceof HashMap) {
            HashMap encodingDictionary = (HashMap) encodingValue;
            Name baseEncoding = library.getName(encodingDictionary, BASE_ENCODING_KEY);
            setBaseEncoding(baseEncoding);

            // not normally found for TrueType fonts
            List differences = (List) library.getObject(encodingDictionary, DIFFERENCES_KEY);
            if (differences != null) {
                int c = 0;
                for (Object oo : differences) {
                    if (c == cMap.length - 1) {
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
        } else {
            encoding = Encoding.standardEncoding;
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
            for (int i = 0, max = widths.size(), max2 = newWidth.length; i < max && i < max2; i++) {
                if (widths.get(i) != null) {
                    newWidth[i] = ((Number) widths.get(i)).floatValue() / 1000f;
                }
            }
            font = font.deriveFont(newWidth, firstchar, missingWidth, ascent, descent, bbox, null);
        } else if (afm != null) {
            font = font.deriveFont(afm.getWidths(), firstchar, missingWidth, ascent, descent, bbox, null);
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

    // todo temp method to get a font,  eventually replace with fontManager selection
    //  or some hybrid
    protected void findFontIfNotEmbedded() {
        if (font == null) {

            findSystemFont();
            if (font != null) {
                return;
            }

            java.awt.Font awtFont;
            // get font style value.
            int style = FontUtil.guessAWTFontStyle(basefont);
            // look at all PS font names and try and find a match
            if (font == null && basefont != null) {
                // Check to see if any of the system fonts match the basefont name
                for (java.awt.Font font1 : fonts) {

                    // remove white space
                    StringTokenizer st = new StringTokenizer(font1.getPSName(), " ", false);
                    StringBuilder fontName = new StringBuilder();
                    while (st.hasMoreElements()) fontName.append(st.nextElement());

                    // if a match is found assign it as the real font
                    if (fontName.toString().equalsIgnoreCase(basefont)) {
                        awtFont = new java.awt.Font(font1.getFamily(), style, 1);
                        font = new OFont(awtFont);
                        basefont = font1.getPSName();
                        isFontSubstitution = true;
                        break;
                    }
                }
            }

            // look at font family name matches against system fonts
            if (font == null && basefont != null) {

                // clean the base name so that is has just the font family
                String fontFamily = FontUtil.guessFamily(basefont);

                for (java.awt.Font font1 : fonts) {
                    // find font family match
                    if (FontUtil.normalizeString(
                            font1.getFamily()).equalsIgnoreCase(fontFamily)) {
                        // create new font with font family name and style
                        awtFont = new java.awt.Font(font1.getFamily(), style, 1);
                        font = new OFont(awtFont);
                        basefont = font1.getFontName();
                        isFontSubstitution = true;
                        break;
                    }
                }
            }
            // if still null, shouldn't be, assigned the basefont name
            // todo, nice to cut in the font subsituttion, fontManger
            if (font == null) {
                try {
                    awtFont = new java.awt.Font(basefont, style, 12);
                    font = new OFont(java.awt.Font.getFont(basefont, awtFont));
                    basefont = font.getName();
                } catch (Exception e) {
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.warning("Error creating awt.font for: " + entries);
                    }
                }
            }
            // If the font substitutions failed then we want to try and pick the proper
            // font family based on what the font name best matches up with none
            // font family font names.  if all else fails use serif as it is the most'
            // common font.
            if (!isFontSubstitution && font == null &&
                    !font.getName().toLowerCase().contains(font.getFamily().toLowerCase())) {
                // see if we working with a sans serif font
                if ((font.getName().toLowerCase().contains("times new roman") ||
                        font.getName().toLowerCase().contains("timesnewroman") ||
                        font.getName().toLowerCase().contains("bodoni") ||
                        font.getName().toLowerCase().contains("garamond") ||
                        font.getName().toLowerCase().contains("minion web") ||
                        font.getName().toLowerCase().contains("stone serif") ||
                        font.getName().toLowerCase().contains("stoneserif") ||
                        font.getName().toLowerCase().contains("georgia") ||
                        font.getName().toLowerCase().contains("bitstream cyberbit"))) {
                    awtFont = new java.awt.Font("serif", font.getStyle(), (int) font.getSize());
                    font = new OFont(awtFont);
                    basefont = "serif";
                }
                // see if we working with a monospaced font
                else if ((font.getName().toLowerCase().contains("helvetica") ||
                        font.getName().toLowerCase().contains("arial") ||
                        font.getName().toLowerCase().contains("trebuchet") ||
                        font.getName().toLowerCase().contains("avant garde gothic") ||
                        font.getName().toLowerCase().contains("avantgardegothic") ||
                        font.getName().toLowerCase().contains("verdana") ||
                        font.getName().toLowerCase().contains("univers") ||
                        font.getName().toLowerCase().contains("futura") ||
                        font.getName().toLowerCase().contains("stone sans") ||
                        font.getName().toLowerCase().contains("stonesans") ||
                        font.getName().toLowerCase().contains("gill sans") ||
                        font.getName().toLowerCase().contains("gillsans") ||
                        font.getName().toLowerCase().contains("akzidenz") ||
                        font.getName().toLowerCase().contains("grotesk"))) {
                    awtFont = new java.awt.Font("sansserif", font.getStyle(), (int) font.getSize());
                    font = new OFont(awtFont);
                    basefont = "sansserif";
                }
                // see if we working with a mono spaced font
                else if ((font.getName().toLowerCase().contains("courier") ||
                        font.getName().toLowerCase().contains("courier new") ||
                        font.getName().toLowerCase().contains("couriernew") ||
                        font.getName().toLowerCase().contains("prestige") ||
                        font.getName().toLowerCase().contains("eversonmono") ||
                        font.getName().toLowerCase().contains("Everson Mono"))) {
                    awtFont = new java.awt.Font("monospaced", font.getStyle(), (int) font.getSize());
                    font = new OFont(awtFont);
                    basefont = "monospaced";
                }
                // if all else fails go with the serif as it is the most common font family
                else {
                    awtFont = new java.awt.Font("serif", font.getStyle(), (int) font.getSize());
                    font = new OFont(awtFont);
                    basefont = "serif";
                }
            }
            // finally if we have an empty font then we default to serif so that
            // we can try and render the character codes.
            if (font == null) {
                awtFont = new java.awt.Font("serif", style, 12);
                font = new OFont(awtFont);
                basefont = "serif";
            }
        }
    }

    protected void parseToUnicode() {
        Object objectUnicode = library.getObject(entries, TO_UNICODE_KEY);
        if (objectUnicode instanceof Stream) {
            Stream cMapStream = (Stream) objectUnicode;
            try {
                toUnicodeCMap = new CMap(cMapStream);
                toUnicodeCMap.init();
            } catch (Throwable e) {
                logger.log(Level.SEVERE, "Error reading CMap file.", e);
            }
        }
        else if (objectUnicode instanceof Name) {
            Name unicodeName = (Name) objectUnicode;
            logger.warning("found unicodeName " + unicodeName);
            if (CMap.IDENTITY_NAME.equals(unicodeName)) {
                toUnicodeCMap = CMap.IDENTITY;
            } else if (CMap.IDENTITY_V_NAME.equals(unicodeName)) {
                toUnicodeCMap = CMap.IDENTITY_V;
            } else if (CMap.IDENTITY_H_NAME.equals(unicodeName)) {
                toUnicodeCMap = CMap.IDENTITY_H;
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
