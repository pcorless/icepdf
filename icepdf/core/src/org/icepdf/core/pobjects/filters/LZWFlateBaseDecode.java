/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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
package org.icepdf.core.pobjects.filters;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.util.HashMap;

/**
 *
 */
public abstract class LZWFlateBaseDecode extends ChunkingInputStream {

    /**
     * No predictor function is used
     */
    protected static final int FLATE_PREDICTOR_NONE = 1;

    /**
     * For every row, each component is derived from corresponding component in entry to left
     */
    protected static final int FLATE_PREDICTOR_TIFF_2 = 2;

    /**
     * For current row, PNG predictor to do nothing
     */
    protected static final int FLATE_PREDICTOR_PNG_NONE = 10;

    /**
     * For current row, derive each byte from byte left-by-bytesPerPixel
     */
    protected static final int FLATE_PREDICTOR_PNG_SUB = 11;

    /**
     * For current row, derive each byte from byte above
     */
    protected static final int FLATE_PREDICTOR_PNG_UP = 12;

    /**
     * For current row, derive each byte from average of byte left-by-bytesPerPixel and byte above
     */
    protected static final int FLATE_PREDICTOR_PNG_AVG = 13;

    /**
     * For current row, derive each byte from non-linear function of byte left-by-bytesPerPixel and byte above and byte left-by-bytesPerPixel of above
     */
    protected static final int FLATE_PREDICTOR_PNG_PAETH = 14;

    /**
     * When given in DecodeParms dict, in stream dict, means first byte of each row is row's predictor
     */
    protected static final int LZW_FLATE_PREDICTOR_PNG_OPTIMUM = 15;

    protected static int DEFAULT_BUFFER_SIZE;

    static {
        DEFAULT_BUFFER_SIZE = Defs.sysPropertyInt("org.icepdf.core.flateDecode.bufferSize",
                16384);
    }

    protected static final Name DECODE_PARMS_VALUE = new Name("DecodeParms");
    protected static final Name PREDICTOR_VALUE = new Name("Predictor");
    protected static final Name WIDTH_VALUE = new Name("Width");
    protected static final Name COLUMNS_VALUE = new Name("Columns");
    protected static final Name COLORS_VALUE = new Name("Colors");
    protected static final Name BITS_PER_COMPONENT_VALUE = new Name("BitsPerComponent");
    protected static final Name EARLY_CHANGE_VALUE = new Name("EarlyChange");

    protected int predictor;
    protected int numComponents;
    protected int bitsPerComponent;
    protected int width;
    protected int bytesPerPixel = 1;            // From RFC 2083 (PNG), it's bytes per pixel, rounded up to 1

    // reference to previous buffer
    protected byte[] aboveBuffer;

    protected LZWFlateBaseDecode(Library library, HashMap props) {
        super();
        // get decode parameters from stream properties
        HashMap decodeParmsDictionary = library.getDictionary(props, DECODE_PARMS_VALUE);
        predictor = library.getInt(decodeParmsDictionary, PREDICTOR_VALUE);
        if (predictor != FLATE_PREDICTOR_NONE && predictor != FLATE_PREDICTOR_TIFF_2 &&
                predictor != FLATE_PREDICTOR_PNG_NONE && predictor != FLATE_PREDICTOR_PNG_SUB &&
                predictor != FLATE_PREDICTOR_PNG_UP && predictor != FLATE_PREDICTOR_PNG_AVG &&
                predictor != FLATE_PREDICTOR_PNG_PAETH && predictor != LZW_FLATE_PREDICTOR_PNG_OPTIMUM) {
            predictor = FLATE_PREDICTOR_NONE;
        }
        if (predictor != FLATE_PREDICTOR_NONE) {
            Number widthNumber = library.getNumber(props, WIDTH_VALUE);
            if (widthNumber != null) {
                width = widthNumber.intValue();
            } else {
                width = library.getInt(decodeParmsDictionary, COLUMNS_VALUE);
//                int height = (int) library.getFloat(entries, "Height");
            }
            // Since DecodeParms.BitsPerComponent has a default value, I don't think we'd
            //   look at entries.ColorSpace to know the number of components. But, here's the info:
            //   /ColorSpace /DeviceGray: 1 comp, /DeviceRBG: 3 comps, /DeviceCMYK: 4 comps, /DeviceN: N comps
            // I'm going to extend that to mean I won't look at entries.BitsPerComponent either

            numComponents = 1;    // DecodeParms.Colors: 1,2,3,4  Default=1
            bitsPerComponent = 8; // DecodeParms.BitsPerComponent: 1,2,4,8,16  Default=8

            Object numComponentsDecodeParmsObj = library.getObject(decodeParmsDictionary, COLORS_VALUE);
            if (numComponentsDecodeParmsObj instanceof Number) {
                numComponents = ((Number) numComponentsDecodeParmsObj).intValue();
            }
            Object bitsPerComponentDecodeParmsObj = library.getObject(decodeParmsDictionary, BITS_PER_COMPONENT_VALUE);
            if (bitsPerComponentDecodeParmsObj instanceof Number) {
                bitsPerComponent = ((Number) bitsPerComponentDecodeParmsObj).intValue();
            }

            bytesPerPixel = Math.max(1, Utils.numBytesToHoldBits(numComponents * bitsPerComponent));

        }
    }


