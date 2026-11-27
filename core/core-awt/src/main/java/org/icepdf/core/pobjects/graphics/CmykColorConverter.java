/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics;

import org.icepdf.core.pobjects.Dictionary;
import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converts CMYK colours to sRGB through an ICC profile, relative colorimetric with black-point compensation (GH-647).
 * <p>
 * Black-point compensation maps the darkest ink the profile can print to sRGB black and scales everything between
 * proportionally, so dark tones and saturated colours keep their separation instead of all landing on a press's
 * grey-brown black. Java's ICC API has no BPC, so the colour goes to CIE XYZ (relative colorimetric), is scaled
 * there, and then goes to sRGB by matrix. The source black point is found the way LittleCMS (Ghostscript, MuPDF)
 * finds it for a CMYK output profile: sRGB black through the profile's perceptual table to ink, and that ink
 * back through the colorimetric table. With Ghostscript's own default profile this reproduces Ghostscript's
 * colours to within a level or two.
 * <p>
 * Setting {@code org.icepdf.core.cmyk.blackPointCompensation=false} restores the plain {@code toRGB} conversion
 * (perceptual, no BPC). Instances are immutable and safe to share across threads.
 */
public final class CmykColorConverter {

    private static final Logger logger = Logger.getLogger(CmykColorConverter.class.getName());

    private static final boolean BLACK_POINT_COMPENSATION =
            Defs.booleanProperty("org.icepdf.core.cmyk.blackPointCompensation", true);
    private static final boolean OUTPUT_INTENTS =
            Defs.booleanProperty("org.icepdf.core.cmyk.outputIntents", true);

    private static final Name OUTPUT_INTENTS_KEY = new Name("OutputIntents");
    private static final Name DEST_OUTPUT_PROFILE_KEY = new Name("DestOutputProfile");
    private static final Name S_KEY = new Name("S");
    private static final Name GTS_PDFX = new Name("GTS_PDFX");

    // the ICC profile connection space white (D50)
    private static final double[] D50 = {0.9642, 1.0, 0.8249};

    // D50 XYZ to linear sRGB, the sRGB primaries Bradford-adapted to D50 as the ICC PCS needs; matches
    // ColorSpace.CS_sRGB.fromCIEXYZ to within a level
    private static final double[][] XYZ_TO_SRGB = {
            {3.1338561, -1.6168667, -0.4906146},
            {-0.9787684, 1.9161415, 0.0334540},
            {0.0719453, -0.2289914, 1.4052427}};

    // LittleCMS caps the detected black point at L* 50, which is Y 0.1842
    private static final double MAX_BLACK_Y = 0.1842;

    private final ICC_ColorSpace colorSpace;
    private final boolean compensate;
    // per-channel scale and offset that take the source black to zero and keep the white
    private final double[] scale = new double[3];
    private final double[] offset = new double[3];

    /**
     * @param colorSpace a four-component ICC colour space
     */
    public CmykColorConverter(ICC_ColorSpace colorSpace) {
        this(colorSpace, BLACK_POINT_COMPENSATION);
    }

    CmykColorConverter(ICC_ColorSpace colorSpace, boolean blackPointCompensation) {
        this.colorSpace = colorSpace;
        // JDK 11 builds a colour space's transforms unsynchronized on first use; build them here, before the
        // converter is shared, so converting needs no lock
        colorSpace.toRGB(new float[4]);
        double blackY = blackPointCompensation ? detectBlackY(colorSpace) : 0;
        compensate = blackY > 0;
        for (int c = 0; c < 3; c++) {
            double black = D50[c] * blackY;
            scale[c] = compensate ? D50[c] / (D50[c] - black) : 1;
            offset[c] = black;
        }
    }

    /**
     * The luminance of the profile's black point, a neutral black being assumed as LittleCMS does; 0 when it
     * can't be found, which turns compensation off.
     */
    private static double detectBlackY(ICC_ColorSpace colorSpace) {
        try {
            float[] ink;
            try {
                // the ink the perceptual table prints for black
                ink = colorSpace.fromRGB(new float[]{0, 0, 0});
            } catch (RuntimeException e) {
                // an input-only profile has no table from the connection space: take every colorant at full
                ink = new float[]{1, 1, 1, 1};
            }
            double y = colorSpace.toCIEXYZ(ink)[1];
            return y > 0 ? Math.min(y, MAX_BLACK_Y) : 0;
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Could not find the CMYK profile's black point", e);
            return 0;
        }
    }

