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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.fonts.AFM;
import org.icepdf.core.pobjects.fonts.Font;
import org.icepdf.core.pobjects.fonts.FontDescriptor;
import org.icepdf.core.pobjects.fonts.FontFile;
import org.icepdf.core.pobjects.fonts.FontManager;
import org.icepdf.core.pobjects.fonts.FontTextEncoder;
import org.icepdf.core.pobjects.fonts.zfont.Encoding;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The measurements a field appearance is laid out with, for the font its {@code /DA} names: how wide
 * a string is, and where capitals, ascenders and descenders reach.  Vertical metrics are in
 * thousandths of an em (glyph space), widths in text space at a given font size.
 * <p>
 * The vertical metrics come from the font descriptor when it has them, else from the standard 14
 * font's AFM - a {@code /Helv} in an AcroForm's {@code /DR} is usually a bare Type1 Helvetica whose
 * substitute font program reports its own, different, ascent - and only then from the font program.
 */
final class FieldFontMetrics {

    private static final Logger logger = Logger.getLogger(FieldFontMetrics.class.getName());

    private final FontFile unitFont;
    private final FontTextEncoder encoder;
    private final float ascent;
    private final float descent;
    private final float capHeight;
    private final float boundingBoxHeight;

    private FieldFontMetrics(FontFile unitFont, FontTextEncoder encoder, float ascent, float descent,
                             float capHeight, float boundingBoxHeight) {
        this.unitFont = unitFont;
        this.encoder = encoder;
        // a font with no usable vertical metrics: fall back on Helvetica's proportions.
        this.ascent = ascent > 0 ? ascent : 718;
        this.descent = descent < 0 ? descent : -207;
        this.capHeight = capHeight > 0 ? capHeight : this.ascent;
        this.boundingBoxHeight = boundingBoxHeight > 0 ? boundingBoxHeight : this.ascent - this.descent;
    }

    /**
     * @param font the field's font; null when it could not be resolved, which measures as Helvetica
     *             in WinAnsiEncoding (what such a font almost always is)
     */
    static FieldFontMetrics of(Font font) {
        FontTextEncoder encoder = FontTextEncoder.of(font);
        FontFile file = font != null ? font.getFont() : null;
        if (file == null) {
            file = helvetica();
        }
        FontFile unitFont = file != null ? file.deriveFont(1f) : null;

        float ascent = 0, descent = 0, capHeight = 0, boundingBoxHeight = 0;
        FontDescriptor descriptor = font != null ? font.getFontDescriptor() : null;
        if (descriptor != null && descriptor.getAscent() > 0) {
            ascent = descriptor.getAscent();
            descent = descriptor.getDescent();
            capHeight = number(descriptor, FontDescriptor.CAP_HEIGHT);
            boundingBoxHeight = boundingBoxHeight(descriptor);
        } else {
            AFM afm = afmOf(font);
            if (afm != null) {
                ascent = afm.getAscender();
                descent = afm.getDescender();
                capHeight = afm.getCapHeight();
                int[] box = afm.getFontBBox();
                boundingBoxHeight = box[3] - box[1];
            } else if (unitFont != null) {
                ascent = (float) unitFont.getAscent() * 1000;
                descent = (float) unitFont.getDescent() * 1000;
                boundingBoxHeight = (float) unitFont.getMaxCharBounds().getHeight() * 1000;
            }
        }
        return new FieldFontMetrics(unitFont, encoder, ascent, descent, capHeight, boundingBoxHeight);
    }

    /** The AFM of a standard 14 font (what a bare {@code /Helv}, {@code /TiRo} or {@code /ZaDb} is). */
    private static AFM afmOf(Font font) {
        String name = font != null ? font.getBaseFont() : "Helvetica";
        if (name == null) return null;
        int subset = name.indexOf('+');
        if (subset >= 0) name = name.substring(subset + 1);
        AFM afm = AFM.AFMs.get(name.toLowerCase());
        if (afm == null) {
            // the common non-standard spellings of the standard fonts.
            String lower = name.toLowerCase();
            if (lower.startsWith("arial") || lower.equals("helv")) afm = AFM.AFMs.get("helvetica");
            else if (lower.startsWith("timesnewroman") || lower.equals("tiro")) afm = AFM.AFMs.get("times-roman");
            else if (lower.startsWith("couriernew") || lower.equals("cour")) afm = AFM.AFMs.get("courier");
        }
        return afm;
    }

    private static FontFile helvetica() {
        try {
            FontFile file = FontManager.getInstance().initialize().getInstance("Helvetica", 0);
            return file != null ? file.deriveFont(Encoding.winAnsiEncoding, null) : null;
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "No fallback font for field metrics", e);
            return null;
        }
    }

    private static float number(FontDescriptor descriptor, org.icepdf.core.pobjects.Name key) {
        Object value = descriptor.getLibrary().getObject(descriptor.getEntries(), key);
        return value instanceof Number ? ((Number) value).floatValue() : 0;
    }

    /** The FontBBox height, read from the array itself (its four numbers are corners, not x y w h). */
    private static float boundingBoxHeight(FontDescriptor descriptor) {
        Object value = descriptor.getLibrary().getObject(descriptor.getEntries(), FontDescriptor.FONT_BBOX);
        if (value instanceof List && ((List<?>) value).size() == 4) {
            List<?> box = (List<?>) value;
            Object lly = descriptor.getLibrary().getObject(box.get(1));
            Object ury = descriptor.getLibrary().getObject(box.get(3));
            if (lly instanceof Number && ury instanceof Number) {
                return Math.abs(((Number) ury).floatValue() - ((Number) lly).floatValue());
            }
        }
        return 0;
    }

    /** The encoder that writes text in this font's character codes. */
    FontTextEncoder getEncoder() {
        return encoder;
    }

    /**
     * @return the advance of {@code text} at {@code fontSize}, in text space; characters the font has
     * no code for measure zero, as they are dropped when the text is written
     */
    float width(String text, float fontSize) {
        if (unitFont == null || text == null) return 0;
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += advance(text.charAt(i));
        }
        return width * fontSize;
    }

    /** The advance of one character at size 1. */
    float advance(char c) {
        if (unitFont == null) return 0;
        int code = encoder.codeOf(c);
        if (code < 0) return 0;
        return (float) unitFont.getAdvance((char) code).getX();
    }

    float getAscent() {
        return ascent;
    }

    float getDescent() {
        return descent;
    }

    float getCapHeight() {
        return capHeight;
    }

    float getBoundingBoxHeight() {
        return boundingBoxHeight;
    }
}
