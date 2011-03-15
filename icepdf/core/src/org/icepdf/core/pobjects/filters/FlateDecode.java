/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.filters;

import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.zip.InflaterInputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */

public class FlateDecode extends ChunkingInputStream {
    /**
     * No predictor function is used
     */
    private static final int LZW_FLATE_PREDICTOR_NONE = 1;

    /**
     * For every row, each component is derived from corresponding component in entry to left
     */
    private static final int LZW_FLATE_PREDICTOR_TIFF_2 = 2;

    /**
     * For current row, PNG predictor to do nothing
     */
    private static final int LZW_FLATE_PREDICTOR_PNG_NONE = 10;

    /**
     * For current row, derive each byte from byte left-by-bpp
     */
    private static final int LZW_FLATE_PREDICTOR_PNG_SUB = 11;

    /**
     * For current row, derive each byte from byte above
     */
    private static final int LZW_FLATE_PREDICTOR_PNG_UP = 12;

    /**
     * For current row, derive each byte from average of byte left-by-bpp and byte above
     */
    private static final int LZW_FLATE_PREDICTOR_PNG_AVG = 13;

    /**
     * For current row, derive each byte from non-linear function of byte left-by-bpp and byte above and byte left-by-bpp of above
     */
    private static final int LZW_FLATE_PREDICTOR_PNG_PAETH = 14;

    /**
     * When given in DecodeParms dict, in stream dict, means first byte of each row is row's predictor
     */
    private static final int LZW_FLATE_PREDICTOR_PNG_OPTIMUM = 15;


    private InputStream originalInputKeptSolelyForDebugging;
    private int width;
    private int numComponents;
    private int bitsPerComponent;
    private int bpp = 1;            // From RFC 2083 (PNG), it's bytes per pixel, rounded up to 1
    private int predictor;
    private byte[] aboveBuffer;


    public FlateDecode(Library library, Hashtable props, InputStream input) {
        super();
        originalInputKeptSolelyForDebugging = input;
        width = 0;
        numComponents = 0;
        bitsPerComponent = 0;
        bpp = 1;

        int intermediateBufferSize = 4096;

        // get decode parameters from stream properties
        Hashtable decodeParmsDictionary = library.getDictionary(props, "DecodeParms");
        predictor = library.getInt(decodeParmsDictionary, "Predictor");
        if (predictor != LZW_FLATE_PREDICTOR_NONE && predictor != LZW_FLATE_PREDICTOR_TIFF_2 &&
                predictor != LZW_FLATE_PREDICTOR_PNG_NONE && predictor != LZW_FLATE_PREDICTOR_PNG_SUB &&
                predictor != LZW_FLATE_PREDICTOR_PNG_UP && predictor != LZW_FLATE_PREDICTOR_PNG_AVG &&
                predictor != LZW_FLATE_PREDICTOR_PNG_PAETH && predictor != LZW_FLATE_PREDICTOR_PNG_OPTIMUM) {
            predictor = LZW_FLATE_PREDICTOR_NONE;
        }
//System.out.println("predictor: " + predictor);
        if (predictor != LZW_FLATE_PREDICTOR_NONE) {
            Number widthNumber = library.getNumber(props, "Width");
            if (widthNumber != null)
                width = widthNumber.intValue();
            else
                width = library.getInt(decodeParmsDictionary, "Columns");
//System.out.println("Width: " + width);
            //int height = (int) library.getFloat(entries, "Height");

            // Since DecodeParms.BitsPerComponent has a default value, I don't think we'd
            //   look at entries.ColorSpace to know the number of components. But, here's the info:
            //   /ColorSpace /DeviceGray: 1 comp, /DeviceRBG: 3 comps, /DeviceCMYK: 4 comps, /DeviceN: N comps
            // I'm going to extend that to mean I won't look at entries.BitsPerComponent either

            numComponents = 1;    // DecodeParms.Colors: 1,2,3,4  Default=1
            bitsPerComponent = 8; // DecodeParms.BitsPerComponent: 1,2,4,8,16  Default=8

            Object numComponentsDecodeParmsObj = library.getObject(decodeParmsDictionary, "Colors");
            if (numComponentsDecodeParmsObj instanceof Number) {
                numComponents = ((Number) numComponentsDecodeParmsObj).intValue();
//System.out.println("numComponents: " + numComponents);
            }
            Object bitsPerComponentDecodeParmsObj = library.getObject(decodeParmsDictionary, "BitsPerComponent");
            if (bitsPerComponentDecodeParmsObj instanceof Number) {
                bitsPerComponent = ((Number) bitsPerComponentDecodeParmsObj).intValue();
//System.out.println("bitsPerComponent: " + bitsPerComponent);
            }

            bpp = Math.max(1, Utils.numBytesToHoldBits(numComponents * bitsPerComponent));
//System.out.println("bpp: " + bpp);

            // Make buffer exactly large enough for one row of data (without predictor)
            intermediateBufferSize =
                    Utils.numBytesToHoldBits(width * numComponents * bitsPerComponent);
//System.out.println("intermediateBufferSize: " + intermediateBufferSize);
        }

        // Create the inflater input stream which will do the encoding
        setInputStream(new InflaterInputStream(input));
        setBufferSize(intermediateBufferSize);
        aboveBuffer = new byte[intermediateBufferSize];
    }

