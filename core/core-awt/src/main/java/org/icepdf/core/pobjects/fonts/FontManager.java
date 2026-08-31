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

import org.icepdf.core.util.Defs;
import org.icepdf.core.util.FontUtil;
import org.icepdf.core.util.SystemProperties;

import java.awt.*;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.icepdf.core.pobjects.fonts.zfont.fontFiles.ZFontTrueType;
import org.apache.fontbox.ttf.TrueTypeFont;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

/**
 * <p>The <code>FontManager</code> class is responsible for finding available
 * fonts on the client operating system.  This class will detect fonts the OS
 * and try and load fonts in the known locations for the particular OS.  The
 * FontManager also does a recursive descent into base folders  to try and find
 * more fonts which is extremely important on Linux systems.</p>
 * <p>It is possible to specify other directories to search for fonts via the
 * readSystemFonts methods extraFontPaths parameter {@link #readSystemFonts}.
 * Reading all of an operating systems font's can be time-consuming. To help
 * speed up this process the method getFontProperties exports font data via a
 * Properties object.  The font Properties object can then be saved to disk or
 * be read back into the FontManager via the setFontProperties method.  </p>
 *
 * @since 2.0
 */
public class FontManager {

    private static final Logger logger =
            Logger.getLogger(FontManager.class.getName());

    /**
     * You can set an allowListPattern by Setting the System Property "org.icepdf.core.pobjects.fonts
     * .fontFileAllowListPattern":
     * <pre><code>
     * System.getProperties().put(FONT_FILE_ALLOW_LIST_PATTERN_PROPERTY, MOST_COMMON_FONTS_PATTERN);
     * </code></pre>
     */
    public static final String FONT_FILE_ALLOW_LIST_PATTERN_PROPERTY =
            "org.icepdf.core.pobjects.fonts.fontFileAllowListPattern";

    /**
     * Target of this Pattern is to get some system fonts, but not too many if memory is a topic. This
     * Pattern has a positive list in the beginning as well as a negative list with the Unicode, to exclude
     * some "huge" Unicode-Fonts.
     * But of course this is just an example to get started with an allowList.
     */
    public static final String MOST_COMMON_FONTS_PATTERN =
            "(?i)^.*?(Times New Roman|arial|Courier New|Helvetica)(?!.*?Unicode).*?";

    /**
     * Allow list pattern for font file names.  If the pattern is empty no filtering takes place.  The default
     * is an empty string which means all fonts are loaded.
     */
    public static final String DEFAULT_FONT_FILE_ALLOW_LIST_PATTERN = "";

    // stores all font data.  volatile for safe publication of the list reference; structural
    // reads/iteration go through snapshotFontList() so a lookup never iterates a list a writer
    // (readSystemFonts/setFontProperties) is concurrently mutating.
    private static volatile List<Object[]> fontList;

    // stores fonts loaded from jar, these won't be cached
    private static volatile List<Object[]> fontJarList;

    // flags for detecting font decorations
    private static final int PLAIN = 0xF0000001;
    private static final int BOLD = 0xF0000010;
    private static final int ITALIC = 0xF0000100;
    private static final int BOLD_ITALIC = 0xF0001000;

    // Differences for type1 fonts which match adobe core14 metrics
    private static final String[][] TYPE1_FONT_DIFFS =
            {{"Bookman-Demi", "URWBookmanL-DemiBold", "Arial"},
                    {"Bookman-DemiItalic", "URWBookmanL-DemiBoldItal", "Arial"},
                    {"Bookman-Light", "URWBookmanL-Ligh", "Arial"},
                    {"Bookman-LightItalic", "URWBookmanL-LighItal", "Arial"},
                    {"Courier", "NimbusMonL-Regu", "Nimbus Mono L", "CourierNew", "CourierNewPSMT"},
                    {"Courier-Oblique", "NimbusMonL-ReguObli", "Nimbus Mono L", "Courier,Italic", "CourierNew-Italic"
                            , "CourierNew,Italic", "CourierNewPS-ItalicMT"},
                    {"Courier-Bold", "NimbusMonL-Bold", "Nimbus Mono L", "Courier,Bold", "CourierNew,Bold",
                            "CourierNew-Bold", "CourierNewPS-BoldMT"},
                    {"Courier-BoldOblique", "NimbusMonL-BoldObli", "Nimbus Mono L", "Courier,BoldItalic", "CourierNew" +
                            "-BoldItalic", "CourierNew,BoldItalic", "CourierNewPS-BoldItalicMT"},
                    {"AvantGarde-Book", "URWGothicL-Book", "Arial"},
                    {"AvantGarde-BookOblique", "URWGothicL-BookObli", "Arial"},
                    {"AvantGarde-Demi", "URWGothicL-Demi", "Arial"},
                    {"AvantGarde-DemiOblique", "URWGothicL-DemiObli", "Arial"},
                    {"Helvetica", "Helvetica", "Arial", "ArialMT", "NimbusSanL-Regu", "Nimbus Sans L"},
                    {"Helvetica-Oblique", "NimbusSanL-ReguItal", "Nimbus Sans L", "Helvetica,Italic", "Helvetica" +
                            "-Italic", "Arial,Italic", "Arial-Italic", "Arial-ItalicMT"},
                    {"Helvetica-Bold", "Helvetica,Bold", "Arial,Bold", "Arial-Bold", "Arial-BoldMT", "NimbusSanL-Bold"
                            , "Nimbus Sans L"},
                    {"Helvetica-BoldOblique", "NimbusSanL-BoldItal", "Helvetica-BlackOblique", "Nimbus Sans L",
                            "Helvetica,BoldItalic", "Helvetica-BoldItalic", "Arial,BoldItalic", "Arial-BoldItalic",
                            "Arial-BoldItalicMT"},
                    {"Helvetica-Black", "Helvetica,Bold", "Arial,Bold", "Arial-Bold", "Arial-BoldMT", "NimbusSanL" +
                            "-Bold", "Nimbus Sans L"},
                    {"Helvetica-BlackOblique", "NimbusSanL-BoldItal", "Helvetica-BlackOblique", "Nimbus Sans L",
                            "Helvetica,BoldItalic", "Helvetica-BoldItalic", "Arial,BoldItalic", "Arial-BoldItalic",
                            "Arial-BoldItalicMT"},
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
                    {"Times-Roman", "NimbusRomNo9L-Regu", "Nimbus Roman No9 L", "TimesNewRoman", "TimesNewRomanPSMT",
                            "TimesNewRomanPS"},
                    {"Times-Italic", "NimbusRomNo9L-ReguItal", "Nimbus Roman No9 L", "TimesNewRoman,Italic",
                            "TimesNewRoman-Italic", "TimesNewRomanPS-Italic", "TimesNewRomanPS-ItalicMT"},
                    {"Times-Bold", "NimbusRomNo9L-Medi", "Nimbus Roman No9 L", "TimesNewRoman,Bold", "TimesNewRoman" +
                            "-Bold", "TimesNewRomanPS-Bold", "TimesNewRomanPS-BoldMT"},
                    {"Times-BoldItalic", "NimbusRomNo9L-MediItal", "Nimbus Roman No9 L", "TimesNewRoman,BoldItalic",
                            "TimesNewRoman-BoldItalic", "TimesNewRomanPS-BoldItalic", "TimesNewRomanPS-BoldItalicMT"},
                    {"Symbol", "StandardSymL", "Standard Symbols L"},
                    {"ZapfChancery-MediumItalic", "URWChanceryL-MediItal", "Arial"},
                    {"ZapfDingbats", "Dingbats", "Zapf-Dingbats"}
            };

