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
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;

import java.io.IOException;

public class PObjectWriter extends BaseWriter {

    public void write(PObject writeable, CountingOutputStream output) throws IOException {
        Reference ref = writeable.getReference();

        writeInteger(ref.getObjectNumber(), output);
        output.write(SPACE);
        writeInteger(ref.getGenerationNumber(), output);
        output.write(SPACE);
        output.write(BEGIN_OBJECT);
        writeValue(writeable, output);
        output.write(NEWLINE);
        output.write(END_OBJECT);

    }
}
