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
package org.icepdf.core.fonts.util;

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
        otfFontMapper.put("Times-Italic", "NotoSerif-Italic.ttf");
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

    /**
     * Loads the embedded OpenType font resource corresponding to the given font name.
     *
     * @param fontName the name of the font for which to load the embedded resource (e.g., "Times-Roman",
     *                 "Helvetica-Bold", etc.)
     * @return a byte array containing the font data if the font name is mapped to an embedded resource, or null if
     * the font name is not mapped or if an error occurs while loading the resource
     */
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

    /**
     * Checks if the given font name is mapped to an embedded OpenType font resource.
     *
     * @param fontName the name of the font to check (e.g., "Times-Roman", "Helvetica-Bold", etc.)
     * @return true if the font name is mapped to an embedded OpenType font resource, false otherwise
     */
    public static boolean isOtfFontMapped(String fontName) {
        return otfFontMapper.containsKey(fontName);
    }


}
