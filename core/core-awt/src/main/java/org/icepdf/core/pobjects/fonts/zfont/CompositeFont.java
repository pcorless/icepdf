package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.util.BoundingBox;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType0;
import org.icepdf.core.util.Library;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class CompositeFont extends Font {

    public static final Name CID_SYSTEM_INFO_KEY = new Name("CIDSystemInfo");
    public static final Name CID_TO_GID_MAP_KEY = new Name("CIDToGIDMap");

    public static final Name DW_KEY = new Name("DW");
    public static final Name W_KEY = new Name("W");

    public static final Name DW2_KEY = new Name("DW2");
    public static final Name W2_KEY = new Name("W2");

    protected String ordering;

    protected final Map<Integer, Float> glyphHeights = new HashMap<>();
    //    private final boolean isEmbedded;
//    private final boolean isDamaged;
    protected AffineTransform fontMatrix;
    protected Float avgWidth = null;
    protected BoundingBox fontBBox;
//    private int[] cid2gid = null;


    public CompositeFont(Library library, HashMap entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        if (inited) {
            return;
        }
        // todo pull from simple and put in font.
        parseFontDescriptor();
        parseCidSystemInfo();
        parseWidths();
    }

    protected abstract void parseCidToGidMap();

    private void parseCidSystemInfo() {
        if (font != null) {
            return;
        }
        // Get CIDSystemInfo dictionary so we can get ordering data
        Object obj = library.getObject(entries, CID_SYSTEM_INFO_KEY);
        if (obj instanceof HashMap) {
            StringObject orderingObject = (StringObject) ((HashMap) obj).get(new Name("Ordering"));
            StringObject registryObject = (StringObject) ((HashMap) obj).get(new Name("Registry"));
            if (orderingObject != null && registryObject != null) {
                ordering = orderingObject.getDecryptedLiteralString(library.getSecurityManager());
                String registry = registryObject.getDecryptedLiteralString(library.getSecurityManager());
                FontManager fontManager = FontManager.getInstance().initialize();
                isFontSubstitution = true;

                // Get flags data if it exists.
                int fontFlags = 0;
                if (fontDescriptor != null) {
                    fontFlags = fontDescriptor.getFlags();
                }

                // find a font and assign a charset.
                // simplified Chinese
                if (ordering.startsWith("GB1") || ordering.startsWith("'CNS1")) {
                    font = fontManager.getChineseSimplifiedInstance(basefont, fontFlags);
                }
                // Korean
                else if (ordering.startsWith("Korea1")) {
                    font = fontManager.getKoreanInstance(basefont, fontFlags);
                }
                // Japanese
                else if (ordering.startsWith("Japan1")) {
                    font = fontManager.getJapaneseInstance(basefont, fontFlags);
                }
                // might be a font loading error a we need check normal system fonts too
                else if (ordering.startsWith("Identity")) {
                    font = fontManager.getInstance(basefont, fontFlags);
                }
                // fallback traditional Chinese.
                else {
                    font = fontManager.getChineseTraditionalInstance(basefont, fontFlags);
                }
                if (font instanceof ZFontTrueType) {
                    // Build a toUnicode table as defined in section 9.10.2.
                    //
                    // a)Map the character code to a character identifier (CID)
                    // according to the font’s CMap.
                    // this is the font encoding
                    // b)Obtain the registry and ordering of the character collection
                    // used by the font’s CMap (for example, Adobe and Japan1) from
                    // its CIDSystemInfo dictionary.
                    // c)Construct a second CMap name by concatenating the registry
                    // and ordering obtained in step (b) in the format
                    // registry–ordering–UCS2 (for example, Adobe–Japan1–UCS2).
                    String ucs2CMapName = registry + '-' + ordering + "-UCS2";
                    // d) Obtain the CMap with the name constructed in step (c)
                    // (available from the ASN Web site; see the Bibliography).

                    // todo complete font cid setup.
//                    CMap ucs2CMap = CMap.getInstance(ucs2CMapName);
                    // e) Map the CID obtained in step (a) according to the CMap
                    // obtained in step (d), producing a Unicode value.
//                    toUnicodeCMap = ucs2CMap;
//                    font = ((NFontTrueType) font).deriveFont(toUnicodeCMap);
                }
            }
        }
    }

    private void parseWidths() {

        float defaultWidth = -1;
        ArrayList individualWidths = null;
        if (library.getObject(entries, W_KEY) != null) {
            individualWidths = (ArrayList) library.getObject(entries, W_KEY);
        }
        if (library.getObject(entries, DW_KEY) != null) {
            defaultWidth =
                    ((Number) library.getObject(entries, DW_KEY)).floatValue();
        }

        if (individualWidths != null || defaultWidth > -1) {
            font = ((ZFontType0) font).deriveFont(defaultWidth, individualWidths);
        } else {
            font = ((ZFontType0) font).deriveFont(1000, null);
        }
    }

}