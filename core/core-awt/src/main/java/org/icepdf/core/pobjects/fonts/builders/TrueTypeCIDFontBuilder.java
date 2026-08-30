/*
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
package org.icepdf.core.pobjects.fonts.builders;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.HorizontalMetricsTable;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.pobjects.fonts.zfont.Type0Font;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY;
import static org.icepdf.core.pobjects.fonts.Font.FONT_DESCRIPTOR_KEY;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.CID_SET;
import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_CID_FONT_TYPE_2;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.CID_SYSTEM_INFO_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.CID_SYSTEM_INFO_ORDERING_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.CID_SYSTEM_INFO_REGISTRY_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.CID_SYSTEM_INFO_SUPPLEMENT_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.CID_TO_GID_MAP_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.DW_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.W_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.SimpleFont.TO_UNICODE_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.Type0Font.DESCENDANT_FONTS_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory.IDENTITY_H_NAME;

/**
 * Builds a composite (Type 0) font with an embedded CIDFontType2 descendant.
 * <p>
 * This is the path for text a simple font cannot show. A simple font's codes are one byte, so it can
 * only ever reach 256 glyphs, and {@code /WinAnsiEncoding} narrows that to what Windows-1252 defines
 * - no CJK, no Greek, no Cyrillic. A composite font addresses glyphs by CID instead, two bytes wide
 * under {@code Identity-H}, so what it can show is limited only by the font programme.
 * <p>
 * The CID is the glyph index in the <em>original</em> font, and {@code /CIDToGIDMap} maps it to the
 * index the same glyph ended up at in the subset. That indirection is what lets the subset drop
 * glyphs without renumbering anything the content stream refers to.
 * <p>
 * This class is based on
 * <a href="https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/TrueTypeEmbedder.java">PDFBox's TrueTypeEmbedder</a>.
 *
 * @author Keiji Suzuki
 * @author John Hewson
 */
public class TrueTypeCIDFontBuilder extends FontBuilder {

    /**
     * A CID with no entry in /W is this wide (PDF 32000-1, 9.7.4.3).
     */
    private static final int DEFAULT_WIDTH = 1000;

    public TrueTypeCIDFontBuilder(Library library, TrueTypeFontEmbedder fontFileSubSetter) {
        super(library, fontFileSubSetter);
    }

    public SimpleFont build() {
        // double check we have an embedded font available for the font name
        if (!(FontFactory.useEmbeddedFonts || fontFileSubSetter.isFontEmbeddable())) {
            throw new IllegalStateException("Font embedding not supported or font is not embeddable.");
        }

        try {
            fontFileSubSetter.createSubsetFont();

            String subsetFontName = fontFileSubSetter.getSubsetTag() + fontFileSubSetter.getFontFile().getName();

            createFontDescriptor(subsetFontName);

            // The subsetter reports which glyph in the original font each glyph in the subset came
            // from.  A CID is the original index, so this is the CID -> subset GID map, which is
            // what /CIDToGIDMap has to contain.
            TreeMap<Integer, Integer> cidToGid = new TreeMap<>();
            fontFileSubSetter.getGidToCid().forEach((subsetGid, originalGid) -> cidToGid.put(originalGid, subsetGid));

            DictionaryEntries descendantFont = createDescendantFont(subsetFontName, cidToGid);
            createType0Font(subsetFontName, descendantFont);

        } catch (IOException e) {
            throw new RuntimeException("Failed create font subset", e);
        }

        return simpleFont;
    }

    /**
     * The parent font: a Type 0 that says how codes in the content stream are read, and points at the
     * descendant that owns the glyphs.  It carries no /FontDescriptor and no /Widths of its own -
     * those belong to the descendant, and both used to be written here because this reused the simple
     * font's dictionary wholesale.
     */
    private void createType0Font(String fontName, DictionaryEntries descendantFont) {
        StateManager stateManager = library.getStateManager();

        Reference descendantReference = stateManager.getNewReferenceNumber();
        stateManager.addTempChange(new PObject(descendantFont, descendantReference));

        DictionaryEntries fontDictionary = new DictionaryEntries();
        fontDictionary.put(Font.TYPE_KEY, Font.TYPE);
        fontDictionary.put(Font.SUBTYPE_KEY, FontFactory.FONT_SUBTYPE_TYPE_0);
        fontDictionary.put(BASEFONT_KEY, new Name(fontName));
        // Identity-H: the code in the content stream is the CID, two bytes, horizontal writing.
        fontDictionary.put(Font.ENCODING_KEY, IDENTITY_H_NAME);
        fontDictionary.put(DESCENDANT_FONTS_KEY, List.of(descendantReference));
        fontDictionary.put(TO_UNICODE_KEY, createCidToUnicodeStream());

        simpleFont = new Type0Font(library, fontDictionary);
        simpleFont.setPObjectReference(stateManager.getNewReferenceNumber());
        stateManager.addTempChange(new PObject(simpleFont, simpleFont.getPObjectReference()));
    }

    private DictionaryEntries createDescendantFont(String fontName, TreeMap<Integer, Integer> cidToGid)
            throws IOException {
        DictionaryEntries cidFont = new DictionaryEntries();
        cidFont.put(Font.TYPE_KEY, Font.TYPE);
        cidFont.put(Font.SUBTYPE_KEY, FONT_SUBTYPE_CID_FONT_TYPE_2);
        cidFont.put(BASEFONT_KEY, new Name(fontName));
        cidFont.put(CID_SYSTEM_INFO_KEY, toCIDSystemInfo("Adobe", "Identity", 0));
        cidFont.put(FONT_DESCRIPTOR_KEY, fontDescriptor.getPObjectReference());
        cidFont.put(DW_KEY, DEFAULT_WIDTH);
        cidFont.put(W_KEY, buildWidths(cidToGid));
        cidFont.put(CID_TO_GID_MAP_KEY, buildCIDToGIDMap(cidToGid));
        fontDescriptor.getEntries().put(CID_SET, buildCIDSet(cidToGid));
        return cidFont;
    }

