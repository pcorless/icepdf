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
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;

import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * This class represents a PDF <code>FontDescriptor</code>.  A FontDescriptor object
 * holds extra information about a particular parent Font object.  In particular
 * information on font widths, flags, to unicode and embedded font program streams.
 *
 * @see org.icepdf.core.pobjects.fonts.Font
 */
public class FontDescriptor extends Dictionary {

    private static final Logger logger =
            Logger.getLogger(FontDescriptor.class.toString());

    private FontFile font;

    public static final String FONT_NAME = "FontName";

    public static final String FONT_FAMILY = "FontFamily";

    public static final String MISSING_Stretch = "FontStretch";

    public static final String FONT_WEIGHT = "FontWeight";

    public static final String FLAGS = "Flags";

    public static final String FONT_BBOX = "FontBBox";

    public static final String ITALIC_ANGLE = "ItalicAngle";

    public static final String ASCENT = "Ascent";

    public static final String DESCENT = "Descent";

    public static final String LEADING = "Leading";

    public static final String CAP_HEIGHT = "CapHeight";

    public static final String X_HEIGHT = "XHeight";

    public static final String STEM_V = "StemV";

    public static final String STEM_H = "StemH";

    public static final String AVG_WIDTH = "AvgWidth";

    public static final String MAX_WIDTH = "MaxWidth";

    public static final String MISSING_WIDTH = "MissingWidth";

    private static final String FONT_FILE = "FontFile";
    private static final String FONT_FILE_2 = "FontFile2";
    private static final String FONT_FILE_3 = "FontFile3";
    private static final String FONT_FILE_3_TYPE_1C = "Type1C";
    private static final String FONT_FILE_3_CID_FONT_TYPE_0 = "CIDFontType0";
    private static final String FONT_FILE_3_CID_FONT_TYPE_2 = "CIDFontType2";
    private static final String FONT_FILE_3_CID_FONT_TYPE_0C = "CIDFontType0C";
    private static final String FONT_FILE_3_OPEN_TYPE = "OpenType";

    /**
     * Creates a new instance of a FontDescriptor.
     *
     * @param l Libaray of all objects in PDF
     * @param h hash of parsed FontDescriptor attributes
     */
    public FontDescriptor(Library l, Hashtable h) {
        super(l, h);
    }

    /**
     * Utility method for creating a FontDescriptor based on the font metrics
     * of the <code>AFM</code>
     *
     * @param library document library
     * @param afm adobe font metrics data
     * @return new instance of a <code>FontDescriptor</code>
     */
    public static FontDescriptor createDescriptor(Library library, AFM afm) {
        Hashtable<String, Object> properties = new Hashtable<String, Object>(5);
        properties.put(FONT_NAME, afm.getFontName());
        properties.put(FONT_FAMILY, afm.getFamilyName());
        properties.put(FONT_BBOX, afm.getFontBBox());
        properties.put(ITALIC_ANGLE, afm.getItalicAngle());
        properties.put(MAX_WIDTH, afm.getMaxWidth());
        properties.put(AVG_WIDTH, afm.getAvgWidth());
        properties.put(FLAGS, afm.getFlags());
        return new FontDescriptor(library, properties);
    }