    /*
     * Substitution candidates per character collection, searched by getAsianInstance.
     *
     * NOTE the search runs from the END of each array towards the front, so the LAST entry has the
     * HIGHEST priority.  The Japanese faces that close the original lists are deliberate last
     * resorts - Han unification means a Japanese face draws most Chinese and Korean text acceptably -
     * but they must not outrank a face made for the document's own collection: a Japanese font has no
     * glyph for the traditional forms (產 U+7522, 內 U+5167) or for Chinese-only characters
     * (另 U+53E6), which came out as .notdef on a Traditional Chinese page.
     *
     * Only families that carry Latin as well as CJK belong here.  A CJK-only face such as Droid Sans
     * Fallback is not a substitute: it has no ASCII at all, so the Latin runs a CJK document mixes in
     * (part numbers, URLs) would turn into .notdef instead.
     */
    /*
     * Last-resort substitutes by family class, tried in order.
     *
     * The Lucida faces the original lists asked for are what the JDK used to install under
     * ${java.home}/lib/fonts; JDK 9 dropped them, so on any modern runtime the whole core-font
     * fallback found nothing and getInstance fell through to "first font in the list", which is
     * alphabetical and therefore arbitrary - a Tahoma document was being drawn in Andale Mono, a
     * monospace face.
     *
     * ORDER IS BY METRIC COMPATIBILITY, not by how complete the face is.  A substitute is laid out
     * on the widths the PDF declares, so a face whose own advances are wider than those widths
     * collides with itself; only the glyph shapes come from the substitute, never the spacing.
     * Liberation and the msttcorefonts faces are width-for-width clones of Helvetica/Times/Courier
     * (measured: ratio 1.000, worst glyph within 0%), which is why they lead.  DejaVu is a fine
     * face with far better coverage, but it runs 6-30% wide against Helvetica and Times metrics,
     * and it used to be first here: every non-embedded Type1 with a name the lists below do not
     * recognise was drawn in a face too wide for its own layout.
     */
    private static final String[] SERIF_SUBSTITUTES = {
            "Liberation Serif", "Times New Roman", "Nimbus Roman", "Thorndale AMT",
            "FreeSerif", "DejaVu Serif", "Serif",
    };

    private static final String[] SANS_SUBSTITUTES = {
            "Liberation Sans", "Arial", "Helvetica", "Nimbus Sans", "Albany AMT",
            "FreeSans", "DejaVu Sans", "SansSerif",
    };

    private static final String[] MONO_SUBSTITUTES = {
            "Liberation Mono", "Courier New", "Nimbus Mono PS", "Cumberland AMT",
            "FreeMono", "DejaVu Sans Mono", "Monospaced",
    };

    /*
     * Condensed counterparts, used when the base name declares a narrow width class.
     *
     * A condensed face laid out on a full-width substitute is the worst case of all - measured
     * ratios of 1.3 to 1.9 against the document's own /Widths, i.e. glyphs half again as wide as
     * the space they are given.  Each list falls back to its full-width equivalent, since a
     * condensed face is far from universally installed and a normal-width match still beats
     * nothing.
     */
    private static final String[] SERIF_CONDENSED_SUBSTITUTES = {
            "Liberation Serif Narrow", "Times New Roman Condensed", "DejaVu Serif Condensed",
    };

    private static final String[] SANS_CONDENSED_SUBSTITUTES = {
            "Liberation Sans Narrow", "Arial Narrow", "Nimbus Sans Narrow", "DejaVu Sans Condensed",
    };

    /*
     * Substitutes for the two symbolic core fonts.  Ranked by GLYPH COVERAGE, the opposite of the
     * text lists above: a symbolic font that falls back to a text face draws nothing but .notdef
     * boxes, which is how a bullet or a copyright sign goes missing.
     *
     * The URW clones lead because they are the real thing - metric clones that keep Adobe's own
     * glyph NAMES (a1-a191 for the dingbats, "alpha"/"copyrightserif" for Symbol), so they need no
     * name-to-Unicode step at all.  They ship as the urw-base35/gsfonts package on Linux and are
     * commonly present wherever Ghostscript is.  Both the modern names (Standard Symbols PS,
     * D050000L) and the older ones (StandardSymL, Dingbats) are listed since distributions renamed
     * them.  After those come Unicode-cmap faces, which work only via the glyph-list lookup in
     * ZFontTrueType.codeToGID.  DejaVu Sans is last and is the reliable one: measured here, it
     * covers 100% of the sampled dingbat, Symbol and everyday (bullet, copyright) code points,
     * where Liberation and the msttcorefonts faces cover none of the dingbats.
     */
    private static final String[] DINGBAT_SUBSTITUTES = {
            "D050000L", "Dingbats", "Zapf Dingbats", "ZapfDingbats", "URW Dingbats L",
            "OpenSymbol", "Noto Sans Symbols2", "Symbola", "DejaVu Sans",
    };

    private static final String[] SYMBOL_SUBSTITUTES = {
            "Standard Symbols PS", "StandardSymL", "Standard Symbols L", "Symbol", "URW Symbol",
            "OpenSymbol", "Noto Sans Symbols", "Symbola", "DejaVu Sans",
    };

    private static final String[] JAPANESE_FONT_NAMES = {
            // windows
            "Arial Unicode MS", "PMingLiU", "MingLiU",
            "MS PMincho", "MS Mincho", "Kochi Mincho", "Hiragino Mincho Pro",
            "KozMinPro Regular Acro", "HeiseiMin W3 Acro", "Adobe Ming Std Acro",
            // linux
            "ipaexmincho", "Kochi Gothic", "Hiragino Kaku Gothic Pro",
            // linux, japanese specific (highest priority)
            "Noto Sans CJK JP", "Noto Serif CJK JP",
    };

    private static final String[] CHINESE_SIMPLIFIED_FONT_NAMES = {
            "Arial Unicode MS", "PMingLiU", "MingLiU",
            "SimSun", "NSimSun", "Kochi Mincho", "STFangsong", "STSong Light Acro",
            "Adobe Song Std Acro", "stsong",
            // linux
            "ipaexmincho", "Kochi Gothic", "Hiragino Kaku Gothic Pro",
            // linux, simplified chinese specific (highest priority)
            "AR PL UMing CN", "AR PL UKai CN", "WenQuanYi Zen Hei",
            "Noto Sans CJK SC", "Noto Serif CJK SC",
    };

    private static final String[] CHINESE_TRADITIONAL_FONT_NAMES = {
            "Arial Unicode MS", "PMingLiU", "MingLiU",
            "SimSun", "NSimSun", "Kochi Mincho", "BiauKai", "MSungStd Light Acro",
            "Adobe Song Std Acro",
            // linux
            "umingcn", "ipaexmincho", "Kochi Gothic", "Hiragino Kaku Gothic Pro",
            // linux, traditional chinese specific (highest priority)
            "AR PL UKai TW", "AR PL UMing TW",
            "Noto Sans CJK TC", "Noto Serif CJK TC",
    };

    /*
     * Korean is the one collection where the Japanese last resort is nearly useless: Han unification
     * covers the hanja, but a Japanese face carries no Hangul whatsoever, so a Korean page drawn with
     * one is mostly .notdef.  The Japanese entries therefore sit at the FRONT here - lowest priority,
     * still better than no font at all - so that any face with Hangul outranks them.  Everywhere else
     * they stay where they were.
     */
    private static final String[] KOREAN_FONT_NAMES = {
            // last resort, hanja only
            "ipaexmincho", "Kochi Gothic", "Hiragino Kaku Gothic Pro",
            "Arial Unicode MS", "Dotum", "Gulim", "New Gulim", "GulimChe", "Batang",
            "BatangChe", "HYSMyeongJoStd Medium Acro", "Adobe Myungjo Std Acro",
            "AppleGothic", "Malgun Gothic", "UnDotum", "UnShinmun", "Baekmuk Gulim",
            // linux, korean specific (highest priority)
            "WenQuanYi Zen Hei", "WenQuanYi Micro Hei",
            "NanumGothic", "NanumMyeongjo", "Noto Sans CJK KR", "Noto Serif CJK KR",
    };

