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
import java.util.List;

/**
 * Geometry-driven, column-contiguous page reading order.
 * <p>
 * Operates on the already-sliced {@link LineText} list produced by
 * {@code PageText.sortAndFormatText()} and returns the same lines re-ordered so that each column is
 * read top-to-bottom before moving to the next column.  Columns are found by {@link ColumnLayout}
 * (a per-x-bin line-count coverage profile), which is robust to the header, title and caption lines
 * that span columns and defeat classic zero-coverage XY-cut gutter detection on dense pages.
 * <p>
 * Full-width lines (titles, rules) act as horizontal band separators: content is split into stripes
 * at each separator, every stripe is read column-by-column, and the separators are emitted between
 * stripes.  Lines that span some but not all columns are read inline in the column their centre
 * falls in.
 * <p>
 * Coordinate note: line bounds are in PDF space where larger {@code y} is higher on the page, so
 * "top first" means descending {@code y}.
 * <p>
 * Design and rationale: {@code READING-ORDER-XYCUT-PLAN.md} and {@code TEXT-SELECTION-PLAN.md}
 * Appendix E/F.  This is opt-in ({@code org.icepdf.core.views.page.text.readingOrder=xycut}); it does
 * not change the default.
 *
 * @since 7.5
 */
public final class XYCutReadingOrder {

    /**
     * A line must span at least this fraction of the text width to act as a horizontal stripe
     * separator (a true page-wide title or rule).  Deliberately higher than
     * {@link ColumnLayout#FULL_WIDTH_RATIO} (which only needs a line to span across a gutter to be
     * excluded from the column profile): a caption or footer that spans two of three columns must
     * <em>not</em> break the page into stripes, or the columns interleave.
     */
    private static final double SEPARATOR_RATIO = 0.85;

    private XYCutReadingOrder() {
    }

    /**
     * Returns {@code lines} re-ordered into reading order.  The input list is not modified.
     */
    public static ArrayList<LineText> order(List<LineText> lines) {
        ArrayList<LineText> in = new ArrayList<>(lines);
        if (in.size() <= 1) {
            return in;
        }
        List<double[]> bands = ColumnLayout.detectBands(in);
        if (bands.size() < 2) {
            // single column: plain top-to-bottom, left-to-right.
            LinePositionComparator.orderTopLeft(in);
            return in;
        }
        return columnBandOrder(in, bands);
    }

    /**
     * Orders a multi-column page: split into horizontal stripes at full-width separators, then read
     * each stripe column-by-column (left-to-right), each column top-to-bottom.
     */
    private static ArrayList<LineText> columnBandOrder(List<LineText> lines, List<double[]> bands) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (LineText line : lines) {
            minX = Math.min(minX, line.getBounds().getMinX());
            maxX = Math.max(maxX, line.getBounds().getMaxX());
        }
        double separatorMin = (maxX - minX) * SEPARATOR_RATIO;

        // separators = full-page-width lines; they define the horizontal stripe boundaries (top-first).
        List<LineText> separators = new ArrayList<>();
        List<LineText> body = new ArrayList<>();
        for (LineText line : lines) {
            if (line.getBounds().getWidth() >= separatorMin) separators.add(line);
            else body.add(line);
        }
        promoteBoundarySpanners(body, separators, bands);
        separators.sort((a, b) -> Double.compare(b.getBounds().getCenterY(), a.getBounds().getCenterY()));

