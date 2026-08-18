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

package org.icepdf.core.util.redaction;

import org.icepdf.core.pobjects.graphics.TextSprite;
import org.icepdf.core.pobjects.graphics.text.GlyphText;
import org.icepdf.core.util.updater.callbacks.StringObjectWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a show operation with the redacted glyphs removed.
 * <p>
 * Whatever the source operator - {@code Tj}, {@code TJ}, {@code '} or {@code "} - the replacement is
 * a single {@code TJ} array: surviving runs become strings, and each removed run becomes one numeric
 * element that steps the text position over the gap it left.
 * <p>
 * Using {@code TJ} adjustments rather than synthesised {@code Td} displacements matters for more than
 * tidiness. A {@code Td} moves the <em>line</em> matrix, so it persists until the next {@code BT} or
 * {@code Tm} and every later operator has to be compensated for it; the adjustment number in a
 * {@code TJ} array moves only the text position, and expires with the operation. It is also correct
 * under horizontal scaling for free - {@code Tz} scales the adjustment and the glyph widths by the
 * same factor, so the ratio between them is invariant - and it applies to the vertical axis in a
 * vertical writing mode, which an x-only {@code Td} cannot express.
 * <p>
 * <b>How far to step.</b> The positions the parser recorded already encode where a reader lands
 * naturally: after showing glyph <i>i</i> the reader sits at
 * {@code x(i) + advance(i) + charSpacing (+ wordSpacing for a space)}. So no adjustment is needed
 * between two surviving neighbours, and where a run was removed the gap is the difference between
 * where the reader would be and where the next survivor sits. Converting that gap to the thousandths
 * of an em a {@code TJ} element is measured in gives {@code -1000 * gap / fontSize}.
 *
 * @since 7.2.0
 */
public class RedactedStringObjectWriter extends StringObjectWriter {

    /**
     * Gaps below this are dropped rather than written: they are float noise from accumulating
     * advances, not a real displacement, and an adjustment of -0.0001 costs bytes and says nothing.
     */
    private static final float NEGLIGIBLE_ADJUSTMENT = 0.01f;

    @Override
    public float writeShownText(ByteArrayOutputStream contentOutputStream,
                                ArrayList<TextSprite> textOperators, boolean isArrayOperator,
                                float lastTdOffset) throws IOException {
        writeRedactedArray(contentOutputStream, textOperators);
        // A TJ adjustment does not move the line matrix, so nothing is left pending for the next
        // show operation to compensate.
        return 0;
    }

    /**
     * Emits the whole show operation as one {@code TJ} array.
     *
     * @param contentOutputStream stream to write to
     * @param textOperators       sprites of the operation being rewritten
     * @throws IOException if the stream cannot be written
     */
    private void writeRedactedArray(ByteArrayOutputStream contentOutputStream,
                                    ArrayList<TextSprite> textOperators) throws IOException {
        List<Placed> glyphs = flatten(textOperators);
        boolean anySurvivor = false;
        for (Placed placed : glyphs) {
            if (!placed.glyphText.isFlagged()) {
                anySurvivor = true;
                break;
            }
        }
        if (!anySurvivor) {
            // Everything was redacted. Emit nothing at all rather than an empty array: there is no
            // text left to show and no position left to preserve, since nothing follows it.
            return;
        }

        contentOutputStream.write(' ');
        contentOutputStream.write('[');
        boolean stringOpen = false;
        GlyphText openedWith = null;
        // Where a reader will be once everything emitted so far has been shown. Seeded with the
        // start of the operation, which is where it sits before the first glyph.
        float readerPosition = glyphs.get(0).position();

        for (int i = 0; i < glyphs.size(); i++) {
            Placed placed = glyphs.get(i);
            if (placed.glyphText.isFlagged()) {
                continue;
            }
            float gap = placed.position() - readerPosition;
            if (Math.abs(gap) > NEGLIGIBLE_ADJUSTMENT) {
                if (stringOpen) {
                    writeDelimiterEnd(openedWith, contentOutputStream, false);
                    stringOpen = false;
                }
                contentOutputStream.write(formatReal(-1000f * gap / placed.fontSize()).getBytes());
            }
            if (!stringOpen) {
                writeDelimiterStart(placed.glyphText, contentOutputStream);
                openedWith = placed.glyphText;
                stringOpen = true;
            }
            writeCharacterCode(placed.glyphText, contentOutputStream);
            readerPosition = placed.position() + placed.naturalAdvance();
        }

        if (stringOpen) {
            writeDelimiterEnd(openedWith, contentOutputStream, false);
        }
        contentOutputStream.write("] TJ ".getBytes());
    }

    /**
     * Flattens the sprites into one ordered run, since a redaction may start in one array element
     * and end in another and the arithmetic only works over the whole operation.
     *
     * @param textOperators sprites of the operation
     * @return every glyph, in show order, paired with the sprite it belongs to
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
     * One glyph together with the sprite whose text state placed it.
     */
    private static final class Placed {
        private final GlyphText glyphText;
        private final TextSprite textSprite;

        private Placed(GlyphText glyphText, TextSprite textSprite) {
            this.glyphText = glyphText;
            this.textSprite = textSprite;
        }

        /**
         * @return the glyph's position along the font's writing axis, in text space. Deliberately
         * the sprite's writing mode and not GlyphText.isVerticalWriting(), which is a page-space
         * question and reports vertical for horizontal text on a rotated page.
         */
        private float position() {
            return textSprite.isVerticalWriting() ? glyphText.getY() : glyphText.getX();
        }

        /**
         * @return how far a reader advances after showing this glyph, in the same units as
         * {@link #position()}
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
            // A zero size would make the adjustment infinite; nothing is visible at that size
            // anyway, so fall back to a unit scale rather than emitting a broken number.
            return size != 0 ? size : 1f;
        }
    }

    // -- superseded by writeShownText, retained until the text editor moves to the shared emitter --

    public float writeTj(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                         float lastTdOffset) throws IOException {
        return writeShownText(contentOutputStream, textOperators, false, lastTdOffset);
    }

    public float writeTJ(ByteArrayOutputStream contentOutputStream, ArrayList<TextSprite> textOperators,
                         float lastTdOffset) throws IOException {
        return writeShownText(contentOutputStream, textOperators, true, lastTdOffset);
    }
}