    /**
     * Java base font class, generally ${java.home}\lib\fonts.  This is the base font directory that is used
     * for searching for system fonts.  If all else fails this should be the fall back directory.
     */
    public static final String JAVA_FONT_PATH = SystemProperties.JAVA_HOME + "/lib/fonts";

    /**
     * Default search path for fonts on windows systems.
     */
    public static final List<String> WINDOWS_FONT_PATHS = Arrays.asList(
            // windir works for winNT and older 9X system, same as "systemroot"
            JAVA_FONT_PATH,
            System.getenv("WINDIR") + "\\Fonts");

    /**
     * Default search path for fonts on Apple systems.
     */
    public static final List<String> MAC_FONT_PATHS = Arrays.asList(
            Defs.sysProperty("user.home") + "/Library/Fonts/",
            "/Library/Fonts/",
            JAVA_FONT_PATH,
            "/Network/Library/Fonts/",
            "/System/Library/Fonts/",
            "/System Folder/Fonts",
            "/usr/local/share/ghostscript/");

    /**
     * Default search path for fonts on Linux/Unix systems.
     */
    public static final List<String> LINUX_FONT_PATHS = Arrays.asList(
            "/usr/share/fonts/",
            JAVA_FONT_PATH,
            "/usr/X11R6/lib/X11/fonts/",
            "/usr/openwin/lib/",
            "/usr/sfw/share/a2ps/afm/",
            "/usr/sfw/share/ghostscript/fonts/",
            Defs.sysProperty("user.home") + "/.local/share/fonts/");

    // array indexes for font data stored in properties.
    private static final int FONT_NAME = 0;
    private static final int FONT_FAMILY = 1;
    private static final int FONT_DECORATIONS = 2;
    private static final int FONT_PATH = 3;

    /**
     * Mutable list of font names that are excluded from font font substitution.
     */
    public static final List<String> BASE_NAME_EXCLUSION_LIST = Arrays.asList(
            "opensymbol",
            "starsymbol",
            "symbolmt",
            "notosanssymbols",
            "arial-black",
            "arial-blackitalic",
            "new",
            // mapping issue with standard ascii, not sure why, TimesNewRomanPSMT is ok.
//            "timesnewromanps",
            // doesn't seem to the correct cid mapping otf version anyways.
            "kozminpro-regular"
    );

    //        "HEB____.TTF"
    /**
     * Mutable list of font file names that are excluded from font font substitution. Font names must also
     * include the file extension.
     */
    public static final List<String> FONT_FILE_NAME_EXCLUSION_LIST = List.of();

    /**
     * Change the base font name from lucidasans which is a Java Physical Font
     * name.  The name should be change to one of Java's logical font names:
     * Dialog,  DialogInput, Monospaced, Serif, SansSerif.  The closest logical
     * name that match LucidaSans is SansSerif.
     */
    private static final String baseFontName;

    private Pattern fontAllowListPattern;

    static {
        baseFontName = Defs.property("org.icepdf.core.font.basefont", "lucidasans");
    }

    // Singleton instance of class.  volatile so double-checked locking in getInstance() publishes safely.
    private static volatile FontManager fontManager;

    /**
     * VisibilityForTesting
     */
    FontManager() {
        fontAllowListPattern = getFontAllowListPattern();
    }

    /**
     * <p>Returns a static instance of the FontManager class.</p>
     *
     * @return instance of the FontManager.
     */
    public static FontManager getInstance() {
        // double-checked locking; fontManager is volatile so the constructed instance is
        // published safely and two threads can't each create their own manager (each build
        // was compiling the allow-list regex needlessly, and worse, one could win the field).
        FontManager local = fontManager;
        if (local == null) {
            synchronized (FontManager.class) {
                local = fontManager;
                if (local == null) {
                    local = new FontManager();
                    fontManager = local;
                }
            }
        }
        return local;
    }

    /**
     * <p>Initializes the fontList by reading the system fonts paths via readSystemFonts()
     * but only if the fontList is null or is empty.  Generally the fontManager
     * is used with the org.icepdf.ri.util.FontPropertiesManager
     *
     * @return instance of the singleton fontManager.
     */
    public synchronized FontManager initialize() {
        // synchronized so the check-then-read is atomic with the readSystemFonts writer;
        // otherwise two first-render threads can both see an empty list and both scan.
        if (fontList == null || fontList.isEmpty()) {
            readSystemFonts(null);
        }
        return fontManager;
    }

    /**
     * Returns a stable snapshot of the system font list for lock-free iteration by the
     * substitution lookups.  Taken under the FontManager monitor so it blocks until any
     * in-progress writer (readSystemFonts/setFontProperties) has finished, then hands back
     * a complete copy the caller can iterate without a ConcurrentModificationException or
     * observing a half-built list.
     *
     * @return copy of the current font list, never null (empty when uninitialised).
     */
    private synchronized List<Object[]> snapshotFontList() {
        return fontList == null ? new ArrayList<>(0) : new ArrayList<>(fontList);
    }

    /**
     * Returns a stable snapshot of the jar font list, or null if none is registered.
     *
     * @return copy of the current jar font list, or null when uninitialised.
     */
    private synchronized List<Object[]> snapshotFontJarList() {
        return fontJarList == null ? null : new ArrayList<>(fontJarList);
    }

    /**
     * <p>Gets a Properties object containing font information for the operating
     * system which the FontManager is running on.  This Properties object
     * can be saved to disk and read at a later time using the {@link #setFontProperties}
     * method.</p>
     *
     * @return Properties object containing font data information.
     */
    public synchronized Properties getFontProperties() {
        Properties fontProperites;
        // make sure we are initialized
        if (fontList == null) {
            fontList = new ArrayList<>();
        }
        // copy all data from fontList into the properties file
        fontProperites = new Properties();
        Iterator<Object[]> fontIterator = fontList.iterator();
        Object[] currentFont;
        String name;
        String family;
        Integer decorations;
        String path;
        // Build the properties file using the font name as the key and
        // the value is the family, decoration and path information
        // separated by the "|" character.
        while (fontIterator.hasNext()) {
            currentFont = fontIterator.next();
            name = (String) currentFont[FONT_NAME];
            family = (String) currentFont[FONT_FAMILY];
            decorations = (Integer) currentFont[FONT_DECORATIONS];
            path = (String) currentFont[FONT_PATH];
            // add the new entry
            fontProperites.put(name, family + "|" + decorations + "|" + path);
        }
        return fontProperites;
    }

    /**
     * <p>Reads font data from the Properties file.  All name and key data replaces
     * any existing font information.</p>
     *
     * @param fontPreferences Properties object containing valid font information.
     * @throws IllegalArgumentException thrown, if there is a problem parsing the
     *                                  Properties file.  If thrown, the calling application should re-read
     *                                  the system fonts.
     */
    public synchronized void setFontProperties(Preferences fontPreferences)
            throws IllegalArgumentException {
        String errorString = "Error parsing font properties ";
        try {
            fontList = new ArrayList<>(500);
            String[] fontKeys = fontPreferences.keys();
            String name;
            String family;
            int decorations;
            String path;
            StringTokenizer tokens;
            Object[] fontProperty;
            // read in font information
            for (String fontKey : fontKeys) {
                name = fontKey;
                tokens = new StringTokenizer(fontPreferences.get(name, null), "|");
                // get family, decoration and path tokens
                family = tokens.nextToken();
                decorations = Integer.parseInt(tokens.nextToken());
                path = tokens.nextToken();
                if (name != null && family != null && path != null) {
                    // check exclusion list
                    fontProperty = new Object[]{name, family, decorations, path};
                    if (!checkExclusionLists(fontProperty)) {
                        fontList.add(new Object[]{name, family, decorations, path});
                    }
                } else {
                    throw new IllegalArgumentException(errorString);
                }
            }
            sortFontListByName();
        } catch (Exception e) {
            logger.log(Level.FINE, "Error setting font properties ", e);
            throw new IllegalArgumentException(errorString);
        }
    }

