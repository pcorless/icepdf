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

import org.apache.fontbox.ttf.HorizontalMetricsTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.icepdf.core.pobjects.Resources.FONT_KEY;
import static org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.CID_SET;
import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_CID_FONT_TYPE_2;
import static org.icepdf.core.pobjects.fonts.zfont.CompositeFont.*;
import static org.icepdf.core.pobjects.fonts.zfont.Type0Font.DESCENDANT_FONTS_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory.IDENTITY_NAME;

/**
 * Helper class to for building TrueTypeCIDFont font dictionaries. Will circle back to this implementation
 * when someone requests cid font support which will require significant refactoring of the current font implementation
 * to support the additional complexity of CID fonts and respective content text string.
 * <p>
 * This class is based on
 * <a href="https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/TrueTypeEmbedder.java"></a>
 *
 * @author Keiji Suzuki
 * @author John Hewson
 */
public class TrueTypeCIDFontBuilder extends FontBuilder {

    enum State {
        FIRST, BRACKET, SERIAL
    }

    public TrueTypeCIDFontBuilder(Library library, TrueTypeFontEmbedder fontFileSubSetter) {
        super(library, fontFileSubSetter);
    }

    public SimpleFont build() {
        // double check we have an embedded font available for the font name
        if (!(FontFactory.useEmbeddedFonts || fontFileSubSetter.isFontEmbeddable())) {
            throw new IllegalStateException("Font embedding not supported or font is not embeddable.");
        }

        // generate the subset font,
        try {
            fontFileSubSetter.createSubsetFont();

            String subsetTag = fontFileSubSetter.getSubsetTag();
            String subsetFontName = subsetTag + fontFileSubSetter.getFontFile().getName();

            // build the descriptor
            createFontDescriptor(subsetFontName);

            // build type 0 parent font
            createSimpleFontFile(subsetFontName);

            // descendant CIDFont
            DictionaryEntries cidFontDictionary = createCIDFont();

            // build CID2GIDMap, because the content stream has been written with the old GIDs
            Map<Integer, Integer> gidToCid = fontFileSubSetter.getGidToCid();
            TreeMap<Integer, Integer> cidToGid = new TreeMap<>();
            gidToCid.forEach((newGID, oldGID) -> cidToGid.put(oldGID, newGID));

            // add remaining CIDFont entries
            buildWidths(cidFontDictionary, cidToGid);
            buildCIDToGIDMap(cidFontDictionary, cidToGid);
            buildCIDSet(cidToGid);

        } catch (IOException e) {
            throw new RuntimeException("Failed create font subset", e);
        }

        return simpleFont;
    }

    private DictionaryEntries createCIDFont() throws IOException {

        DictionaryEntries cidFontDictionary = new DictionaryEntries();
        cidFontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY, FONT_KEY);
        cidFontDictionary.put(Font.SUBTYPE_KEY, FONT_SUBTYPE_CID_FONT_TYPE_2);
        cidFontDictionary.put(BASEFONT_KEY, fontDescriptor.getFontName());
        cidFontDictionary.put(CID_SYSTEM_INFO_KEY, toCIDSystemInfo("Adobe", "Identity", 0));
        cidFontDictionary.put(FONT_DESCRIPTOR_KEY, fontDescriptor.getPObjectReference());
        cidFontDictionary.put(CID_TO_GID_MAP_KEY, IDENTITY_NAME);

        buildWidths(cidFontDictionary);

