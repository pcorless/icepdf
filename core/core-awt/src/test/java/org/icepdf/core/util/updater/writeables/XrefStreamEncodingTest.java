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

import org.icepdf.core.pobjects.Reference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What GH-636's corpus A/B found in the writer once encrypted documents got a cross-reference stream
 * too. Each of these made a document unreadable to some reader, and none of them was new: the
 * encrypted documents, where they turn up, used to take the classic table.
 */
public class XrefStreamEncodingTest {

    /** Objects with a non-zero generation are found by it: written as 0 they were lost (/Encrypt 115 2 R). */
    @DisplayName("a used entry carries the object's generation")
    @Test
    void generationIsWritten() {
        List<Entry> entries = Arrays.asList(new Entry(new Reference(1, 0), 15), new Entry(new Reference(5, 2), 300));
        List<Integer> widths = CompressedXrefTableWriter.widths(entries);
        assertEquals(Arrays.asList(1, 2, 1), widths);
        assertArrayEquals(new byte[]{1, 0, 15, 0, 1, 1, 44, 2},
                CompressedXrefTableWriter.createXrefDataStream(entries, widths));
    }

    /** Ghostscript refuses an encrypted document whose cross-reference stream has /W [4 8 4]. */
    @DisplayName("field widths are as narrow as the values")
    @Test
    void widthsFitTheValues() {
        assertEquals(Arrays.asList(1, 4, 1), CompressedXrefTableWriter.widths(
                Arrays.asList(new Entry(new Reference(1, 0), 21_000_000L))));
    }

    /**
     * A reader turns an unescaped CR or LF in a string into a single LF, so an /O password hash with
     * a CR byte in it came back as a different hash and the document couldn't be decrypted.
     */
    @DisplayName("end-of-line bytes in a literal string are escaped, with the delimiters")
    @Test
    void endOfLineEscaped() {
        assertEquals("a\\rb\\nc\\(d\\)e\\\\f", LiteralStringWriter.escape("a\rb\nc(d)e\\f"));
        assertEquals("plain", LiteralStringWriter.escape("plain"));
    }
}
