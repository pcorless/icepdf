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
package org.icepdf.fx.view;

/**
 * Identity of a cached raster.  Everything that changes the pixels is part of the key, so a
 * lookup can never return a buffer rendered for a different zoom, rotation or screen scale.
 */
public sealed interface CacheKey permits CacheKey.Tile, CacheKey.AnnotationTile, CacheKey.Preview {

    int pageIndex();

    /** Everything about how a page is rendered except which tile. */
    record Params(float zoom, float rotation, double scale, int boundary) {
    }

    /** One device-pixel tile of a page. */
    record Tile(int pageIndex, Params params, int column, int row) implements CacheKey {
    }

    /** Which annotation raster: plain src-over appearances, or those carrying a blend mode. */
    enum AnnotationLayer {NORMAL, BLEND}

    /**
     * One device-pixel tile of a page's annotation raster - transparent, annotations only.
     * {@code generation} is the page's annotation generation, bumped on every add/edit/delete, so
     * a tile rendered before a change can never be shown after it.
     */
    record AnnotationTile(int pageIndex, Params params, AnnotationLayer layer, int column, int row,
                          int generation) implements CacheKey {
    }

    /** A whole-page low-resolution render shown while tiles are missing. */
    record Preview(int pageIndex, int boundary) implements CacheKey {
    }
}
