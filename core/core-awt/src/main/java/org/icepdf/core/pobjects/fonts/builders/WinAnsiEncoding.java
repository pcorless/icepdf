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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * The one place that turns a Unicode character into the character code a {@code /WinAnsiEncoding}
 * font is shown with, and back.
 * <p>
 * Fonts built here declare {@code /WinAnsiEncoding}, which is Windows-1252 (PDF 32000-1, Annex D).
 * That makes a character code a single byte in the range 0-255, and it is <em>not</em> the Unicode
 * value: U+201C, a left double quote, is code 0x93.  Three separate things have to agree on that
 * mapping - the {@code /Widths} array, which is indexed by code; the {@code /ToUnicode} CMap, which
 * maps code back to Unicode; and the bytes written into the content stream.  When they disagreed the
 * document was wrong in three different ways at once, so the mapping lives here rather than being
 * done again at each site.
 * <p>
 * The table is derived from the charset rather than written out by hand: 0x80-0x9F is where
 * Windows-1252 and Latin-1 differ and where a hand-written table would be wrong.
 *
 * @since 7.5.0
 */
public final class WinAnsiEncoding {

    /**
     * WinAnsiEncoding is Windows-1252.
     */
    public static final Charset CHARSET = Charset.forName("windows-1252");

    /**
     * Unicode value of each code, or -1 where the encoding defines nothing.
     */
    private static final int[] TO_UNICODE = new int[256];

    /**
     * The same table read backwards, built once. Scanning for a code is a per-character operation on
     * the content-stream writing path, and the encoding does not change.
     */
    private static final Map<Integer, Integer> TO_CODE = new HashMap<>(256);

    static {
        for (int code = 0; code < 256; code++) {
            String decoded = new String(new byte[]{(byte) code}, CHARSET);
            // Undefined positions decode to the replacement character.  Written as an escape rather
            // than the character itself: the build pins no source encoding, so a literal here would
            // compile differently on a machine whose default is not UTF-8.
            TO_UNICODE[code] = decoded.length() == 1 && decoded.charAt(0) != '\uFFFD'
                    ? decoded.charAt(0) : -1;
            if (TO_UNICODE[code] >= 0) {
                // first code wins, matching the scan this replaced
                TO_CODE.putIfAbsent(TO_UNICODE[code], code);
            }
        }
    }

    private WinAnsiEncoding() {
    }

    /**
     * @param unicode a Unicode code point
     * @return the character code that shows it, or -1 if this encoding cannot show it at all
     */
    public static int codeOf(int unicode) {
        Integer code = TO_CODE.get(unicode);
        return code != null ? code : -1;
    }

    /**
     * @param code a character code
     * @return the Unicode value it means, or -1 if the encoding defines nothing at that code
     */
    public static int unicodeOf(int code) {
        return code >= 0 && code < TO_UNICODE.length ? TO_UNICODE[code] : -1;
    }

    /**
     * @param unicode a Unicode code point
     * @return whether a WinAnsiEncoding font can show it
     */
    public static boolean canShow(int unicode) {
        return codeOf(unicode) >= 0;
    }
}
