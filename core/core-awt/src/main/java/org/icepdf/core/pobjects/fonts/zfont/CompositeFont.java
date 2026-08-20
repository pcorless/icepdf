/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.core.pobjects.fonts.zfont;

import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.util.BoundingBox;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StringObject;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontType2;
import org.icepdf.core.util.Library;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class CompositeFont extends SimpleFont {

    public static final Name CID_SYSTEM_INFO_KEY = new Name("CIDSystemInfo");
    public static final Name CID_TO_GID_MAP_KEY = new Name("CIDToGIDMap");

    public static final Name CID_SYSTEM_INFO_ORDERING_KEY = new Name("Ordering");
    public static final Name CID_SYSTEM_INFO_REGISTRY_KEY = new Name("Registry");
    public static final Name CID_SYSTEM_INFO_SUPPLEMENT_KEY = new Name("Supplement");

    public static final Name DW_KEY = new Name("DW");
    public static final Name W_KEY = new Name("W");

    public static final Name DW2_KEY = new Name("DW2");
    public static final Name W2_KEY = new Name("W2");

    protected String ordering;
    protected String registry;
    /** Lazily resolved CID&rarr;Unicode map for the CIDSystemInfo character collection; null when the
     *  collection has none.  See {@link #getUcs2CMap()}. */
    private CMap ucs2CMap;
    private boolean ucs2CMapResolved;

    protected final Map<Integer, Float> glyphHeights = new HashMap<>();
    protected BoundingBox fontBBox;

    protected float defaultWidth = 1.0f;
    protected float[] widths = null;


    public CompositeFont(Library library, DictionaryEntries entries) {
        super(library, entries);
    }

    @Override
    public void init() {
        super.init();
        if (inited) {
            return;
        }
        parseFontDescriptor();
        findFontIfNotEmbedded();
        parseCidSystemInfo();
        parseWidths();
    }

    protected abstract void parseCidToGidMap() throws IOException;

    /**
     * Reads the descendant font's {@code CIDSystemInfo}: the character collection its CIDs belong to.
     * Always read, whether or not the font is embedded &mdash; an embedded font still needs the
     * collection to map its CIDs to Unicode for extraction when it carries no {@code /ToUnicode}
     * (see {@link #getUcs2CMap()}).  Only the font <em>substitution</em> that follows is conditional.
     */
    protected void parseCidSystemInfo() {
        Object obj = library.getObject(entries, CID_SYSTEM_INFO_KEY);
        if (obj instanceof DictionaryEntries) {
            DictionaryEntries cidSystemInfo = (DictionaryEntries) obj;
            // /Registry and /Ordering may be indirect, so they have to be resolved rather than read
            // straight out of the dictionary - a raw get() hands back the Reference itself.  This
            // runs for embedded fonts too now, so a cast failure here aborts Font.init() and the
            // whole text block draws with no font at all.
            String orderingValue = literalStringOf(cidSystemInfo, CID_SYSTEM_INFO_ORDERING_KEY);
            String registryValue = literalStringOf(cidSystemInfo, CID_SYSTEM_INFO_REGISTRY_KEY);
            if (orderingValue != null && registryValue != null) {
                ordering = orderingValue;
                registry = registryValue;
            }
        }
        substituteFontForOrdering();
    }

    /**
     * Reads one {@code CIDSystemInfo} entry as a string, resolving an indirect reference and
     * tolerating a producer that wrote a name where the spec calls for a string.
     */
    private String literalStringOf(DictionaryEntries cidSystemInfo, Name key) {
        Object value = library.getObject(cidSystemInfo, key);
        if (value instanceof StringObject) {
            return ((StringObject) value).getDecryptedLiteralString(library.getSecurityManager());
        }
        if (value instanceof Name) {
            return value.toString();
        }
        return null;
    }

    /**
     * Picks a system font for a CID font that has no usable embedded font program, and wires the
     * character collection's CMaps into it.  A no-op once a real embedded font is in hand.
     */
    protected void substituteFontForOrdering() {
        if (font != null && !isFontSubstitution) {
            return;
        }
        Object obj = library.getObject(entries, CID_SYSTEM_INFO_KEY);
        if (obj instanceof DictionaryEntries) {
            // resolved, not read straight out of the dictionary: /Supplement may be indirect, and a
            // producer may write it as a real number rather than an integer
            Object supplementValue = library.getObject((DictionaryEntries) obj, CID_SYSTEM_INFO_SUPPLEMENT_KEY);
            int supplement = supplementValue instanceof Number ? ((Number) supplementValue).intValue() : 0;
            if (ordering != null && registry != null) {
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
                    // the substitute is very nearly always a TrueType, but a system font list that
                    // offers only a Type1 face must not take the whole font down with a cast error
                    if (font instanceof ZFontTrueType) {
                        font = new ZFontType2((ZFontTrueType) font);
                    }
                }
                // fallback traditional Chinese.
                else {
                    font = fontManager.getChineseTraditionalInstance(basefont, fontFlags);
                }
                // substitution will almost be trueType font
                if (font instanceof ZFontTrueType) {
                    try {
                        // Build a toUnicode table as defined in section 9.10.2.
                        // b)Obtain the registry and ordering of the character collection
                        // used by the font’s CMap (for example, Adobe and Japan1) from
                        // its CIDSystemInfo dictionary.
                        String cmapName = null;
                        if (registry.equalsIgnoreCase("adobe") &&
                                (ordering.equalsIgnoreCase("japan1") ||
                                        ordering.equalsIgnoreCase("GB1") ||
                                        ordering.equalsIgnoreCase("CNS1") ||
                                        ordering.equalsIgnoreCase("Korea1"))) {
                            cmapName = registry + '-' + ordering + '-' + supplement;
                        } else if (encodingName != null) {
                            cmapName = encodingName.getName();
                        }
                        if (cmapName != null) {
                            CMap cidSystemCmap = CMapFactory.getPredefinedCMap(cmapName);
                            // c)Construct a second CMap name by concatenating the registry
                            // and ordering obtained in step (b) in the format
                            // registry–ordering–UCS2 (for example, Adobe–Japan1–UCS2).
                            String ucs2CMapName = registry + '-' + cidSystemCmap.getOrdering() + "-UCS2";
                            // d) Obtain the CMap with the name constructed in step (c)
                            CMap ucs2CMap = CMapFactory.getPredefinedCMap(ucs2CMapName);
                            if (!(font instanceof ZFontType2)) {
                                ZFontType2 zFontType2 = new ZFontType2((ZFontTrueType) font);
                                zFontType2.setUcs2Cmap(ucs2CMap);
                                zFontType2.setIsCidSubstituted();
                                font = zFontType2;
                                font = font.deriveFont(encoding, toUnicodeCMap);
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Error creating CMap for font: " + basefont);
                    }
                }
            }
        }
    }

    /**
     * The CID&rarr;Unicode map for this font's character collection, used to build a
     * {@code /ToUnicode} equivalent when the font supplies none (PDF 32000-1 9.10.2 (b)&ndash;(d):
     * take the registry and ordering from {@code CIDSystemInfo}, then load
     * {@code <Registry>-<Ordering>-UCS2}).
     * <p>
     * Returns null for the {@code Identity} ordering, which is not a character collection at all
     * &mdash; its CIDs are the embedded font's own glyph indices and carry no Unicode meaning, so
     * there is no {@code Adobe-Identity-UCS2} to load.  Such a font can only be extracted with a
     * {@code /ToUnicode} CMap.
     *
     * @return the collection's UCS2 CMap, or null if it has none
     */
    public CMap getUcs2CMap() {
        if (!ucs2CMapResolved) {
            ucs2CMapResolved = true;
            if (registry != null && ordering != null && !ordering.startsWith("Identity")) {
                String name = registry + '-' + ordering + "-UCS2";
                try {
                    ucs2CMap = CMapFactory.getPredefinedCMap(name);
                } catch (Exception e) {
                    logger.warning("Could not load the character collection's CMap " + name);
                }
                if (ucs2CMap == null) {
                    logger.fine(() -> "No UCS2 CMap for character collection " + registry + '-' + ordering);
                }
            }
        }
        return ucs2CMap;
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
                // the c [w1 w2 ...] group's width array may be given as an
                // indirect reference rather than an inline array.
                if (currentNext instanceof Reference) {
                    currentNext = library.getObject(currentNext);
                }
                if (currentNext instanceof ArrayList) {
                    ArrayList widths2 = (ArrayList) currentNext;
                    Object tmp;
                    Number width;
                    for (int j = 0, max2 = widths2.size(); j < max2; j++) {
                        tmp = widths2.get(j);
                        if (tmp instanceof Number) {
                            width = (Number) widths2.get(j);
                        } else {
                            // one off where the array isn't all numbers.
                            width = (Number) library.getObject(tmp);
                        }
                        widths[current + j] = (float) (width).intValue() * 0.001f;
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
            if (currentNext instanceof Reference) {
                currentNext = library.getObject(currentNext);
            }
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