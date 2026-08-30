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

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

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

    private ToUnicodeCMap() {
    }

    /**
     * @param library the document to add the stream to
     * @param codes   character codes the font can show
     * @return reference to a CMap stream mapping each code to its Unicode value
     */
    public static Reference forCodes(Library library, Collection<Integer> codes) {
        Map<Integer, Integer> mappings = new TreeMap<>();
        for (int code : codes) {
            int unicode = WinAnsiEncoding.unicodeOf(code);
            // A code the encoding defines nothing at means nothing; saying so with a mapping to the
            // replacement character is worse than saying nothing at all.
            if (unicode >= 0) {
                mappings.put(code, unicode);
            }
        }
        return create(library, mappings);
    }

    /**
     * The subset path.  A font subset is collected as Unicode - that is what the font's own cmap is
     * keyed by - and a CMap is keyed by character code, which under WinAnsiEncoding is not the same
     * number above 0x7F.  Treating one as the other agreed with the truth for ASCII and nowhere else,
     * so the characters that actually needed a {@code /ToUnicode} entry were exactly the ones that
     * lost it.
     *
     * @param library    the document to add the stream to
     * @param codePoints Unicode code points the font can show
     * @return reference to the CMap stream
     */
    public static Reference forUnicode(Library library, Collection<Integer> codePoints) {
        Map<Integer, Integer> mappings = new TreeMap<>();
        for (int codePoint : codePoints) {
            int code = WinAnsiEncoding.codeOf(codePoint);
            if (code >= 0) {
                mappings.put(code, codePoint);
            }
        }
        return create(library, mappings);
    }

    /**
     * A composite font's codes are two bytes wide under Identity-H, and a CMap's codespace range is
     * what says so - the width of the hex digits in it is the width of a code, not a formatting
     * choice.  Written one byte wide, a reader splits a two-byte code into two characters.
     *
     * @param mappings CID to Unicode code point
     * @return reference to the CMap stream
     */
    public static Reference forCids(Library library, Map<Integer, Integer> mappings) {
        return create(library, mappings, 2);
    }

    /**
     * @param mappings character code to Unicode code point
     * @return reference to the CMap stream
     */
    public static Reference create(Library library, Map<Integer, Integer> mappings) {
        return create(library, mappings, 1);
    }

    /**
     * @param codeBytes width of a character code, which the codespace range has to agree with
     */
    private static Reference create(Library library, Map<Integer, Integer> mappings, int codeBytes) {
        String codeFormat = "%0" + (codeBytes * 2) + "X";
        String low = String.format(codeFormat, 0);
        String high = String.format(codeFormat, (1 << (codeBytes * 8)) - 1);
        StringBuilder cmap = new StringBuilder();
        cmap.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
                .append("/CIDSystemInfo <</Registry (Adobe) /Ordering (UCS) /Supplement 0>> def\n")
                .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
                .append("1 begincodespacerange\n<").append(low).append("> <").append(high)
                .append(">\nendcodespacerange\n");
        Integer[] codes = mappings.keySet().toArray(new Integer[0]);
        // a bfchar section holds at most 100 entries
        for (int start = 0; start < codes.length; start += 100) {
            int end = Math.min(start + 100, codes.length);
            cmap.append(end - start).append(" beginbfchar\n");
            for (int i = start; i < end; i++) {
                cmap.append(String.format("<" + codeFormat + "> <", codes[i]))
                        .append(utf16BigEndian(mappings.get(codes[i]))).append(">\n");
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

    /**
     * The destination of a bfchar entry, which is a UTF-16BE string (PDF 32000-1 9.10.3) - so a code
     * point above the basic plane is a surrogate pair and takes eight hex digits, not four. Written
     * as four it came out an odd number of digits, which is not a hex string a reader can parse, and
     * the whole CMap with it.
     */
    private static String utf16BigEndian(int codePoint) {
        StringBuilder hex = new StringBuilder(8);
        for (char unit : Character.toChars(codePoint)) {
            hex.append(String.format("%04X", (int) unit));
        }
        return hex.toString();
    }
}