    protected int fillInternalBuffer() throws IOException {
        // Swap buffers, so that aboveBuffer is what buffer just was
        byte[] temp = aboveBuffer;
        aboveBuffer = buffer;
        buffer = temp;

        // If there's no predictor, then do a block at a time,
        // Else if there is a predictor, do a row at a time

        if (predictor == LZW_FLATE_PREDICTOR_NONE) {
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0)
                return -1;
            return numRead;
        } else if (predictor == LZW_FLATE_PREDICTOR_TIFF_2) {
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0)
                return -1;
            if( bitsPerComponent == 8) {
                for(int i = 0; i < numRead; i++) {
                    int prevIndex = i - numComponents;
                    if( prevIndex >= 0 ) {
                        buffer[i] += buffer[prevIndex];
                    }
                }
            }
            return numRead;

            // Each component is derived from corresponding component in entry to left
            //TODO Find an example PDF to develop this functionality against
        }
        else if (predictor >= LZW_FLATE_PREDICTOR_PNG_NONE && predictor <= LZW_FLATE_PREDICTOR_PNG_OPTIMUM) {
            int currPredictor = predictor;
            int cp = in.read();
            //System.out.println("  PNG predictor.  Row predictor byte: " + cp);
            if (cp < 0)
                return -1;
            // I've seen code that conditionally updates currPredictor:
            //   if predictor == LZW_FLATE_PREDICTOR_PNG_OPTIMUM
            //       currPredictor = cp + LZW_FLATE_PREDICTOR_PNG_NONE
            //if( predictor == LZW_FLATE_PREDICTOR_PNG_OPTIMUM )
            currPredictor = cp + LZW_FLATE_PREDICTOR_PNG_NONE;
//System.out.println("bpp: " + bpp + "  predictor: " + predictor + "  cp: " + cp + "  currPredictor: " + currPredictor);
            //System.out.println("  PNG predictor.  Row predictor used: " + currPredictor);

            int numRead = fillBufferFromInputStream();
            if (numRead <= 0)
                return -1;
//System.out.println("numRead: " + numRead);

            for (int i = 0; i < numRead; i++) {
//System.out.print(Integer.toHexString( ((int)buffer[i]) & 0xFF) + ":");
                // For current row, PNG predictor to do nothing
                if (currPredictor == LZW_FLATE_PREDICTOR_PNG_NONE)
                    break; // We could continue, but we'd do that numRead times
                    // For current row, derive each byte from byte left-by-bpp
                else if (currPredictor == LZW_FLATE_PREDICTOR_PNG_SUB) {
                    if ((i - bpp) >= 0)
                        buffer[i] += buffer[(i - bpp)];
//System.out.print(Integer.toHexString( ((int)buffer[i]) & 0xFF) + "  ");
                }
                // For current row, derive each byte from byte above
                else if (currPredictor == LZW_FLATE_PREDICTOR_PNG_UP) {
                    if (aboveBuffer != null)
                        buffer[i] += aboveBuffer[i];
                }
                // For current row, derive each byte from average of byte left-by-bpp and byte above
                else if (currPredictor == LZW_FLATE_PREDICTOR_PNG_AVG) {
                    // PNG AVG: output(x) = curr_line(x) + floor((curr_line(x-bpp)+above(x))/2)
                    // From RFC 2083 (PNG), sum with no overflow, using >= 9 bit arithmatic
                    int left = 0;
                    if ((i - bpp) >= 0)
                        left = (((int) buffer[(i - bpp)]) & 0xFF);
                    int above = 0;
                    if (aboveBuffer != null)
                        above = (((int) aboveBuffer[i]) & 0xFF);
                    int sum = left + above;
                    byte avg = (byte) ((sum >>> 1) & 0xFF);
                    buffer[i] += avg;
                }
                // For current row, derive each byte from non-linear function of
                // byte left-by-bpp and byte above and byte left-by-bpp of above
                else if (currPredictor == LZW_FLATE_PREDICTOR_PNG_PAETH) {
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
                    if ((i - bpp) >= 0)
                        left = (((int) buffer[(i - bpp)]) & 0xFF);
                    int above = 0;
                    if (aboveBuffer != null)
                        above = (((int) aboveBuffer[i]) & 0xFF);
                    int aboveLeft = 0;
                    if ((i - bpp) >= 0 && aboveBuffer != null)
                        aboveLeft = (((int) aboveBuffer[i - bpp]) & 0xFF);
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
//System.out.println();
            return numRead;
        }

        return -1;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.append(", orig: ");
        if (originalInputKeptSolelyForDebugging == null)
            sb.append("null");
        else
            sb.append(originalInputKeptSolelyForDebugging.toString());
        return sb.toString();
    }
}
