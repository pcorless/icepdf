/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Geometry-driven page reading order via recursive XY-cut (projection profiling).
 * <p>
 * Operates on the already-sliced {@link LineText} list produced by
 * {@code PageText.sortAndFormatText()} and returns the same lines re-ordered.  At each region it
 * prefers a <em>vertical</em> cut (a whitespace gutter between columns) so a column is read
 * top-to-bottom before moving right; failing that a <em>horizontal</em> cut (a band break); and if
 * neither is significant the region is a leaf and is sorted plain top-to-bottom.
 * <p>
 * Coordinate note: line bounds are in PDF space where larger {@code y} is higher on the page, so
 * "top first" means descending {@code y}.
 * <p>
 * Design and rationale: {@code READING-ORDER-XYCUT-PLAN.md}.  This is opt-in
 * ({@code org.icepdf.core.views.page.text.readingOrder=xycut}); it does not change the default.
 *
 * @since 7.5
 */
public final class XYCutReadingOrder {

    /** Column gutter must exceed this many median line-heights to split columns. */
    private static final double COL_GUTTER_MIN = 1.5;
    /** Band gap must exceed this many median line-heights to split rows. */
    private static final double ROW_GAP_MIN = 1.5;
    /** A column side must overlap the other in y by at least this fraction to be a genuine column. */
    private static final double COL_Y_OVERLAP_MIN = 0.25;
    /** Refuse a vertical cut that would leave a side with fewer than this many lines. */
    private static final int MIN_COLUMN_LINES = 2;
    /** Recursion guard against pathological input. */
    private static final int MAX_DEPTH = 48;

    private XYCutReadingOrder() {
    }

    /** Reading-order comparator for a leaf region: top-to-bottom (y desc), then left-to-right. */
    private static final Comparator<LineText> TOP_LEFT =
            (a, b) -> {
                int c = Double.compare(b.getBounds().getY(), a.getBounds().getY());
                return c != 0 ? c : Double.compare(a.getBounds().getX(), b.getBounds().getX());
            };

    /**
     * Returns {@code lines} re-ordered into reading order.  The input list is not modified.
     */
    public static ArrayList<LineText> order(List<LineText> lines) {
        ArrayList<LineText> out = new ArrayList<>(lines.size());
        order(new ArrayList<>(lines), 0, out);
        return out;
    }

    private static void order(List<LineText> region, int depth, List<LineText> out) {
        int n = region.size();
        if (n <= 1 || depth > MAX_DEPTH) {
            emitLeaf(region, out);
            return;
        }
        double scale = medianHeight(region);

        // 1. columns first: a vertical gutter, provided both sides are genuinely side-by-side.
        Gap gutter = widestGap(region, true);
        if (gutter != null && gutter.width > COL_GUTTER_MIN * scale) {
            List<LineText> left = new ArrayList<>();
            List<LineText> right = new ArrayList<>();
            for (LineText line : region) {
                if (line.getBounds().getCenterX() < gutter.mid) left.add(line);
                else right.add(line);
            }
            if (left.size() >= MIN_COLUMN_LINES && right.size() >= MIN_COLUMN_LINES
                    && yOverlapFraction(left, right) >= COL_Y_OVERLAP_MIN) {
                order(left, depth + 1, out);   // left-to-right
                order(right, depth + 1, out);
                return;
            }
        }

        // 2. otherwise a horizontal band break (harmless within a single column: order is preserved).
        Gap band = widestGap(region, false);
        if (band != null && band.width > ROW_GAP_MIN * scale) {
            List<LineText> top = new ArrayList<>();
            List<LineText> bottom = new ArrayList<>();
            for (LineText line : region) {
                if (line.getBounds().getCenterY() > band.mid) top.add(line);   // larger y = higher
                else bottom.add(line);
            }
            if (!top.isEmpty() && !bottom.isEmpty()) {
                order(top, depth + 1, out);    // top-to-bottom
                order(bottom, depth + 1, out);
                return;
            }
        }

        // 3. leaf.
        emitLeaf(region, out);
    }

    /**
     * Emits a leaf region.  A skinny, tall block (a vertical glyph stack such as rotated marginal
     * text) is left in its incoming order rather than forced top-to-bottom: we do not know its true
     * flow direction, and re-sorting a bottom-to-top vertical run would reverse it.  Fixing vertical
     * text order is an explicit non-goal (see plan); preserving plot order is the no-regression choice.
     * Everything else is a normal horizontal block sorted top-to-bottom, left-to-right.
     */
    private static void emitLeaf(List<LineText> region, List<LineText> out) {
        if (isVerticalStack(region)) {
            out.addAll(region);
        } else {
            region.sort(TOP_LEFT);
            out.addAll(region);
        }
    }

