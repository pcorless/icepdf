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
package org.icepdf.core.pobjects.fonts;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.URL;
import java.util.Map;

/**
 * Font file interfaces.  Common methods which encapsulate NFont and OFont
 * font rendering libraries.
 *
 * @since 3.0
 */
public interface FontFile {

    /**
     * Possible encoding format of string that was designed to work with this
     * font.  Type is determined by queues in the parent Cmap definition.
     */
    enum ByteEncoding {
        ONE_BYTE, TWO_BYTE, MIXED_BYTE
    }

    long LAYOUT_NONE = 0;

    Point2D echarAdvance(char ech);

    FontFile deriveFont(AffineTransform at);

    FontFile deriveFont(Encoding encoding, CMap toUnicode);

    FontFile deriveFont(float[] widths, int firstCh, float missingWidth,
                        float ascent, float descent, char[] diff);

    FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth,
                        float ascent, float descent, char[] diff);

    /**
     * Can the character <var>ch</var> in the nfont's encoding be rendered?
     *
     * @param ech character to test for displayability.
     * @return true if renderable, false otherwise.
     */
    boolean canDisplayEchar(char ech);

    void setIsCid();

    /**
     * Creates nfont a new <var>pointsize</var>, assuming 72 ppi.
     * Note to subclassers: you must make a complete independent instance of the nfont here,
     * even if point size and everything else is the same, as other <code>deriveFont</code> methods use this to make a clone and might make subsequent changes.
     *
     * @param pointsize point size of font.
     * @return fontFile with associated point size.
     */
    FontFile deriveFont(float pointsize);

    CMap getToUnicode();

    String toUnicode(String displayText);

    String toUnicode(char displayChar);

    org.apache.fontbox.encoding.Encoding getEncoding();

    /**
     * Returns name of nfont, such as "Times".
     *
     * @return font family of font.
     */
    String getFamily();

    float getSize();

    /**
     * Returns maximum ascent glyphs above baseline.
     *
     * @return fonts ascent.
     */
    double getAscent();

    /**
     * Returns maximum descent of glyphs below baseline.
     *
     * @return fonts descent
     */
    double getDescent();

    /**
     * Returns left in rectangle's x, ascent in y, width in width, height in height.
     *
     * @return max character bounds.
     */
    Rectangle2D getMaxCharBounds();

    /**
     * Returns a copy of the transform associated with this font file.
     *
     * @return fonts transform matrix.
     */
    AffineTransform getTransform();

    /**
     * Returns nfont usage rights bit mask.
     *
     * @return fonts permission/rights.
     */
    int getRights();

    /**
     * Returns name of nfont, such as "Times-Roman", which is different than the filename.
     *
     * @return font name
     */
    String getName();

    /**
     * Returns <code>true</code> iff nfont has hinted outlines, which is Type 1 and TrueType is a sign of higher quality.
     *
     * @return true if the font is hinted, otherwise false.
     */
    boolean isHinted();

    /**
     * Returns number of glyphs defined in nfont.
     *
     * @return number of glyphs in font.
     */
    int getNumGlyphs();

    /**
     * Gests the fonts's style.
     *
     * @return font style
     */
    int getStyle();

    /**
     * Returns the character that seems to be used as a space in the current encoding, or NOTDEF_CHAR if no such character.
     * @return associated space character.
     */
    char getSpaceEchar();

    Rectangle2D getEstringBounds(String estr, int beginIndex, int limit);

    /**
     * Returns primary format, such as "Type1" or "OpenType".
     * @return  "Type1" or "OpenType"
     */
    String getFormat();

    void drawEstring(Graphics2D g, String estr, float x,
                     float y, long layout, int mode,
                     Color strokeColor);

    /**
     * Get the glyph outline shape for the given estr translated to x,y.
     *
     * @param estr text to calculate glyph outline shape
     * @param x    x coordinate to translate outline shape.
     * @param y    y coordinate to translate outline shape.
     * @return glyph outline of the estr.
     */
    Shape getEstringOutline(String estr, float x, float y);

    ByteEncoding getByteEncoding();

    /**
     * Gets the source url of the underlying file if any.  Embedded fonts will
     * not have a source.
     *
     * @return null if the font is embedded, otherwise the font system path.
     */
    URL getSource();
}
