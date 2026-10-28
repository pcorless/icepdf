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

import org.icepdf.core.pobjects.DictionaryEntries;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.pobjects.acroform.SignatureDictionary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What may go in an object stream (PDF 32000-1 7.5.7), and the one thing that may but must not.
 */
public class ObjectStreamWriterTest {

    private static DictionaryEntries dictionary(Name key, Object value) {
        DictionaryEntries entries = new DictionaryEntries();
        entries.put(key, value);
        return entries;
    }

    @DisplayName("plain objects are packed")
    @Test
    void plainObjects() {
        assertTrue(ObjectStreamWriter.canPack(new PObject(dictionary(new Name("S"), new Name("P")),
                new Reference(10, 0))));
        assertTrue(ObjectStreamWriter.canPack(new PObject(List.of(1, 2, 3), new Reference(11, 0))));
    }

    @DisplayName("streams, a non-zero generation and the encryption dictionary are not")
    @Test
    void forbidden() {
        Stream stream = new Stream(new DictionaryEntries(), new byte[0]);
        assertFalse(ObjectStreamWriter.canPack(new PObject(stream, new Reference(12, 0))));
        assertFalse(ObjectStreamWriter.canPack(new PObject(dictionary(new Name("S"), new Name("P")),
                new Reference(13, 1))));
        assertFalse(ObjectStreamWriter.canPack(new PObject(dictionary(new Name("Filter"), new Name("Standard")),
                new Reference(14, 0), true)), "the /Encrypt dictionary is written unencrypted, and on its own");
    }

    /** The signer patches /Contents and /ByteRange into the written bytes, so they must be there to patch. */
    @DisplayName("a signature dictionary stays at the top level")
    @Test
    void signatureDictionary() {
        assertFalse(ObjectStreamWriter.canPack(new PObject(dictionary(SignatureDictionary.BYTE_RANGE_KEY,
                List.of(0, 0, 0, 0)), new Reference(15, 0))));
    }
}
