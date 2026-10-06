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

import org.apache.fontbox.cmap.CMap;
import org.icepdf.core.pobjects.fonts.Encoding;
import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.URL;
import java.util.Map;

/**
 * A FontFile whose every glyph has the same advance, so the text-space coordinates a writer emits
 * are exact and the expected values in a test can be written by hand.
 * <p>
 * With a constant advance of {@code w}, the glyph at index {@code i} of a run starting at {@code x0}
 * sits at {@code x0 + i*w}, and a removed run of {@code n} glyphs is worth exactly {@code n*w} of
 * text space - which is the number a TJ adjustment has to reproduce.
 * <p>
 * Only metrics are implemented. Anything to do with rasterising, outlines or encoding tables throws,
 * so a test that strays into painting fails loudly rather than silently asserting against a stub.
 */
public class StubFontFile implements FontFile {

    /**
     * Default advance in glyph space, chosen so that a 12pt font gives a round 6.0 of text space.
     */
    public static final float DEFAULT_ADVANCE = 0.5f;

    private final float advance;
    private final float size;
    private final String name;

    public StubFontFile() {
        this(DEFAULT_ADVANCE, 1f, "StubFont");
    }

    public StubFontFile(float advance, float size, String name) {
        this.advance = advance;
        this.size = size;
        this.name = name;
    }

    /**
     * The advance every glyph of this font reports, scaled by the font size - the value a writer
     * sees through {@link #getAdvance(char)}.
     *
     * @return advance in text space
     */
    public float advanceInTextSpace() {
        return advance * size;
    }

    @Override
    public Point2D getAdvance(char ech) {
        return new Point2D.Float(advanceInTextSpace(), 0);
    }

    @Override
    public FontFile deriveFont(float pointSize) {
        return new StubFontFile(advance, pointSize, name);
    }

    @Override
    public FontFile deriveFont(AffineTransform at) {
        return this;
    }

    @Override
    public FontFile deriveFont(Encoding encoding, CMap toUnicode) {
        return this;
    }

    @Override
    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth, float ascent,
                               float descent, Rectangle2D bbox, char[] diff) {
        return this;
    }

    @Override
    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth,
                               float ascent, float descent, Rectangle2D bbox, char[] diff) {
        return this;
    }

    @Override
    public boolean canDisplay(char ech) {
        return true;
    }

    @Override
    public String toUnicode(char displayChar) {
        return String.valueOf(displayChar);
    }

    @Override
    public String toUnicode(String displayText) {
        return displayText;
    }

    @Override
    public char toSelector(char unicode) {
        return unicode;
    }

    @Override
    public float getSize() {
        return size;
    }

    @Override
    public double getAscent() {
        return size * 0.75;
    }

    @Override
    public double getDescent() {
        return -size * 0.25;
    }

    @Override
    public Rectangle2D getMaxCharBounds() {
        return new Rectangle2D.Float(0, (float) -getAscent(), advanceInTextSpace(), size);
    }

    @Override
    public Rectangle2D getBounds(char estr, int beginIndex, int limit) {
        return new Rectangle2D.Float(0, (float) -getAscent(), advanceInTextSpace(), size);
    }

    @Override
    public AffineTransform getFontTransform() {
        return new AffineTransform();
    }

    @Override
    public AffineTransform getTransform() {
        return new AffineTransform();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFamily() {
        return name;
    }

    @Override
    public String getFormat() {
        return "Type1";
    }

    @Override
    public int getNumGlyphs() {
        return 256;
    }

    @Override
    public int getStyle() {
        return 0;
    }

    @Override
    public int getRights() {
        return 0;
    }

    @Override
    public boolean isHinted() {
        return false;
    }

    @Override
    public char getSpace() {
        return ' ';
    }

    @Override
    public ByteEncoding getByteEncoding() {
        return ByteEncoding.ONE_BYTE;
    }

    @Override
    public URL getSource() {
        return null;
    }

    @Override
    public CMap getToUnicode() {
        return null;
    }

    @Override
    public org.apache.fontbox.encoding.Encoding getEncoding() {
        return null;
    }

    // -- not metrics: a redaction writer has no business here -------------------------------------

    @Override
    public void paint(Graphics2D g, char estr, float x, float y, long layout, int mode,
                      Color strokeColor) {
        throw new UnsupportedOperationException("StubFontFile does not paint");
    }

    @Override
    public Shape getGlphyShape(char estr) {
        throw new UnsupportedOperationException("StubFontFile has no glyph shapes");
    }

    @Override
    public Shape getOutline(char estr, float x, float y) {
        throw new UnsupportedOperationException("StubFontFile has no outlines");
    }
}
