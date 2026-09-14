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
 * <br>
 * Two outlines are cached: the plain outline keyed by character code, and the grid-fitted outline
 * keyed by {@code (code, ppem)}, since grid fitting depends on the pixels-per-em the glyph is
 * rendered at.  "Hinting" names the feature that is switched on and off; "grid fit" names what the
 * code paths below actually do.
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
 * - a second, grid-fitted outline is cached alongside the plain one; PDFBox has no equivalent
 * @see ZSimpleFont#getGlphyShape(char)
 */
final class GlyphCache {

    private static final Logger logger =
            Logger.getLogger(GlyphCache.class.getName());

    private final ZSimpleFont font;
    private final Map<Integer, Shape> cache = new ConcurrentHashMap<>();
    private final Map<Long, Shape> gridFitCache = new ConcurrentHashMap<>();

    GlyphCache(ZSimpleFont font) {
        this.font = font;
    }

    /**
     * Returns the grid-fitted glyph outline for the given character code at the given ppem, falling
     * back to the plain outline when the font cannot grid-fit that glyph/ppem.  Results are cached
     * per {@code (code, ppem)}.
     *
     * @param code character code in a PDF
     * @param ppem the pixels-per-em the glyph will be rendered at
     * @return the grid-fitted outline if available, otherwise the plain outline
     */
    Shape getGridFitPathForCharacterCode(char code, int ppem) {
        long key = ((long) ppem << 32) | (code & 0xFFFFFFFFL);
        Shape cached = gridFitCache.get(key);
        if (cached != null) {
            return cached;
        }
        Shape path = null;
        try {
            path = font.getGridFitGlyphShape(code, ppem);
        } catch (IOException | RuntimeException e) {
            // a failed grid-fit is recoverable - the plain outline below is still correct, just
            // not snapped to the pixel grid - so log quietly and cache the fallback
            logger.log(Level.FINE, "Grid fit failed for code " + (int) code + " in font " + font.getName(), e);
        }
        // fall back to the plain outline (itself cached by code); cache the decision per (code, ppem)
        Shape result = path != null ? path : getPathForCharacterCode(code);
        gridFitCache.put(key, result);
        return result;
    }

    /**
     * Returns the plain (not grid-fitted) glyph outline for the given character code, caching it by
     * code.
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
