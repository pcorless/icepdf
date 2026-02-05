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
import java.util.List;

import static org.icepdf.core.pobjects.fonts.Font.*;
import static org.icepdf.core.pobjects.fonts.Font.TYPE;
import static org.icepdf.core.pobjects.fonts.FontDescriptor.*;
import static org.icepdf.core.pobjects.fonts.FontFactory.FONT_SUBTYPE_TYPE_0;
import static org.icepdf.core.pobjects.fonts.zfont.cmap.CMapFactory.IDENTITY_V_NAME;

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

    protected void createFontFile(String fontName) {
        DictionaryEntries fontDictionary = new DictionaryEntries();
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.TYPE_KEY, TYPE);
        // move assignment out of base class, as it's builder specific
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.SUBTYPE_KEY, FONT_SUBTYPE_TYPE_0);
        fontDictionary.put(ENCODING_KEY, IDENTITY_V_NAME);
        fontDictionary.put(org.icepdf.core.pobjects.fonts.Font.BASEFONT_KEY, new Name(fontName));

        // build font descriptor
        fontDictionary.put(FONT_DESCRIPTOR_KEY, fontDescriptor.getPObjectReference());

        // write out the font as TrueType with embedded font file
        simpleFont = new TrueTypeFont(library, fontDictionary);
        simpleFont.setPObjectReference(library.getStateManager().getNewReferenceNumber());
        library.getStateManager().addTempChange(new PObject(simpleFont, simpleFont.getPObjectReference()));
    }

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
        flags = setFlagBit(flags, FONT_FLAG_SYMBOLIC, true);
        flags = setFlagBit(flags, FONT_FLAG_NON_SYMBOLIC, false);
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
