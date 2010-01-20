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

import org.icepdf.core.util.Defs;
import org.icepdf.core.util.FontUtil;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * <p>The <code>FontManager</code> class is responsible for finding available
 * fonts on the client operating system.  This class by default checks the
 * following directories when the readSystemFonts method is called without any
 * parameters.</p>
 * <p/>
 * <p>The default font directories are as follows:</p>
 * <ul>
 * <li><b>Windows</b> - "c:\\windows\\fonts\\", "d:\\windows\\fonts\\", "e:\\windows\\fonts\\", "f:\\windows\\fonts\\", "c:\\winnt\\Fonts\\", "d:\\winnt\\Fonts\\"</li>
 * <li><b>Macintosh</b> - "/Network/Library/Fonts/", "/System/Library/Fonts/", "/System Folder/Fonts"</li>
 * <li><b>Linux/Unix</b> - /system/etc/fonts/","/usr/share/fonts/truetype/", "/usr/share/fonts/local/", "/etc/fonts/","/usr/lib/X11/fonts", "/usr/X11R6/lib/X11/fonts", "/usr/openwin/lib/X11/fonts", "/usr/openwin/lib/X11/fonts/misc/"</li>
 * <li><b>Java default</b> - sysProp("java.home") + "/lib/fonts"</li>
 * </ul>
 * <p/>
 * <p>It is possible to specify other directories to search for fonts via the
 * readSystemFonts methods extraFontPaths parameter {@link #readSystemFonts}.
 * Reading all of an operating systems font's can be time consuming. To help
 * speed up this process the method getFontProperties exports font data via a
 * Properties object.  The font Properties object can then be saved to disk or
 * be read back into the FontManager via the setFontProperties method.  </p>
 *
 * @since 2.0
 */
public class FontManager {

    private static final Logger logger =
            Logger.getLogger(FontManager.class.toString());

    // stores all font data
    private static ArrayList<Object[]> fontList;

    // flags for detecting font decorations
    private static int PLAIN = 0xF0000001;
    private static int BOLD = 0xF0000010;
    private static int ITALIC = 0xF0000100;
    private static int BOLD_ITALIC = 0xF0001000;

    // Differences for type1 fonts which match adobe core14 metrics
    private static final String TYPE1_FONT_DIFFS[][] =
            {{"Bookman-Demi", "URWBookmanL-DemiBold", "Arial"},
                    {"Bookman-DemiItalic", "URWBookmanL-DemiBoldItal", "Arial"},
                    {"Bookman-Light", "URWBookmanL-Ligh", "Arial"},
                    {"Bookman-LightItalic", "URWBookmanL-LighItal", "Arial"},
                    {"Courier", "NimbusMonL-Regu", "Nimbus Mono L", "CourierNew", "CourierNewPSMT"},
                    {"Courier-Oblique", "NimbusMonL-ReguObli", "Nimbus Mono L", "Courier,Italic", "CourierNew-Italic", "CourierNew,Italic", "CourierNewPS-ItalicMT"},
                    {"Courier-Bold", "NimbusMonL-Bold", "Nimbus Mono L", "Courier,Bold", "CourierNew,Bold", "CourierNew-Bold", "CourierNewPS-BoldMT"},
                    {"Courier-BoldOblique", "NimbusMonL-BoldObli", "Nimbus Mono L", "Courier,BoldItalic", "CourierNew-BoldItalic", "CourierNew,BoldItalic", "CourierNewPS-BoldItalicMT"},
                    {"AvantGarde-Book", "URWGothicL-Book", "Arial"},
                    {"AvantGarde-BookOblique", "URWGothicL-BookObli", "Arial"},
                    {"AvantGarde-Demi", "URWGothicL-Demi", "Arial"},
                    {"AvantGarde-DemiOblique", "URWGothicL-DemiObli", "Arial"},
                    {"Helvetica", "Arial", "ArialMT", "NimbusSanL-Regu", "Nimbus Sans L"},
//             {"Helvetica", "NimbusSanL-Regu", "Nimbus Sans L", "Arial", "ArialMT"},  // known problem in Phelps nfont engine
                    {"Helvetica-Oblique", "NimbusSanL-ReguItal", "Nimbus Sans L", "Helvetica,Italic", "Helvetica-Italic", "Arial,Italic", "Arial-Italic", "Arial-ItalicMT"},
//             {"Helvetica-Bold", "NimbusSanL-Bold", "Nimbus Sans L", "Helvetica-Black", "Helvetica,Bold", "Arial,Bold", "Arial-Bold", "Arial-BoldMT"},  // known problem in Phelps nfont engine
                    {"Helvetica-Bold", "Helvetica,Bold", "Arial,Bold", "Arial-Bold", "Arial-BoldMT", "NimbusSanL-Bold", "Nimbus Sans L"},
                    {"Helvetica-BoldOblique", "NimbusSanL-BoldItal", "Helvetica-BlackOblique", "Nimbus Sans L", "Helvetica,BoldItalic", "Helvetica-BoldItalic", "Arial,BoldItalic", "Arial-BoldItalic", "Arial-BoldItalicMT"},
                    {"Helvetica-Black", "Helvetica,Bold", "Arial,Bold", "Arial-Bold", "Arial-BoldMT", "NimbusSanL-Bold", "Nimbus Sans L"},
                    {"Helvetica-BlackOblique", "NimbusSanL-BoldItal", "Helvetica-BlackOblique", "Nimbus Sans L", "Helvetica,BoldItalic", "Helvetica-BoldItalic", "Arial,BoldItalic", "Arial-BoldItalic", "Arial-BoldItalicMT"},
                    {"Helvetica-Narrow", "NimbusSanL-ReguCond", "Nimbus Sans L"},
                    {"Helvetica-Narrow-Oblique", "NimbusSanL-ReguCondItal", "Nimbus Sans L"},
                    {"Helvetica-Narrow-Bold", "NimbusSanL-BoldCond", "Nimbus Sans L"},
                    {"Helvetica-Narrow-BoldOblique", "NimbusSanL-BoldCondItal", "Nimbus Sans L"},
                    {"Helvetica-Condensed", "NimbusSanL-ReguCond", "Nimbus Sans L"},
                    {"Helvetica-Condensed-Oblique", "NimbusSanL-ReguCondItal", "Nimbus Sans L"},
                    {"Helvetica-Condensed-Bold", "NimbusSanL-BoldCond", "Nimbus Sans L"},
                    {"Helvetica-Condensed-BoldOblique", "NimbusSanL-BoldCondItal", "Nimbus Sans L"},
                    {"Palatino-Roman", "URWPalladioL-Roma", "Arial"},
                    {"Palatino-Italic", "URWPalladioL-Ital", "Arial"},
                    {"Palatino-Bold", "URWPalladioL-Bold", "Arial"},
                    {"Palatino-BoldItalic", "URWPalladioL-BoldItal", "Arial"},
                    {"NewCenturySchlbk-Roman", "CenturySchL-Roma", "Arial"},
                    {"NewCenturySchlbk-Italic", "CenturySchL-Ital", "Arial"},
                    {"NewCenturySchlbk-Bold", "CenturySchL-Bold", "Arial"},
                    {"NewCenturySchlbk-BoldItalic", "CenturySchL-BoldItal", "Arial"},
                    {"Times-Roman", "NimbusRomNo9L-Regu", "Nimbus Roman No9 L", "TimesNewRoman", "TimesNewRomanPSMT", "TimesNewRomanPS"},
                    {"Times-Italic", "NimbusRomNo9L-ReguItal", "Nimbus Roman No9 L", "TimesNewRoman,Italic", "TimesNewRoman-Italic", "TimesNewRomanPS-Italic", "TimesNewRomanPS-ItalicMT"},
                    {"Times-Bold", "NimbusRomNo9L-Medi", "Nimbus Roman No9 L", "TimesNewRoman,Bold", "TimesNewRoman-Bold", "TimesNewRomanPS-Bold", "TimesNewRomanPS-BoldMT"},
                    {"Times-BoldItalic", "NimbusRomNo9L-MediItal", "Nimbus Roman No9 L", "TimesNewRoman,BoldItalic", "TimesNewRoman-BoldItalic", "TimesNewRomanPS-BoldItalic", "TimesNewRomanPS-BoldItalicMT"},
                    {"Symbol", "StandardSymL", "Standard Symbols L"},
                    {"ZapfChancery-MediumItalic", "URWChanceryL-MediItal", "Arial"},
                    {"ZapfDingbats", "Dingbats", "Zapf-Dingbats"}
            };

    private static final String[] JAPANESE_FONT_NAMES = {
            "Arial Unicode MS", "PMingLiU", "MingLiU",
            "MS PMincho", "MS Mincho", "Kochi Mincho", "Hiragino Mincho Pro",
            "KozMinPro Regular Acro", "HeiseiMin W3 Acro", "Adobe Ming Std Acro"
    };

    private static final String[] CHINESE_SIMPLIFIED_FONT_NAMES = {
            "Arial Unicode MS", "PMingLiU", "MingLiU",
            "SimSun", "NSimSun", "Kochi Mincho", "STFangsong", "STSong Light Acro",
            "Adobe Song Std Acro"
    };

    private static final String[] CHINESE_TRADITIONAL_FONT_NAMES = {
            "Arial Unicode MS", "PMingLiU", "MingLiU",
            "SimSun", "NSimSun", "Kochi Mincho", "BiauKai", "MSungStd Light Acro",
            "Adobe Song Std Acro"
    };

    private static final String[] KOREAN_FONT_NAMES = {
            "Arial Unicode MS", "Gulim", "Batang",
            "BatangChe", "HYSMyeongJoStd Medium Acro", "Adobe Myungjo Std Acro"
    };

    // Default system directories to scan for font programs. This variable can
    // not be declared final as it will hard wire the java.home font directory, which
    // is bad, should be different for different environments.
    private static String[] SYSTEM_FONT_PATHS =
            new String[]{
                    // windows
                    "c:\\windows\\fonts\\",
                    "d:\\windows\\fonts\\",
                    "e:\\windows\\fonts\\",
                    "f:\\windows\\fonts\\",
                    "c:\\winnt\\Fonts\\",
                    "d:\\winnt\\Fonts\\",
                    "c:\\cygwin\\usr\\share\\ghostscript\\fonts\\",
                    "d:\\cygwin\\usr\\share\\ghostscript\\fonts\\",

                    // Mac
                    "/Network/Library/Fonts/",
                    "/System/Library/Fonts/",
                    "/System Folder/Fonts",
                    "/usr/local/share/ghostscript/",
                    "/Applications/GarageBand.app/Contents/Resources/",
                    "/Applications/NeoOffice.app/Contents/share/fonts/truetype/",
                    "/Library/Dictionaries/Shogakukan Daijisen.dictionary/Contents/",
                    "/Library/Dictionaries/Shogakukan Progressive English-Japanese Japanese-English Dictionary.dictionary/Contents/",
                    "/Library/Dictionaries/Shogakukan Ruigo Reikai Jiten.dictionary/Contents/",
                    "/Library/Fonts/",
                    "/Volumes/Untitled/WINDOWS/Fonts/",
                    "/usr/share/enscript/",
                    "/usr/share/groff/1.19.2/font/devps/generate/",
                    "/usr/X11/lib/X11/fonts/Type1/",
                    "/usr/X11/lib/X11/fonts/TrueType/",
                    "/usr/X11/lib/X11/fonts/",

                    // Linux
                    "/etc/fonts/",
                    "/system/etc/fonts/",
                    "/usr/lib/X11/fonts",
                    "/usr/share/a2ps/afm/",
                    "/usr/share/enscript/afm/",
                    "/usr/share/fonts/local/",
                    "/usr/share/fonts/truetype/",
                    "/usr/share/fonts/truetype/freefont/",
                    "/usr/share/fonts/truetype/msttcorefonts/",
                    "/usr/share/fonts/Type1/",
                    "/usr/share/fonts/type1/gsfonts/",
                    "/usr/share/fonts/X11/Type1/",
                    "/usr/share/ghostscript/fonts/",
                    "/usr/share/groff/1.18.1/font/devps/",
                    "/usr/share/groff/1.18.1/font/devps/generate/",
                    "/usr/share/libwmf/fonts/",
                    "/usr/share/ogonkify/afm/",
                    "/usr/X11R6/lib/X11/fonts/",
                    "/var/lib/defoma/gs.d/dirs/fonts/",
                    // solaris
                    "/usr/openwin/lib/locale/ar/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/euro_fonts/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/hi_IN.UTF-8/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_13/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_15/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_2/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_2/X11/fonts/Type1/afm/",
                    "/usr/openwin/lib/locale/iso_8859_4/X11/fonts/Type1/afm/",
                    "/usr/openwin/lib/locale/iso_8859_5/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_5/X11/fonts/Type1/afm/",
                    "/usr/openwin/lib/locale/iso_8859_7/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_7/X11/fonts/Type1/afm/",
                    "/usr/openwin/lib/locale/iso_8859_8/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_8/X11/fonts/Type1/",
                    "/usr/openwin/lib/locale/iso_8859_8/X11/fonts/Type1/afm/",
                    "/usr/openwin/lib/locale/iso_8859_9/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/iso_8859_9/X11/fonts/Type1/afm/",
                    "/usr/openwin/lib/locale/ja//X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/K0I8-R/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/ru.ansi-1251/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/th_TH/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/zh.GBK/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/zh/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/zh_CN.GB18030/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/zh_TW.BIG5/X11/fonts/TrueType/",
                    "/usr/openwin/lib/locale/zh_TW/X11/fonts/TrueType/",
                    "/usr/openwin/lib/X11/fonts/",
                    "/usr/openwin/lib/X11/fonts/F3/afm/",
                    "/usr/openwin/lib/X11/fonts/misc/",
                    "/usr/openwin/lib/X11/fonts/TrueType/",
                    "/usr/openwin/lib/X11/fonts/Type1/",
                    "/usr/openwin/lib/X11/fonts/Type1/afm/",
                    "/usr/openwin/lib/X11/fonts/Type1/outline/",
                    "/usr/openwin/lib/X11/fonts/Type1/sun/",
                    "/usr/openwin/lib/X11/fonts/Type1/sun/afm/",
                    "/usr/sfw/share/a2ps/afm/",
                    "/usr/sfw/share/ghostscript/fonts/",
                    "/usr/sfw/share/ghostscript/fonts/",

                    // Java default
                    Defs.sysProperty("java.home") + "/lib/fonts"
            };

    // Singleton instance of class
    private static FontManager fontManager;


    /**
     * <p>Returns a static instance of the FontManager class.</p>
     *
     * @return instance of the FontManager.
     */
    public static FontManager getInstance() {
        // make sure we have initialized the manager
        if (fontManager == null) {
            fontManager = new FontManager();
        }
        return fontManager;
    }

    /**
     * <p>Gets a Properties object containing font information for the operating
     * system which the FontManager is running on.  This Properties object
     * can be saved to disk and read at a later time using the {@see #setFontProperties}
     * method.</p>
     *
     * @return Properties object containing font data information.
     */
    public Properties getFontProperties() {
        Properties fontProperites;
        // make sure we are initialized
        if (fontList == null) {
            readSystemFonts(null);
        }
        // copy all data from fontList into the properties file
        fontProperites = new Properties();
        Iterator fontIterator = fontList.iterator();
        Object[] currentFont;
        String name;
        String family;
        Integer decorations;
        String path;
        // Build the properties file using the font name as the key and
        // the value is the family, decoration and path information
        // separated by the "|" character.
        while (fontIterator.hasNext()) {
            currentFont = (Object[]) fontIterator.next();
            name = (String) currentFont[0];
            family = (String) currentFont[1];
            decorations = (Integer) currentFont[2];
            path = (String) currentFont[3];
            // add the new entry
            fontProperites.put(name, family + "|" + decorations + "|" + path);
        }
        return fontProperites;
    }

    /**
     * <p>Reads font data from the Properties file.  All name and key data replaces
     * any existing font information.</p>
     *
     * @param fontProperties Properties object containing valid font information.
     * @throws IllegalArgumentException thrown, if there is a problem parsing the
     *                                  Properties file.  If thrown, the calling application should re-read
     *                                  the system fonts.
     */
    public void setFontProperties(Properties fontProperties)
            throws IllegalArgumentException {
        String errorString = "Error parsing font properties ";
        try {
            fontList = new ArrayList<Object[]>(150);
            Enumeration fonts = fontProperties.propertyNames();
            String name;
            String family;
            Integer decorations;
            String path;
            StringTokenizer tokens;
            // read in font information
            while (fonts.hasMoreElements()) {
                name = (String) fonts.nextElement();
                tokens = new StringTokenizer((String) fontProperties.get(name), "|");
                // get family, decoration and path tokens
                family = tokens.nextToken();
                decorations = new Integer(tokens.nextToken());
                path = tokens.nextToken();
                if (name != null && family != null && path != null) {
                    fontList.add(new Object[]{name, family, decorations, path});
                } else {
                    throw new IllegalArgumentException(errorString);
                }
            }
        }
        catch (Throwable e) {
            logger.log(Level.FINE, "Error setting font properties ", e);
            throw new IllegalArgumentException(errorString);
        }
    }


    /**
     * Clears internal font list of items. Used to clean list while custructing
     * a new list.
     */
    public void clearFontList() {
        if (fontList != null) {
            fontList.clear();
            fontList.trimToSize();
        }
    }

    /**
     * <p>Searches all default system font paths and any font paths
     * specified by the extraFontPaths parameter, and records data about all
     * found fonts.  This font data is used to substitute fonts which are not
     * embedded inside a PDF document.</p>
     *
     * @param extraFontPaths array String object where each entry represents
     *                       a system directory path containing font programs.
     */
    public void readSystemFonts(String[] extraFontPaths) {

        // create a new font list if needed.
        if (fontList == null) {
            fontList = new ArrayList<Object[]>(150);
        }

        // Setup parameters
        FontFile font;
        String path;
        StringBuilder fontPath;
        String fontName;
        String[] fontPaths;
        File directory;

        // Copy any extra font paths to the
        String[] fontDirectories;
        if (extraFontPaths == null) {
            fontDirectories = SYSTEM_FONT_PATHS;
        } else {
            int length = SYSTEM_FONT_PATHS.length + extraFontPaths.length;
            fontDirectories = new String[length];
            // copy the static list into the new list
            System.arraycopy(SYSTEM_FONT_PATHS, 0, fontDirectories, 0, SYSTEM_FONT_PATHS.length);
            // added any paths specified by the user
            System.arraycopy(extraFontPaths, 0, fontDirectories, SYSTEM_FONT_PATHS.length, extraFontPaths.length);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Reading system fonts:");
        }

        // Iterate through SYSTEM_FONT_PATHS and load all readable fonts
        for (int i = fontDirectories.length - 1; i >= 0; i--) {
            path = fontDirectories[i];
            // if the path is valid start reading fonts. 
            if (path != null) {
                directory = new File(path);
                if (directory.canRead()) {
                    fontPaths = directory.list();
                    for (int j = fontPaths.length - 1; j >= 0; j--) {
                        fontName = fontPaths[j];
                        fontPath = new StringBuilder(25);
                        fontPath.append(directory.getAbsolutePath()).append(
                                File.separatorChar).append(fontName);
                        if (logger.isLoggable(Level.FINER)){
                            logger.finer("Trying to load font file: " + fontPath);
                        }
                        // try loading the font
                        font = buildFont(fontPath.toString());
                        // if a readable font was found
                        if (font != null) {
                            // normalize name
                            fontName = font.getName().toLowerCase();
                            // Add new font data to the font list
                            fontList.add(new Object[]{font.getName().toLowerCase(), // original PS name
                                    FontUtil.normalizeString(font.getFamily()), // family name
                                    guessFontStyle(fontName), // weight and decorations, mainly bold,italic
                                    fontPath.toString()});  // path to font on OS
                            if (logger.isLoggable(Level.FINER)) {
                                logger.finer("Adding system font: " + font.getName() + " " + fontPath.toString());
                            }
                        }
                    }
                }
            }
        }
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
     * <p>Gets all available font names on the operating system.</p>
     *
     * @return font names of all found fonts.
     */
    public String[] getAvailableNames() {
        if (fontList != null) {
            String[] availableNames = new String[fontList.size()];
            Iterator nameIterator = fontList.iterator();
            Object[] fontData;
            for (int i = 0; nameIterator.hasNext(); i++) {
                fontData = (Object[]) nameIterator.next();
                availableNames[i] = fontData[0].toString();
            }
            return availableNames;
        }
        return null;
    }

    /**
     * <p>Gets all available font family names on the operating system.</p>
     *
     * @return font family names of all found fonts.
     */
    public String[] getAvailableFamilies() {
        if (fontList != null) {
            String[] availableNames = new String[fontList.size()];
            Iterator nameIterator = fontList.iterator();
            Object[] fontData;
            for (int i = 0; nameIterator.hasNext(); i++) {
                fontData = (Object[]) nameIterator.next();
                availableNames[i] = fontData[1].toString();
            }
            return availableNames;
        }
        return null;
    }

    /**
     * <p>Gets all available font styles on the operating system.</p>
     *
     * @return font style names of all found fonts.
     */
    public String[] getAvailableStyle() {
        if (fontList != null) {
            String[] availableStyles = new String[fontList.size()];
            Iterator nameIterator = fontList.iterator();
            Object[] fontData;
            int decorations;
            String style = "";
            for (int i = 0; nameIterator.hasNext(); i++) {
                fontData = (Object[]) nameIterator.next();
                decorations = (Integer) fontData[2];
                if ((decorations & BOLD_ITALIC) == BOLD_ITALIC) {
                    style += " BoldItalic";
                } else if ((decorations & BOLD) == BOLD) {
                    style += " Bold";
                } else if ((decorations & ITALIC) == ITALIC) {
                    style += " Italic";
                } else if ((decorations & PLAIN) == PLAIN) {
                    style += " Plain";
                }
                availableStyles[i] = style;
                style = "";
            }
            return availableStyles;
        }
        return null;
    }

    public FontFile getJapaneseInstance(String name, int fontFlags) {
        return getAsianInstance(name, JAPANESE_FONT_NAMES, fontFlags);
    }

    public FontFile getKoreanInstance(String name, int fontFlags) {
        return getAsianInstance(name, KOREAN_FONT_NAMES, fontFlags);
    }

    public FontFile getChineseTraditionalInstance(String name, int fontFlags) {
        return getAsianInstance(name, CHINESE_TRADITIONAL_FONT_NAMES, fontFlags);
    }

    public FontFile getChineseSimplifiedInstance(String name, int fontFlags) {
        return getAsianInstance(name, CHINESE_SIMPLIFIED_FONT_NAMES, fontFlags);
    }

    private FontFile getAsianInstance(String name, String[] list, int flags) {

        if (fontList == null) {
            readSystemFonts(null);
        }

        FontFile font = null;
        if (list != null) {
            // search for know list of fonts
            for (int i = list.length - 1; i >= 0; i--) {
                // try and find an instance of the name and family from the font list
                font = findFont(name, flags);
                if (font != null) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("Font Substitution: Found Asian font: " + font.getName() + " for named font " + name);
                    }
                    return font;
                }
            }

            // lastly see if we can't a system font that matches the list names.
            // search for know list of fonts
            for (int i = list.length - 1; i >= 0; i--) {
                // try and find an instance of the name and family from the font list
                font = findFont(list[i], flags);
                if (font != null) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("Font Substitution: Found Asian font: " + font.getName() + " for named font " + name);
                    }
                    return font;
                }
            }
        }

        return font;
    }

    /**
     * <p>Get an instance of a NFont from the given font name and flag decoration
     * information.</p>
     *
     * @param name  base name of font.
     * @param flags flags used to describe font.
     * @return a new instance of NFont which best approximates the font described
     *         by the name and flags attribute.
     */
    public FontFile getInstance(String name, int flags) {

        if (fontList == null) {
            readSystemFonts(null);
        }

        FontFile font;

        // try and find equivalent type1 font
        font = getType1Fonts(name, flags);
        if (font != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Font Substitution: Found type1 font: " + font.getName() + " for named font " + name);
            }
            return font;
        }

        // try and find an instance of the name and family from the font list
        font = findFont(name, flags);
        if (font != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Font Substitution: Found system font: " + font.getName() + " for named font " + name);
            }
            return font;
        }

        // try and find an equivalent java font
        font = getCoreJavaFont(name, flags);
        if (font != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Font Substitution: Found java font: " + font.getName() + " for named font " + name);
            }
            return font;
        }

        // if all else fails return first font in fontList, this should never
        // happen, but just in case.
        if (fontList.size() > 0) {
            Object[] fontData = fontList.get(0);
            font = buildFont((String) fontData[3]);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Font Substitution: Found failed " + name + " " + font.getName());
            }
        }
        if (font == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("No Fonts can be found on your system. ");
            }
        }

        return font;
    }

    /**
     * Utility method for search the fontList array for an particular font name
     * that has the specified sytle.
     *
     * @param fontName fontname with any decoration information still appended to name.
     * @param flags    flags from content parser, to help guess style.
     * @return a valid font if found, null otherwise
     */
    private FontFile findFont(String fontName, int flags) {

        FontFile font = null;
        // references for system font list.
        Object[] fontData;
        String baseName;
        String familyName;
        // normalize the fontName we are trying to find a match for
        int decorations = guessFontStyle(fontName);
        String name = FontUtil.normalizeString(fontName);
        int style;

        if (fontList != null) {
            for (int i = fontList.size() - 1; i >= 0; i--) {
                fontData = fontList.get(i);
                baseName = (String) fontData[0];
                familyName = (String) fontData[1];
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest(baseName + " : " + familyName + "  : " + name);
                }
                if (name.indexOf(familyName) >= 0 ||
                        fontName.toLowerCase().indexOf(baseName) >= 0) {
                    style = (Integer) fontData[2];
                    boolean found = false;
                    // ignore this font, as the cid mapping are not correct, or ther is
                    // just look and feel issues with them.
                    if (baseName.equals("opensymbol") ||
                            baseName.equals("starsymbol")
                            || baseName.equals("arial-black")
                            || baseName.equals("arial-blackitalic")
                            // mapping issue with standard ascii, not sure why, TimesNewRomanPSMT is ok.
                            || baseName.equals("timesnewromanps")
                            ) {
                        //found = false;
                    } else if (((decorations & BOLD_ITALIC) == BOLD_ITALIC) &&
                            ((style & BOLD_ITALIC) == BOLD_ITALIC)) {
                        found = true;
                    } else if (((decorations & BOLD) == BOLD) &&
                            ((style & BOLD) == BOLD)) {
                        found = true;
                    } else if (((decorations & ITALIC) == ITALIC) &&
                            ((style & ITALIC) == ITALIC)) {
                        found = true;
                    } else if (((decorations & PLAIN) == PLAIN) &&
                            ((style & PLAIN) == PLAIN)) {
                        found = true;
                    }
                    // symbol type fonts don't have an associated style, so
                    // no point trying to match  them based on style. 
                    else if (baseName.indexOf("wingdings") >= 0 ||
                            baseName.indexOf("zapfdingbats") >= 0 ||
                            baseName.indexOf("symbol") >= 0) {
                        found = true;
                    }

                    if (found) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer("----> Found font: " + baseName +
                                    " family: " + getFontSytle(style, 0) +
                                    " for: " + fontName);
                        }
                        font = buildFont((String) fontData[3]);
                        // make sure the font does indeed exist
                        if (font != null) {
                            break;
                        }
                    }
                }
            }
        }
        return font;
    }

    /**
     * Loads a font specified by the fontpath parameter.  If font path is invalid
     * or the file can not be loaded, null is returned.
     *
     * @param fontPath font path of font program to laod
     * @return a valid font if loadable, null otherwise
     */
    private FontFile buildFont(String fontPath) {
        FontFile font = null;
        try {
            File file = new File(fontPath);
            if (!file.canRead()) {
                return null;
            }

            FontFactory fontFactory = FontFactory.getInstance();
            // found true type font
            if ((fontPath.endsWith(".ttf") || fontPath.endsWith(".TTF")) ||
                    (fontPath.endsWith(".dfont") || fontPath.endsWith(".DFONT")) ||
                    (fontPath.endsWith(".ttc") || fontPath.endsWith(".TTC"))) {
                font = fontFactory.createFontFile(file, FontFactory.FONT_TRUE_TYPE);
            }
            // found Type 1 font
            else if ((fontPath.endsWith(".pfa") || fontPath.endsWith(".PFA")) ||
                    (fontPath.endsWith(".pfb") || fontPath.endsWith(".PFB"))) {
                font = fontFactory.createFontFile(file, FontFactory.FONT_TYPE_1);
            }
            // found OpenType font
            else if ((fontPath.endsWith(".otf") || fontPath.endsWith(".OTF")) ||
                    (fontPath.endsWith(".otc") || fontPath.endsWith(".OTC"))) {
                font = fontFactory.createFontFile(file, FontFactory.FONT_OPEN_TYPE);
            }
        } catch (Throwable e) {
           logger.log(Level.FINE, "Error reading font program.", e);
        }
        return font;
    }

    /**
     * Gets a NFont instance by matching against font sytle commonalities in the
     * Java Cores libraries.
     *
     * @param fontName font name to search for
     * @param flags    style flags
     * @return a valid NFont if a match is found, null otherwise.
     */
    private FontFile getCoreJavaFont(String fontName, int flags) {

        int decorations = guessFontStyle(fontName);
        fontName = FontUtil.normalizeString(fontName);
        FontFile font;
        // If no name are found then match against the core java font names
        // "Serif", java equivalent is  "Lucida Bright"
        if ((fontName.indexOf("timesnewroman") >= 0 ||
                fontName.indexOf("bodoni") >= 0 ||
                fontName.indexOf("garamond") >= 0 ||
                fontName.indexOf("minionweb") >= 0 ||
                fontName.indexOf("stoneserif") >= 0 ||
                fontName.indexOf("georgia") >= 0 ||
                fontName.indexOf("bitstreamcyberbit") >= 0)) {
            // important, add style information
            font = findFont("lucidabright-" + getFontSytle(decorations, flags), 0);
        }
        // see if we working with a monospaced font, we sub "Sans Serif",
        // java equivalent is "Lucida Sans"
        else if ((fontName.indexOf("helvetica") != -1 ||
                fontName.indexOf("arial") != -1 ||
                fontName.indexOf("trebuchet") != -1 ||
                fontName.indexOf("avantgardegothic") != -1 ||
                fontName.indexOf("verdana") != -1 ||
                fontName.indexOf("univers") != -1 ||
                fontName.indexOf("futura") != -1 ||
                fontName.indexOf("stonesans") != -1 ||
                fontName.indexOf("gillsans") != -1 ||
                fontName.indexOf("akzidenz") != -1 ||
                fontName.indexOf("frutiger") != -1 ||
                fontName.indexOf("grotesk") != -1)) {
            // important, add style information
            font = findFont("lucidasans-" + getFontSytle(decorations, flags), 0);
        }
        // see if we working with a mono spaced font "Mono Spaced"
        // java equivalent is "Lucida Sans Typewriter"
        else if ((fontName.indexOf("courier") != -1 ||
                fontName.indexOf("couriernew") != -1 ||
                fontName.indexOf("prestige") != -1 ||
                fontName.indexOf("eversonmono") != -1)) {
            // important, add style information
            font = findFont("lucidasanstypewriter-" + getFontSytle(decorations, flags), 0);
        }
        // if all else fails go with the serif as it is the most common font family
        else {
//            System.out.println("Decorations " + decorations);
//            System.out.println("flags " + flags);
//            System.out.println("sytel " + getFontSytle(decorations, flags));
            font = findFont("lucidabright-" + getFontSytle(decorations, flags), 0);
        }

        return font;
    }

    /**
     * Gets a NFont instance by matching against font sytle commonalities in the
     * of know type1 fonts
     *
     * @param fontName font name to search for
     * @param flags    style flags
     * @return a valid NFont if a match is found, null otherwise.
     */
    private FontFile getType1Fonts(String fontName, int flags) {
        FontFile font = null;
        boolean found = false;
        boolean isType1Available = true;
        // find a match for family in the type 1 nfont table
        for (int i = 0, max = TYPE1_FONT_DIFFS.length; i < max; i++) {
            for (int j = 0, max2 = TYPE1_FONT_DIFFS[i].length; j < max2; j++) {
                // first check to see font name matches any elements
                if (TYPE1_FONT_DIFFS[i][0].indexOf(fontName) >= 0) {
                    // next see if know type1 fonts are installed
                    if (isType1Available) {
                        font = findFont(TYPE1_FONT_DIFFS[i][1], flags);
                        if (font != null) {
                            found = true;
                            break;
                        } else {
                            isType1Available = false;
                        }
                    }
                    // do a full search for possible matches.
                    font = findFont(TYPE1_FONT_DIFFS[i][j], flags);
                    if (font != null) {
                        found = true;
                        break;
                    }
                }

            }
            // break out of second loop
            if (found) break;
        }
        return font;
    }

    /**
     * Utitility method which maps know sytle strings to an integer value which
     * is used later for effeciant font searching.
     * todo: move out to FontUtil and use awt contants
     *
     * @param name base name of font.
     * @return integer representing dffs
     */
    private static int guessFontStyle(String name) {
        name = name.toLowerCase();
        int decorations = 0;
        if ((name.indexOf("boldital") > 0) || (name.indexOf("demiital") > 0)) {
            decorations |= BOLD_ITALIC;
        } else if (name.indexOf("bold") > 0 || name.indexOf("black") > 0
                || name.indexOf("demi") > 0) {
            decorations |= BOLD;
        } else if (name.indexOf("ital") > 0 || name.indexOf("obli") > 0) {
            decorations |= ITALIC;
        } else {
            decorations |= PLAIN;
        }
        return decorations;
    }

    /**
     * Returns the string representation of a font style specified by the
     * decoration and flags integers.
     *
     * @param sytle style specified by known offsets
     * @param flags flags from pdf dictionary
     * @return string representation of styles specified by the two integers.
     */
    private String getFontSytle(int sytle, int flags) {
        // Get any useful data from the flags integer.
        String style = "";
        if ((sytle & BOLD_ITALIC) == BOLD_ITALIC) {
            style += " BoldItalic";
        } else if ((sytle & BOLD) == BOLD) {
            style += " Bold";
        } else if ((sytle & ITALIC) == ITALIC) {
            style += " Italic";
        } else if ((sytle & PLAIN) == PLAIN) {
            style += " Plain";
        }
        return style;
    }
}
