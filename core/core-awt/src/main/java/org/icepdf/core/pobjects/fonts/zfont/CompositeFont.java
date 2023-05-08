package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.util.BoundingBox;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType2;
import org.icepdf.core.util.Library;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class CompositeFont extends SimpleFont {

    public static final Name CID_SYSTEM_INFO_KEY = new Name("CIDSystemInfo");
    public static final Name CID_TO_GID_MAP_KEY = new Name("CIDToGIDMap");

    public static final Name DW_KEY = new Name("DW");
    public static final Name W_KEY = new Name("W");

    public static final Name DW2_KEY = new Name("DW2");
    public static final Name W2_KEY = new Name("W2");

    protected String ordering;

    protected final Map<Integer, Float> glyphHeights = new HashMap<>();
    protected BoundingBox fontBBox;

    protected float defaultWidth = 1.0f;
    protected float[] widths = null;


    public CompositeFont(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        if (inited) {
            return;
        }
        parseFontDescriptor();
        findFontIfNotEmbedded();
        parseCidSystemInfo();
        parseWidths();
    }

    protected abstract void parseCidToGidMap();

    protected void parseCidSystemInfo() {
        if (font != null) {
            return;
        }
        // Get CIDSystemInfo dictionary so we can get ordering data
        Object obj = library.getObject(entries, CID_SYSTEM_INFO_KEY);
        if (obj instanceof DictionaryEntries) {
            StringObject orderingObject = (StringObject) ((DictionaryEntries) obj).get(new Name("Ordering"));
            StringObject registryObject = (StringObject) ((DictionaryEntries) obj).get(new Name("Registry"));
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
                    font = new ZFontType2((ZFontTrueType) font);
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

                    // todo complete font cid setup in future Non-roman font fix release
                    //  finish implementation once 7.0 is released.
//                    CMap ucs2CMap = CMap.getInstance(new Name(ucs2CMapName));
                    // e) Map the CID obtained in step (a) according to the CMap
                    // obtained in step (d), producing a Unicode value.
//                    toUnicodeCMap = ucs2CMap;
//                    font = ((ZFontTrueType) font).deriveFont(toUnicodeCMap);
                }
            }
        }
    }

    protected void parseWidths() {

        if (library.getObject(entries, W_KEY) != null) {
            ArrayList individualWidths = (ArrayList) library.getObject(entries, W_KEY);
            int maxLength = calculateWidthLength(individualWidths);
            widths = new float[maxLength];
            int current;
            Object currentNext;
            for (int i = 0, max = individualWidths.size() - 1; i < max; i++) {
                current = ((Number) individualWidths.get(i)).intValue();
                currentNext = individualWidths.get(i + 1);
                if (currentNext instanceof ArrayList) {
                    ArrayList widths2 = (ArrayList) currentNext;
                    for (int j = 0, max2 = widths2.size(); j < max2; j++) {
                        widths[current + j] = (float) (((Number) widths2.get(j)).intValue()) * 0.001f;
                    }
                    i++;
                } else if (currentNext instanceof Number) {
                    int currentEnd = ((Number) currentNext).intValue();
                    Object tmp = individualWidths.get(i + 2);
                    float width2;
                    if (tmp instanceof Number) {
                        width2 = (float) (((Number) tmp).intValue());
                    } else if (tmp instanceof Reference) {
                        tmp = library.getObject(tmp);
                        width2 = (float) (((Number) tmp).intValue());
                    } else {
                        width2 = 1.0f;
                    }
                    for (; current <= currentEnd; current++) {
                        widths[current] = width2 * 0.001f;
                    }
                    i += 2;
                }
            }
        }
        if (library.getObject(entries, DW_KEY) != null) {
            defaultWidth =
                    ((Number) library.getObject(entries, DW_KEY)).floatValue() * 0.001f;
        }

        if (fontDescriptor != null) {
            float missingWidth = fontDescriptor.getMissingWidth() / 1000f;
            float ascent = fontDescriptor.getAscent() / 1000f;
            float descent = fontDescriptor.getDescent() / 1000f;
            Rectangle2D bbox = fontDescriptor.getFontBBox();
            // firstCh not a concept in cid fonts
            font = font.deriveFont(widths, 0, missingWidth, ascent, descent, bbox, null);
        }


    }

    private int calculateWidthLength(ArrayList widths) {
        int current;
        Object currentNext;
        int maxGlph = 0;
        for (int i = 0, max = widths.size() - 1; i < max; i++) {
            current = ((Number) widths.get(i)).intValue();
            currentNext = widths.get(i + 1);
            if (currentNext instanceof ArrayList) {
                ArrayList widths2 = (ArrayList) currentNext;
                int newMax = current + widths2.size();
                maxGlph = Math.max(newMax, maxGlph);
                i++;
            } else if (currentNext instanceof Number) {
                int newMax = ((Number) currentNext).intValue();
                maxGlph = Math.max(newMax, maxGlph);
                i += 2;
            }
        }
        return maxGlph + 1;
    }


    public BoundingBox getFontBBox() {
        return fontBBox;
    }
}