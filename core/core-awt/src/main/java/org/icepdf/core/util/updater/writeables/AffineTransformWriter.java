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

    public void write(AffineTransform writeable, CountingOutputStream output) throws IOException {
        output.write(BEGIN_ARRAY);
        writeLong((long) writeable.getScaleX(), output);
        output.write(SPACE);
        writeLong((long) writeable.getShearX(), output);
        output.write(SPACE);
        writeLong((long) writeable.getTranslateX(), output);
        output.write(SPACE);
        writeLong((long) writeable.getScaleY(), output);
        output.write(SPACE);
        writeLong((long) writeable.getShearY(), output);
        output.write(SPACE);
        writeLong((long) writeable.getTranslateY(), output);
        output.write(END_ARRAY);
    }
}