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
package org.icepdf.core.pobjects.filters;

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * Predictor decoder for LZW and Flate data streams.  Uses the same streaming
 * as our other Filters but simplifies how the bytes are read in as we treat
 * the parent (LZW or Flate) stream as a regular ChunkingInputStream.
 *
 * @since 5.0.6
 */
public class PredictorDecode extends ChunkingInputStream {

    /**
     * No predictor function is used
     */
    public static final int PREDICTOR_NONE = 1;

    /**
     * For every row, each component is derived from corresponding component in entry to left
     */
    public static final int PREDICTOR_TIFF_2 = 2;

    /**
     * For current row, PNG predictor to do nothing
     */
    public static final int PREDICTOR_PNG_NONE = 10;

    /**
     * For current row, derive each byte from byte left-by-bytesPerPixel
     */
    public static final int PREDICTOR_PNG_SUB = 11;

    /**
     * For current row, derive each byte from byte above
     */
    public static final int PREDICTOR_PNG_UP = 12;

    /**
     * For current row, derive each byte from average of byte left-by-bytesPerPixel and byte above
     */
    public static final int PREDICTOR_PNG_AVG = 13;

    /**
     * For current row, derive each byte from non-linear function of byte left-by-bytesPerPixel and byte above and byte left-by-bytesPerPixel of above
     */
    public static final int PREDICTOR_PNG_PAETH = 14;

    /**
     * When given in DecodeParms dict, in stream dict, means first byte of each row is row's predictor
     */
    public static final int PREDICTOR_PNG_OPTIMUM = 15;

    protected static final Name PREDICTOR_VALUE = new Name("Predictor");
    protected static final Name WIDTH_VALUE = new Name("Width");
    protected static final Name COLUMNS_VALUE = new Name("Columns");
    protected static final Name COLORS_VALUE = new Name("Colors");
    protected static final Name BITS_PER_COMPONENT_VALUE = new Name("BitsPerComponent");
    protected static final Name EARLY_CHANGE_VALUE = new Name("EarlyChange");
    // default values for non image streams.
    protected final int predictor;
    protected int numComponents;
    protected int bitsPerComponent;
    protected int width = 1;
    protected int bytesPerPixel;// From RFC 2083 (PNG), it's bytes per pixel, rounded up to 1

    // reference to previous buffer
    protected byte[] aboveBuffer;

    public PredictorDecode(InputStream input, Library library, DictionaryEntries entries) {
        super();
        // get decode parameters from stream properties
        DictionaryEntries decodeParmsDictionary = ImageParams.getDecodeParams(library, entries);
        predictor = library.getInt(decodeParmsDictionary, PREDICTOR_VALUE);

        Number widthNumber = library.getNumber(entries, WIDTH_VALUE);
        if (widthNumber != null) {
            width = widthNumber.intValue();
        }
        int columns = library.getInt(decodeParmsDictionary, COLUMNS_VALUE);
        if (columns > 0) width = columns;
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

        // Make buffer exactly large enough for one row of data (without predictor)
        int intermediateBufferSize = Utils.numBytesToHoldBits(
                width * numComponents * bitsPerComponent);

        // last row of data above our current buffer
        aboveBuffer = new byte[intermediateBufferSize];
        setBufferSize(intermediateBufferSize);

        setInputStream(input);
    }

    @Override
    protected int fillInternalBuffer() throws IOException {
        byte[] temp = aboveBuffer;
        aboveBuffer = buffer;
        buffer = temp;

        int currPredictor;
        int cp = in.read();
        if (cp < 0) return -1;
        // I've seen code that conditionally updates currPredictor:
        //   if predictor == PREDICTOR_PNG_OPTIMUM
        //       currPredictor = cp + PREDICTOR_PNG_NONE
        //if( predictor == PREDICTOR_PNG_OPTIMUM )
        currPredictor = cp + PREDICTOR_PNG_NONE;

        // fill the buffer
        int numRead = fillBufferFromInputStream();
        if (numRead <= 0) return -1;

        // apply predictor logic
        applyPredictor(numRead, currPredictor);

        return numRead;
    }