        int stripes = separators.size() + 1;
        int cols = bands.size();
        List<List<List<LineText>>> buckets = new ArrayList<>(stripes);
        for (int s = 0; s < stripes; s++) {
            List<List<LineText>> row = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) row.add(new ArrayList<>());
            buckets.add(row);
        }
        for (LineText line : body) {
            double cy = line.getBounds().getCenterY();
            int stripe = 0;
            for (LineText sep : separators) {
                if (sep.getBounds().getCenterY() > cy) stripe++;   // separators above this line
            }
            int col = ColumnLayout.bandOf(line.getBounds().getMinX(), line.getBounds().getMaxX(), bands);
            buckets.get(stripe).get(col).add(line);
        }

        ArrayList<LineText> out = new ArrayList<>(lines.size());
        for (int s = 0; s < stripes; s++) {
            for (int c = 0; c < cols; c++) {
                List<LineText> bucket = buckets.get(s).get(c);
                // Order the column top-to-bottom.  orderTopLeft (not a naive y-sort) bands near-equal
                // y fragments and sorts them left-to-right, so a line split into two boxes at a
                // sub-point y offset still reads in order.  A rotated glyph-stack is left in incoming
                // order: re-sorting a bottom-to-top vertical run would reverse it.
                if (!isVerticalStack(bucket)) {
                    LinePositionComparator.orderTopLeft(bucket);
                }
                out.addAll(bucket);
            }
            if (s < separators.size()) out.add(separators.get(s));
        }
        return out;
    }

    /** A line must overlap a band by at least this much to count as reaching into it, so that a line
     *  merely clipping a band's edge isn't mistaken for one that spans the gutter. */
    private static final double BAND_REACH = 2.0;

    /**
     * Promotes gutter-spanning lines at the very top and very bottom of the page from body to
     * separator, so they read before / after the columns rather than inside whichever column they
     * happen to be centred over.
     * <p>
     * A running header ("TECHNOLOGY REPORT") or a strap-line that spans the gutter is centred in the
     * right-hand band, so it sorts with that column &mdash; i.e. after the whole left column, even
     * though it sits above everything on the page.  A drag that starts on it and moves down then runs
     * backwards through the reading order.  Promoting it makes it a stripe separator, which is
     * emitted at its y position.
     * <p>
     * Only lines that bound the remaining content are promoted &mdash; the current topmost or
     * bottommost body line, repeatedly &mdash; so the stripe they create is always empty on one side.
     * The line must also stand alone at its height: a boundary region that is itself multi-column
     * (side-by-side legal blocks at the foot of a page) has nothing to promote, because emitting such
     * lines as separators would sort those blocks together by y and interleave them.  A
     * gutter-spanning caption in the <em>middle</em> of the body is likewise left alone (see
     * {@link #SEPARATOR_RATIO}).
     */
    private static void promoteBoundarySpanners(List<LineText> body, List<LineText> separators,
                                                List<double[]> bands) {
        for (boolean fromTop : new boolean[]{true, false}) {
            while (!body.isEmpty()) {
                LineText edge = body.get(0);
                for (LineText line : body) {
                    double cy = line.getBounds().getCenterY(), best = edge.getBounds().getCenterY();
                    if (fromTop ? cy > best : cy < best) edge = line;
                }
                if (!spansGutter(edge, bands) || !standsApart(edge, body)) break;
                separators.add(edge);
                body.remove(edge);
            }
        }
    }

    /**
     * True when {@code line} has its height on the page to itself <em>and</em> is separated from the
     * rest of the body by at least a blank line's worth of whitespace &mdash; the signature of a
     * running header, strap-line or footer.
     * <p>
     * The whitespace test is what keeps a wide multi-line block at the foot of a page (side-by-side
     * legal paragraphs, a spanning notice under a table) from being promoted line by line: its lines
     * sit a normal leading apart, so the first one is not set apart from its own continuation and the
     * block stays intact in its column.
     */
    private static boolean standsApart(LineText line, List<LineText> body) {
        Rectangle2D.Double b = line.getBounds();
        double gapMin = medianHeight(body);
        double nearest = Double.MAX_VALUE;
        for (LineText other : body) {
            if (other == line) continue;
            Rectangle2D.Double o = other.getBounds();
            if (o.getMaxY() > b.getMinY() && o.getMinY() < b.getMaxY()) return false;   // beside it
            nearest = Math.min(nearest, o.getMinY() > b.getMaxY()
                    ? o.getMinY() - b.getMaxY() : b.getMinY() - o.getMaxY());
        }
        return nearest >= gapMin;
    }

    /** True when the line reaches into two or more column bands, i.e. it crosses a gutter. */
    private static boolean spansGutter(LineText line, List<double[]> bands) {
        Rectangle2D.Double b = line.getBounds();
        int reached = 0;
        for (double[] band : bands) {
            double overlap = Math.min(b.getMaxX(), band[1]) - Math.max(b.getMinX(), band[0]);
            if (overlap >= BAND_REACH) reached++;
        }
        return reached >= 2;
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
        double medianLineHeight = medianHeight(region);
        boolean tallerThanWide = (maxY - minY) > (maxX - minX);
        boolean glyphWideLines = medianLineWidth < medianLineHeight * 3;
        return tallerThanWide && glyphWideLines;
    }

    private static double medianHeight(List<LineText> region) {
        double[] h = new double[region.size()];
        for (int i = 0; i < region.size(); i++) h[i] = region.get(i).getBounds().getHeight();
        java.util.Arrays.sort(h);
        double m = h[h.length / 2];
        return m > 0 ? m : 1; // guard against zero-height degenerate lines
    }
}
