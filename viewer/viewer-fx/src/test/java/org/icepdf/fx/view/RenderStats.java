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

import org.icepdf.fx.render.RenderService;

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

    /** The shared render pool's thread count. */
    public static int poolThreads() {
        return RenderService.shared().size().threads();
    }

    /** The render pool, its queue, every view and the heap, as the devtools window sees them.  FX thread. */
    public static RenderService.Snapshot snapshot() {
        return RenderService.shared().snapshot();
    }

    /** Render tasks waiting for a thread, process-wide. */
    public static int queued() {
        return RenderService.shared().queued();
    }

    /** Live render pool threads; at most {@link #poolThreads()}, and 0 a while after the pool idles. */
    public static java.util.List<String> poolThreadNames() {
        return Thread.getAllStackTraces().keySet().stream().map(Thread::getName)
                .filter(n -> n.startsWith("icepdf-fx-render-")).sorted().toList();
    }

    /** Whether a view's render work goes ahead of background views' (its window is focused). */
    public static boolean isForeground(PdfView view) {
        return view.getSkin() instanceof PdfViewSkin skin && skin.isRenderForeground();
    }

    /** True when annotations render in one pass with the content (system property). */
    public static boolean singlePassAnnotations() {
        return TileRenderer.SINGLE_PASS_ANNOTATIONS;
    }
}