    /**
     * Apply predictor logic to buffer[] using  aboveBuffer[] from previous pass.
     *
     * @param numRead       number of bytes read in last pass.
     * @param currPredictor predictor to apply to buffer data.
     */
    protected void applyPredictor(int numRead, int currPredictor) {
        // currPredictor is constant for the whole row, so branch on it once and
        // run a tight per-case loop rather than re-testing it for every byte.
        switch (currPredictor) {
            // For current row, derive each byte from byte left-by-bpp.  The
            // first bytesPerPixel bytes have no left neighbour, so start past
            // them rather than testing the bound each iteration.
            case PREDICTOR_PNG_SUB:
                for (int i = bytesPerPixel; i < numRead; i++) {
                    buffer[i] += applyLeftPredictor(buffer, bytesPerPixel, i);
                }
                break;
            // For current row, derive each byte from byte above.
            case PREDICTOR_PNG_UP:
                if (aboveBuffer != null) {
                    for (int i = 0; i < numRead; i++) {
                        buffer[i] += applyAbovePredictor(aboveBuffer, i);
                    }
                }
                break;
            // For current row, derive each byte from average of byte left-by-bpp and byte above.
            // PNG AVG: output(x) = curr_line(x) + floor((curr_line(x-bpp)+above(x))/2)
            // From RFC 2083 (PNG), sum with no overflow, using >= 9 bit arithmetic.
            case PREDICTOR_PNG_AVG:
                for (int i = 0; i < numRead; i++) {
                    int left = (i - bytesPerPixel) >= 0 ? applyLeftPredictor(buffer, bytesPerPixel, i) : 0;
                    int above = aboveBuffer != null ? applyAbovePredictor(aboveBuffer, i) : 0;
                    buffer[i] += (byte) (((left + above) >>> 1) & 0xFF);
                }
                break;
            // For current row, derive each byte from non-linear function of
            // byte left-by-bpp and byte above and byte left-by-bpp of above.
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
            case PREDICTOR_PNG_PAETH:
                for (int i = 0; i < numRead; i++) {
                    int left = (i - bytesPerPixel) >= 0 ? applyLeftPredictor(buffer, bytesPerPixel, i) : 0;
                    int above = aboveBuffer != null ? applyAbovePredictor(aboveBuffer, i) : 0;
                    int aboveLeft = ((i - bytesPerPixel) >= 0 && aboveBuffer != null)
                            ? applyAboveLeftPredictor(aboveBuffer, bytesPerPixel, i) : 0;
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
                break;
            // PREDICTOR_PNG_NONE (and any unknown per-row predictor): nothing to do.
            default:
                break;
        }
    }

    private static int applyLeftPredictor(byte[] buffer, int bytesPerPixel, int i) {
        return (((int) buffer[(i - bytesPerPixel)]) & 0xFF);
    }

    private static int applyAbovePredictor(byte[] aboveBuffer, int i) {
        return (((int) aboveBuffer[i]) & 0xFF);
    }

    private static int applyAboveLeftPredictor(byte[] aboveBuffer, int bytesPerPixel, int i) {
        return (((int) aboveBuffer[i - bytesPerPixel]) & 0xFF);
    }

    public static boolean isPredictor(Library library, DictionaryEntries entries) {
        DictionaryEntries decodeParmsDictionary = ImageParams.getDecodeParams(library, entries);
        if (decodeParmsDictionary == null) {
            return false;
        }
        int predictor = library.getInt(decodeParmsDictionary, PREDICTOR_VALUE);
        return predictor == PREDICTOR_PNG_NONE || predictor == PREDICTOR_PNG_SUB ||
                predictor == PREDICTOR_PNG_UP || predictor == PREDICTOR_PNG_AVG ||
                predictor == PREDICTOR_PNG_PAETH || predictor == PREDICTOR_PNG_OPTIMUM;
    }

}
