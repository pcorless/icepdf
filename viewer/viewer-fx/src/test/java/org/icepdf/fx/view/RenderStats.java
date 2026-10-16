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
 * Test-side access to the package-private renderer's counters, for the smoke and bench runs in
 * other packages.  Not part of the library.
 */
public final class RenderStats {

    private RenderStats() {
    }

    /** Content tile renders since start. */
    public static long contentRenderCount() {
        return TileRenderer.contentRenderCount();
    }

    /** Bytes of tiles and previews a view holds in its cache; 0 without its skin. */
    public static long cachedBytes(PdfView view) {
        return view.getSkin() instanceof PdfViewSkin skin ? skin.cachedBytes() : 0;
    }

    /** True when annotations render in one pass with the content (system property). */
    public static boolean singlePassAnnotations() {
        return TileRenderer.SINGLE_PASS_ANNOTATIONS;
    }
}