    /**
     * True for a rotated glyph-stack (e.g. a vertical marginal label): three or more lines, the block
     * taller than it is wide, and each line about as wide as it is tall &mdash; i.e. every "line" is a
     * single glyph rather than a run of text.  Real horizontal text, even in a narrow column, has lines
     * far wider than tall, so it fails this test and is sorted top-to-bottom normally.
     */
    private static boolean isVerticalStack(List<LineText> region) {
        if (region.size() < 3) return false;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double[] widths = new double[region.size()];
        int i = 0;
        for (LineText l : region) {
            Rectangle2D.Double b = l.getBounds();
            widths[i++] = b.getWidth();
            minX = Math.min(minX, b.getMinX());
            maxX = Math.max(maxX, b.getMaxX());
            minY = Math.min(minY, b.getMinY());
            maxY = Math.max(maxY, b.getMaxY());
        }
        java.util.Arrays.sort(widths);
        double medianLineWidth = widths[widths.length / 2];
        boolean tallerThanWide = (maxY - minY) > (maxX - minX);
        boolean glyphWideLines = medianLineWidth < medianHeight(region) * 3;
        return tallerThanWide && glyphWideLines;
    }

    /**
     * Widest zero-coverage gap along one axis, computed by projecting each line box onto that axis,
     * merging overlapping intervals, and taking the largest interior gap.
     *
     * @param horizontalAxis true for the x-axis (column gutter), false for the y-axis (band break)
     * @return the widest gap, or null if the projection has no interior gap
     */
    private static Gap widestGap(List<LineText> region, boolean horizontalAxis) {
        double[][] iv = new double[region.size()][2];
        for (int i = 0; i < region.size(); i++) {
            Rectangle2D.Double b = region.get(i).getBounds();
            iv[i][0] = horizontalAxis ? b.getMinX() : b.getMinY();
            iv[i][1] = horizontalAxis ? b.getMaxX() : b.getMaxY();
        }
        java.util.Arrays.sort(iv, Comparator.comparingDouble(a -> a[0]));
        double bestLo = 0, bestHi = 0, best = 0;
        double coverEnd = iv[0][1];
        for (int i = 1; i < iv.length; i++) {
            if (iv[i][0] > coverEnd) {
                double w = iv[i][0] - coverEnd;
                if (w > best) {
                    best = w;
                    bestLo = coverEnd;
                    bestHi = iv[i][0];
                }
            }
            coverEnd = Math.max(coverEnd, iv[i][1]);
        }
        return best > 0 ? new Gap(bestLo, bestHi, best) : null;
    }

    /**
     * Fraction of the smaller y-extent over which the two column candidates overlap.  Stacked blocks
     * (one above the other) overlap little and are rejected as columns; true side-by-side columns
     * overlap heavily.
     */
    private static double yOverlapFraction(List<LineText> a, List<LineText> b) {
        double aMin = Double.MAX_VALUE, aMax = -Double.MAX_VALUE;
        double bMin = Double.MAX_VALUE, bMax = -Double.MAX_VALUE;
        for (LineText l : a) {
            aMin = Math.min(aMin, l.getBounds().getMinY());
            aMax = Math.max(aMax, l.getBounds().getMaxY());
        }
        for (LineText l : b) {
            bMin = Math.min(bMin, l.getBounds().getMinY());
            bMax = Math.max(bMax, l.getBounds().getMaxY());
        }
        double overlap = Math.min(aMax, bMax) - Math.max(aMin, bMin);
        if (overlap <= 0) return 0;
        double smaller = Math.min(aMax - aMin, bMax - bMin);
        return smaller <= 0 ? 0 : overlap / smaller;
    }

    private static double medianHeight(List<LineText> region) {
        double[] h = new double[region.size()];
        for (int i = 0; i < region.size(); i++) h[i] = region.get(i).getBounds().getHeight();
        java.util.Arrays.sort(h);
        double m = h[h.length / 2];
        return m > 0 ? m : 1; // guard against zero-height degenerate lines
    }

    /** A zero-coverage interval [lo, hi] of the given width along one axis. */
    private static final class Gap {
        final double mid;
        final double width;

        Gap(double lo, double hi, double width) {
            this.mid = (lo + hi) / 2;
            this.width = width;
        }
    }
}
