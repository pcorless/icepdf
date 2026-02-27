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

import java.io.IOException;
import java.util.List;

public class ArrayWriter extends BaseWriter {

    private static final byte[] BEGIN_ARRAY = "[".getBytes();
    private static final byte[] END_ARRAY = "]".getBytes();

    public void write(PObject pObject, CountingOutputStream output) throws IOException {
        List<Object> writeable = (List<Object>) pObject.getObject();
        output.write(BEGIN_ARRAY);
        for (int i = 0, size = writeable.size(); i < size; i++) {
            writeValue(new PObject(writeable.get(i), pObject.getReference(), pObject.isDoNotEncrypt()), output);
            if (i < size - 1) {
                output.write(SPACE);
            }
        }
        output.write(END_ARRAY);
    }
}
