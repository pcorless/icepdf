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
package org.icepdf.core.pobjects.fonts;

import org.icepdf.core.pobjects.fonts.zfont.Encoding;
import org.icepdf.core.pobjects.fonts.zfont.GlyphList;
import org.icepdf.core.pobjects.fonts.zfont.SimpleFont;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns text into the character codes a given font is shown with, and into the show string those
 * codes are written as.
 * <p>
 * A character code is not a Unicode value, and how wide one is belongs to the font: a simple font's
 * codes are one byte and are whatever its {@code /Encoding} says, while a composite font's are two.
 * Getting that wrong does not fail - it draws the wrong glyphs, or twice as many of them - so the
 * rule lives in one place rather than at each site that writes text.
 * <p>
 * The widget annotations wrote every character as four hex digits whatever the font was. A text
 * field showing "Ab" with the usual {@code /Helv} produced {@code <00410062>}, which a simple font
 * reads as the four codes {@code 00 41 00 62}: a .notdef in front of every character.
 *
 * @since 7.5.0
 */
public class FontTextEncoder {

    private final boolean twoByteCodes;

    /**
     * Unicode to character code, for a simple font. Null for a composite one, whose codes are CIDs.
     */
    private final Map<Integer, Integer> unicodeToCode;

    private FontTextEncoder(boolean twoByteCodes, Map<Integer, Integer> unicodeToCode) {
        this.twoByteCodes = twoByteCodes;
        this.unicodeToCode = unicodeToCode;
    }

    /**
     * @param font the font the text will be shown in; null is treated as a simple WinAnsiEncoding
     *             font, which is what an unresolvable {@code /DA} font is most likely to be
     * @return an encoder for that font
     */
    public static FontTextEncoder of(Font font) {
        if (font != null && font.getSubTypeFormat() == Font.CID_FORMAT) {
            // A composite font's codes are CIDs. Which CID shows which character is the font's own
            // business - it needs the encoding CMap reversed - so this keeps what the widgets have
            // always done and treats the Unicode value as the CID, which is right for the
            // Identity-H fonts written here and unverified for anything else.
            return new FontTextEncoder(true, null);
        }
        return new FontTextEncoder(false, reverseEncodingOf(font));
    }

    /**
     * Inverts a simple font's {@code /Encoding}: it maps code to glyph name, and what is needed here
     * is the character to the code that shows it. Built by walking the 256 codes rather than from a
     * table, so a {@code /Differences} array is honoured without any special case.
     */
    private static Map<Integer, Integer> reverseEncodingOf(Font font) {
        Encoding encoding = font instanceof SimpleFont ? ((SimpleFont) font).getFontEncoding() : null;
        if (encoding == null) {
            encoding = Encoding.winAnsiEncoding;
        }
        Map<Integer, Integer> unicodeToCode = new HashMap<>(256);
        for (int code = 0; code < 256; code++) {
            String name = encoding.getName(code);
            if (name == null) {
                continue;
            }
            String unicode = GlyphList.getAdobeGlyphList().toUnicode(name);
            if (unicode != null && unicode.length() == 1) {
                // first code wins: an encoding can name the same glyph twice, and the lower code is
                // the one a reader is most likely to have a width for
                unicodeToCode.putIfAbsent((int) unicode.charAt(0), code);
            }
        }
        return unicodeToCode;
    }

    /**
     * @return true if a character code in this font is two bytes rather than one
     */
    public boolean isTwoByteCodes() {
        return twoByteCodes;
    }

    /**
     * @param unicode a Unicode code point
     * @return the character code that shows it, or -1 if this font has no code for it
     */
    public int codeOf(int unicode) {
        if (twoByteCodes) {
            return unicode;
        }
        Integer code = unicodeToCode.get(unicode);
        return code != null ? code : -1;
    }

    /**
     * Appends the text as a PDF string operand, ready to be followed by a show operator.
     * <p>
     * Hex for two-byte codes, because every code has to occupy the same number of digits or the
     * string cannot be split back into codes; a literal string for one-byte codes, which is what the
     * rest of the content stream is written as and what a reader expects of a simple font.
     * <p>
     * Characters the font has no code for are dropped. Writing them at some other code would draw a
     * different letter, which is worse than drawing none.
     *
     * @param out  where to append
     * @param text the text to show
     * @return out, for chaining
     */
    public StringBuilder appendShowString(StringBuilder out, String text) {
        if (twoByteCodes) {
            out.append('<');
            for (int i = 0; i < text.length(); i++) {
                appendHex(out, text.charAt(i) >> 8);
                appendHex(out, text.charAt(i));
            }
            return out.append('>');
        }
        out.append('(');
        for (int i = 0; i < text.length(); i++) {
            int code = codeOf(text.charAt(i));
            if (code < 0) {
                continue;
            }
            appendLiteralByte(out, code);
        }
        return out.append(')');
    }

    /**
     * An encoder for a font whose code width is already known, used where the codes have been
     * resolved before this point - the appearance writer resolves them while laying the text out,
     * because it needs the glyph anyway.
     *
     * @param twoByteCodes whether a character code in that font is two bytes
     */
    public static FontTextEncoder forCodeWidth(boolean twoByteCodes) {
        return new FontTextEncoder(twoByteCodes, twoByteCodes ? null : reverseEncodingOf(null));
    }

    /**
     * Appends already-resolved character codes as a PDF string operand.
     *
     * @param codes character codes in this font
     */
    public StringBuilder appendShowStringForCodes(StringBuilder out, List<Integer> codes) {
        if (twoByteCodes) {
            out.append('<');
            for (int code : codes) {
                appendHex(out, code >> 8);
                appendHex(out, code);
            }
            return out.append('>');
        }
        out.append('(');
        for (int code : codes) {
            appendLiteralByte(out, code);
        }
        return out.append(')');
    }

    private static void appendHex(StringBuilder out, int value) {
        out.append(Character.forDigit((value >> 4) & 0x0F, 16))
                .append(Character.forDigit(value & 0x0F, 16));
    }

    /**
     * One byte of a literal string. The three characters that end or escape a string have to be
     * escaped, and anything outside printable ASCII is written as an octal escape so the content
     * stream stays seven-bit whatever charset it is later written with.
     */
    private static void appendLiteralByte(StringBuilder out, int code) {
        if (code == '(' || code == ')' || code == '\\') {
            out.append('\\').append((char) code);
        } else if (code >= 32 && code < 127) {
            out.append((char) code);
        } else {
            out.append('\\')
                    .append((char) ('0' + ((code >> 6) & 0x7)))
                    .append((char) ('0' + ((code >> 3) & 0x7)))
                    .append((char) ('0' + (code & 0x7)));
        }
    }
}
