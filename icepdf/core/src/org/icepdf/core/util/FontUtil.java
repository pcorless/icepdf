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
package org.icepdf.core.util;

/**
 * Font utility conatin a bunch of commonly used font utitlity methods.
 *
 * @since 3.1
 */
public class FontUtil {

    /**
     * Utitility method which maps know sytle strings to an AWT font style constants.  The style
     * attribute read as follows fron the java.awt.font constructor:
     * <ul>
     *   the style constant for the Font The style argument is an integer bitmask that may be PLAIN, or a bitwise
     *   union of BOLD and/or ITALIC (for example, ITALIC or BOLD|ITALIC). If the style argument does not conform to
     *   one of the expected integer bitmasks then the style is set to PLAIN.
     * </ul>
     *
     * @param name base name of font.
     * @return integer representing dffs
     */
    public static int guessAWTFontStyle(String name) {
        name = name.toLowerCase();
        int decorations = 0;
        if ((name.indexOf("boldital") > 0) || (name.indexOf("demiital") > 0)) {
            decorations |= java.awt.Font.BOLD | java.awt.Font.ITALIC;
        } else if (name.indexOf("bold") > 0 || name.indexOf("black") > 0 || name.indexOf("demi") > 0) {
            decorations |= java.awt.Font.BOLD;
        } else if (name.indexOf("ital") > 0 || name.indexOf("obli") > 0) {
            decorations |= java.awt.Font.ITALIC;
        } else {
            decorations |= java.awt.Font.PLAIN;
        }
        return decorations;
    }

    /**
     * <p>Utility method for guessing a font family name from its base name.</p>
     *
     * @param name base name of font.
     * @return guess of the base fonts name.
     */
    public static String guessFamily(String name) {
        String fam = name;
        int inx;
        // Family name usually precedes a common, ie. "Arial,BoldItalic"
        if ((inx = fam.indexOf(',')) > 0)
            fam = fam.substring(0, inx);
        // Family name usually precedes a dash, example "Times-Bold",
        if ((inx = fam.lastIndexOf('-')) > 0)
            fam = fam.substring(0, inx);
        return fam;
    }

    /**
     * Utility method for normailing strings, to lowercase and remove any spaces.
     *
     * @param name base name of font
     * @return normalized copy of string.
     */
    public static String normalizeString(String name) {
        name = guessFamily(name);
        StringBuilder normalized = new StringBuilder(name.toLowerCase());
        for (int k = normalized.length() - 1; k >= 0; k--) {
            if (normalized.charAt(k) == 32) {
                normalized.deleteCharAt(k);
                k--;
            }
        }
        return normalized.toString();
    }

}
