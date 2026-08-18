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
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    private static boolean partiallyFlaggedGlyphs(ArrayList<GlyphText> glyphTexts) {
        for (GlyphText glyphText : glyphTexts) {
            if (glyphText.isFlagged()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rewrites one show operation as a single {@code TJ} array, whatever operator produced it.
     * <p>
     * Surviving runs become strings; a removed run becomes a numeric element stepping over the gap
     * it left, plus whatever {@link #writeRunReplacement} chooses to put in its place. Numeric
     * elements move the text position only, unlike a {@code Td}, which moves the line matrix and
     * would then have to be compensated for in every later operator.
     * <p>
     * How far to step comes from the positions the parser recorded: after showing a glyph the reader
     * sits at {@code position + advance + charSpacing (+ wordSpacing for a space)}, so two surviving
     * neighbours need no adjustment and a gap is the difference between where the reader lands and
     * where the next survivor sits. A {@code TJ} element is in thousandths of an em and subtracts,
     * hence {@code -1000 * gap / fontSize}.
     *
     * @param contentOutputStream stream to write the replacement to
     * @param textOperators       sprites of the show operation being rewritten
     * @throws IOException if the stream cannot be written
     */
    public void writeShownText(ByteArrayOutputStream contentOutputStream,
                               ArrayList<TextSprite> textOperators) throws IOException {
        List<Placed> glyphs = flatten(textOperators);
        if (!hasContentToWrite(glyphs)) {
            return;
        }

        contentOutputStream.write(' ');
        contentOutputStream.write('[');
        OpenString openString = new OpenString();
        // Where a reader will be once everything emitted so far has been shown, seeded with the
        // start of the operation.
        float readerPosition = glyphs.get(0).position();

        for (int i = 0; i < glyphs.size(); ) {
            Placed placed = glyphs.get(i);
            if (!placed.glyphText.isFlagged()) {
                readerPosition = writeSurvivingGlyph(contentOutputStream, placed, readerPosition, openString);
                i++;
                continue;
            }
            // A removed run. Only step to where it began if something is going to be written
            // there; with nothing to put in the gap the next surviving glyph adjusts over the whole
            // run in one element, rather than splitting it into two that a reader must add up.
            if (writesReplacementText()) {
                readerPosition = writeAdjustment(contentOutputStream, placed.position(), readerPosition,
                        placed.fontSize(), openString);
                closeString(contentOutputStream, openString);
                readerPosition += writeRunReplacement(contentOutputStream, placed.textSprite, placed.glyphText);
            }
            while (i < glyphs.size() && glyphs.get(i).glyphText.isFlagged()) {
                i++;
            }
        }

        closeString(contentOutputStream, openString);
        contentOutputStream.write("] TJ ".getBytes());
    }

    /**
     * Text to put in place of a removed run, for an implementation that replaces rather than simply
     * removes. The default writes nothing, which is what a redaction wants.
     *
     * @param contentOutputStream stream to write to
     * @param textSprite          sprite whose text state placed the removed run
     * @param firstRemoved        first glyph of the run being replaced
     * @return advance consumed by whatever was written, in the same units as a glyph position
     * @throws IOException if the stream cannot be written
     */
    protected float writeRunReplacement(ByteArrayOutputStream contentOutputStream,
                                        TextSprite textSprite, GlyphText firstRemoved) throws IOException {
        return 0;
    }

    /**
     * True when the operation still has something to emit. An operation whose glyphs were all
     * removed, and which puts nothing in their place, is dropped entirely rather than written as an
     * empty array: there is no text to show and no position to preserve, since nothing follows it.
     */
    private boolean hasContentToWrite(List<Placed> glyphs) {
        if (glyphs.isEmpty()) {
            return false;
        }
        for (Placed placed : glyphs) {
            if (!placed.glyphText.isFlagged()) {
                return true;
            }
        }
        return writesReplacementText();
    }

    /**
     * Whether {@link #writeRunReplacement} still has something to write. Consulted before stepping
     * to a removed run - with nothing to put there, the next surviving glyph adjusts over the whole
     * run in one element - and to decide whether an operation with no survivors is dropped entirely.
     *
     * @return true when a replacement is still outstanding
     */
    protected boolean writesReplacementText() {
        return false;
    }

    private float writeSurvivingGlyph(ByteArrayOutputStream contentOutputStream, Placed placed,
                                      float readerPosition, OpenString openString) throws IOException {
        readerPosition = writeAdjustment(contentOutputStream, placed.position(), readerPosition,
                placed.fontSize(), openString);
        if (openString.glyphText == null) {
            writeDelimiterStart(placed.glyphText, contentOutputStream);
            openString.glyphText = placed.glyphText;
        }
        writeCharacterCode(placed.glyphText, contentOutputStream);
        return placed.position() + placed.naturalAdvance();
    }

    /**
     * Emits an adjustment when the reader is not already where the next content belongs.
     *
     * @return the reader position after the adjustment
     */
    private float writeAdjustment(ByteArrayOutputStream contentOutputStream, float target,
                                  float readerPosition, float fontSize, OpenString openString) throws IOException {
        float adjustment = -1000f * (target - readerPosition) / fontSize;
        if (Math.abs(adjustment) <= NEGLIGIBLE_ADJUSTMENT) {
            return readerPosition;
        }
        closeString(contentOutputStream, openString);
        // Numbers need a separator: PDF does not treat '-' as a delimiter, so two adjacent
        // adjustments written as "-250-3000" lex as one malformed number rather than two elements.
        contentOutputStream.write(' ');
        contentOutputStream.write(formatReal(adjustment).getBytes());
        return target;
    }

    private void closeString(ByteArrayOutputStream contentOutputStream, OpenString openString) throws IOException {
        if (openString.glyphText != null) {
            writeDelimiterEnd(openString.glyphText, contentOutputStream);
            openString.glyphText = null;
        }
    }

    /**
     * Flattens the sprites into one ordered run, since a removal may start in one array element and
     * end in another and the arithmetic only works over the whole operation.
     */
    private List<Placed> flatten(ArrayList<TextSprite> textOperators) {
        List<Placed> glyphs = new ArrayList<>();
        for (TextSprite textSprite : textOperators) {
            if (textSprite == null) {
                continue;
            }
            for (GlyphText glyphText : textSprite.getGlyphSprites()) {
                glyphs.add(new Placed(glyphText, textSprite));
            }
        }
        return glyphs;
    }

    /**
     * Adjustments smaller than this are dropped: they are float noise from accumulating advances
     * rather than a real displacement, and cost bytes to say nothing. Expressed in the thousandths
     * of an em a TJ element is written in, so the same rule applies at every font size - measuring
     * the raw gap instead would suppress ten times as much at 60pt as at 6pt.
     */
    private static final float NEGLIGIBLE_ADJUSTMENT = 0.5f;

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /**
     * The string currently open in the array, if any. Held in an object so the helpers above can
     * close it without threading it back through every return value.
     */
    private static final class OpenString {
        private GlyphText glyphText;
    }

    /**
     * One glyph together with the sprite whose text state placed it.
     */
    protected static final class Placed {
        private final GlyphText glyphText;
        private final TextSprite textSprite;

        private Placed(GlyphText glyphText, TextSprite textSprite) {
            this.glyphText = glyphText;
            this.textSprite = textSprite;
        }

        /**
         * The glyph's position along the font's writing axis, in text space. Deliberately the
         * sprite's writing mode and not {@code GlyphText.isVerticalWriting()}, which is a page-space
         * question and reports vertical for horizontal text on a rotated page.
         */
        private float position() {
            return textSprite.isVerticalWriting() ? glyphText.getY() : glyphText.getX();
        }

        /**
         * How far a reader advances after showing this glyph, in the same units as
         * {@link #position()}.
         */
        private float naturalAdvance() {
            float advance = textSprite.isVerticalWriting()
                    ? glyphText.getAdvanceY() : glyphText.getAdvanceX();
            advance += textSprite.getCharSpacing();
            // Word spacing applies to the single byte code 32 only.
            if (glyphText.getCid() == 32) {
                advance += textSprite.getWordSpacing();
            }
            return advance;
        }

        private float fontSize() {
            float size = textSprite.getFontSize();
            return size != 0 ? size : 1f;
        }
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
            writeOctalEscape(cid, contentOutputStream);
        }
    }

    private static void writeOctalEscape(char cid, ByteArrayOutputStream contentOutputStream) {
        contentOutputStream.write('\\');
        // Three digits always, so the escape cannot run short and absorb a following digit. Written
        // digit by digit rather than through String.format, which builds a Formatter per glyph.
        contentOutputStream.write('0' + ((cid >> 6) & 0x7));
        contentOutputStream.write('0' + ((cid >> 3) & 0x7));
        contentOutputStream.write('0' + (cid & 0x7));
    }

    protected static void writeCidCharacterCode(char cid, ByteArrayOutputStream contentOutputStream) throws IOException {
        // Every code must occupy the same number of hex digits or the string cannot be split back
        // into codes: a three digit code such as 0x100 written as <100> shifts everything after it
        // by a nibble.
        for (int shift = 12; shift >= 0; shift -= 4) {
            contentOutputStream.write(HEX_DIGITS[(cid >> shift) & 0xF]);
        }
    }

    protected static void writeDelimiterStart(GlyphText glyphText, ByteArrayOutputStream contentOutputStream) {
        int fontSubType = glyphText.getFontSubTypeFormat();
        char delimiter = fontSubType == Font.SIMPLE_FORMAT ? '(' : '<';
        contentOutputStream.write(' ');
        contentOutputStream.write(delimiter);
    }

    /**
     * Closes a string. No show operator follows it: every string this writer emits is an element of
     * a TJ array, and the array's own operator shows them all.
     *
     * @param glyphText           glyph whose font decides the delimiter
     * @param contentOutputStream stream to write to
     * @throws IOException if the stream cannot be written
     */
    protected static void writeDelimiterEnd(GlyphText glyphText, ByteArrayOutputStream contentOutputStream)
            throws IOException {
        char delimiter = glyphText.getFontSubTypeFormat() == Font.SIMPLE_FORMAT ? ')' : '>';
        contentOutputStream.write(delimiter);
    }

}