    /**
     * The converter for a document's CMYK output intent (§14.11.5): the {@code /DestOutputProfile} of the first
     * {@code /OutputIntents} entry whose profile is CMYK, a PDF/X intent ({@code /S /GTS_PDFX}) ahead of any
     * other. The profile describes the press the document was made for, so DeviceCMYK means that press's ink
     * rather than a generic one, as Acrobat treats it by default. Off with
     * {@code org.icepdf.core.cmyk.outputIntents=false}.
     *
     * @param library the document
     * @return the output intent's converter, or null when the document has no usable CMYK output intent
     */
    public static CmykColorConverter fromOutputIntents(Library library) {
        if (!OUTPUT_INTENTS || library.getCatalog() == null) {
            return null;
        }
        Object intents = library.getObject(library.getCatalog().getEntries(), OUTPUT_INTENTS_KEY);
        if (!(intents instanceof List)) {
            return null;
        }
        CmykColorConverter other = null;
        for (Object entry : (List<?>) intents) {
            Object intent = library.getObject(entry);
            if (!(intent instanceof DictionaryEntries) && !(intent instanceof Dictionary)) {
                continue;
            }
            DictionaryEntries entries = intent instanceof Dictionary
                    ? ((Dictionary) intent).getEntries() : (DictionaryEntries) intent;
            boolean pdfx = GTS_PDFX.equals(library.getObject(entries, S_KEY));
            if (!pdfx && other != null) {
                continue;
            }
            CmykColorConverter converter = fromProfile(library.getObject(entries, DEST_OUTPUT_PROFILE_KEY));
            if (converter != null) {
                if (pdfx) {
                    return converter;
                }
                other = converter;
            }
        }
        return other;
    }

    private static CmykColorConverter fromProfile(Object profileStream) {
        if (!(profileStream instanceof Stream)) {
            return null;
        }
        try {
            Stream stream = (Stream) profileStream;
            synchronized (ICCBased.lock) {
                byte[] bytes = stream.getDecodedStreamBytes(0);
                if (bytes == null) {
                    return null;
                }
                ICC_Profile profile = ICC_Profile.getInstance(bytes);
                if (profile.getColorSpaceType() != ColorSpace.TYPE_CMYK || profile.getNumComponents() != 4) {
                    return null;
                }
                return new CmykColorConverter(new ICC_ColorSpace(profile));
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Could not use the output intent's profile", e);
            return null;
        }
    }

    public ICC_ColorSpace getColorSpace() {
        return colorSpace;
    }

    /**
     * @param cmyk the four components, each 0 to 1
     * @return the colour as opaque 0xAARRGGBB
     */
    public int toArgb(float[] cmyk) {
        if (!compensate) {
            float[] rgb = colorSpace.toRGB(cmyk);
            return 0xff000000 | ((int) (rgb[0] * 255) & 0xff) << 16 | ((int) (rgb[1] * 255) & 0xff) << 8
                    | ((int) (rgb[2] * 255) & 0xff);
        }
        float[] xyz = colorSpace.toCIEXYZ(cmyk);
        double x = Math.max(0, (xyz[0] - offset[0]) * scale[0]);
        double y = Math.max(0, (xyz[1] - offset[1]) * scale[1]);
        double z = Math.max(0, (xyz[2] - offset[2]) * scale[2]);
        int argb = 0xff000000;
        for (int c = 0; c < 3; c++) {
            double[] row = XYZ_TO_SRGB[c];
            argb |= encode(row[0] * x + row[1] * y + row[2] * z) << (16 - 8 * c);
        }
        return argb;
    }

    /** Linear sRGB to an 8-bit sRGB-encoded level. */
    private static int encode(double linear) {
        if (linear <= 0) return 0;
        if (linear >= 1) return 255;
        double v = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(v * 255);
    }
}
