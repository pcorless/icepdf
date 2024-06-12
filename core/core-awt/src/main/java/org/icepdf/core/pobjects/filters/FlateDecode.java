/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.graphics.images.ImageParams;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */

public class FlateDecode extends ChunkingInputStream {


    private static final int DEFAULT_BUFFER_SIZE;

    static {
        DEFAULT_BUFFER_SIZE = Defs.sysPropertyInt("org.icepdf.core.flateDecode.bufferSize",
                16384);
    }

    public static final Name DECODE_PARMS_VALUE = new Name("DecodeParms");
    public static final Name PREDICTOR_KEY = new Name("Predictor");
    public static final Name WIDTH_KEY = new Name("Width");
    public static final Name COLUMNS_KEY = new Name("Columns");
    public static final Name COLORS_KEY = new Name("Colors");
    public static final Name BITS_PER_COMPONENT_KEY = new Name("BitsPerComponent");


    private final InputStream originalInputKeptSolelyForDebugging;
    private int numComponents = 1;
    private int bitsPerComponent = 8;
    private int predictor;


    public FlateDecode(Library library, DictionaryEntries props, InputStream input) {
        super();
        originalInputKeptSolelyForDebugging = input;

        int intermediateBufferSize = DEFAULT_BUFFER_SIZE;

        // get decode parameters from stream properties
        DictionaryEntries decodeParmsDictionary = ImageParams.getDecodeParams(library, props);
        predictor = library.getInt(decodeParmsDictionary, PREDICTOR_KEY);
        if (predictor != PredictorDecode.PREDICTOR_NONE &&
                predictor != PredictorDecode.PREDICTOR_TIFF_2 &&
                predictor != PredictorDecode.PREDICTOR_PNG_NONE &&
                predictor != PredictorDecode.PREDICTOR_PNG_SUB &&
                predictor != PredictorDecode.PREDICTOR_PNG_UP &&
                predictor != PredictorDecode.PREDICTOR_PNG_AVG &&
                predictor != PredictorDecode.PREDICTOR_PNG_PAETH &&
                predictor != PredictorDecode.PREDICTOR_PNG_OPTIMUM) {
            predictor = PredictorDecode.PREDICTOR_NONE;
        }
        if (predictor != PredictorDecode.PREDICTOR_NONE) {
            Number widthNumber = library.getNumber(props, WIDTH_KEY);
            // default values for non image streams.
            int width = 1;
            if (widthNumber != null) {
                width = widthNumber.intValue();
            }
            int columns = library.getInt(decodeParmsDictionary, COLUMNS_KEY);
            if (columns > 0) width = columns;

            // Since DecodeParms.BitsPerComponent has a default value, I don't think we'd
            //   look at entries.ColorSpace to know the number of components. But, here's the info:
            //   /ColorSpace /DeviceGray: 1 comp, /DeviceRBG: 3 comps, /DeviceCMYK: 4 comps, /DeviceN: N comps
            // I'm going to extend that to mean I won't look at entries.BitsPerComponent either

            numComponents = 1;    // DecodeParms.Colors: 1,2,3,4  Default=1
            bitsPerComponent = 8; // DecodeParms.BitsPerComponent: 1,2,4,8,16  Default=8

            Object numComponentsDecodeParmsObj = library.getObject(decodeParmsDictionary, COLORS_KEY);
            if (numComponentsDecodeParmsObj instanceof Number) {
                numComponents = ((Number) numComponentsDecodeParmsObj).intValue();
            }
            Object bitsPerComponentDecodeParmsObj = library.getObject(decodeParmsDictionary, BITS_PER_COMPONENT_KEY);
            if (bitsPerComponentDecodeParmsObj instanceof Number) {
                bitsPerComponent = ((Number) bitsPerComponentDecodeParmsObj).intValue();
            }

            // Make buffer exactly large enough for one row of data (without predictor)
            intermediateBufferSize = Utils.numBytesToHoldBits(width * numComponents * bitsPerComponent);
        }

        // Create the inflater input stream which will do the encoding
        try {
            // skip zlib header
            input.read();
            input.read();
        } catch (IOException e) {
            // e.printStackTrace();
        }
        // force support GZIP compatible compression
        setInputStream(new InflaterInputStream(input, new Inflater(true)));
        setBufferSize(intermediateBufferSize);
    }

    protected int fillInternalBuffer() throws IOException {

        if (predictor == PredictorDecode.PREDICTOR_NONE) {
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0)
                return -1;
            return numRead;
        } else if (predictor == PredictorDecode.PREDICTOR_TIFF_2) {
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0)
                return -1;
            if (bitsPerComponent == 8) {
                for (int i = 0; i < numRead; i++) {
                    int prevIndex = i - numComponents;
                    if (prevIndex >= 0) {
                        buffer[i] += buffer[prevIndex];
                    }
                }
            }
            return numRead;
        }
        // Predictor decode is handle by the PredictorDecode class as it's also
        // used by LZW Decode. So all we need to do is fill the buffer.
        else if (predictor >= PredictorDecode.PREDICTOR_PNG_NONE &&
                predictor <= PredictorDecode.PREDICTOR_PNG_OPTIMUM) {
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0) return -1;
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
            sb.append(originalInputKeptSolelyForDebugging);
        return sb.toString();
    }
}
