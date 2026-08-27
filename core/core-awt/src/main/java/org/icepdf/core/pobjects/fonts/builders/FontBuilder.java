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

import org.apache.fontbox.ttf.HeaderTable;
import org.apache.fontbox.ttf.HorizontalHeaderTable;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.PostScriptTable;
import org.icepdf.core.pobjects.*;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;
import org.icepdf.core.pobjects.fonts.zfont.TrueTypeFont;
import org.icepdf.core.util.Library;

import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.icepdf.core.pobjects.fonts.Font.*;
import static org.icepdf.core.pobjects.fonts.Font.TYPE;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.*;
import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_TRUE_TYPE;
import static org.icepdf.core.pobjects.fonts.zfont.SimpleFont.TO_UNICODE_KEY;
import static org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory.IDENTITY_NAME;

public class FontBuilder {

    private static final int ITALIC = 1;
    private static final int OBLIQUE = 512;

    protected Library library;
    protected TrueTypeFontEmbedder fontFileSubSetter;

    protected SimpleFont simpleFont;
    protected FontDescriptor fontDescriptor;

    public FontBuilder(Library library, TrueTypeFontEmbedder fontFileSubSetter) {
        this.library = library;
        this.fontFileSubSetter = fontFileSubSetter;
    }

    /**
     * Creates a simple font dictionary for the given font name, and sets up the necessary entries for a TrueType
     * font with embedded font file.  The simple font dictionary includes a reference to the font descriptor, which
     * in turn includes a reference to the embedded font file stream.  This method is responsible for creating the
     * simple font dictionary and linking it to the font descriptor, but does not handle the creation of the font
     * descriptor or the font file stream itself, which are handled in separate methods.
     *
     * @param fontName the name of the font to be used in the simple font dictionary.  This should match the font name
     *                 used in the font descriptor and the embedded font file.
     */
    protected void createSimpleFontFile(String fontName) {
        DictionaryEntries fontDictionary = new DictionaryEntries();
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY, TYPE);

        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, FONT_SUBTYPE_TRUE_TYPE);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.ENCODING_KEY, new Name("WinAnsiEncoding"));
        fontDictionary.put(TO_UNICODE_KEY, createToUnicodeStream());
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));

        // build font descriptor
        fontDictionary.put(FONT_DESCRIPTOR_KEY, fontDescriptor.getPObjectReference());

        // write out the font as TrueType with embedded font file
        simpleFont = new TrueTypeFont(library, fontDictionary);
        simpleFont.setPObjectReference(library.getStateManager().getNewReferenceNumber());
        library.getStateManager().addTempChange(new PObject(simpleFont, simpleFont.getPObjectReference()));
    }

    /**
     * Creates a font descriptor for the given font name and the TTF font file.  The font descriptor is created
     * based on the font metrics and properties of the TTF font file, and includes a reference to the
     * embedded font file stream.  This work was inspired by the PDFBox PDTrueTypeFontEmbedder.createFontDescriptor()
     * method,
     * but has been adapted to work with the ZFontTrueType font file and the icePDF font objects.
     *
     * @author Ben Litchfield
     * @author John Hewson
     * @author Patrick Corless
     */
    protected void createFontDescriptor(String fontName) throws IOException {
        org.apache.fontbox.ttf.TrueTypeFont trueTypeFontFile = fontFileSubSetter.getFontFile().getTrueTypeFont();
        String ttfName = trueTypeFontFile.getName();
        OS2WindowsMetricsTable os2 = trueTypeFontFile.getOS2Windows();
        if (os2 == null) {
            throw new IOException("OS2 table is missing in font " + ttfName);
        }
        PostScriptTable post = trueTypeFontFile.getPostScript();
        if (post == null) {
            throw new IOException("POST table is missing in font " + ttfName);
        }

        DictionaryEntries fontDescriptorDictionary = new DictionaryEntries();
        fontDescriptorDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY, new Name("FontDescriptor"));
        fontDescriptorDictionary.put(new Name("FontName"), new Name(fontName));


        HorizontalHeaderTable hhea = trueTypeFontFile.getHorizontalHeader();
        // Flags FLAGS
        int flags = 0;
        flags = setFlagBit(flags, FONT_FLAG_FIXED_PITCH, post.getIsFixedPitch() > 0 || hhea.getNumberOfHMetrics() == 1);

        int fsSelection = os2.getFsSelection();
        flags = setFlagBit(flags, FONT_FLAG_ITALIC, (fsSelection & (ITALIC | OBLIQUE)) != 0);

        switch (os2.getFamilyClass()) {
            case OS2WindowsMetricsTable.FAMILY_CLASS_CLAREDON_SERIFS:
            case OS2WindowsMetricsTable.FAMILY_CLASS_FREEFORM_SERIFS:
            case OS2WindowsMetricsTable.FAMILY_CLASS_MODERN_SERIFS:
            case OS2WindowsMetricsTable.FAMILY_CLASS_OLDSTYLE_SERIFS:
            case OS2WindowsMetricsTable.FAMILY_CLASS_SLAB_SERIFS:
                flags = setFlagBit(flags, FONT_FLAG_SERIF, true);
                break;
            case OS2WindowsMetricsTable.FAMILY_CLASS_SCRIPTS:
                flags = setFlagBit(flags, FONT_FLAG_SCRIPT, true);
                break;
            default:
                break;
        }
        // Exactly one of Symbolic and Nonsymbolic shall be set (PDF 32000-1, Table 123), and they
        // are mutually exclusive.  This font is written with /WinAnsiEncoding and draws text, so it
        // is nonsymbolic.  Setting the nonsymbolic bit to false and leaving symbolic commented out
        // left /Flags 0, declaring neither - invalid, and rejected by validators stricter than
        // veraPDF at 1b.
        flags = setFlagBit(flags, FONT_FLAG_SYMBOLIC, false);
        flags = setFlagBit(flags, FONT_FLAG_NON_SYMBOLIC, true);
        fontDescriptorDictionary.put(FLAGS, flags);

        // FontBBox
        HeaderTable header = trueTypeFontFile.getHeader();
        float scaling = 1000f / header.getUnitsPerEm();
        fontDescriptorDictionary.put(FONT_BBOX,
                List.of(header.getXMin() * scaling,
                        header.getYMin() * scaling,
                        header.getXMax() * scaling,
                        header.getYMax() * scaling
                ));

        // font metrics
        fontDescriptorDictionary.put(FONT_WEIGHT, os2.getWeightClass());
        fontDescriptorDictionary.put(ITALIC_ANGLE, post.getItalicAngle());
        fontDescriptorDictionary.put(ASCENT, hhea.getAscender() * scaling);
        fontDescriptorDictionary.put(DESCENT, hhea.getDescender() * scaling);
        if (os2.getVersion() >= 1.2) {
            fontDescriptorDictionary.put(CAP_HEIGHT, os2.getCapHeight() * scaling);
            fontDescriptorDictionary.put(X_HEIGHT, os2.getHeight() * scaling);
        } else {
            GeneralPath capHPath = trueTypeFontFile.getPath("H");
            if (capHPath != null) {
                fontDescriptorDictionary.put(CAP_HEIGHT, Math.round(capHPath.getBounds2D().getMaxY()) * scaling);
            } else {
                // estimate by summing the typographical +ve ascender and -ve descender
                fontDescriptorDictionary.put(CAP_HEIGHT, (os2.getTypoAscender() + os2.getTypoDescender()) * scaling);
            }
            GeneralPath xPath = trueTypeFontFile.getPath("x");
            if (xPath != null) {
                fontDescriptorDictionary.put(X_HEIGHT, Math.round(xPath.getBounds2D().getMaxY()) * scaling);
            } else {
                // estimate by halving the typographical ascender
                fontDescriptorDictionary.put(X_HEIGHT, os2.getTypoAscender() / 2.0f * scaling);
            }
        }
        // StemV - there's no true TTF equivalent of this, so we estimate it
        fontDescriptorDictionary.put(STEM_V, (header.getXMax() - header.getXMin()) * .13f);

        // create font file stream
        StateManager stateManager = library.getStateManager();
        Reference fontFileReference = stateManager.getNewReferenceNumber();

        // add the subfont font data
        Stream fontFileStream = Stream.createStream(library, fontFileSubSetter.getSubsetFontData());
        fontFileStream.setPObjectReference(fontFileReference);
        stateManager.addTempChange(new PObject(fontFileStream, fontFileReference));
        fontDescriptorDictionary.put(new Name("FontFile2"), fontFileReference);

        Reference fontDescriptorReference = stateManager.getNewReferenceNumber();
        fontDescriptor = new FontDescriptor(library, fontDescriptorDictionary);
        fontDescriptor.setPObjectReference(fontDescriptorReference);
        stateManager.addTempChange(new PObject(fontDescriptor, fontDescriptorReference));
    }

    /**
     * Builds the {@code /ToUnicode} CMap for the subset, as a stream.
     * <p>
     * It used to write the <em>name</em> {@code /Identity}, which is not what the entry takes:
     * {@code /ToUnicode} is a stream containing a CMap (PDF 32000-1 9.10.3). Nothing at PDF/A-1b
     * checks it, but every "a" conformance level requires text to be extractable, and text drawn in
     * this font could not be extracted at all.
     * <p>
     * The codes are the ones the {@code /Widths} array is indexed by, so the map is built from the
     * same subset the widths came from.
     *
     * @return reference to the CMap stream
     */
    protected Reference createToUnicodeStream() {
        List<Integer> codePoints = new ArrayList<>(fontFileSubSetter.getSubsetCodePoints());
        Collections.sort(codePoints);
        StringBuilder cmap = new StringBuilder();
        cmap.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
                .append("/CIDSystemInfo <</Registry (Adobe) /Ordering (UCS) /Supplement 0>> def\n")
                .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
                .append("1 begincodespacerange\n<00> <FF>\nendcodespacerange\n");
        // bfchar sections are capped at 100 entries by the CMap syntax.
        for (int start = 0; start < codePoints.size(); start += 100) {
            List<Integer> chunk = codePoints.subList(start, Math.min(start + 100, codePoints.size()));
            cmap.append(chunk.size()).append(" beginbfchar\n");
            for (int codePoint : chunk) {
                cmap.append(String.format("<%02X> <%04X>%n", codePoint & 0xFF, codePoint));
            }
            cmap.append("endbfchar\n");
        }
        cmap.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend");

        StateManager stateManager = library.getStateManager();
        Reference reference = stateManager.getNewReferenceNumber();
        Stream toUnicode = Stream.createStream(library,
                cmap.toString().getBytes(StandardCharsets.ISO_8859_1));
        toUnicode.setPObjectReference(reference);
        stateManager.addTempChange(new PObject(toUnicode, reference));
        return reference;
    }

    private int setFlagBit(int flags, int bit, boolean value) {
        if (value) {
            flags = flags | bit;
        } else {
            flags = flags & (~bit);
        }
        return flags;
    }

    protected void createFontFileStream() {

    }

}
