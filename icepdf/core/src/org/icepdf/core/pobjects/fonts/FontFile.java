/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.core.pobjects.fonts;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;

/**
 * Font file interfaces.  Common methods which encapsulate NFont and OFont
 * font rendering libraries.
 *
 * @since 3.0
 */
public interface FontFile {

    public static final long LAYOUT_NONE = 0;

    public Point2D echarAdvance(char ech);

    public FontFile deriveFont(AffineTransform at);

    public FontFile deriveFont(Encoding encoding, CMap toUnicode);

    public FontFile deriveFont(float[] widths, int firstCh, float missingWidth,
                               float ascent, float descent, char[] diff);

    public FontFile deriveFont(Map<Integer, Float> widths, int firstCh, float missingWidth,
                               float ascent, float descent, char[] diff);

    /**
     * Can the character <var>ch</var> in the nfont's encoding be rendered?
     */
    public boolean canDisplayEchar(char ech);

    /**
     * Creates nfont a new <var>pointsize</var>, assuming 72 ppi.
     * Note to subclassers: you must make a complete independent instance of the nfont here,
     * even if pointsize and everything else is the same, as other <code>deriveFont</code> methods use this to make a clone and might make subsequent changes.
     */
    public FontFile deriveFont(float pointsize);

    public CMap getToUnicode();

    public String toUnicode(String displayText);

    public char toUnicode(char displayChar);

    /**
     * Returns name of nfont, such as "Times".
     */
    public String getFamily();

    public float getSize();

    /**
     * Returns maximum ascent glyphs above baseline.
     */
    public double getAscent();

    /**
     * Returns maximum descent of glyphs below baseline.
     */
    public double getDescent();

    /**
     * Returns left in rectangle's x, ascent in y, width in width, height in height.
     */
    public Rectangle2D getMaxCharBounds();

    /**
     * Returns a copy of the transform associated with this font file.
     */
    public AffineTransform getTransform();

    /**
     * Returns nfont usage rights bit mask.
     */
    public int getRights();

    /**
     * Returns name of nfont, such as "Times-Roman", which is different than the filename.
     */
    public String getName();

    /**
     * Returns <code>true</code> iff nfont has hinted outlines, which is Type 1 and TrueType is a sign of higher quality.
     */
    public boolean isHinted();

    /**
     * Returns number of glyphs defined in nfont.
     */
    public int getNumGlyphs();

    public int getStyle();

    /**
     * Returns the character that seems to be used as a space in the current encoding, or NOTDEF_CHAR if no such character.
     */
    public char getSpaceEchar();

    public Rectangle2D getEstringBounds(String estr, int beginIndex, int limit);

    /**
     * Returns primary format, such as "Type1" or "OpenType".
     */
    public String getFormat();

    public abstract void drawEstring(Graphics2D g, String estr, float x,
                                     float y, long layout, int mode,
                                     Color strokecolor);

}
