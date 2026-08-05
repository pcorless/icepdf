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
        ArrayList<LineText> out = new ArrayList<>(in.size());
        orderRegion(in, out, 0);
        return out;
    }

    /** Recursion guard; layouts this deeply nested don't occur in practice. */
    private static final int MAX_CUT_DEPTH = 12;
    /** How many candidate horizontal cuts to try before giving up on a region. */
    private static final int MAX_GAP_CANDIDATES = 24;

    /**
     * Appends one region to {@code out} in reading order, cutting it recursively.  Three cuts are
     * tried in turn, and whichever fires first recurses into its parts:
     * <ol>
     *     <li><b>Separator</b> &mdash; lines spanning the region's own width (a title, a table rule, a
     *     full-width table row) are lifted out; they split the region into stripes and are emitted
     *     between them, at their own y.</li>
     *     <li><b>Vertical</b> &mdash; the region's gutters, if it has any: each column left to right.</li>
     *     <li><b>Horizontal</b> &mdash; a band of whitespace that no line crosses: top half, then
     *     bottom.</li>
     * </ol>
     * Anything that survives all three is a leaf and is read plain top-to-bottom.
     * <p>
     * Recursing rather than deciding once per page is what makes the order regional, and regional is
     * what real pages need: they change layout down the page.  On a rate card, two side-by-side
     * sponsorship panels sit above a billing form whose wide rows run straight across the panels'
     * gutter.  Decide once for the whole page and there is no right answer &mdash; call it two-column
     * and the form is torn along the panels' gutter, call it one and the panels interleave row-wise.
     * Cutting the form away first lets the panels answer for themselves.
     *
     * @see #revealingHorizontalCut for why only structure-revealing horizontal cuts are taken
     */
    private static void orderRegion(List<LineText> region, List<LineText> out, int depth) {
        if (region.size() <= 1 || depth >= MAX_CUT_DEPTH) {
            orderColumn(region);
            out.addAll(region);
            return;
        }
        // 1. separator cut: lines spanning the region's width split it into stacked stripes.
        double separatorMin = width(region) * SEPARATOR_RATIO;
        List<LineText> wide = new ArrayList<>();
        for (LineText line : region) {
            if (line.getBounds().getWidth() >= separatorMin) wide.add(line);
        }
        if (stripeAt(region, wide, out, depth)) return;

        // 2. vertical cut: the region's own columns, left to right.
        List<double[]> bands = ColumnLayout.detectBands(region);
        if (bands.size() >= 2) {
            // A line straddling the gutter is a heading over the columns, not a member of one; emit it
            // at its own y instead (see spanningSeparators).
            if (stripeAt(region, spanningSeparators(region, bands), out, depth)) return;
            List<List<LineText>> columns = new ArrayList<>(bands.size());
            for (int c = 0; c < bands.size(); c++) columns.add(new ArrayList<>());
            for (LineText line : region) {
                columns.get(ColumnLayout.bandOf(line.getBounds().getMinX(), line.getBounds().getMaxX(), bands))
                        .add(line);
            }
            boolean progress = true;
            for (List<LineText> column : columns) {
                if (column.size() == region.size()) progress = false;   // everything landed in one band
            }
            if (progress) {
                for (List<LineText> column : columns) {
                    orderRegion(column, out, depth + 1);
                }
                return;
            }
        }
        // 3. horizontal cut at a whitespace band, top half first.
        List<LineText> sorted = topFirst(region);
        int split = revealingHorizontalCut(sorted);
        if (split > 0) {
            orderRegion(new ArrayList<>(sorted.subList(0, split)), out, depth + 1);
            orderRegion(new ArrayList<>(sorted.subList(split, sorted.size())), out, depth + 1);
            return;
        }
        // 4. leaf.
        orderColumn(region);
        out.addAll(region);
    }

    /**
     * Chooses a horizontal cut for a region that has no gutter of its own.
     * <p>
     * Candidates are the bands of whitespace that <em>no</em> line crosses, tried widest first.  A
     * candidate is taken only when it <em>reveals structure</em>: one of the two halves has a gutter
     * that the undivided region did not.  That self-justifying test is deliberate &mdash; picking
     * cuts by a whitespace-width threshold means tuning a number against whichever document is in
     * front of you, and the gap that matters here (5pt between a panel block and the table under it)
     * is narrower than gaps that must not be cut elsewhere on the same page.  Requiring the cut to
     * pay for itself needs no threshold at all, and leaves genuinely single-column regions untouched:
     * no half of them finds a gutter, so nothing is cut and the region reads top-to-bottom as before.
     *
     * @param topFirst region's lines sorted top-first
     * @return index in {@code topFirst} to split before, or -1 for no useful cut
     */
    private static int revealingHorizontalCut(List<LineText> topFirst) {
        // A gap exists before line i when every line above it bottoms out above line i's top.
        List<double[]> candidates = new ArrayList<>();     // {gap, index}
        double lowestBottom = topFirst.get(0).getBounds().getMinY();
        for (int i = 1; i < topFirst.size(); i++) {
            Rectangle2D.Double b = topFirst.get(i).getBounds();
            double gap = lowestBottom - b.getMaxY();
            if (gap > 0) candidates.add(new double[]{gap, i});
            lowestBottom = Math.min(lowestBottom, b.getMinY());
        }
        candidates.sort((a, b) -> Double.compare(b[0], a[0]));      // widest gap first
        int tried = 0;
        for (double[] candidate : candidates) {
            if (++tried > MAX_GAP_CANDIDATES) break;
            int at = (int) candidate[1];
            if (ColumnLayout.detectBands(topFirst.subList(0, at)).size() >= 2
                    || ColumnLayout.detectBands(topFirst.subList(at, topFirst.size())).size() >= 2) {
                return at;
            }
        }
        return -1;
    }

    /** @return the x extent covered by {@code lines}. */
    private static double width(List<LineText> lines) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (LineText line : lines) {
            minX = Math.min(minX, line.getBounds().getMinX());
            maxX = Math.max(maxX, line.getBounds().getMaxX());
        }
        return maxX - minX;
    }

    /** @return a copy of {@code region} sorted by top edge, highest first. */
    private static List<LineText> topFirst(List<LineText> region) {
        List<LineText> sorted = new ArrayList<>(region);
        sorted.sort((a, b) -> Double.compare(b.getBounds().getMaxY(), a.getBounds().getMaxY()));
        return sorted;
    }

    /**
     * Orders one column top-to-bottom.  {@code orderTopLeft} (not a naive y-sort) bands near-equal y
     * fragments and sorts them left-to-right, so a line split into two boxes at a sub-point y offset
     * still reads in order.  A rotated glyph-stack is left in incoming order: re-sorting a
     * bottom-to-top vertical run would reverse it.
     */
    private static void orderColumn(List<LineText> column) {
        if (!isVerticalStack(column)) {
            LinePositionComparator.orderTopLeft(column);
        }
    }

    /** A line must overlap a band by at least this much to count as reaching into it, so that a line
     *  merely clipping a band's edge isn't mistaken for one that spans the gutter. */
    private static final double BAND_REACH = 2.0;

    /**
     * The lines of {@code region} that straddle its gutters and so belong to no column: a running
     * header, a strap-line, a heading centred over both columns of a panel pair.
     * <p>
     * Such a line is centred in a gutter or in one band, so bucketing it by x sorts it into a column
     * &mdash; i.e. after everything above it in that column, even though it sits above all of them on
     * the page.  A drag that starts on it and moves down then runs backwards through the reading
     * order.  Emitting them as stripe separators instead puts them at their own y.
     * <p>
     * Two tests keep this to real headings.  A candidate must be <em>alone at its height</em>, and it
     * must be an <em>isolated</em> spanner &mdash; neither the line above nor the line below it spans
     * as well.  Without the isolation test a wide multi-line block (the side-by-side legal paragraphs
     * at the foot of xr_650 p5) is promoted line by line, and emitting those as separators sorts the
     * blocks together by y and interleaves them.  Isolation, rather than a minimum whitespace gap
     * above the line, because the gap that separates a heading from its block is a typographic choice:
     * 5pt on a rate card's panel heading, a full line elsewhere.  How many lines in a row span the
     * gutter is not &mdash; one is a heading, a dozen is a block of text.
     */
    private static List<LineText> spanningSeparators(List<LineText> region, List<double[]> bands) {
        List<LineText> sorted = topFirst(region);
        boolean[] spanning = new boolean[sorted.size()];
        for (int i = 0; i < sorted.size(); i++) {
            spanning[i] = spansGutter(sorted.get(i), bands) && aloneAtItsHeight(sorted.get(i), region);
        }
        List<LineText> spanners = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (!spanning[i]) continue;
            if (i > 0 && spanning[i - 1]) continue;                       // part of a spanning block
            if (i < sorted.size() - 1 && spanning[i + 1]) continue;
            spanners.add(sorted.get(i));
        }
        return spanners;
    }

    /**
     * Emits {@code region} as stripes divided by {@code separators}: each stripe is cut recursively,
     * and the separator that closed it follows at its own y.
     *
     * @return false when there is nothing to divide (no separators, or they are the whole region), in
     * which case nothing has been written to {@code out}
     */
    private static boolean stripeAt(List<LineText> region, List<LineText> separators,
                                    List<LineText> out, int depth) {
        if (separators.isEmpty() || separators.size() == region.size()) return false;
        List<LineText> body = new ArrayList<>(region);
        body.removeAll(separators);
        separators.sort((a, b) -> Double.compare(b.getBounds().getCenterY(), a.getBounds().getCenterY()));
        List<List<LineText>> stripes = new ArrayList<>(separators.size() + 1);
        for (int s = 0; s <= separators.size(); s++) stripes.add(new ArrayList<>());
        for (LineText line : body) {
            int stripe = 0;
            for (LineText separator : separators) {
                // A separator counts as above the line unless the line clears its top outright.  Not
                // centre against centre: a narrow fragment sharing a visual line with a separator (a
                // trademark glyph at the right edge of a body line) overlaps it, their centres tie to
                // within a hundredth of a point, and the fragment must read after the line it sits on
                // rather than closing the stripe above it.
                if (line.getBounds().getCenterY() < separator.getBounds().getMaxY()) stripe++;
            }
            stripes.get(stripe).add(line);
        }
        for (int s = 0; s < stripes.size(); s++) {
            orderRegion(stripes.get(s), out, depth + 1);
            if (s < separators.size()) out.add(separators.get(s));
        }
        return true;
    }

    /**
     * True when no other line in {@code region} overlaps {@code line} vertically: it has its height to
     * itself, so lifting it out reorders nothing that sits beside it.
     */
    private static boolean aloneAtItsHeight(LineText line, List<LineText> region) {
        Rectangle2D.Double b = line.getBounds();
        for (LineText other : region) {
            if (other == line) continue;
            Rectangle2D.Double o = other.getBounds();
            if (o.getMaxY() > b.getMinY() && o.getMinY() < b.getMaxY()) return false;
        }
        return true;
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
