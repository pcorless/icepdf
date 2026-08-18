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
package org.icepdf.core.util.updater.callbacks;

import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.ArrayList;

/**
 * StringObjectWriter is responsible for rewriting text that has been marked as redacted.   This is done by building
 * out new TJ/Tj layout operations and adjusted Td offset as needed.  This was hard.
 *
 * @since 7.2.0
 */
public abstract class StringObjectWriter {
    public static boolean containsFlaggedText(ArrayList<TextSprite> textOperators) {
        for (TextSprite textSprite : textOperators) {
            boolean hasFlagged = partiallyFlaggedGlyphs(textSprite.getGlyphSprites());
            if (hasFlagged) {
                return true;
            }
        }
        return false;
    }

    public static boolean partiallyFlaggedGlyphs(ArrayList<GlyphText> glyphTexts) {
        for (GlyphText glyphText : glyphTexts) {
            if (glyphText.isFlagged()) {
                return true;
            }
        }
        return false;
    }

    public static boolean fullyFlagged(ArrayList<GlyphText> glyphTexts) {
        for (GlyphText glyphText : glyphTexts) {
            if (!glyphText.isFlagged()) {
                return false;
            }
        }
        return true;
    }

    public static int flaggedCount(ArrayList<GlyphText> glyphTexts) {
        int count = 0;
        for (GlyphText glyphText : glyphTexts) {
            if (glyphText.isFlagged()) {
                count++;
            }
        }
        return count;
    }

