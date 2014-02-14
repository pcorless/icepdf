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

import org.icepdf.core.util.Library;
import org.icepdf.core.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.InflaterInputStream;

/**
 * @author Mark Collette
 * @since 2.0
 */

public class FlateDecode extends LZWFlateBaseDecode {

    private InputStream originalInputKeptSolelyForDebugging;

    public FlateDecode(Library library, HashMap props, InputStream input) {

        super(library, props);
        originalInputKeptSolelyForDebugging = input;

        // Make buffer exactly large enough for one row of data (without predictor)
        int intermediateBufferSize = DEFAULT_BUFFER_SIZE;
        if (predictor != FLATE_PREDICTOR_NONE) {
            intermediateBufferSize =
                    Utils.numBytesToHoldBits(width * numComponents * bitsPerComponent);
        }
        // last row of data above our current buffer
        aboveBuffer = new byte[intermediateBufferSize];
        setBufferSize(intermediateBufferSize);

        // Create the inflater input stream which will do the encoding
        setInputStream(new InflaterInputStream(input));

    }

    protected int fillInternalBuffer() throws IOException {

        // Swap buffers, so that aboveBuffer is what buffer just was
        byte[] temp = aboveBuffer;
        aboveBuffer = buffer;
        buffer = temp;

        // If there's no predictor, then do a block at a time,
        // Else if there is a predictor, do a row at a time
        if (predictor == FLATE_PREDICTOR_NONE) {
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0) {
                return -1;
            }
            return numRead;
        } else if (predictor == FLATE_PREDICTOR_TIFF_2) {
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0) {
                return -1;
            }
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
        // Each component is derived from corresponding component in entry to left
        else if (predictor >= FLATE_PREDICTOR_PNG_NONE && predictor <= LZW_FLATE_PREDICTOR_PNG_OPTIMUM) {
            int currPredictor;
            int cp = in.read();
            if (cp < 0) return -1;
            // I've seen code that conditionally updates currPredictor:
            //   if predictor == LZW_FLATE_PREDICTOR_PNG_OPTIMUM
            //       currPredictor = cp + FLATE_PREDICTOR_PNG_NONE
            //if( predictor == LZW_FLATE_PREDICTOR_PNG_OPTIMUM )
            currPredictor = cp + FLATE_PREDICTOR_PNG_NONE;

            // fill the buffer
            int numRead = fillBufferFromInputStream();
            if (numRead <= 0) return -1;

            // apply predictor logic
            applyPredictor(numRead, currPredictor);

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