    /**
     * Clears internal font list of items. Used to clean list while constructing
     * a new list.
     */
    public synchronized void clearFontList() {
        if (fontList != null) {
            fontList.clear();
        }
    }

    /**
     * <p>Reads font from the specified array of file paths only, no .  This font data is used to substitute fonts
     * which are not
     * embedded inside a PDF document.</p>
     *
     * @param extraFontPaths array String object where each entry represents
     *                       a system directory path containing font programs.
     */
    public synchronized void readFonts(String[] extraFontPaths) {
        readSystemFonts(extraFontPaths, true);
    }

    /**
     * Reads system fonts as defined in SYSTEM_FONT_PATHS plush any extra fonts paths.  The reading
     * of system fonts can be suspended with the param skipSystemFonts.
     *
     * @param extraFontPaths  optional, extra fonts path to read.
     * @param skipSystemFonts true to skip system fonts, extraFontsPaths should not be null if skipSystemFonts=true.
     */
    private synchronized void readSystemFonts(String[] extraFontPaths, boolean skipSystemFonts) {
        // create a new font list if needed.
        if (fontList == null) {
            fontList = new ArrayList<>(150);
        }


        ArrayList<String> fontDirectories = new ArrayList<>();
        // load the appropriate font set for the OS.
        if (!skipSystemFonts) {
            String operationSystem = SystemProperties.OS_NAME;
            if (operationSystem != null) {
                operationSystem = operationSystem.toLowerCase();
                if (operationSystem.contains("win")) {
                    logger.finer("Detected Windows loading appropriate font paths.");
                    fontDirectories.addAll(WINDOWS_FONT_PATHS);
                } else if (operationSystem.contains("mac")) {
                    logger.finer("Detected OSX loading appropriate font paths.");
                    fontDirectories.addAll(MAC_FONT_PATHS);
                } else {
                    // must be an inix.
                    logger.finer("Detected Unix/Linux loading appropriate font paths.");
                    fontDirectories.addAll(LINUX_FONT_PATHS);
                }
            }

        }
        // tack on the extraFontPaths
        if (extraFontPaths != null) {
            logger.finer("Loading extraFontPaths specified by users");
            fontDirectories.addAll(Arrays.asList(extraFontPaths));
        }

        // check to make sure we have at least a few fonts.
        if (fontDirectories.size() == 0) {
            // fall back to at least a few fonts.
            logger.finer("No fonts specified or detected falling back to JAVA font paths.");
            fontDirectories.add(JAVA_FONT_PATH);
        }

        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Starting recursive scan of specified font directories for system fonts.");
        }
        loadSystemFont(fontDirectories);
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
    public synchronized void readSystemFonts(String[] extraFontPaths) {
        readSystemFonts(extraFontPaths, false);
    }