        simpleFont.getEntries().put(DESCENDANT_FONTS_KEY, List.of(cidFontDictionary));
        return cidFontDictionary;
    }

    private DictionaryEntries toCIDSystemInfo(String registry, String ordering, int supplement) {
        DictionaryEntries cidSystemInfo = new DictionaryEntries();
        cidSystemInfo.put(CID_SYSTEM_INFO_REGISTRY_KEY, registry);
        cidSystemInfo.put(CID_SYSTEM_INFO_ORDERING_KEY, ordering);
        cidSystemInfo.put(CID_SYSTEM_INFO_SUPPLEMENT_KEY, supplement);
        return cidSystemInfo;
    }

    /**
     * Build widths with Identity CIDToGIDMap (for embedding full font).
     */
    private void buildWidths(DictionaryEntries cidFontDictionary) throws IOException {
        TTFParser ttfParser = new TTFParser(true);
        org.apache.fontbox.ttf.TrueTypeFont trueTypeFontFile =
                ttfParser.parse(new RandomAccessReadBuffer(fontFileSubSetter.getSubsetFontData()));

        int cidMax = trueTypeFontFile.getNumberOfGlyphs();
        int[] gidWidths = new int[cidMax * 2];
        HorizontalMetricsTable horizontalMetricsTable = trueTypeFontFile.getHorizontalMetrics();
        for (int cid = 0; cid < cidMax; cid++) {
            gidWidths[cid * 2] = cid;
            gidWidths[cid * 2 + 1] = horizontalMetricsTable.getAdvanceWidth(cid);
        }
        cidFontDictionary.put(W_KEY, getWidths(gidWidths));
    }

    /**
     * Builds widths with a custom CIDToGIDMap (for embedding font subset).
     */
    private void buildWidths(DictionaryEntries cidFontDictionary, TreeMap<Integer, Integer> cidToGid) throws IOException {
        org.apache.fontbox.ttf.TrueTypeFont trueTypeFontFile = fontFileSubSetter.getFontFile().getTrueTypeFont();
        float scaling = 1000f / trueTypeFontFile.getHeader().getUnitsPerEm();

        ArrayList widths = new ArrayList<>();
        ArrayList ws = new ArrayList<>();
        int prev = Integer.MIN_VALUE;
        // Use a sorted list to get an optimal width array
        HorizontalMetricsTable horizontalMetricsTable = trueTypeFontFile.getHorizontalMetrics();
        for (Map.Entry<Integer, Integer> entry : cidToGid.entrySet()) {
            int cid = entry.getKey();
            int gid = entry.getValue();
            long width = Math.round(horizontalMetricsTable.getAdvanceWidth(gid) * scaling);
            if (width == 1000) {
                // skip default width
                continue;
            }
            // c [w1 w2 ... wn]
            if (prev != cid - 1) {
                ws = new ArrayList();
                widths.add(cid); // c
                widths.add(ws);
            }
            ws.add(width); // wi
            prev = cid;
        }
        cidFontDictionary.put(W_KEY, widths);
    }

    private ArrayList getWidths(int[] widths) throws IOException {
        if (widths.length < 2) {
            throw new IllegalArgumentException("length of widths must be >= 2");
        }

        org.apache.fontbox.ttf.TrueTypeFont trueTypeFontFile = fontFileSubSetter.getFontFile().getTrueTypeFont();
        float scaling = 1000f / trueTypeFontFile.getHeader().getUnitsPerEm();

        long lastCid = widths[0];
        long lastValue = Math.round(widths[1] * scaling);

        ArrayList inner = new ArrayList<>();
        ArrayList outer = new ArrayList<>();
        outer.add(lastCid);

        State state = State.FIRST;

        for (int i = 2; i < widths.length - 1; i += 2) {
            long cid = widths[i];
            long value = Math.round(widths[i + 1] * scaling);

            switch (state) {
                case FIRST:
                    if (cid == lastCid + 1 && value == lastValue) {
                        state = State.SERIAL;
                    } else if (cid == lastCid + 1) {
                        state = State.BRACKET;
                        inner = new ArrayList();
                        inner.add(lastValue);
                    } else {
                        inner = new ArrayList();
                        inner.add(lastValue);
                        outer.add(inner);
                        outer.add(cid);
                    }
                    break;
                case BRACKET:
                    if (cid == lastCid + 1 && value == lastValue) {
                        state = State.SERIAL;
                        outer.add(inner);
                        outer.add(lastCid);
                    } else if (cid == lastCid + 1) {
                        inner.add(lastValue);
                    } else {
                        state = State.FIRST;
                        inner.add(lastValue);
                        outer.add(inner);
                        outer.add(cid);
                    }
                    break;
                case SERIAL:
                    if (cid != lastCid + 1 || value != lastValue) {
                        outer.add(lastCid);
                        outer.add(lastValue);
                        outer.add(cid);
                        state = State.FIRST;
                    }
                    break;
            }
            lastValue = value;
            lastCid = cid;
        }

        switch (state) {
            case FIRST:
                inner = new ArrayList();
                inner.add(lastValue);
                outer.add(inner);
                break;
            case BRACKET:
                inner.add(lastValue);
                outer.add(inner);
                break;
            case SERIAL:
                outer.add(lastCid);
                outer.add(lastValue);
                break;
        }
        return outer;
    }

    private void buildCIDToGIDMap(DictionaryEntries cidFontDictionary, TreeMap<Integer, Integer> cidToGid) throws IOException {
        int cidMax = cidToGid.lastKey();
        byte[] buffer = new byte[cidMax * 2 + 2];
        int bi = 0;
        for (int i = 0; i <= cidMax; i++) {
            Integer gid = cidToGid.get(i);
            if (gid != null) {
                buffer[bi] = (byte) (gid >> 8 & 0xff);
                buffer[bi + 1] = (byte) (gid & 0xff);
            }
            // else keep 0 initialization
            bi += 2;
        }

        StateManager stateManager = library.getStateManager();
        Reference cidToGidReference = stateManager.getNewReferenceNumber();
        // setup CIDToGIDMap stream as external reference
        Stream cidToGidStream = Stream.createStream(library, fontFileSubSetter.getSubsetFontData());
        cidToGidStream.setPObjectReference(cidToGidReference);
        stateManager.addTempChange(new PObject(cidToGidStream, cidToGidReference));

        cidFontDictionary.put(CID_TO_GID_MAP_KEY, cidToGidReference);
    }

    /**
     * Builds the CIDSet entry, required by PDF/A. This lists all CIDs in the font, including those
     * that don't have a GID.
     */
    private void buildCIDSet(TreeMap<Integer, Integer> cidToGid) throws IOException {
        int cidMax = cidToGid.lastKey();
        byte[] bytes = new byte[cidMax / 8 + 1];
        for (int cid = 0; cid <= cidMax; cid++) {
            int mask = 1 << 7 - cid % 8;
            bytes[cid / 8] |= mask;
        }

        StateManager stateManager = library.getStateManager();
        Reference cidSetReference = stateManager.getNewReferenceNumber();
        // setup CIDToGIDMap stream as external reference
        Stream cidSetStream = Stream.createStream(library, fontFileSubSetter.getSubsetFontData());
        cidSetStream.setPObjectReference(cidSetReference);
        stateManager.addTempChange(new PObject(cidSetStream, cidSetReference));

        fontDescriptor.getEntries().put(CID_SET, cidSetStream);
    }

}
