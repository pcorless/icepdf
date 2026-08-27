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

import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Helper class to create a subset of a TrueType font for embedding in a PDF.  This class is based on
 * <a href="https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/TrueTypeEmbedder.java"></a>
 *
 * @author Keiji Suzuki
 * @author John Hewson
 */
public class TrueTypeFontEmbedder {

    private static final Logger LOGGER = Logger.getLogger(TrueTypeFontEmbedder.class.toString());

    private static final String BASE25 = "BCDEFGHIJKLMNOPQRSTUVWXYZ";

    private ZFontTrueType fontFile;
    private Set<Integer> subsetCodePoints = new HashSet<>();

    // which kind of font the declared text needs, worked out once - see requiresCompositeFont()
    private boolean composite;
    private boolean compositeDecided;

    // subset info
    private Map<Integer, Integer> gidToCid;
    private String subsetTag;
    private byte[] subsetFontData;

    // get the ZFontTrueType font file
    public TrueTypeFontEmbedder(FontFile fontFile) {
        if (fontFile instanceof ZFontTrueType) {
            this.fontFile = (ZFontTrueType) fontFile;
        }
    }

    public ZFontTrueType getFontFile() {
        return fontFile;
    }

    public void addToSubset(int codePoint) {
        if (compositeDecided && !composite && !WinAnsiEncoding.canShow(codePoint)) {
            // Text laid out earlier has already been written with one-byte codes, and the font this
            // character forces is one where a code is two bytes.  Both cannot be true of one font,
            // so the earlier text would be read as pairs of the wrong glyphs.  Every caller that
            // lays out more than one run has to declare all of it before laying out any.
            LOGGER.warning("U+" + Integer.toHexString(codePoint).toUpperCase()
                    + " needs a composite font but text has already been laid out for a simple one;"
                    + " it will not be shown correctly");
        }
        subsetCodePoints.add(codePoint);
    }

    /**
     * Declares text the font will have to show, without laying any of it out.
     * <p>
     * Which kind of font gets built is a property of all the text that shares it, so a caller that
     * lays out several runs has to say what they all are first.  One run of Japanese in the fourth
     * line of a signature appearance decides the font for the three Latin lines above it.
     *
     * @param text text that will be shown in this font
     */
    public void addToSubset(String text) {
        text.codePoints().forEach(this::addToSubset);
    }

    /**
     * Whether the text declared so far needs a composite font, which is decided by whether any of it
     * falls outside what a simple font's one-byte WinAnsiEncoding codes can reach.
     * <p>
     * The answer is latched the first time it is asked for.  The question gets asked once while text
     * is being laid out and again when the font dictionary is built, and an answer that changed in
     * between would describe the codes with a font that does not match them.
     *
     * @return true if a Type 0 font is needed
     */
    public boolean requiresCompositeFont() {
        if (!compositeDecided) {
            composite = SimpleFontFactory.requiresCompositeFont(subsetCodePoints);
            compositeDecided = true;
        }
        return composite;
    }

    public boolean isFontEmbeddable() {
        try {
            return this.fontFile != null &&
                    hasGlyfOutlines(this.fontFile.getTrueTypeFont()) &&
                    isEmbeddingPermitted(this.fontFile.getTrueTypeFont()) &&
                    isSubsettingPermitted(this.fontFile.getTrueTypeFont());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Whether the font keeps its outlines in a {@code glyf} table, which is what the subsetter reads.
     * <p>
     * An OpenType font with PostScript outlines keeps them in {@code CFF } instead, and subsetting one
     * fails with {@code UnsupportedOperationException: OTF fonts do not have a glyf table} - from
     * inside the subsetter, well after this method has said the font can be embedded. Answering
     * honestly here sends such a font down the non-embedded fallback instead, which draws it.
     * <p>
     * This is the one thing standing between the CJK fonts every Linux distribution ships as
     * OpenType collections and being usable: they are excluded from the font scan altogether, partly
     * because including them turns a substitution that quietly picks another face into a failure in
     * the middle of writing an appearance stream. Embedding them properly needs a CFF subsetter,
     * which is a separate piece of work.
     */
    private static boolean hasGlyfOutlines(TrueTypeFont font) {
        try {
            return font != null && font.getGlyph() != null;
        } catch (Exception e) {
            // OpenTypeFont.getGlyph() throws rather than returning null when the outlines are CFF
            return false;
        }
    }

    public Map<Integer, Integer> getGidToCid() {
        return gidToCid;
    }

    public Set<Integer> getSubsetCodePoints() {
        return subsetCodePoints;
    }

    public String getSubsetTag() {
        return subsetTag;
    }

    public byte[] getSubsetFontData() {
        return subsetFontData;
    }

    /**
     * Creates a subset font file byte array from the original font file
     * and the set of code points to include in the subset.  This work is based on
     * <a href="https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/TrueTypeEmbedder.java">
     * TrueTypeEmbedder</a> by Ben Litchfield and John Hewson.
     * <br>
     * @throws IOException if there is an error creating the subset
     */
    public void createSubsetFont() throws IOException {
        List<String> tables = new ArrayList<>();
        tables.add("head");
        tables.add("hhea");
        tables.add("loca");
        tables.add("maxp");
        tables.add("cvt ");
        tables.add("prep");
        tables.add("glyf");
        tables.add("hmtx");
        tables.add("fpgm");
        tables.add("cmap");

        // set the GIDs to subset
        TrueTypeFont trueTypeFont = fontFile.getTrueTypeFont();
        TTFSubsetter subsetter = new TTFSubsetter(trueTypeFont, tables);
        subsetter.addAll(subsetCodePoints);

        // calculate deterministic tag based on the chosen subset
        gidToCid = subsetter.getGIDMap();
        subsetTag = getTag(gidToCid);
        subsetter.setPrefix(subsetTag);

        // save the subset font
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        subsetter.writeToStream(out);
        trueTypeFont.close();
        // re-build the embedded font
        subsetFontData = out.toByteArray();
    }

    private boolean isEmbeddingPermitted(TrueTypeFont ttf) throws IOException {
        if (ttf.getOS2Windows() != null) {
            int fsType = ttf.getOS2Windows().getFsType();
            int maskedFsType = fsType & 0x000F;
            if (maskedFsType == OS2WindowsMetricsTable.FSTYPE_RESTRICTED) {
                return false;
            } else
                return (fsType & OS2WindowsMetricsTable.FSTYPE_BITMAP_ONLY) != OS2WindowsMetricsTable.FSTYPE_BITMAP_ONLY;
        }
        return true;
    }

    private boolean isSubsettingPermitted(TrueTypeFont ttf) throws IOException {
        if (ttf.getOS2Windows() != null) {
            int fsType = ttf.getOS2Windows().getFsType();
            return (fsType & OS2WindowsMetricsTable.FSTYPE_NO_SUBSETTING) != OS2WindowsMetricsTable.FSTYPE_NO_SUBSETTING;
        }
        return true;
    }

    /**
     * Returns an uppercase 6-character unique tag for the given subset.
     */
    private String getTag(Map<Integer, Integer> gidToCid) {
        // deterministic
        long num = gidToCid.hashCode();

        // base25 encode
        StringBuilder sb = new StringBuilder();
        do {
            long div = num / 25;
            int mod = (int) (num % 25);
            sb.append(BASE25.charAt(mod));
            num = div;
        } while (num != 0 && sb.length() < 6);

        // pad
        while (sb.length() < 6) {
            sb.insert(0, 'A');
        }

        sb.append('+');
        return sb.toString();
    }
}