    private void loadSystemFont(List<String> fontDirectories) {
        try {
            for (String fontDirectory : fontDirectories) {
                File directory = new File(fontDirectory);
                if (directory.canRead() && directory.isDirectory()) {
                    logger.finer("looking into directory " + directory.getAbsolutePath());
                    // load files
                    File[] files = directory.listFiles();
                    if (files != null) {
                        List<String> dirPaths = new ArrayList<>();
                        for (File file : files) {
                            if (isFontFileAllowed(file)) {
                                // load the font.
                                evaluateFontForInsertion(file.getAbsolutePath());
                            } else if (file.isDirectory()) {
                                dirPaths.add(file.getAbsolutePath());
                            }
                        }
                        // If we have some directories, then we want ot recursively descend.
                        loadSystemFont(dirPaths);
                    }
                } else if (directory.canRead() && directory.isFile()) {
                    // load the font.
                    evaluateFontForInsertion(directory.getAbsolutePath());
                }
            }
        } catch (SecurityException e) {
            logger.log(Level.WARNING, "SecurityException: failed to load fonts from directory: ", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load fonts from directory: ", e);
        }
    }

    private void evaluateFontForInsertion(String fontPath) {
        // a collection holds several faces and has to contribute one entry each, or only the first
        // would ever be found by name.
        if (isFontCollection(fontPath)) {
            evaluateFontCollectionForInsertion(fontPath);
            return;
        }
        // try loading the font
        FontFile font = buildFont(fontPath);
        // if a readable font was found
        if (font != null) {
            logger.finer("Found font file" + fontPath);
            // normalize name
            String fontName = font.getName().toLowerCase();
            // Add new font data to the font list
            Object[] fontProperty = new Object[]{font.getName().toLowerCase(), // original PS name
                    FontUtil.normalizeString(font.getFamily()), // family name
                    guessFontStyle(fontName), // weight and decorations, mainly bold,italic
                    fontPath};
            if (!checkExclusionLists(fontProperty)) {
                fontList.add(fontProperty);  // path to font on OS
            }
            if (logger.isLoggable(Level.FINER)) {
                logger.finer("Adding system font: " + font.getName() + " " + fontPath);
            }
        }
    }

    /**
     * Registers every face of a TrueType/OpenType collection, one font-list entry each, all pointing
     * at the same file.  {@link #buildFont(String, String)} later re-selects the face by the
     * PostScript name recorded here.
     * <p>
     * Only the names are needed at scan time, so the faces are read and dropped; nothing keeps the
     * collection's buffer alive.
     */
    private void evaluateFontCollectionForInsertion(String fontPath) {
        File file = new File(fontPath);
        if (!file.canRead()) {
            return;
        }
        try {
            TrueTypeCollection collection = new TrueTypeCollection(
                    new ByteArrayInputStream(Files.readAllBytes(file.toPath())));
            collection.processAllFonts(face -> {
                String name = face.getName();
                if (name == null || isPostScriptOutlines(face)) {
                    // CFF-outline faces (Noto CJK and most OpenType collections) are skipped: the
                    // renderer only draws glyf outlines for system fonts, so registering them would
                    // hand the substitution machinery a font that silently draws nothing.
                    return;
                }
                String fontName = name.toLowerCase();
                Object[] fontProperty = new Object[]{fontName,      // original PS name
                        FontUtil.normalizeString(face.getNaming() != null
                                ? face.getNaming().getFontFamily() : name),  // family name
                        guessFontStyle(fontName),                   // weight and decorations
                        fontPath};
                if (!checkExclusionLists(fontProperty)) {
                    fontList.add(fontProperty);
                }
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("Adding system font from collection: " + name + " " + fontPath);
                }
            });
            collection.close();
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font collection " + fontPath, e);
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
    public synchronized String[] getAvailableNames() {
        if (fontList != null) {
            String[] availableNames = new String[fontList.size()];
            Iterator<Object[]> nameIterator = fontList.iterator();
            Object[] fontData;
            for (int i = 0; nameIterator.hasNext(); i++) {
                fontData = nameIterator.next();
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
    public synchronized String[] getAvailableFamilies() {
        if (fontList != null) {
            String[] availableNames = new String[fontList.size()];
            Iterator<Object[]> nameIterator = fontList.iterator();
            Object[] fontData;
            for (int i = 0; nameIterator.hasNext(); i++) {
                fontData = nameIterator.next();
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
    public synchronized String[] getAvailableStyle() {
        if (fontList != null) {
            String[] availableStyles = new String[fontList.size()];
            Iterator<Object[]> nameIterator = fontList.iterator();
            Object[] fontData;
            int decorations;
            StringBuilder style = new StringBuilder();
            for (int i = 0; nameIterator.hasNext(); i++) {
                fontData = nameIterator.next();
                decorations = (Integer) fontData[2];
                if ((decorations & BOLD_ITALIC) == BOLD_ITALIC) {
                    style.append(" BoldItalic");
                } else if ((decorations & BOLD) == BOLD) {
                    style.append(" Bold");
                } else if ((decorations & ITALIC) == ITALIC) {
                    style.append(" Italic");
                } else if ((decorations & PLAIN) == PLAIN) {
                    style.append(" Plain");
                }
                availableStyles[i] = style.toString();
                style = new StringBuilder();
            }
            return availableStyles;
        }
        return null;
    }

    public FontFile getJapaneseInstance(String name, int fontFlags) {
        return getAsianInstance(snapshotFontList(), name, JAPANESE_FONT_NAMES, fontFlags);
    }

    public FontFile getKoreanInstance(String name, int fontFlags) {
        return getAsianInstance(snapshotFontList(), name, KOREAN_FONT_NAMES, fontFlags);
    }

    public FontFile getChineseTraditionalInstance(String name, int fontFlags) {
        return getAsianInstance(snapshotFontList(), name, CHINESE_TRADITIONAL_FONT_NAMES, fontFlags);
    }

    public FontFile getChineseSimplifiedInstance(String name, int fontFlags) {
        return getAsianInstance(snapshotFontList(), name, CHINESE_SIMPLIFIED_FONT_NAMES, fontFlags);
    }

    private FontFile getAsianInstance(List<Object[]> fontList, String name, String[] list, int flags) {

        // fontList is a snapshot supplied by the caller (never null, may be empty).
        FontFile font;
        if (list != null) {
            // search for know list of fonts
            for (int i = list.length - 1; i >= 0; i--) {
                // try and find an instance of the name and family from the font list
                font = findFont(fontList, name, flags);
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
                font = findFont(fontList, list[i], flags);
                if (font != null) {
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("Font Substitution: Found Asian font: " + font.getName() + " for named font " + name);
                    }
                    return font;
                }
            }
        }

        return null;
    }

    /**
     * Reads the specified resources from the specified package.  This method
     * is intended to aid in the packaging of fonts used for font substitution
     * and avoids the need to install fonts on the client operating system.
     * <br>
     * The following font resource types are supported are support:
     * <ul>
     * <li>TrueType - *.ttf, *.dfont, *.ttc</li>
     * <li>Type1 - *.pfa, *.pfb</li>
     * <li>OpenType - *.otf, *.otc</li>
     * </ul>
     *
     * @param fontResourcePackage package to look for the resources in.
     * @param resources           file names of font resources to load.
     */
    public synchronized void readFontPackage(String fontResourcePackage, List<String> resources) {
        if (fontJarList == null) {
            fontJarList = new ArrayList<>(35);
        }
        URL resourcePath;
        FontFile font;
        String fontName;
        for (String resourceName : resources) {
            // build the url and add the font to the font list.
            resourcePath = FontManager.class.getResource("/" + fontResourcePackage + "/" + resourceName);
            // try loading the font
            font = buildFont(resourcePath);
            // if a readable font was found
            if (font != null) {
                // normalize name
                fontName = font.getName().toLowerCase();
                // Add new font data to the font list
                fontJarList.add(new Object[]{font.getName().toLowerCase(), // original PS name
                        FontUtil.normalizeString(font.getFamily()), // family name
                        guessFontStyle(fontName), // weight and decorations, mainly bold,italic
                        resourcePath.toString()});  // path to font on OS
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("Adding system font: " + font.getName() + " " + resourcePath);
                }
            }
        }
    }

    /**
     * <p>Get an instance of a FontFile from the given font name and flag decoration
     * information.</p>
     *
     * @param name  base name of font.
     * @param flags flags used to describe font.
     * @return a new instance of FontFile which best approximates the font described
     * by the name and flags attribute.
     */
    public FontFile getInstance(String name, int flags) {

        // Iterate stable snapshots rather than the live static lists: the lookups below
        // (and the slow buildFont disk reads they trigger) then run lock-free while a
        // concurrent readSystemFonts/setFontProperties can safely rebuild the shared lists.
        final List<Object[]> fontList = snapshotFontList();
        final List<Object[]> fontJarList = snapshotFontJarList();

        FontFile font;

        // try any attached jars first as they are likely controlled.
        if (fontJarList != null) {
            font = getType1Fonts(fontJarList, name, flags);
            if (font != null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Font Substitution: Found type1 font: " + font.getName() + " for named font " + name);
                }
                return font;
            }
        }

        // try and find equivalent type1 font
        font = getType1Fonts(fontList, name, flags);
        if (font != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Font Substitution: Found type1 font: " + font.getName() + " for named font " + name);
            }
            return font;
        }

        // check the font name first against the jars list.
        if (fontJarList != null) {
            font = findFont(fontJarList, name, flags);
            if (font != null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Font Substitution: Found type1 font: " + font.getName() + " for named font " + name);
                }
                return font;
            }
        }

        // try and find an instance of the name and family from the font list
        font = findFont(fontList, name, flags);
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

        // if all else fails return first font in fontList with matching style,
        // this should never happen, but just in case.
        if (fontList.size() > 0) {
            Object[] fontData;
            boolean found = false;
            int decorations = guessFontStyle(name);
            int style;
            // get first font that has a matching style
            for (int i = fontList.size() - 1; i >= 0; i--) {
                fontData = fontList.get(i);
                style = (Integer) fontData[FONT_DECORATIONS];
                if (((decorations & BOLD_ITALIC) == BOLD_ITALIC) &&
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
                if (found) {
                    font = buildFont((String) fontData[3], (String) fontData[0]);
                    break;
                }
            }
            if (!found) {
                fontData = fontList.get(0);
                font = buildFont((String) fontData[3], (String) fontData[0]);
            }
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
     * that has the specified style.
     *
     * @param fontName font name with any decoration information still appended to name.
     * @param flags    flags from content parser, to help guess style.
     * @return a valid font if found, null otherwise
     */
    private FontFile findFont(List<Object[]> fontList, String fontName, int flags) {

        FontFile font = null;
        // references for system font list.
        Object[] fontData;
        String baseName;
        String familyName;
        String path;
        // normalize the fontName we are trying to find a match for
        int decorations = guessFontStyle(fontName);
        String name = FontUtil.normalizeString(fontName);
        int style;

        if (fontList != null) {
            for (int i = fontList.size() - 1; i >= 0; i--) {
                fontData = fontList.get(i);
                baseName = (String) fontData[FONT_NAME];
                familyName = ((String) fontData[FONT_FAMILY]).replaceAll("(?i)(psmt|ps|mt)$", "");
                path = (String) fontData[FONT_PATH];
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest(baseName + " : " + familyName + "  : " + name);
                }
                if (fontName.toLowerCase().contains(baseName) || name.equals(familyName)) {
                    style = (Integer) fontData[2];
                    boolean found = false;
                    // ignore this font, as the cid mapping are not correct, or ther is
                    // just look and feel issues with them.
                    if (((decorations & BOLD_ITALIC) == BOLD_ITALIC) &&
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
                    else if (baseName.contains("wingdings") ||
                            baseName.contains("zapfdingbats") ||
                            baseName.contains("dingbats") ||
                            baseName.contains("symbol")) {
                        found = true;
                    }

                    if (found) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer("Match Found for: " + fontName + ":" + getFontStyle(style, 0).trim() +
                                    " Substituting " + baseName + ":" + path);
                        }
                        font = buildFont((String) fontData[3], (String) fontData[0]);
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
        return buildFont(fontPath, null);
    }

    /**
     * @param fontPath       font file to load
     * @param postScriptName the face wanted, when {@code fontPath} is a TrueType/OpenType collection
     *                       holding several; ignored for a single-font file
     */
    private FontFile buildFont(String fontPath, String postScriptName) {
        FontFile font = null;
        try {
            if (fontPath.startsWith("jar:file")) {
                font = buildFont(new URL(fontPath));
            } else {
                File file = new File(fontPath);
                if (!file.canRead()) {
                    return null;
                }
                font = isFontCollection(fontPath)
                        ? buildCollectionFont(file, postScriptName) : buildFont(file);
            }
        } catch (Exception e) {
            // there are a lot of system font that don't ready correctly, so don't get to noisy
            logger.log(Level.FINE, "Error reading font program.", e);
        }
        return font;
    }

    /**
     * True for a TrueType or OpenType <em>collection</em>, a single file holding several faces.  The
     * CJK fonts shipped by most Linux distributions (Noto CJK, AR PL UMing/UKai) come this way, and
     * FontBox's plain TrueType parser cannot read the {@code ttcf} header they start with.
     */
    private static boolean isFontCollection(String fontPath) {
        String lower = fontPath.toLowerCase();
        return lower.endsWith(".ttc") || lower.endsWith(".otc");
    }

    /**
     * Loads one face out of a collection by its PostScript name, the name the scan recorded for it.
     *
     * @return the named face, or the first one if the name is unknown; null if the file won't parse
     */
    private FontFile buildCollectionFont(File file, String postScriptName) {
        try {
            // Parse from memory: the returned faces read through the collection's buffer, so a
            // file-backed collection would have to stay open for as long as any face is alive.
            TrueTypeCollection collection = new TrueTypeCollection(
                    new ByteArrayInputStream(Files.readAllBytes(file.toPath())));
            // Not getFontByName: the scan records names lower-cased, and that method matches exactly,
            // so every lookup would miss and quietly hand back the collection's first face - which on
            // Noto CJK is the Japanese one, whatever collection was actually asked for.
            FaceByName wanted = new FaceByName(postScriptName);
            collection.processAllFonts(wanted);
            TrueTypeFont face = wanted.match != null ? wanted.match : wanted.first;
            if (face != null) {
                return new ZFontTrueType(face, file.toURI().toURL());
            }
        } catch (Throwable e) {
            logger.log(Level.FINE, "Error reading font collection " + file, e);
        }
        return null;
    }

    /**
     * True when the face carries PostScript/CFF outlines rather than {@code glyf} ones.  ZFontTrueType
     * and ZFontOpenType both read glyphs out of the {@code glyf} table, so a CFF face parses cleanly
     * and then draws nothing at all.
     */
    private static boolean isPostScriptOutlines(TrueTypeFont face) {
        try {
            return face instanceof org.apache.fontbox.ttf.OpenTypeFont
                    && ((org.apache.fontbox.ttf.OpenTypeFont) face).isPostScript();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Finds a collection's face by PostScript name, ignoring case, and remembers the first face as a
     * fallback.  FontBox only offers a visitor over a collection, hence the accumulator.
     */
    private static class FaceByName implements TrueTypeCollection.TrueTypeFontProcessor {
        private final String wanted;
        private TrueTypeFont match;
        private TrueTypeFont first;

        private FaceByName(String postScriptName) {
            this.wanted = postScriptName != null ? postScriptName.toLowerCase() : null;
        }

        @Override
        public void process(TrueTypeFont ttf) {
            if (first == null) {
                first = ttf;
            }
            if (match == null && wanted != null) {
                try {
                    String name = ttf.getName();
                    if (name != null && wanted.equals(name.toLowerCase())) {
                        match = ttf;
                    }
                } catch (IOException e) {
                    logger.log(Level.FINE, "Could not read a collection face's name", e);
                }
            }
        }
    }

    private FontFile buildFont(File fontFile) {
        String fontPath = fontFile.getPath();
        FontFactory fontFactory = FontFactory.getInstance();
        FontFile font = null;
        // found true type font
        if ((fontPath.endsWith(".ttf") || fontPath.endsWith(".TTF")) ||
                (fontPath.endsWith(".dfont") || fontPath.endsWith(".DFONT")) ||
                (fontPath.endsWith(".ttc") || fontPath.endsWith(".TTC"))) {
            font = fontFactory.createFontFile(fontFile, FontFactory.FONT_TRUE_TYPE, null);
        }
        // found Type 1 font
        else if ((fontPath.endsWith(".pfa") || fontPath.endsWith(".PFA")) ||
                (fontPath.endsWith(".pfb") || fontPath.endsWith(".PFB"))) {
            font = fontFactory.createFontFile(fontFile, FontFactory.FONT_TYPE_1, null);
        }
        // found OpenType font
        else if ((fontPath.endsWith(".otf") || fontPath.endsWith(".OTF")) ||
                (fontPath.endsWith(".otc") || fontPath.endsWith(".OTC"))) {
            font = fontFactory.createFontFile(fontFile, FontFactory.FONT_OPEN_TYPE, null);
        }
        return font;
    }

    private FontFile buildFont(URL fontUri) {
        FontFile font = null;
        try {
            String fontPath = fontUri.getPath();
            FontFactory fontFactory = FontFactory.getInstance();
            // found true type font
            if ((fontPath.endsWith(".ttf") || fontPath.endsWith(".TTF")) ||
                    (fontPath.endsWith(".dfont") || fontPath.endsWith(".DFONT")) ||
                    (fontPath.endsWith(".ttc") || fontPath.endsWith(".TTC"))) {
                font = fontFactory.createFontFile(fontUri, FontFactory.FONT_TRUE_TYPE, null);
            }
            // found Type 1 font
            else if ((fontPath.endsWith(".pfa") || fontPath.endsWith(".PFA")) ||
                    (fontPath.endsWith(".pfb") || fontPath.endsWith(".PFB"))) {
                font = fontFactory.createFontFile(fontUri, FontFactory.FONT_TYPE_1, null);
            }
            // found OpenType font
            else if ((fontPath.endsWith(".otf") || fontPath.endsWith(".OTF")) ||
                    (fontPath.endsWith(".otc") || fontPath.endsWith(".OTC"))) {
                font = fontFactory.createFontFile(fontUri, FontFactory.FONT_OPEN_TYPE, null);
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "Error reading font program.", e);
        }
        return font;
    }

    /**
     * Gets a FontFile instance by matching against font style commonalities in the
     * Java Cores libraries.
     *
     * @param fontName font name to search for
     * @param flags    style flags
     * @return a valid FontFile if a match is found, null otherwise.
     */
    /**
     * Returns the first of {@code candidates} that is actually installed, styled to match.
     * <p>
     * A list rather than a single name because the right answer differs per platform and per decade:
     * the Lucida faces the original code asked for shipped with the JDK until 9 removed them, and
     * nothing replaced them, so the lookup silently failed everywhere.
     */
    private FontFile findFirstAvailable(List<Object[]> fontList, String[] candidates,
                                        int decorations, int flags) {
        String style = getFontStyle(decorations, flags);
        // an explicitly configured base font wins over the built-in candidates
        List<String> ordered = new ArrayList<>(candidates.length + 1);
        if (baseFontName != null && !baseFontName.isEmpty()) {
            ordered.add(baseFontName);
        }
        ordered.addAll(Arrays.asList(candidates));
        for (String candidate : ordered) {
            FontFile font = findFont(fontList, FontUtil.normalizeString(candidate) + "-" + style, 0);
            if (font != null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Font Substitution: core font fallback " + candidate + " -> " + font.getName());
                }
                return font;
            }
        }
        return null;
    }

    private FontFile getCoreJavaFont(String fontName, int flags) {

        // iterate a stable snapshot, not the live static list
        final List<Object[]> fontList = snapshotFontList();
        int decorations = guessFontStyle(fontName);
        // the width class has to come off the RAW name: normalizeString truncates at the last dash,
        // so "Futura-CondensedBold" normalizes to "futura" and "Frutiger-Cn" to "frutiger" - the
        // very part that says the face is narrow is the part that gets thrown away.
        boolean isCondensed = isCondensedName(fontName);
        fontName = FontUtil.normalizeString(fontName);
        FontFile font;

        // read font flags as it can sometimes give us hints as to serif
        // san sarif or a monospace font, there is more data we can pull if needed too.
        boolean isFixedPitch = (flags & org.icepdf.core.pobjects.fonts.Font.FONT_FLAG_FIXED_PITCH) != 0;
        boolean isSerif = (flags & org.icepdf.core.pobjects.fonts.Font.FONT_FLAG_SERIF) != 0;
//        boolean isSymbolic = (flags & org.icepdf.core.pobjects.fonts.Font.FONT_FLAG_SYMBOLIC) != 0;
//        boolean isNotSymbolic = (flags & org.icepdf.core.pobjects.fonts.Font.FONT_FLAG_NON_SYMBOLIC) != 0;
        // A symbolic font has to be answered with a symbolic face; a text face has none of the
        // glyphs and every code draws .notdef.  Tested before the text families below because
        // "Symbol" would otherwise never be reached at all.
        //
        // Deliberately keyed on the NAME, never on the descriptor's symbolic flag: that flag is set
        // on any font with a non-standard encoding, which includes a great many perfectly ordinary
        // embedded text fonts (a subset Arial in the corpus carries Flags=6), and routing those to a
        // dingbat face would be far worse than the bug being fixed.
        if (isDingbatName(fontName)) {
            font = findFirstAvailable(fontList, DINGBAT_SUBSTITUTES, decorations, flags);
            if (font != null) {
                return font;
            }
        } else if (isSymbolName(fontName)) {
            font = findFirstAvailable(fontList, SYMBOL_SUBSTITUTES, decorations, flags);
            if (font != null) {
                return font;
            }
        }
        // If no name are found then match against the core java font names
        // "Serif", java equivalent is  "Lucida Bright"
        if (fontName.contains("timesnewroman") ||
                fontName.contains("bodoni") ||
                fontName.contains("garamond") ||
                fontName.contains("minionweb") ||
                fontName.contains("stoneserif") ||
                fontName.contains("georgia") ||
                fontName.contains("bitstreamcyberbit")) {
            // important, add style information
            font = findFirstAvailable(fontList, serifCandidates(isCondensed), decorations, flags);
        }
        // see if we working with a monospaced font, we sub "Sans Serif",
        // java equivalent is "Lucida Sans"
        else if (fontName.contains("helvetica") ||
                fontName.contains("arial") ||
                fontName.contains("trebuchet") ||
                fontName.contains("avantgardegothic") ||
                fontName.contains("verdana") ||
                fontName.contains("univers") ||
                fontName.contains("futura") ||
                fontName.contains("stonesans") ||
                fontName.contains("gillsans") ||
                fontName.contains("akzidenz") ||
                fontName.contains("frutiger") ||
                fontName.contains("grotesk")) {
            // important, add style information
            font = findFirstAvailable(fontList, sansCandidates(isCondensed), decorations, flags);
        }
        // see if we working with a mono spaced font "Mono Spaced"
        // java equivalent is "Lucida Sans Typewriter"
        else if (fontName.contains("courier") ||
                fontName.contains("couriernew") ||
                fontName.contains("prestige") ||
                fontName.contains("eversonmono")) {
            // important, add style information
            font = findFirstAvailable(fontList, MONO_SUBSTITUTES, decorations, flags);
        }
        // first try get the first match based on the style type and finally on failure
        // failure go with the serif as it is the most common font family
        else {
            if (isSerif) {
                font = findFirstAvailable(fontList, serifCandidates(isCondensed), decorations, flags);
            } else if (isFixedPitch) {
                font = findFirstAvailable(fontList, MONO_SUBSTITUTES, decorations, flags);
            } else {
                // sans serif
                font = findFirstAvailable(fontList, sansCandidates(isCondensed), decorations, flags);
            }
        }

        return font;
    }

    /**
     * True when the base name declares a narrow width class.  Must be given the name as the
     * document wrote it, before {@link FontUtil#normalizeString} strips everything after the last
     * dash.
     * <p>
     * "Cn", "Cnd" and "Scn" are Adobe's abbreviations (Frutiger-Cn, Introspect-BldCnd,
     * AdobeCorpID-MyriadRgScn).  They are matched only as a suffix of the whole name: two letters
     * are common enough inside real family names that a substring test would misfire.
     */
    private static boolean isCondensedName(String fontName) {
        if (fontName == null) return false;
        String name = FontUtil.removeBaseFontSubset(fontName).toLowerCase();
        return name.contains("cond")            // condensed, -Cond, CondensedBold
                || name.contains("compressed")
                || name.contains("narrow")
                || name.endsWith("cn") || name.endsWith("scn") || name.endsWith("cnd");
    }

    /**
     * True for the dingbat families: ZapfDingbats and the Microsoft equivalents, whose codes map to
     * pictographs rather than letters.  Takes an already normalized (lower case, unspaced) name.
     * <p>
     * NOTE, unfinished business: Wingdings and Webdings are lumped in with ZapfDingbats, but they do
     * not share its code layout.  The geometric bullets happen to agree - the codes for the filled
     * circle and square land on the same shapes - while the checkmarks and arrows do not, so those
     * documents get a plausible wrong glyph where they used to get an empty box.  That trade is
     * deliberate and matches what other viewers do, but it is a guess, not a mapping.  Doing it
     * properly means a real Wingdings-to-Unicode table (the glyphs are in Unicode 7.0 and later, at
     * U+1F5xx among others) and a substitute that actually carries them; revisit if a document turns
     * up where the wrong glyph is worse than nothing.
     */
    private static boolean isDingbatName(String normalizedName) {
        return normalizedName.contains("dingbat")           // ZapfDingbats, Dingbats, URW Dingbats
                || normalizedName.contains("wingding")      // Wingdings, Wingdings2, Wingdings3
                || normalizedName.contains("webding")
                || normalizedName.startsWith("d050000l");   // the URW ZapfDingbats clone by its own name
    }

    /**
     * True for the Symbol family: Greek and mathematical glyphs.  "Symbol" has to be matched
     * tightly - it turns up inside plenty of unrelated names (NotoSansSymbols is itself a
     * substitute, not a request for one) - so this looks for the family, not the substring.
     */
    private static boolean isSymbolName(String normalizedName) {
        return normalizedName.equals("symbol")              // also "Symbol,Bold": guessFamily cuts at the comma
                || normalizedName.startsWith("symbolmt")
                || normalizedName.startsWith("standardsym"); // StandardSymL, Standard Symbols PS
    }

    /** Substitute list for a sans face, condensed candidates first when the name asks for them. */
    private static String[] sansCandidates(boolean isCondensed) {
        return isCondensed ? concat(SANS_CONDENSED_SUBSTITUTES, SANS_SUBSTITUTES) : SANS_SUBSTITUTES;
    }

    /** Substitute list for a serif face, condensed candidates first when the name asks for them. */
    private static String[] serifCandidates(boolean isCondensed) {
        return isCondensed ? concat(SERIF_CONDENSED_SUBSTITUTES, SERIF_SUBSTITUTES) : SERIF_SUBSTITUTES;
    }

    private static String[] concat(String[] first, String[] second) {
        String[] all = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    /**
     * Gets a FontFile instance by matching against font style commonalities in the
     * of know type1 fonts
     *
     * @param fontName font name to search for
     * @param flags    style flags
     * @return a valid FontFile if a match is found, null otherwise.
     */
    private FontFile getType1Fonts(List<Object[]> fontList, String fontName, int flags) {
        FontFile font = null;
        boolean found = false;
        boolean isType1Available = true;
        for (String[] TYPE1_FONT_DIFF : TYPE1_FONT_DIFFS) {
            for (String aTYPE1_FONT_DIFF : TYPE1_FONT_DIFF) {
                // first check to see font name matches any elements
                if (TYPE1_FONT_DIFF[0].contains(fontName)) {
                    // next see if know type1 fonts are installed
                    if (isType1Available) {
                        font = findFont(fontList, TYPE1_FONT_DIFF[1], flags);
                        if (font != null) {
                            found = true;
                            break;
                        } else {
                            isType1Available = false;
                        }
                    }
                    // do a full search for possible matches.
                    font = findFont(fontList, aTYPE1_FONT_DIFF, flags);
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
     * Gets a Font instance by matching against font style commonalities in the
     * of know type1 fonts
     *
     * @param fontName font name to search for
     * @param fontSize requested font size.
     * @return a valid AWT Font if a match is found, null otherwise.
     */
    public java.awt.Font getType1AWTFont(String fontName, int fontSize) {
        java.awt.Font font = null;
        boolean found = false;
        boolean isType1Available = true;
        // find a match for family in the type 1 nfont table
        for (String[] TYPE1_FONT_DIFF : TYPE1_FONT_DIFFS) {
            for (String aTYPE1_FONT_DIFF : TYPE1_FONT_DIFF) {
                // first check to see font name matches any elements
                if (TYPE1_FONT_DIFF[0].contains(fontName)) {
                    // next see if know type1 fonts are installed
                    if (isType1Available) {
                        font = findAWTFont(TYPE1_FONT_DIFF[1]);
                        if (font != null) {
                            found = true;
                            break;
                        } else {
                            isType1Available = false;
                        }
                    }
                    // do a full search for possible matches.
                    font = findAWTFont(aTYPE1_FONT_DIFF);
                    if (font != null) {
                        found = true;
                        break;
                    }
                }
            }
            // break out of second loop
            if (found) break;
        }
        if (font != null) {
            font = font.deriveFont((float) fontSize);
        }
        return font;
    }

    /**
     * Utility method for search the fontList array for an particular font name
     * that has the specified style.
     *
     * @param fontName font name with any decoration information still appended to name.
     * @return a valid font if found, null otherwise
     */
    private java.awt.Font findAWTFont(String fontName) {
        java.awt.Font font = null;
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
                baseName = (String) fontData[FONT_NAME];
                style = (Integer) fontData[FONT_DECORATIONS];
                familyName = (String) fontData[FONT_FAMILY];
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest(baseName + " : " + familyName + "  : " + name);
                }
                if (name.contains(familyName) ||
                        fontName.toLowerCase().contains(baseName)) {
                    boolean found = false;
                    // ignore this font, as the cid mapping are not correct, or ther is
                    // just look and feel issues with them.
                    if (((decorations & BOLD_ITALIC) == BOLD_ITALIC) &&
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
                    else if (baseName.contains("wingdings") ||
                            baseName.contains("zapfdingbats") ||
                            baseName.contains("symbol")) {
                        found = true;
                    }

                    if (found) {
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer("----> Found font: " + baseName +
                                    " family: " + getFontStyle(style, 0) +
                                    " for: " + fontName);
                        }
                        try {
                            // found true type font
                            String fontPath = (String) fontData[3];
                            String fontPathLower = fontPath.toLowerCase();
                            if (fontPathLower.endsWith(".ttf") || fontPathLower.endsWith(".dfont") ||
                                    fontPathLower.endsWith(".ttc")) {
                                font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT,
                                        new File(fontPath));
                            }
                            // found Type 1 font
                            else if (fontPathLower.endsWith(".pfa") || fontPathLower.endsWith(".pfb")) {
                                font = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT,
                                        new File(fontPath));
                            }
                        } catch (FontFormatException e) {
                            logger.log(Level.FINE, "Error create new font", e);
                        } catch (IOException e) {
                            logger.log(Level.FINE, "Error reading font", e);
                        }
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
     * Utility method which maps know style strings to an integer value which
     * is used later for efficient font searching.
     * todo: move out to FontUtil and use awt constants
     *
     * @param name base name of font.
     * @return integer representing dffs
     */
    private static int guessFontStyle(String name) {
        name = name.toLowerCase();
        int decorations = 0;
        if ((name.indexOf("boldital") > 0) || (name.indexOf("demiital") > 0) ||
                (name.indexOf("bold obli") > 0) || name.indexOf("bold ital") > 0 || name.indexOf("fett kursiv") > 0) {
            decorations |= BOLD_ITALIC;
        } else if (name.indexOf("bold") > 0 || name.indexOf("black") > 0 || name.endsWith("bt")
                || name.indexOf("demi") > 0 || name.indexOf("fett") > 0) {
            decorations |= BOLD;
        } else if (name.indexOf("ital") > 0 || name.indexOf("obli") > 0 || name.indexOf("kursiv") > 0) {
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
    private String getFontStyle(int sytle, int flags) {
        // Get any useful data from the flags integer.
        String style = "";
        if ((sytle & BOLD_ITALIC) == BOLD_ITALIC) {
            style += " BoldItalic";
        } else if ((sytle & BOLD) == BOLD ||
                (flags & org.icepdf.core.pobjects.fonts.Font.FONT_FLAG_FORCE_BOLD) != 0) {
            style += " Bold";
        } else if ((sytle & ITALIC) == ITALIC ||
                (flags & org.icepdf.core.pobjects.fonts.Font.FONT_FLAG_ITALIC) != 0) {
            style += " Italic";
        } else if ((sytle & PLAIN) == PLAIN) {
            style += " Plain";
        }
        return style;
    }

    private static boolean checkExclusionLists(Object[] fontData) {
        // check against know font base names that cause problems
        String baseName = (String) fontData[FONT_NAME];
        if (BASE_NAME_EXCLUSION_LIST != null && BASE_NAME_EXCLUSION_LIST.size() > 0) {
            for (String fontName : BASE_NAME_EXCLUSION_LIST) {
                if (fontName != null && fontName.equals(baseName)) {
                    return true;
                }
            }
        }
        // check against actual font names as a worst case scenario.
        String fontFileName = (String) fontData[FONT_PATH];
        if (FONT_FILE_NAME_EXCLUSION_LIST != null && FONT_FILE_NAME_EXCLUSION_LIST.size() > 0) {
            for (String fontPath : FONT_FILE_NAME_EXCLUSION_LIST) {
                if (fontFileName != null && fontFileName.endsWith(fontPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Pattern getFontAllowListPattern() {
        final Pattern fileAllowListPattern;
        String patternString =
                Defs.sysProperty(FONT_FILE_ALLOW_LIST_PATTERN_PROPERTY, DEFAULT_FONT_FILE_ALLOW_LIST_PATTERN);
        fileAllowListPattern = Pattern.compile(patternString);
        return fileAllowListPattern;
    }

    /**
     * Check to see if the given file is allowed based on the current
     * fontAllowListPattern.  If the pattern is empty all files are allowed.
     *
     * VisibilityForTesting
     *
     * @param file file to check
     * @return true if the file is allowed, false otherwise.
     */
    boolean isFontFileAllowed(File file) {
        if (!file.isFile()) {
            return false;
        }
        if (fontAllowListPattern.pattern().isEmpty()) {
            return true;
        }
        return fontAllowListPattern.matcher(file.getName()).matches();
    }

    /**
     * Sorts the fontList of system fonts by font name or the first element
     * int the object[] store.
     */
    private static void sortFontListByName() {
        fontList.sort((o1, o2) -> ((String) o2[0]).compareTo((String) o1[0]));
    }
}