    private DictionaryEntries toCIDSystemInfo(String registry, String ordering, int supplement) {
        DictionaryEntries cidSystemInfo = new DictionaryEntries();
        cidSystemInfo.put(CID_SYSTEM_INFO_REGISTRY_KEY, registry);
        cidSystemInfo.put(CID_SYSTEM_INFO_ORDERING_KEY, ordering);
        cidSystemInfo.put(CID_SYSTEM_INFO_SUPPLEMENT_KEY, supplement);
        return cidSystemInfo;
    }

    /**
     * {@code /W} in its run form: {@code c [w1 w2 ... wn]}, one run per stretch of consecutive CIDs.
     * A CID whose width is the default is left out, which is what /DW is for.
     */
    private List<Object> buildWidths(TreeMap<Integer, Integer> cidToGid) throws IOException {
        org.apache.fontbox.ttf.TrueTypeFont trueTypeFontFile = fontFileSubSetter.getFontFile().getTrueTypeFont();
        float scaling = 1000f / trueTypeFontFile.getHeader().getUnitsPerEm();
        HorizontalMetricsTable horizontalMetrics = trueTypeFontFile.getHorizontalMetrics();

        List<Object> widths = new ArrayList<>();
        List<Object> run = null;
        int previousCid = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> entry : cidToGid.entrySet()) {
            int cid = entry.getKey();
            // The advance is read at the glyph's index in the *original* font: the subset's own hmtx
            // is indexed by the new glyph index, and reading one with the other silently returns some
            // other glyph's width.
            long width = Math.round(horizontalMetrics.getAdvanceWidth(cid) * scaling);
            if (width == DEFAULT_WIDTH) {
                continue;
            }
            if (run == null || previousCid != cid - 1) {
                run = new ArrayList<>();
                widths.add(cid);
                widths.add(run);
            }
            run.add(width);
            previousCid = cid;
        }
        return widths;
    }

    /**
     * {@code /CIDToGIDMap} as a stream: two bytes per CID from 0 to the highest one used, giving the
     * glyph index that CID ended up at in the subset, and 0 - the notdef glyph - for the CIDs in
     * between that the subset dropped.
     * <p>
     * This used to build that array and then write the <em>font programme</em> to the stream instead,
     * so every CID resolved through whatever bytes of the TTF happened to sit at its offset.
     */
    private Reference buildCIDToGIDMap(TreeMap<Integer, Integer> cidToGid) {
        int cidMax = cidToGid.lastKey();
        byte[] buffer = new byte[cidMax * 2 + 2];
        for (int cid = 0; cid <= cidMax; cid++) {
            Integer gid = cidToGid.get(cid);
            if (gid != null) {
                buffer[cid * 2] = (byte) (gid >> 8 & 0xff);
                buffer[cid * 2 + 1] = (byte) (gid & 0xff);
            }
        }
        return addStream(buffer);
    }

    /**
     * {@code /CIDSet}, required of an embedded CIDFont subset by PDF/A: one bit per CID, saying which
     * the font programme contains.
     * <p>
     * Every bit from 0 to the highest CID is set, which claims CIDs the subset does not actually have.
     * Setting only the CIDs it does have reads truer, and PDF/A-2 6.2.11.4.2 is worded as though that
     * is what it wants - "shall identify all CIDs which are present in the font program" - but
     * veraPDF rejects the exact set and accepts the contiguous one, and PDFBox writes the contiguous
     * one too. Measured, not assumed: the exact set fails 6.2.11.4.2-2 at PDF/A-2a, 2b, 2u and 3b,
     * and indexing by subset glyph index instead of CID fails it as well. The validator is the thing
     * this has to satisfy, so it wins over the reading of the sentence.
     * <p>
     * The stream used to be written with the font programme's bytes rather than these ones, which is
     * the fault that mattered.
     */
    private Reference buildCIDSet(TreeMap<Integer, Integer> cidToGid) {
        int cidMax = cidToGid.lastKey();
        byte[] bytes = new byte[cidMax / 8 + 1];
        for (int cid = 0; cid <= cidMax; cid++) {
            bytes[cid / 8] |= 1 << (7 - cid % 8);
        }
        return addStream(bytes);
    }

    /**
     * The parent's {@code /ToUnicode}, which is what text extraction and search read.  It is keyed by
     * the code in the content stream - the CID, under Identity-H - so it is built from the same
     * Unicode-to-glyph lookup the CIDs themselves come from.
     */
    private Reference createCidToUnicodeStream() {
        Map<Integer, Integer> cidToUnicode = new TreeMap<>();
        try {
            CmapLookup cmapLookup = fontFileSubSetter.getFontFile().getTrueTypeFont().getUnicodeCmapLookup();
            for (int codePoint : fontFileSubSetter.getSubsetCodePoints()) {
                int cid = cmapLookup.getGlyphId(codePoint);
                if (cid > 0) {
                    cidToUnicode.put(cid, codePoint);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the font's character map", e);
        }
        return ToUnicodeCMap.forCids(library, cidToUnicode);
    }

    private Reference addStream(byte[] bytes) {
        StateManager stateManager = library.getStateManager();
        Reference reference = stateManager.getNewReferenceNumber();
        Stream stream = Stream.createStream(library, bytes);
        stream.setPObjectReference(reference);
        stateManager.addTempChange(new PObject(stream, reference));
        return reference;
    }
}
