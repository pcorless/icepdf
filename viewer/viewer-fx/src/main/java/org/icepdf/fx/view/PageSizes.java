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

import org.icepdf.core.pobjects.PDimension;

import java.util.function.IntFunction;

/**
 * Every page's size at zoom 1 for the current rotation and boundary, and the zoom a fit mode asks
 * for.  Only the first pages are read when a document opens - reading every page's size of a long
 * document stalls the FX thread - the rest take their average, marked estimated, until they come
 * into view and {@link #resolve} reads them, as the Swing viewer does.
 */
final class PageSizes {

    static final int READ_AHEAD = 10;
    // a page whose size can't be read is US Letter.
    private static final double DEFAULT_WIDTH = 612;
    private static final double DEFAULT_HEIGHT = 792;

    private double[] widths = new double[0];
    private double[] heights = new double[0];
    private boolean[] estimated = new boolean[0];
    private IntFunction<PDimension> reader = i -> null;

    /**
     * Reads the sizes of the first pages, and of any page already read for the previous rotation or
     * boundary; the rest get the average of the first ones.
     *
     * @param count        the document's page count
     * @param reader       a page's size at zoom 1 (null when it can't be read)
     * @param keepResolved false for a new document, true to re-read the pages read so far
     */
    void load(int count, IntFunction<PDimension> reader, boolean keepResolved) {
        this.reader = reader;
        boolean[] read = keepResolved && estimated.length == count ? estimated : null;
        widths = new double[count];
        heights = new double[count];
        estimated = new boolean[count];
        int ahead = Math.min(count, READ_AHEAD);
        double sumW = 0;
        double sumH = 0;
        for (int i = 0; i < ahead; i++) {
            read(i);
            sumW += widths[i];
            sumH += heights[i];
        }
        double avgW = ahead > 0 ? sumW / ahead : DEFAULT_WIDTH;
        double avgH = ahead > 0 ? sumH / ahead : DEFAULT_HEIGHT;
        for (int i = ahead; i < count; i++) {
            if (read != null && !read[i]) {
                read(i);
            } else {
                widths[i] = avgW;
                heights[i] = avgH;
                estimated[i] = true;
            }
        }
    }

    private void read(int i) {
        PDimension size = reader.apply(i);
        widths[i] = size != null ? size.getWidth() : DEFAULT_WIDTH;
        heights[i] = size != null ? size.getHeight() : DEFAULT_HEIGHT;
        estimated[i] = false;
    }

    int count() {
        return widths.length;
    }

    double width(int pageIndex) {
        return widths[pageIndex];
    }

    double height(int pageIndex) {
        return heights[pageIndex];
    }

    boolean isEstimated(int pageIndex) {
        return pageIndex < estimated.length && estimated[pageIndex];
    }

    /** What {@link #resolve} found: some estimate was wrong, and whether the current page's was. */
    record Resolution(boolean changed, boolean currentChanged) {
    }

    /** Reads the real size of the estimated pages among {@code pages}. */
    Resolution resolve(Iterable<Integer> pages, int current) {
        boolean changed = false;
        boolean currentChanged = false;
        for (int i : pages) {
            if (!isEstimated(i)) continue;
            double w = widths[i];
            double h = heights[i];
            read(i);
            if (Math.abs(w - widths[i]) > 0.5 || Math.abs(h - heights[i]) > 0.5) {
                changed = true;
                currentChanged |= i == current;
            }
        }
        return new Resolution(changed, currentChanged);
    }

    /** The sizes at a zoom, for {@link DocumentLayout}. */
    DocumentLayout.PageSizes atZoom(double zoom) {
        double[] w = widths;
        double[] h = heights;
        return new DocumentLayout.PageSizes() {
            public int count() {
                return w.length;
            }

            public double width(int i) {
                return w[i] * zoom;
            }

            public double height(int i) {
                return h[i] * zoom;
            }
        };
    }

    /**
     * The zoom that fits the view: the current page (and its facing partner) by width or whole,
     * or in a continuous fit-width the widest page.
     *
     * @return the zoom, or NaN for {@link FitMode#NONE}, no pages or an empty viewport
     */
    double fitZoom(FitMode mode, ViewMode viewMode, boolean coverPage, int current, double gap,
                   double viewportW, double viewportH) {
        if (mode == FitMode.NONE || widths.length == 0 || viewportW <= 0 || viewportH <= 0) return Double.NaN;
        boolean facing = viewMode.isFacing();
        double width;
        double height;
        if (mode == FitMode.WIDTH && viewMode.isContinuous()) {
            width = 0;
            for (double uw : widths) width = Math.max(width, uw);
            height = 0;
        } else {
            width = widths[current];
            height = heights[current];
            if (facing) {
                int partner = partnerOf(current, coverPage);
                if (partner >= 0) {
                    width = Math.max(width, widths[partner]);
                    height = Math.max(height, heights[partner]);
                }
            }
        }
        double across = facing ? 2 * width : width;
        double gaps = facing ? 3 * gap : 2 * gap;
        double zoom = (viewportW - gaps) / Math.max(1, across);
        if (mode == FitMode.PAGE) zoom = Math.min(zoom, (viewportH - 2 * gap) / Math.max(1, height));
        return zoom;
    }

    /** The page shown beside {@code page} in a facing mode, or -1. */
    int partnerOf(int page, boolean coverPage) {
        int offset = coverPage ? page - 1 : page;
        if (coverPage && page == 0) return -1;
        int partner = offset % 2 == 0 ? page + 1 : page - 1;
        return partner >= 0 && partner < widths.length ? partner : -1;
    }
}
