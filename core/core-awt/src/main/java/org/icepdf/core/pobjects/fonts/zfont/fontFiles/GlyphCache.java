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
package org.icepdf.core.pobjects.fonts.zfont.fontFiles;

import org.icepdf.core.pobjects.fonts.FontFile;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple glyph outline cache.  Glyph outlines in ICEpdf are produced in raw font (glyph) units
 * and are independent of the font's point size and text/graphics-state transform, which are applied
 * separately at paint time.  This makes the outlines safe to cache and reuse for the lifetime of a
 * (derived) font instance.
 * <br>
 * FontBox caches the char string of a Type1/CFF glyph but rebuilds a TrueType {@code glyf} outline
 * from its contours on every call, so an outline that is painted once per repaint is re-rendered
 * once per repaint.  Caching here removes that work from every repaint, zoom and scroll after the
 * first.
 *
 * @author John Hewson
 * <p>
 * Derived from {@code org.apache.pdfbox.rendering.GlyphCache} of Apache PDFBox 3.0.6, used under
 * the Apache License, Version 2.0.  Changes for ICEpdf:
 * - keyed on the ICEpdf {@link ZSimpleFont} / {@link FontFile} model rather than PDFBox's
 *   {@code PDVectorFont}, so lookups take a {@code char} code and yield a {@link Shape}
 * - {@link java.util.concurrent.ConcurrentHashMap} rather than {@code HashMap}, as a font instance
 *   is shared by the page-rendering threads
 * - {@code java.util.logging} rather than commons-logging
 * - dropped the {@code hasGlyph} diagnostics, the Type0 CID warning and the PDFBOX-4001 standard-14
 *   line-feed special case, none of which apply here
 * - {@code RuntimeException} is caught as well as {@code IOException}, and the empty fallback
 *   outline is cached rather than returned uncached, so a bad glyph costs one throw and not one
 *   per paint
 * @see ZSimpleFont#getGlphyShape(char)
 */
final class GlyphCache {

    private static final Logger logger =
            Logger.getLogger(GlyphCache.class.getName());

    private final ZSimpleFont font;
    private final Map<Integer, Shape> cache = new ConcurrentHashMap<>();

    GlyphCache(ZSimpleFont font) {
        this.font = font;
    }

    /**
     * Returns the glyph outline for the given character code, caching it by code.
     *
     * @param code character code in a PDF
     * @return the glyph outline, or an empty path on error
     */
    Shape getPathForCharacterCode(char code) {
        Shape path = cache.get((int) code);
        if (path != null) {
            return path;
        }
        try {
            path = font.getGlphyShape(code);
        } catch (IOException | RuntimeException e) {
            // RuntimeException covers fontbox throwing for unsupported tables
            // (e.g. "OTF fonts do not have a glyf table"); cache the empty outline so the
            // throw isn't repeated for every paint of the glyph.
            logger.log(Level.FINE, "Glyph rendering failed for code " + (int) code + " in font " + font.getName(),
                    e);
        }
        if (path == null) {
            path = new GeneralPath();
        }
        cache.put((int) code, path);
        return path;
    }
}
