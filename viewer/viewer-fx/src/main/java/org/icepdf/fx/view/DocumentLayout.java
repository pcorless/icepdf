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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where every page sits in document space, for one zoom/rotation/view-mode combination.
 * <p>
 * Document space is logical (not device) pixels at the current zoom, origin top-left.  The view
 * owns a viewport rectangle in this space; intersecting it with {@link #slotsIntersecting} is the
 * whole of "what is visible" - the equivalent of the Swing viewer's JViewport view rect, which a
 * JavaFX ScrollPane does not expose.
 * <p>
 * Pure math with no toolkit types, so it is unit-testable and could serve the Swing viewer too.
 * An instance is immutable; build a new one when any input changes.
 */
public final class DocumentLayout {

    /** Page sizes in logical px at the layout's zoom and rotation. */
    public interface PageSizes {
        int count();

        double width(int pageIndex);

        double height(int pageIndex);
    }

    /** A page's rectangle in document space. */
    public record PageSlot(int pageIndex, double x, double y, double width, double height) {
        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean intersects(double rx, double ry, double rw, double rh) {
            return rw > 0 && rh > 0 && x < rx + rw && rx < maxX() && y < ry + rh && ry < maxY();
        }
    }

    private record Row(double y, double height, List<PageSlot> slots) {
    }

    private final ViewMode viewMode;
    private final List<Row> rows;
    // indexed by page; null when the page isn't laid out (non-continuous modes).
    private final PageSlot[] slotByPage;
    private final double width;
    private final double height;

    /**
     * @param sizes          page sizes at the target zoom/rotation
     * @param viewMode       arrangement
     * @param coverPage      in facing modes, show the first page alone on the right (book style)
     * @param currentPage    the page shown by the non-continuous modes; ignored when continuous
     * @param gap            space between pages and around the document, logical px
     * @param viewportWidth  used to centre content narrower than the viewport
     * @param viewportHeight used to centre content shorter than the viewport
     */
    public DocumentLayout(PageSizes sizes, ViewMode viewMode, boolean coverPage, int currentPage, double gap,
                          double viewportWidth, double viewportHeight) {
        this.viewMode = viewMode;
        int count = sizes.count();
        slotByPage = new PageSlot[count];
        if (count == 0) {
            rows = Collections.emptyList();
            width = Math.max(0, viewportWidth);
            height = Math.max(0, viewportHeight);
            return;
        }

        // group pages into rows of one (single) or two (facing) pages.
        List<int[]> groups = new ArrayList<>();
        if (viewMode.isFacing()) {
            int i = 0;
            if (coverPage) {
                groups.add(new int[]{-1, 0});
                i = 1;
            }
            for (; i < count; i += 2) {
                groups.add(i + 1 < count ? new int[]{i, i + 1} : new int[]{i, -1});
            }
        } else {
            for (int i = 0; i < count; i++) groups.add(new int[]{i});
        }
        if (!viewMode.isContinuous()) {
            int current = Math.max(0, Math.min(count - 1, currentPage));
            int[] chosen = groups.get(0);
            for (int[] g : groups) {
                if (contains(g, current)) {
                    chosen = g;
                    break;
                }
            }
            groups = List.of(chosen);
        }

        // facing spreads hinge on a common spine, so each half gets the widest page on that side;
        // a lone cover or last page then sits on its own side of the spine, not centred.
        double leftHalf = 0;
        double rightHalf = 0;
        double contentWidth = 0;
        for (int[] g : groups) {
            if (viewMode.isFacing()) {
                if (g[0] >= 0) leftHalf = Math.max(leftHalf, sizes.width(g[0]));
                if (g[1] >= 0) rightHalf = Math.max(rightHalf, sizes.width(g[1]));
            } else {
                contentWidth = Math.max(contentWidth, sizes.width(g[0]));
            }
        }
        if (viewMode.isFacing()) {
            // symmetric so the spine is centred whether or not the spreads are lopsided.
            double half = Math.max(leftHalf, rightHalf);
            contentWidth = half * 2 + gap;
        }
        width = Math.max(contentWidth + gap * 2, viewportWidth);
        double centreX = width / 2;

        List<Row> built = new ArrayList<>(groups.size());
        double y = gap;
        for (int[] g : groups) {
            double rowHeight = 0;
            for (int p : g) if (p >= 0) rowHeight = Math.max(rowHeight, sizes.height(p));
            List<PageSlot> slots = new ArrayList<>(2);
            if (viewMode.isFacing()) {
                if (g[0] >= 0) {
                    double w = sizes.width(g[0]);
                    double h = sizes.height(g[0]);
                    slots.add(new PageSlot(g[0], centreX - gap / 2 - w, y + (rowHeight - h) / 2, w, h));
                }
                if (g[1] >= 0) {
                    double w = sizes.width(g[1]);
                    double h = sizes.height(g[1]);
                    slots.add(new PageSlot(g[1], centreX + gap / 2, y + (rowHeight - h) / 2, w, h));
                }
            } else {
                double w = sizes.width(g[0]);
                double h = sizes.height(g[0]);
                slots.add(new PageSlot(g[0], centreX - w / 2, y, w, h));
            }
            built.add(new Row(y, rowHeight, Collections.unmodifiableList(slots)));
            y += rowHeight + gap;
        }
        double contentHeight = y;
        // centre vertically when everything fits, as the Swing two-page layouts do.
        double offsetY = contentHeight < viewportHeight ? (viewportHeight - contentHeight) / 2 : 0;
        if (offsetY > 0) {
            List<Row> shifted = new ArrayList<>(built.size());
            for (Row r : built) {
                List<PageSlot> slots = new ArrayList<>(r.slots().size());
                for (PageSlot s : r.slots()) {
                    slots.add(new PageSlot(s.pageIndex(), s.x(), s.y() + offsetY, s.width(), s.height()));
                }
                shifted.add(new Row(r.y() + offsetY, r.height(), Collections.unmodifiableList(slots)));
            }
            built = shifted;
        }
        height = Math.max(contentHeight, viewportHeight);
        rows = Collections.unmodifiableList(built);
        for (Row r : rows) for (PageSlot s : r.slots()) slotByPage[s.pageIndex()] = s;
    }

    private static boolean contains(int[] group, int page) {
        for (int p : group) if (p == page) return true;
        return false;
    }

    public ViewMode getViewMode() {
        return viewMode;
    }

    /** Total document width, at least the viewport width. */
    public double getWidth() {
        return width;
    }

    /** Total document height, at least the viewport height. */
    public double getHeight() {
        return height;
    }

    /**
     * @return the page's slot, or null if the page isn't part of this layout (a non-continuous mode
     * showing a different page, or an out-of-range index).
     */
    public PageSlot getSlot(int pageIndex) {
        return pageIndex >= 0 && pageIndex < slotByPage.length ? slotByPage[pageIndex] : null;
    }

    /** Pages whose slot intersects the rectangle, in page order.  O(log rows + result). */
    public List<PageSlot> slotsIntersecting(double x, double y, double w, double h) {
        if (rows.isEmpty() || w <= 0 || h <= 0) return Collections.emptyList();
        int first = firstRowEndingAfter(y);
        List<PageSlot> out = new ArrayList<>();
        for (int i = first; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.y() >= y + h) break;
            for (PageSlot s : r.slots()) {
                if (s.intersects(x, y, w, h)) out.add(s);
            }
        }
        return out;
    }

    /**
     * The page a user would call "current" for the viewport: the page covering the most of it,
     * ties going to the earlier page.  Returns -1 for an empty layout.
     */
    public int currentPage(double x, double y, double w, double h) {
        int best = -1;
        double bestArea = -1;
        for (PageSlot s : slotsIntersecting(x, y, w, h)) {
            double iw = Math.min(s.maxX(), x + w) - Math.max(s.x(), x);
            double ih = Math.min(s.maxY(), y + h) - Math.max(s.y(), y);
            double area = iw * ih;
            if (area > bestArea) {
                bestArea = area;
                best = s.pageIndex();
            }
        }
        if (best < 0 && !rows.isEmpty()) {
            // in a gap between rows: take the next row down, else the last.
            int i = Math.min(firstRowEndingAfter(y), rows.size() - 1);
            best = rows.get(i).slots().get(0).pageIndex();
        }
        return best;
    }

    // binary search for the first row whose bottom edge is below y.
    private int firstRowEndingAfter(double y) {
        int lo = 0;
        int hi = rows.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            Row r = rows.get(mid);
            if (r.y() + r.height() <= y) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }
}
