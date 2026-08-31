/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.text;

import org.icepdf.core.util.Defs;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Word text represents an individual word in the document.  A word can
 * also represent white space between words th isWhiteSpace method can be used
 * to distinguish between words and whiteSpace
 * <br>
 * If extracted text has extract white space then the space width fraction
 * can be adjusted.  The deault value a 4th of the current character width.  To
 * add more sapces the number can be increase or decrease to limit the number
 * of spaces that are added. The system property is as follows:
 * Default<br>
 * org.icepdf.core.views.page.text.spaceFraction=3
 *
 * @since 4.0
 */
public class WordText extends AbstractText implements TextSelect {

    private static final Logger logger =
            Logger.getLogger(WordText.class.getName());

    // Space Glyph width fraction, the default is 1/x of the character width,
    // constitutes a potential space between glyphs.
    public static int spaceFraction;

    public static boolean autoSpaceInsertion;

    // When true a detected gap is filled with as many synthetic spaces as the gap width implies (layout-preserving
    // mode).  The default emits a single space per gap, which is what search/extraction want - multiple runs of spaces
    // produce variable spacing that breaks phrase matching.
    public static boolean insertMultipleSpaces;

    // When true, space/new-line detection is evaluated along each glyph's actual writing axis so that rotated or
    // vertically-stacked text is grouped along its baseline instead of being shattered one glyph per line.
    public static boolean detectVerticalText;

