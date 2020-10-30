package org.icepdf.core.pobjects.fonts.zfont;

import org.icepdf.core.io.SeekableInput;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.AFM;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.util.FontUtil;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

// todo likely will become the base class replacing the abstract pobject.font class
public class Font extends org.icepdf.core.pobjects.fonts.Font {

    private static final Logger logger =
            Logger.getLogger(Font.class.toString());

    public static final Name FONT_DESCRIPTOR_KEY = new Name("FontDescriptor");
    public static final Name TO_UNICODE_KEY = new Name("ToUnicode");
    public static final Name BASE_ENCODING_KEY = new Name("BaseEncoding");

    public static final Name WIDTHS_KEY = new Name("Widths");
    public static final Name FIRST_CHAR_KEY = new Name("FirstChar");
    public static final Name DIFFERENCES_KEY = new Name("Differences");

    // todo simple font props

    // An array of (LastChar ? FirstChar + 1) widths, each element being the
    // glyph width for the character code that equals FirstChar plus the array index.
    // For character codes outside the range FirstChar to LastChar, the value
    // of MissingWidth from the FontDescriptor entry for this font is used.
    protected List widths;

    // Base character mapping of 256 chars
    protected String[] cMap;

    protected Encoding encoding;

    /**
     * Creates a new instance of a PDF Font.
     *
     * @param library library of all objects in PDF
     * @param entries hash of parsed font attributes
     */
    public Font(Library library, HashMap entries) {
        super(library, entries);
        // todo pull in base class dictionary init.
    }

    @Override
    public void init() {
        if (inited) {
            return;
        }

        parseFontDescriptor();
        parseToUnicode();
        parseEncoding();
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
        font = font.deriveFont(encoding, null);
    }

    protected void setBaseEncoding(Name baseEncoding) {

        // todo some of these rules are wrong according the spec
        if (baseEncoding != null) {
            encoding = Encoding.getInstance(baseEncoding.getName());
        } else if (!isFontSubstitution) {
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

    protected void parseFontDescriptor() {
        // Assign the font descriptor
        Object of = library.getObject(entries, FONT_DESCRIPTOR_KEY);
        if (of instanceof FontDescriptor) {
            fontDescriptor = (FontDescriptor) of;
        }
        // encase of missing the type entry so we
        else if (of instanceof HashMap) {
            fontDescriptor = new FontDescriptor(library, (HashMap) of);
        }
        if (fontDescriptor != null) {
            fontDescriptor.init();
            if (fontDescriptor.getEmbeddedFont() != null) {
                font = fontDescriptor.getEmbeddedFont();
                isFontSubstitution = false;
            }
        }
        // If there is no FontDescriptor then we most likely have a core afm
        if (fontDescriptor == null && basefont != null) {
            AFM fontMetrix = AFM.AFMs.get(basefont.toLowerCase());
            if (fontMetrix != null) {
                fontDescriptor = FontDescriptor.createDescriptor(library, fontMetrix);
                fontDescriptor.init();
            }
        }

    }

    protected void parseToUnicode() {
        Object objectUnicode = library.getObject(entries, TO_UNICODE_KEY);
        if (objectUnicode instanceof Stream) {
            Stream cMapStream = (Stream) objectUnicode;
            InputStream cMapInputStream = cMapStream.getDecodedByteArrayInputStream();
            try {
                // todo move into cmap class.
                if (logger.isLoggable(Level.FINER)) {
                    String content;
                    if (cMapInputStream instanceof SeekableInput) {
                        content = Utils.getContentFromSeekableInput((SeekableInput) cMapInputStream, false);
                    } else {
                        InputStream[] inArray = new InputStream[]{cMapInputStream};
                        content = Utils.getContentAndReplaceInputStream(inArray, false);
                        cMapInputStream = inArray[0];
                    }
                    logger.finer("ToUnicode CMAP = " + content);
                }

                // try and load the cmap stream
//                new CMap(library, new HashMap(), (Stream) cMapStream);
            } catch (Throwable e) {
                logger.log(Level.SEVERE, "Error reading cmap file.", e);
            } finally {
                try {
                    // close the stream
                    if (cMapInputStream != null)
                        cMapInputStream.close();
                } catch (IOException e) {
                    logger.log(Level.FINE, "CMap Reading/Parsing Error.", e);
                }
            }
        }
        // todo handle toUnicode maps that are name based.
        else if (objectUnicode instanceof Name) {
            Name unicodeName = (Name) objectUnicode;
            logger.warning("found unicodeName " + unicodeName);
//            if (CMap.CMapNames.identity.equals(unicodeName)) {
//                toUnicodeCMap = CMap.IDENTITY;
//            } else if (CMap.CMapNames.identityV.equals(unicodeName)) {
//                toUnicodeCMap = CMap.IDENTITY_V;
//            } else if (CMap.CMapNames.identityH.equals(unicodeName)) {
//                toUnicodeCMap = CMap.IDENTITY_H;
//            }
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

        // crystal report ecoding specific, this will have to made more
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
