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

/**
 * Helper class to create a subset of a TrueType font for embedding in a PDF.  This class is based on
 * <a href="https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/font/TrueTypeEmbedder.java"></a>
 *
 * @author Keiji Suzuki
 * @author John Hewson
 */
public class TrueTypeFontEmbedder {

    private static final String BASE25 = "BCDEFGHIJKLMNOPQRSTUVWXYZ";

    private ZFontTrueType fontFile;
    private Set<Integer> subsetCodePoints = new HashSet<>();

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
        subsetCodePoints.add(codePoint);
    }

    public boolean isFontEmbeddable() {
        try {
            return this.fontFile != null &&
                    isEmbeddingPermitted(this.fontFile.getTrueTypeFont()) &&
                    isSubsettingPermitted(this.fontFile.getTrueTypeFont());
        } catch (IOException e) {
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
     *
     * @return byte array of the subset font file
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
        // Windows ClearType
        tables.add("gasp");

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