    static {
        // sets the shadow colour of the decorator.
        try {
            spaceFraction = Defs.sysPropertyInt(
                    "org.icepdf.core.views.page.text.spaceFraction", 3);
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text space fraction");
            }
        }
        // sets the shadow colour of the decorator.
        try {
            autoSpaceInsertion = Defs.sysPropertyBoolean(
                    "org.icepdf.core.views.page.text.autoSpace", true);
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text text auto space detection");
            }
        }
        // opt-in layout-preserving mode; default single space per detected gap.
        try {
            insertMultipleSpaces = Defs.sysPropertyBoolean(
                    "org.icepdf.core.views.page.text.multipleSpaces", false);
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading text multiple space insertion");
            }
        }
        // rotated/vertical text grouping; on by default - horizontal text is unaffected because the vertical
        // branches are gated per glyph on the actual writing axis.  Set the property false to restore the old
        // one-glyph-per-line behaviour for rotated/stacked text.
        try {
            detectVerticalText = Defs.sysPropertyBoolean(
                    "org.icepdf.core.views.page.text.detectVerticalText", true);
        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading vertical text detection");
            }
        }
    }

    private GlyphText currentGlyph;
    private final ArrayList<GlyphText> glyphs;

    // cached text values.
    private final StringBuilder text;
    // is glyph white space.
    private boolean isWhiteSpace;
    // reference to last added text.
    private int previousGlyphText;

    public WordText(float pageRotation) {
        text = new StringBuilder();
        glyphs = new ArrayList<>(4);
        this.pageRotation = pageRotation;
    }

    /**
     * An independent copy, glyphs and all. See {@link GlyphText#copy()} for why a placement needs
     * one.
     *
     * @return a copy holding copies of this word's glyphs
     */
    public WordText copy() {
        WordText copy = new WordText(pageRotation);
        for (GlyphText glyph : glyphs) {
            copy.addText(glyph.copy());
        }
        copy.isWhiteSpace = isWhiteSpace;
        return copy;
    }

    public int size(){
        return text.length();
    }

    public boolean isWhiteSpace() {
        return isWhiteSpace;
    }

    public void setWhiteSpace(boolean whiteSpace) {
        isWhiteSpace = whiteSpace;
    }

    protected boolean detectNewLine(GlyphText sprite) {
        if (currentGlyph != null && autoSpaceInsertion) {
            Rectangle2D previousBounds = currentGlyph.getTextExtractionBounds();
            Rectangle2D currentBounds = sprite.getTextExtractionBounds();

            if (detectVerticalText && currentGlyph.isVerticalWriting()) {
                // vertical text runs along y, so a new line is a shift along the cross (x) axis.
                double tolerance = previousBounds.getWidth() / spaceFraction;
                double xdiff = Math.abs(currentBounds.getX() - previousBounds.getX());
                return xdiff > tolerance;
            }
            // half previous glyph width will be used to determine a space
            double tolerance = previousBounds.getHeight() / spaceFraction;
            // checking the y coordinate as well as any shift normall means a new work, this might need to get fuzzy later.
            double ydiff = Math.abs(currentBounds.getY() - previousBounds.getY());
            return ydiff > tolerance;
        } else {
            return false;
        }
    }

    protected boolean detectSpace(GlyphText sprite) {
        if (currentGlyph != null && autoSpaceInsertion) {
            Rectangle2D previousBounds = currentGlyph.getTextExtractionBounds();
            Rectangle2D currentBounds = sprite.getTextExtractionBounds();

            if (detectVerticalText && currentGlyph.isVerticalWriting()) {
                // vertical text advances along y; the inter-glyph gap that marks a space is measured along y and the
                // reading direction may be either sign, so take the smaller of the two box-to-box gaps.
                double gapDown = Math.abs(currentBounds.getY() - (previousBounds.getY() + previousBounds.getHeight()));
                double gapUp = Math.abs(previousBounds.getY() - (currentBounds.getY() + currentBounds.getHeight()));
                double space = Math.min(gapDown, gapUp);
                double tolerance = previousBounds.getHeight() / spaceFraction;
                return space > tolerance;
            }
            // spaces can be negative if we have a LTR layout.
            double space = Math.abs(currentBounds.getX() - (previousBounds.getX() + previousBounds.getWidth()));
            // half previous glyph width will be used to determine a space
            double tolerance = previousBounds.getWidth() / spaceFraction;
            // checking the y coordinate as well as any shift normall means a new work, this might need to get fuzzy later.
            double ydiff = Math.abs(currentBounds.getY() - previousBounds.getY());
            return space > tolerance || ydiff > tolerance;
        } else {
            return false;
        }
    }

    protected static boolean detectPunctuation(GlyphText sprite, WordText currentWord) {
        String glyphText = sprite.getUnicode();
        // make sure we don't have a decimal, we want to keep double numbers
        // as one word.
        if (glyphText != null && glyphText.length() > 0) {
            int c = glyphText.charAt(0);
            return isPunctuation(c) && !isDigit(currentWord);
        } else {
            return false;
        }
    }

    protected static boolean detectWhiteSpace(GlyphText sprite) {
        String glyphText = sprite.getUnicode();
        if (glyphText != null && glyphText.length() > 0) {
            int c = glyphText.charAt(0);
            return isWhiteSpace(c);
        } else {
            return false;
        }
    }

    public static boolean isPunctuation(int c) {
        return ((c == '.') || (c == ',') || (c == '?') || (c == '!') ||
                (c == ':') || (c == ';') || (c == '"') || (c == '\'')
                || (c == '/') || (c == '\\') || (c == '`') || (c == '#'));
    }

    public static boolean isWhiteSpace(int c) {
        return ((c == ' ') || (c == '\t') || (c == '\r') ||
                (c == '\n') || (c == '\f') || (c == 160));
    }

    public static boolean isDigit(WordText currentWord) {
        if (currentWord != null) {
            int c = currentWord.getPreviousGlyphText();
            return isDigit((char) c);
        } else {
            return false;
        }
    }

    public static boolean isDigit(char c) {
        return c >= 48 && c <= 57;
    }

    protected WordText buildSpaceWord(GlyphText sprite, boolean autoSpaceInsertion) {

        // because we are in a normalized user space we can work with ints
        Rectangle2D.Double bounds1 = currentGlyph.getTextExtractionBounds();
        Rectangle2D.Double bounds2 = sprite.getTextExtractionBounds();
        double space = bounds2.x - (bounds1.x + bounds1.width);

        // max width of previous and next glyph, average can be broken by l or i etc.
        double maxWidth = Math.max(bounds1.width, bounds2.width) / 2f;
        int spaces = (int) (space / maxWidth);
        if (spaces == 0) {
            spaces = 1;
        }
        // add extra spaces
        WordText whiteSpace = new WordText(this.pageRotation);
        double offset;
        GlyphText spaceText = null;
        Rectangle2D.Double spaceBounds;
        // RTL layout
        double spaceWidth = space / spaces;
        boolean ltr = true;
        if (spaces > 0) {
            offset = bounds1.x + bounds1.width;
            spaceBounds = new Rectangle2D.Double(
                    bounds1.x + bounds1.width,
                    bounds1.y,
                    spaceWidth, bounds1.height);
        }
        // LTR layout
        else {
            ltr = false;
            offset = bounds1.x - bounds1.width;
            spaces = 1;//Math.abs(spaces);
            spaceBounds = new Rectangle2D.Double(
                    bounds.x - spaceWidth,
                    bounds1.y,
                    spaceWidth, bounds1.height);
        }
        // todo: consider just using one space with a wider bound
        // Max out the spaces in the case the spaces value scale factor was
        // not correct.  We can end up with a very large number of spaces being
        // inserted in some cases.
        if (autoSpaceInsertion && insertMultipleSpaces) {
            for (int i = 0; i < spaces && i < 50; i++) {
                whiteSpace = autoSpaceCalculation(offset, spaceBounds, whiteSpace);
                if (ltr) {
                    spaceBounds.x += spaceBounds.width;
                    offset += spaceWidth;
                } else {
                    spaceBounds.x -= spaceBounds.width;
                    offset -= spaceWidth;
                }
            }
        } else {
            whiteSpace = autoSpaceCalculation(offset, spaceBounds, whiteSpace);
        }
        return whiteSpace;
    }

    private WordText autoSpaceCalculation(double offset,
                                          Rectangle2D.Double spaceBounds,
                                          WordText whiteSpace) {
        GlyphText spaceText = new GlyphText((float) offset,
                currentGlyph.getY(),
                (float) offset, 0,
                new Rectangle2D.Double(spaceBounds.x,
                        spaceBounds.y,
                        spaceBounds.width,
                        spaceBounds.height),
                0,
                (char) 32,
                String.valueOf((char) 32),
                currentGlyph.getFontName());
        // synthetic spaces are never normalized, so carry the writing direction of the glyph they follow to keep
        // rotated/vertical runs classified consistently.
        spaceText.inheritWriteDirection(currentGlyph);
        whiteSpace.addText(spaceText);
        whiteSpace.setWhiteSpace(true);
        return whiteSpace;
    }


    protected void addText(GlyphText sprite) {
        // the sprite
        glyphs.add(sprite);

        currentGlyph = sprite;
        Rectangle2D.Double rect = sprite.getBounds();
        // append the bounds calculation
        if (bounds == null) {
            bounds = new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height);
        } else {
            if (glyphs.size() > 1) {
                // compare with previous bounds to see if we can extend the width
                GlyphText previous = glyphs.get(glyphs.size() - 2);
                Rectangle2D.Double previousRect = previous.getBounds();
                double diff = rect.x - (previousRect.x + previousRect.width);
                if (diff > 0) {
                    previousRect.setRect(previousRect.x, previousRect.y,
                            previousRect.width + diff, previousRect.height);
                }
            }
            bounds.add(sprite.getBounds());
        }
        if (textSelectionBounds == null) {
            rect = sprite.getTextSelectionBounds();
            textSelectionBounds = new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height);
        } else {
            textSelectionBounds.add(sprite.getTextSelectionBounds());
        }

        // append the text that maps up the sprite
        String unicode = sprite.getUnicode();
        previousGlyphText = unicode != null && unicode.length() > 0 ?
                unicode.charAt(0) : 0;
        text.append(unicode);
    }

    public Rectangle2D.Double getBounds() {
        if (bounds == null) {
            // increase bounds as glyphs are detected.
            for (GlyphText glyph : glyphs) {
                if (bounds == null) {
                    bounds = new Rectangle2D.Double();
                    bounds.setRect(glyph.getBounds());
                } else {
                    bounds.add(glyph.getBounds());
                }
                if (textSelectionBounds == null) {
                    Rectangle2D.Double rect = glyph.getTextSelectionBounds();
                    textSelectionBounds = new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height);
                } else {
                    textSelectionBounds.add(glyph.getTextSelectionBounds());
                }
            }
        }
        return bounds;
    }

    public ArrayList<GlyphText> getGlyphs() {
        return glyphs;
    }

    public StringBuilder getSelected() {
        StringBuilder selectedText = new StringBuilder();
        for (GlyphText glyph : glyphs) {
            if (glyph.isSelected()) {
                selectedText.append(glyph.getUnicode());
            }
        }
        return selectedText;
    }

    public void clearHighlighted() {
        setHighlighted(false);
        setHasHighlight(false);
        for (GlyphText glyph : glyphs) {
            glyph.setHighlighted(false);
        }
    }

    public void clearSelected() {
        setSelected(false);
        setHasSelected(false);
        for (GlyphText glyph : glyphs) {
            glyph.setSelected(false);
        }
    }

    public void clearHighlightedCursor() {
        setHasHighlightCursor(false);
        setHighlightCursor(false);
        for (GlyphText glyph : glyphs) {
            glyph.setHasHighlightCursor(false);
        }
    }

    public void selectAll() {
        setSelected(true);
        setHasSelected(true);
        for (GlyphText glyph : glyphs) {
            glyph.setSelected(true);
        }
    }

    public String getText() {
        // iterate of sprites and get text.
//        if (isWhiteSpace) {
//            return text.toString().replace(" ", "_|");
//        } else if (text.toString().equals(""))
//            return text.toString().replace("", "*");
//        else {
        return text.toString();
//        }
    }

    public int getPreviousGlyphText() {
        return previousGlyphText;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WordText) {
            WordText wordText = (WordText) obj;
            return wordText.getBounds().equals(this.getBounds()) &&
                    wordText.getText().equals(this.getText());
        }
        return super.equals(obj);
    }

    public String toString() {
        return getText();
    }
}