    /**
     * Returns the PostScript name of the font.
     *
     * @return PostScript name of font.
     */
    public String getFontName() {
        Object value = library.getObject(entries, FONT_NAME);
        if (value instanceof Name) {
            return ((Name) value).getName();
        }
        else if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    /**
     * Gets a string specifying the preferred font family name.  For example, the font
     * "Time Bold Italic" would have a font family of Times.
     *
     * @return preferred font family name.
     */
    public String getFontFamily() {
        Object value = library.getObject(entries, FONT_FAMILY);
        if (value instanceof StringObject) {
            StringObject familyName = (StringObject) value;
            return familyName.getDecryptedLiteralString(library.getSecurityManager());
        }
        return FONT_NAME;
    }

    /**
     * Gets the weight (thickness) component of the fully-qualified font name or
     * font specifier.  The default value is zero.
     *
     * @return the weight of the font name.
     */
    public float getFontWeight() {
        Object value = library.getObject(entries, FONT_WEIGHT);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0.0f;
    }

    /**
     * Gets the width to use for character codes whose widths are not specifed in
     * the font's dictionary.   The default value is zero.
     *
     * @return width of non-specified characters.
     */
    public float getMissingWidth() {
        Object value = library.getObject(entries, MISSING_WIDTH);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0.0f;
    }

    /**
     * Gets the average width of glyphs in the font. The default value is zero.
     *
     * @return average width of glyphs.
     */
    public float getAverageWidth() {
        Object value = library.getObject(entries, AVG_WIDTH);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0.0f;
    }

    /**
     * Gets the maximum width of glyphs in the font. The default value is zero.
     *
     * @return maximum width of glyphs.
     */
    public float getMaxWidth() {
        Object value = library.getObject(entries, MAX_WIDTH);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0.0f;
    }

    /**
     * Gets the ascent of glyphs in the font. The default value is zero.
     *
     * @return ascent of glyphs.
     */
    public float getAscent() {
        Object value = library.getObject(entries, ASCENT);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0.0f;
    }

    /**
     * Gets the descent of glyphs in the font. The default value is zero.
     *
     * @return descent of glyphs.
     */
    public float getDescent() {
        Object value = library.getObject(entries, DESCENT);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0.0f;
    }

    /**
     * Gets the embeddedFont if any.
     *
     * @return embedded font; null, if there is no valid embedded font.
     */
    public FontFile getEmbeddedFont() {
        return font;
    }

    /**
     * Gets the fonts bounding box.
     *
     * @return bounding box in PDF coordinate space.
     */
    public PRectangle getFontBBox() {
        Object value = library.getObject(entries, FONT_BBOX);
        if (value instanceof Vector) {
            Vector rectangle = (Vector) value;
            return new PRectangle(rectangle);
        }
        return null;
    }

    /**
     * Gets the font flag value, which is a collection of various characteristics
     * that describe the font.
     *
     * @return int value representing the flags; bits must be looked at to get
     *         attribute values.
     */
    public int getFlags() {
        Object value = library.getObject(entries, FLAGS);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * Initiate the Font Descriptor object. Reads embedded font programs
     * or CMap streams.
     */
    public void init() {

        /**
         * FontFile1 = A stream containing a Type 1 font program
         * FontFile2 = A stream containing a TrueType font program
         * FontFile3 = A stream containing a font program other than Type 1 or
         * TrueType. The format of the font program is specified by the Subtype entry
         * in the stream dictionary
         */
        try {

            // get an instance of our font factory
            FontFactory fontFactory = FontFactory.getInstance();

            if (entries.containsKey(FONT_FILE)) {
                Stream fontStream = (Stream) library.getObject(entries, FONT_FILE);
                if (fontStream != null) {
                    font = fontFactory.createFontFile(
                            fontStream, FontFactory.FONT_TYPE_1);
                }
            }

            if (entries.containsKey(FONT_FILE_2)) {
                Stream fontStream = (Stream) library.getObject(entries, FONT_FILE_2);
                if (fontStream != null) {
                    font = fontFactory.createFontFile(
                            fontStream, FontFactory.FONT_TRUE_TYPE);
                }
            }

            if (entries.containsKey(FONT_FILE_3)) {

                Stream fontStream = (Stream) library.getObject(entries, FONT_FILE_3);
                String subType = fontStream.getObject("Subtype").toString();
                if (subType != null &&
                        (subType.equals(FONT_FILE_3_TYPE_1C) ||
                                subType.equals(FONT_FILE_3_CID_FONT_TYPE_0) ||
                                subType.equals(FONT_FILE_3_CID_FONT_TYPE_0C))
                        ) {
                    font = fontFactory.createFontFile(
                            fontStream, FontFactory.FONT_TYPE_1);
                }
                if (subType != null && subType.equals(FONT_FILE_3_OPEN_TYPE)) {
//                        font = new NFontOpenType(fontStreamBytes);
                    font = fontFactory.createFontFile(
                            fontStream, FontFactory.FONT_OPEN_TYPE);
                }
            }
        }
        // catch everything, we can fall back to font substitution if a failure
        // occurs.
        catch (Throwable e) {
            logger.log(Level.FINE, "Error Reading Embedded Font ", e);
        }

    }

    /**
     * Return a string representation of the all the FontDescriptor object's
     * parsed attributes.
     *
     * @return all of FontDescriptors parsed attributes.
     */
    public String toString() {
        String name = null;
        if (font != null)
            name = font.getName();
        return super.getPObjectReference() + " FONTDESCRIPTOR= " + entries.toString() + " - " + name;
    }
}
