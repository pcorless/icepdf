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
import org.icepdf.core.pobjects.Reference;

import java.io.IOException;

public class ReferenceWriter extends BaseWriter {

    public void write(Reference writeable, CountingOutputStream output) throws IOException {
        writeInteger(writeable.getObjectNumber(), output);
        output.write(SPACE);
        writeInteger(writeable.getGenerationNumber(), output);
        output.write(SPACE);
        output.write(REFERENCE);
    }
}