    protected void applyPredictor(int numRead, int currPredictor) {
        // loop back over the buffer and update with predicted values.
        for (int i = 0; i < numRead; i++) {
            // For current row, PNG predictor to do nothing
            if (currPredictor == FLATE_PREDICTOR_PNG_NONE) {
                break; // We could continue, but we'd do that numRead times
            }
            // For current row, derive each byte from byte left-by-bpp
            else if (currPredictor == FLATE_PREDICTOR_PNG_SUB) {
                if ((i - bytesPerPixel) >= 0) {
                    buffer[i] += (((int) buffer[(i - bytesPerPixel)]) & 0xFF);
                }
            }
            // For current row, derive each byte from byte above
            else if (currPredictor == FLATE_PREDICTOR_PNG_UP) {
                if (aboveBuffer != null) {
                    buffer[i] += aboveBuffer[i];
                }
            }
            // For current row, derive each byte from average of byte left-by-bpp and byte above
            else if (currPredictor == FLATE_PREDICTOR_PNG_AVG) {
                // PNG AVG: output(x) = curr_line(x) + floor((curr_line(x-bpp)+above(x))/2)
                // From RFC 2083 (PNG), sum with no overflow, using >= 9 bit arithmatic
                int left = 0;
                if ((i - bytesPerPixel) >= 0) {
                    left = (((int) buffer[(i - bytesPerPixel)]) & 0xFF);
                }
                int above = 0;
                if (aboveBuffer != null) {
                    above = (((int) aboveBuffer[i]) & 0xFF);
                }
                int sum = left + above;
                byte avg = (byte) ((sum >>> 1) & 0xFF);
                buffer[i] += avg;
            }
            // For current row, derive each byte from non-linear function of
            // byte left-by-bpp and byte above and byte left-by-bpp of above
            else if (currPredictor == FLATE_PREDICTOR_PNG_PAETH) {
                // From RFC 2083 (PNG)
                // PNG PAETH:  output(x) = curr_line(x) + PaethPredictor(curr_line(x-bpp), above(x), above(x-bpp))
                //   PaethPredictor(left, above, aboveLeft)
                //     p          = left + above - aboveLeft
                //     pLeft      = abs(p - left)
                //     pAbove     = abs(p - above)
                //     pAboveLeft = abs(p - aboveLeft)
                //     if( pLeft <= pAbove && pLeft <= pAboveLeft ) return left
                //     if( pAbove <= pAboveLeft ) return above
                //     return aboveLeft
                int left = 0;
                if ((i - bytesPerPixel) >= 0) {
                    left = (((int) buffer[(i - bytesPerPixel)]) & 0xFF);
                }
                int above = 0;
                if (aboveBuffer != null) {
                    above = (((int) aboveBuffer[i]) & 0xFF);
                }
                int aboveLeft = 0;
                if ((i - bytesPerPixel) >= 0 && aboveBuffer != null) {
                    aboveLeft = (((int) aboveBuffer[i - bytesPerPixel]) & 0xFF);
                }
                int p = left + above - aboveLeft;
                int pLeft = Math.abs(p - left);
                int pAbove = Math.abs(p - above);
                int pAboveLeft = Math.abs(p - aboveLeft);
                int paeth = ((pLeft <= pAbove && pLeft <= pAboveLeft)
                        ? left
                        : ((pAbove <= pAboveLeft)
                        ? above
                        : aboveLeft));
                buffer[i] += ((byte) (paeth & 0xFF));
            }
        }
    }
}
