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
package org.icepdf.core.pobjects.graphics.text;

import org.icepdf.core.pobjects.Name;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link PageText} tree by hand, with no PDF, no parser and no font.
 * <p>
 * Everything downstream of extraction - offsets, hit testing, search, reading order - is a function
 * of glyph geometry and nothing else, so a test can state that geometry directly and get exact
 * numbers back.  Every glyph here is {@link #GLYPH_WIDTH} wide and {@link #LINE_HEIGHT} tall on a
 * fixed grid, which makes the page-space coordinate of any character in a fixture arithmetic rather
 * than a guess: character {@code i} of a run starting at {@code x} spans
 * {@code [x + i*GLYPH_WIDTH, x + (i+1)*GLYPH_WIDTH)}.
 * <p>
 * Words are added to lines explicitly rather than through {@link LineText#addText}, so the word
 * split is the one the test asked for and not the one the space-detection heuristic would infer.
 * Page space has y increasing upwards, so the first line of a page has the <em>largest</em> y.
 */
public final class TextFixtures {

    /** Width of every glyph, in page space. */
    public static final double GLYPH_WIDTH = 10;
    /** Height of every glyph, and the baseline-to-baseline step between lines. */
    public static final double LINE_HEIGHT = 12;
    /** y of the first line of a page; each following line sits {@code LINE_HEIGHT * 2} lower. */
    public static final double FIRST_LINE_Y = 700;
    /** x of the left margin. */
    public static final double LEFT_MARGIN = 50;

    private static final Name FONT = new Name("F1");

    private TextFixtures() {
    }

    /**
     * A single glyph on the grid.
     *
     * @param character character the glyph spells
     * @param x         left edge, page space
     * @param y         bottom edge, page space
     * @return the glyph
     */
    public static GlyphText glyph(char character, double x, double y) {
        return new GlyphText((float) x, (float) y, (float) GLYPH_WIDTH, 0,
                new Rectangle2D.Double(x, y, GLYPH_WIDTH, LINE_HEIGHT),
                0, character, String.valueOf(character), FONT);
    }

    /**
     * A word laid out left to right from {@code x}.
     *
     * @param text       characters of the word
     * @param x          left edge of the first glyph
     * @param y          bottom edge
     * @param whiteSpace true to flag the word as the inter-word spacing
     * @return the word
     */
    public static WordText word(String text, double x, double y, boolean whiteSpace) {
        WordText word = new WordText(0);
        for (int i = 0; i < text.length(); i++) {
            word.addText(glyph(text.charAt(i), x + i * GLYPH_WIDTH, y));
        }
        word.setWhiteSpace(whiteSpace);
        return word;
    }

    /**
     * A line of words separated by single spaces, starting at {@link #LEFT_MARGIN}.
     *
     * @param y     bottom edge of the line
     * @param words words of the line, in reading order
     * @return the line
     */
    public static LineText line(double y, String... words) {
        return lineAt(LEFT_MARGIN, y, words);
    }

    /**
     * A line of words separated by single spaces, starting at an explicit x.
     *
     * @param x     left edge of the first word
     * @param y     bottom edge of the line
     * @param words words of the line, in reading order
     * @return the line
     */
    public static LineText lineAt(double x, double y, String... words) {
        LineText line = new LineText(0);
        List<WordText> built = new ArrayList<>();
        double cursor = x;
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                built.add(word(" ", cursor, y, true));
                cursor += GLYPH_WIDTH;
            }
            built.add(word(words[i], cursor, y, false));
            cursor += words[i].length() * GLYPH_WIDTH;
        }
        line.addAll(built);
        return line;
    }

    /**
     * A page of the given lines, in the order supplied.
     *
     * @param lines lines of the page
     * @return page text holding them
     */
    public static PageText page(LineText... lines) {
        PageText pageText = new PageText();
        ArrayList<LineText> pageLines = new ArrayList<>();
        java.util.Collections.addAll(pageLines, lines);
        pageText.addPageLines(pageLines);
        return pageText;
    }

    /**
     * A page whose lines each hold one run of words, laid out top to bottom on the grid.
     *
     * @param lines one string per line; words are split on single spaces
     * @return page text holding them
     */
    public static PageText pageOf(String... lines) {
        LineText[] built = new LineText[lines.length];
        for (int i = 0; i < lines.length; i++) {
            built[i] = line(FIRST_LINE_Y - i * LINE_HEIGHT * 2, lines[i].split(" "));
        }
        return page(built);
    }

    /**
     * x of the centre of the {@code index}-th glyph of a line that starts at {@link #LEFT_MARGIN},
     * counting the single spaces between words.
     *
     * @param index character index within the line
     * @return page-space x
     */
    public static double glyphCentreX(int index) {
        return LEFT_MARGIN + index * GLYPH_WIDTH + GLYPH_WIDTH / 2;
    }

    /**
     * y of the centre of line {@code index} of a page built by {@link #pageOf}.
     *
     * @param index line index from the top
     * @return page-space y
     */
    public static double lineCentreY(int index) {
        return FIRST_LINE_Y - index * LINE_HEIGHT * 2 + LINE_HEIGHT / 2;
    }
}
