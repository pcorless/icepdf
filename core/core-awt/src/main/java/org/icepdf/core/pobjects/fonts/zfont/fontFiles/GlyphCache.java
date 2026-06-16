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
 * Two outlines are cached: the unhinted outline keyed by character code, and the grid-fitted
 * (TrueType-hinted) outline keyed by {@code (code, ppem)} since the hinted result depends on the
 * pixels-per-em the glyph is rendered at.  Ported from Apache PDFBox's {@code GlyphCache}.
 *
 * @see ZSimpleFont#getHintedGlphyShape(char, int)
 */
final class GlyphCache {

    private static final Logger logger =
            Logger.getLogger(GlyphCache.class.getName());

    private final ZSimpleFont font;
    private final Map<Integer, Shape> cache = new ConcurrentHashMap<>();
    private final Map<Long, Shape> hintedCache = new ConcurrentHashMap<>();

    GlyphCache(ZSimpleFont font) {
        this.font = font;
    }

    /**
     * Returns the grid-fitted (hinted) glyph outline for the given character code at the given ppem,
     * falling back to the unhinted outline when the font does not hint that glyph/ppem.  Results are
     * cached per {@code (code, ppem)}.
     *
     * @param code character code in a PDF
     * @param ppem the pixels-per-em the glyph will be rendered at
     * @return the hinted outline if available, otherwise the unhinted outline
     */
    Shape getPathForCharacterCode(char code, int ppem) {
        long key = ((long) ppem << 32) | (code & 0xFFFFFFFFL);
        Shape cached = hintedCache.get(key);
        if (cached != null) {
            return cached;
        }
        Shape path = null;
        try {
            path = font.getHintedGlphyShape(code, ppem);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Hinting failed for code " + (int) code + " in font " + font.getName(), e);
        }
        // fall back to the unhinted outline (itself cached by code); cache the decision per (code, ppem)
        Shape result = path != null ? path : getPathForCharacterCode(code);
        hintedCache.put(key, result);
        return result;
    }

    /**
     * Returns the unhinted glyph outline for the given character code, caching it by code.
     *
     * @param code character code in a PDF
     * @return the unhinted glyph outline, or an empty path on error
     */
    Shape getPathForCharacterCode(char code) {
        Shape path = cache.get((int) code);
        if (path != null) {
            return path;
        }
        try {
            path = font.getGlphyShape(code);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Glyph rendering failed for code " + (int) code + " in font " + font.getName(),
                    e);
        }
        if (path == null) {
            path = new GeneralPath();
        }
        cache.put((int) code, path);
        return path;
    }
}