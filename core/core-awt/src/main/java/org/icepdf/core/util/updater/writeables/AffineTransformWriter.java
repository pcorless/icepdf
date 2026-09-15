/*
 * Copyright 2026 Patrick Corless
 *
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
package org.icepdf.core.util.updater.writeables;

import org.icepdf.core.io.CountingOutputStream;

import java.awt.geom.AffineTransform;
import java.io.IOException;

public class AffineTransformWriter extends BaseWriter {

    private static final byte[] BEGIN_ARRAY = "[".getBytes();
    private static final byte[] END_ARRAY = "]".getBytes();

    /**
     * Writes the transform as the six-number matrix array of 8.3.3, {@code [a b c d e f]}, which is
     * {@code {m00, m10, m01, m11, m02, m12}} - the same order {@code AffineTransform(float[])} reads
     * back, so a matrix written here survives a reopen.  The values are written as reals: a matrix
     * is routinely a fractional scale, and rounding one to a whole number scales the content it
     * places by whatever the fraction was.
     *
     * @param writeable transform to write
     * @param output    stream to write to
     * @throws IOException if the stream cannot be written to
     */
    public void write(AffineTransform writeable, CountingOutputStream output) throws IOException {
        double[] matrix = new double[6];
        writeable.getMatrix(matrix);
        output.write(BEGIN_ARRAY);
        for (int i = 0; i < matrix.length; i++) {
            if (i > 0) {
                output.write(SPACE);
            }
            writeFloat((float) matrix[i], output);
        }
        output.write(END_ARRAY);
    }
}