    public abstract float writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                                  float lastTdOffset) throws IOException;

    public abstract float writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                                  float lastTdOffset) throws IOException;

    /**
     * Rewrites one show operation, whichever operator produced it.
     * <p>
     * The default routes to the Tj/TJ pair above, which keep a running Td offset the caller has to
     * carry between operations. An implementation that needs no such state - one emitting TJ
     * adjustments, which do not touch the line matrix - overrides this and returns 0.
     *
     * @param contentOutputStream stream to write the replacement to
     * @param textOperators       sprites of the show operation being rewritten
     * @param isArrayOperator     true when the source operator was TJ
     * @param lastTdOffset        offset carried from the previous show operation
     * @return offset to carry to the next show operation, 0 when none is pending
     * @throws IOException if the stream cannot be written
     */
    public float writeShownText(ByteArrayOutputStream contentOutputStream,
                                ArrayList<TextSprite> textOperators, boolean isArrayOperator,
                                float lastTdOffset) throws IOException {
        return isArrayOperator
                ? writeTJ(contentOutputStream, textOperators, lastTdOffset)
                : writeTj(contentOutputStream, textOperators, lastTdOffset);
    }


    protected static float writeLastTdOffset(ByteArrayOutputStream contentOutputStream, float lastTdOffset,
                                             float start, float advance) throws IOException {
        // still not sure how to handle this in a 100% of cases as advance can technically be negative
        // but if we have a negative glyph advance we likely have a negative font value and should
        // treat this as a positive value when writing the advance.
        advance += start;
        if (advance < 0) {
            advance = Math.abs(advance);
        }
        return writeTdOffset(contentOutputStream, advance, lastTdOffset);
    }

    protected static float writeLastTdOffset(ByteArrayOutputStream contentOutputStream, float lastTdOffset,
                                             GlyphText glyphText) throws IOException {
        float advance = glyphText != null ? glyphText.getX() + glyphText.getAdvanceX() : 0;
        // still not sure how to handle this in a 100% of cases as advance can technically be negative
        // but if we have a negative glyph advance we likely have a negative font value and should
        // treat this as a positive value when writing the advance.
        if (glyphText != null && glyphText.getAdvanceX() < 0) {
            advance = Math.abs(advance);
        }
        return writeTdOffset(contentOutputStream, advance, lastTdOffset);
    }

    protected static float writeStartTdOffset(ByteArrayOutputStream contentOutputStream, float lastTdOffset,
                                              GlyphText glyphText) throws IOException {
        float advance = glyphText.getX();
        return writeTdOffset(contentOutputStream, advance, lastTdOffset);
    }

    protected static float writeTdOffset(ByteArrayOutputStream contentOutputStream, float advance,
                                         float lastTdOffset) throws IOException {
        float delta = advance - lastTdOffset;
        lastTdOffset = advance;
        contentOutputStream.write(' ');
        contentOutputStream.write(formatReal(delta).getBytes());
        contentOutputStream.write(' ');
        contentOutputStream.write('0');
        contentOutputStream.write(" Td ".getBytes());
        return lastTdOffset;
    }

    /**
     * Formats a number the way PDF requires it.
     * <p>
     * PDF 32000-1 7.3.3 has no exponent form for a real, so {@code String.valueOf(float)} cannot be
     * used: it yields {@code 1.0E-5} for small magnitudes and {@code 1.0E7} for large ones, and a
     * conforming reader will reject or truncate the operand. Going through the float's own shortest
     * decimal representation also drops the binary noise that
     * {@code BigDecimal.valueOf(0.1f)} would otherwise spill into the stream
     * ({@code 0.10000000149011612}).
     *
     * @param value number to write into a content stream
     * @return plain decimal text, no exponent, no trailing zeros
     */
    public static String formatReal(float value) {
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }

    protected static void writeCharacterCode(GlyphText glyphText, ByteArrayOutputStream contentOutputStream)
            throws IOException {
        writeCharacterCode(glyphText.getCid(), glyphText.getFontSubTypeFormat(), contentOutputStream);
    }

    protected static void writeCharacterCode(char cid, int subType, ByteArrayOutputStream contentOutputStream) throws IOException {
        if (subType == Font.SIMPLE_FORMAT) {
            writeSimpleCharacterCode(cid, contentOutputStream);
        } else {
            writeCidCharacterCode(cid, contentOutputStream);
        }
    }

    protected static void writeSimpleCharacterCode(char cid, ByteArrayOutputStream contentOutputStream) throws IOException {
        // simple fonts
        if (cid <= 127) {
            if (cid == '(' || cid == ')' || cid == '\\') {
                contentOutputStream.write('\\');
                contentOutputStream.write(cid);
            } else if (cid == '\r' || cid == '\n') {
                // PDF 32000-1 7.3.4.2: an unescaped end-of-line inside a literal string is read
                // back as a single line feed, so a raw CR would round-trip as LF and a CRLF pair
                // would collapse. Escaping is the only way these codes survive.
                writeOctalEscape(cid, contentOutputStream);
            } else {
                contentOutputStream.write(cid);
            }
        } else {
            // Codes 128-255 are always exactly three octal digits (200-377), so this cannot run
            // short and absorb a following digit; writeOctalEscape pads regardless.
            writeOctalEscape(cid, contentOutputStream);
        }
    }

    private static void writeOctalEscape(char cid, ByteArrayOutputStream contentOutputStream) throws IOException {
        contentOutputStream.write('\\');
        contentOutputStream.write(String.format("%03o", (int) cid).getBytes());
    }

    protected static void writeCidCharacterCode(char cid, ByteArrayOutputStream contentOutputStream) throws IOException {
        // Every code must occupy the same number of hex digits or the string cannot be split back
        // into codes: a three digit code such as 0x100 written as <100> shifts everything after it
        // by a nibble.
        contentOutputStream.write(String.format("%04x", (int) cid).getBytes());
    }

    protected static void writeDelimiterStart(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) {
        int fontSubType = glyphText.getFontSubTypeFormat();
        char delimiter = fontSubType == Font.SIMPLE_FORMAT ? '(' : '<';
        contentOutputStream.write(' ');
        contentOutputStream.write(delimiter);
    }

    protected static void writeDelimiterEnd(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) throws IOException {
        writeDelimiterEnd(glyphText, contentOutputStream, true);
    }

    /**
     * Closes a string, optionally showing it.
     *
     * @param glyphText           glyph whose font decides the delimiter
     * @param contentOutputStream stream to write to
     * @param withShowOperator    true to follow the delimiter with Tj. False inside a TJ array,
     *                            where the array's own operator shows every element and an inner Tj
     *                            would be a syntax error.
     * @throws IOException if the stream cannot be written
     */
    protected static void writeDelimiterEnd(GlyphText glyphText, ByteArrayOutputStream contentOutputStream,
                                            boolean withShowOperator) throws IOException {
        int fontSubType = glyphText.getFontSubTypeFormat();
        char delimiter = fontSubType == Font.SIMPLE_FORMAT ? ')' : '>';
        contentOutputStream.write(delimiter);
        if (withShowOperator) {
            contentOutputStream.write(" Tj ".getBytes());
        }
    }

}
