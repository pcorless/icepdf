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
package org.icepdf.core.pobjects.fonts.builders;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFactory;
import org.icepdf.core.pobjects.fonts.zfont.TrueTypeFont;
import org.icepdf.core.util.FontUtil;
import org.icepdf.core.util.Library;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static org.icepdf.core.pobjects.fonts.Font.TYPE;
import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_CID_FONT_TYPE_2;


public class TrueTypeFontBuilder {

    private Library library;
    private TrueTypeFontEmbedder fontFileSubSetter;

    public TrueTypeFontBuilder(Library library, TrueTypeFontEmbedder fontFileSubSetter) {
        this.library = library;
        this.fontFileSubSetter = fontFileSubSetter;
    }

    public TrueTypeFont Build() {
        // double check we have an embedded font available for the font name
        if (!(FontFactory.useEmbeddedFonts || fontFileSubSetter.isFontEmbeddable())) {
            throw new IllegalStateException("Font embedding not supported or font is not embeddable.");
        }

        // generate the subset font
        byte[] subsetFontData;
        Map<Integer, Integer> gidToCid;
        String subsetTag;
        try {
            fontFileSubSetter.createSubsetFont();
            subsetFontData = fontFileSubSetter.getSubsetFontData();
            gidToCid = fontFileSubSetter.getGidToCid();
            subsetTag = fontFileSubSetter.getSubsetTag();
        } catch (IOException e) {
            throw new RuntimeException("Failed create font subset", e);
        }

        // build CID2GIDMap, because the content stream has been written with the old GIDs
        TreeMap<Integer, Integer> cidToGid = new TreeMap<>();
        gidToCid.forEach((newGID, oldGID) -> cidToGid.put(oldGID, newGID));

        // font name

        // font descriptor

        // widths

        // cidToGidMap

        // CidSet

        // base dictionary
        DictionaryEntries fontDictionary = new DictionaryEntries();
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY, TYPE);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, FONT_SUBTYPE_CID_FONT_TYPE_2);


        // setup basics needed
//        String fontName = fontFileSubSetter.getFontFile().getName();
//        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));
//        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY, new Name("WinAnsiEncoding"));
//        fontDictionary.put(TO_UNICODE_KEY, IDENTITY_NAME);

        // write out the font as TrueType with embedded font file
//            FontDescriptor fontDescriptor = creatFontDescriptor(library, fontName);
//            fontDictionary.put(FONT_DESCRIPTOR_KEY, fontDescriptor.getPObjectReference());

        TrueTypeFont font = new TrueTypeFont(library, fontDictionary);
        font.setPObjectReference(library.getStateManager().getNewReferenceNumber());
        library.getStateManager().addTempChange(new PObject(font, font.getPObjectReference()));
        return font;
    }

    private FontDescriptor creatFontDescriptor(Library library, String fontName) {
        StateManager stateManager = library.getStateManager();
        DictionaryEntries fontDescriptorDictionary = new DictionaryEntries();
        fontDescriptorDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY, new Name("FontDescriptor"));
        fontDescriptorDictionary.put(new Name("FontName"), new Name(fontName));

        // todo pull real values from the font file
        fontDescriptorDictionary.put(new Name("Flags"), 4);
        fontDescriptorDictionary.put(new Name("FontBBox"), Arrays.asList(-1022, -693, 2418, 1966));
        fontDescriptorDictionary.put(new Name("ItalicAngle"), 0);
        fontDescriptorDictionary.put(new Name("Ascent"), 1966);
        fontDescriptorDictionary.put(new Name("Descent"), -693);
        fontDescriptorDictionary.put(new Name("StemV"), 80);

        // create font file stream
        Reference fontFileReference = stateManager.getNewReferenceNumber();
        // generate the subset font

        // we already have the fontFile, so we should use it to created the stream
        Stream fontFileStream = FontUtil.createFontFileStream(library, fontName);
        fontFileStream.setPObjectReference(fontFileReference);
        stateManager.addTempChange(new PObject(fontFileStream, fontFileReference));
        fontDescriptorDictionary.put(new Name("FontFile2"), fontFileReference);

        Reference fontDescriptorReference = stateManager.getNewReferenceNumber();
        FontDescriptor fontDescriptor = new FontDescriptor(library, fontDescriptorDictionary);
        fontDescriptor.setPObjectReference(fontDescriptorReference);
        stateManager.addTempChange(new PObject(fontDescriptor, fontDescriptorReference));
        return fontDescriptor;
    }
}
