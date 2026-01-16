package org.icepdf.fonts.util;

import java.io.InputStream;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility class for managing embedded OpenType font resources.
 */
public class EmbeddedFontUtil {

    private static final Logger logger =
            Logger.getLogger(EmbeddedFontUtil.class.toString());

    // font available in the icepdf font resources jar
    private static final Map<String, String> otfFontMapper = new java.util.HashMap<>();
    private static final String OTF_FONT_PATH = "/org/icepdf/core/fonts/";

    static {
        otfFontMapper.put("Times-Roman", "NotoSerif-Regular.ttf");
        otfFontMapper.put("Times-Bold", "NotoSerif-Bold.ttf");
        otfFontMapper.put("Times-Italic ", "NotoSerif-Italic.ttf");
        otfFontMapper.put("Times-BoldItalic", "NotoSerif-BoldItalic.ttf");
        otfFontMapper.put("Helvetica", "Roboto-Regular.ttf");
        otfFontMapper.put("Helvetica-Bold", "Roboto-Bold.ttf");
        otfFontMapper.put("Helvetica-Oblique", "Roboto-Italic.ttf");
        otfFontMapper.put("Helvetica-BoldOblique", "Roboto-BoldItalic.ttf");
        otfFontMapper.put("Courier", "RobotoMono-Regular.ttf");
        otfFontMapper.put("Courier-Bold", "RobotoMono-Bold.ttf");
        otfFontMapper.put("Courier-Oblique", "RobotoMono-Italic.ttf");
        otfFontMapper.put("Courier-BoldOblique", "RobotoMono-BoldItalic.ttf");
        otfFontMapper.put("Symbol", "NotoSansSymbols-Regular.ttf");
        otfFontMapper.put("ZapfDingbats", "NotoSansSymbols2-Regular.ttf");
    }

    public static byte[] getOtfEmbeddedFontResource(String fontName) {
        String otfFontName = otfFontMapper.get(fontName);
        if (otfFontName != null) {
            try (InputStream is = EmbeddedFontUtil.class.getResourceAsStream(OTF_FONT_PATH + otfFontName)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (Exception e) {
                logger.severe("Error loading embedded font resource: " + e.getMessage());
            }
        }
        return null;
    }

    public static boolean isOtfFontMapped(String fontName) {
        return otfFontMapper.containsKey(fontName);
    }


}
