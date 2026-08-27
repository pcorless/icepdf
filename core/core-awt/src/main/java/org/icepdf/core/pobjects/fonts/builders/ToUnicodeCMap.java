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
package org.icepdf.core.pobjects.fonts.builders;

import org.icepdf.core.pobjects.PObject;
import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.StateManager;
import org.icepdf.core.pobjects.Stream;
import org.icepdf.core.util.Library;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds the {@code /ToUnicode} CMap for a generated font.
 * <p>
 * The entry takes a stream containing a CMap (PDF 32000-1 9.10.3), and both font builders used to
 * write the <em>name</em> {@code /Identity} instead. Text drawn in a generated font could not be
 * extracted, and every "a" conformance level requires that it can be.
 * <p>
 * Shared between the builders so the two cannot drift: one had it fixed and the other did not, which
 * is how the fallback font kept the bug after the main path lost it.
 *
 * @since 7.5.0
 */
public final class ToUnicodeCMap {

    /**
     * WinAnsiEncoding is Windows-1252, which is what both builders declare, so the Unicode a code
     * maps to is whatever that charset decodes it to. Deriving it beats a hand-written table: the
     * range 0x80-0x9F is where WinAnsi and Latin-1 disagree and where a table would be wrong.
     */
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private ToUnicodeCMap() {
    }

    /**
     * @param library the document to add the stream to
     * @param codes   character codes the font can show
     * @return reference to a CMap stream mapping each code to its Unicode value
     */
    public static Reference createWinAnsi(Library library, Collection<Integer> codes) {
        Map<Integer, Integer> mappings = new LinkedHashMap<>();
        for (int code : new TreeSet<>(codes)) {
            if (code < 0 || code > 0xFF) {
                continue;
            }
            String unicode = new String(new byte[]{(byte) code}, WIN_ANSI);
            // undefined positions in the encoding decode to the replacement character; a mapping to
            // it says the code means nothing, which is worse than saying nothing at all
            if (unicode.length() == 1 && unicode.charAt(0) != '�') {
                mappings.put(code, (int) unicode.charAt(0));
            }
        }
        return create(library, mappings);
    }

    /**
     * @param mappings character code to Unicode code point
     * @return reference to the CMap stream
     */
    public static Reference create(Library library, Map<Integer, Integer> mappings) {
        StringBuilder cmap = new StringBuilder();
        cmap.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
                .append("/CIDSystemInfo <</Registry (Adobe) /Ordering (UCS) /Supplement 0>> def\n")
                .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
                .append("1 begincodespacerange\n<00> <FF>\nendcodespacerange\n");
        Integer[] codes = mappings.keySet().toArray(new Integer[0]);
        // a bfchar section holds at most 100 entries
        for (int start = 0; start < codes.length; start += 100) {
            int end = Math.min(start + 100, codes.length);
            cmap.append(end - start).append(" beginbfchar\n");
            for (int i = start; i < end; i++) {
                cmap.append(String.format("<%02X> <%04X>%n", codes[i], mappings.get(codes[i])));
            }
            cmap.append("endbfchar\n");
        }
        cmap.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend");

        StateManager stateManager = library.getStateManager();
        Reference reference = stateManager.getNewReferenceNumber();
        Stream stream = Stream.createStream(library, cmap.toString().getBytes(StandardCharsets.ISO_8859_1));
        stream.setPObjectReference(reference);
        stateManager.addTempChange(new PObject(stream, reference));
        return reference;
    }
}
