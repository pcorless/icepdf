/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared column-band detection for a page's {@link LineText} set.  A single geometry-based routine
 * so the two consumers agree on where the columns are:
 * <ul>
 *     <li>{@link TextSequence#columns()} — column-aware mouse selection, and</li>
 *     <li>{@link XYCutReadingOrder} — column-contiguous reading order.</li>
 * </ul>
 * <p>
 * Columns are the x-bands between vertical gutters.  Coverage is measured as a per-x-bin <em>line
 * count</em> over the non-full-width lines, not a boolean union, so a handful of column-spanning
 * header / title / caption lines can't paint over a gutter that the tall body columns leave empty —
 * the failure mode that makes zero-coverage gutter detection collapse a multi-column page to one
 * column.
 *
 * @since 7.5
 */
final class ColumnLayout {

    /** A line at least this fraction of the text width spans across columns (a full-width intro,
     *  header or rule) and is excluded from the column-coverage profile so it can't bridge a gutter.
     *  Set below a single body column's typical share of the width so a two-column-spanning intro
     *  paragraph is excluded, but a normal one-column body line is not. */
    static final double FULL_WIDTH_RATIO = 0.6;
    /** A gutter must be at least this multiple of the median line height wide. */
    static final double GUTTER_HEIGHT_RATIO = 0.5;
    /** An x-bin counts as column body once its line count clears this fraction of the busiest bin. */
    static final double GUTTER_COVERAGE_RATIO = 0.25;
    /**
     * A band is a real column only if its busiest bin reaches this fraction of the page's busiest
     * bin.  A real column is dense (about as many lines as its neighbours); a sparse strip of
     * right-edge fragments (superscripts, trademark glyphs, a couple of long lines poking past a
     * ragged margin) is not, and must not be split off as a column.
     */
    static final double MIN_COLUMN_PEAK_RATIO = 0.33;
    /**
     * A real column's busiest bin must also stack at least this many lines outright.  On a sparse
     * page the ratio test alone can't tell a thin body column from a two-line fragment strip (both
     * have low peaks); an absolute floor keeps such pages single-column rather than splitting off a
     * spurious column of line-end superscripts.
     */
    static final int MIN_COLUMN_PEAK = 3;
    /**
     * A line at least this fraction of the text width is a page-wide rule, title or footer; such a
     * line crosses every gutter on the page by definition and is not counted as bridging one.
     * Matches {@code XYCutReadingOrder}'s stripe-separator ratio.
     */
    static final double SPANNING_RATIO = 0.85;
    /** Up to this many lines may cross a gutter (a couple of column-spanning headings) before it is
     *  judged bridged; below this count the ratio test is not applied at all. */
    static final int BRIDGE_ALLOWANCE = 2;
    /** A gutter crossed by more than this fraction of the thinner adjacent band's lines is bridged:
     *  the "columns" it separates are really one column plus the ragged edge of the crossing lines. */
    static final double BRIDGE_RATIO = 0.5;

    private ColumnLayout() {
    }

    /**
     * Detects the column x-bands of {@code lines}, left to right.
     *
     * @param lines the page's sorted lines
     * @return one {@code {minX, maxX}} band per detected column; a single band spanning the text
     * width when no gutter is found (single-column or empty).
     */
    static List<double[]> detectBands(List<LineText> lines) {
        List<double[]> single = new ArrayList<>(1);
        if (lines.isEmpty()) {
            single.add(new double[]{0, 0});
            return single;
        }
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (LineText line : lines) {
            minX = Math.min(minX, line.getBounds().getMinX());
            maxX = Math.max(maxX, line.getBounds().getMaxX());
        }
        single.add(new double[]{minX, maxX});
        double width = maxX - minX;
        double gutterMin = medianHeight(lines) * GUTTER_HEIGHT_RATIO;
        if (width <= 0 || gutterMin <= 0) return single;

        // per-bin line-count coverage over the non-full-width lines.
        double fullWidth = width * FULL_WIDTH_RATIO;
        int bins = (int) Math.ceil(width) + 1;
        int[] coverage = new int[bins];
        for (LineText line : lines) {
            Rectangle2D.Double b = line.getBounds();
            if (b.getWidth() >= fullWidth) continue;
            int from = (int) Math.floor(b.getMinX() - minX);
            int to = (int) Math.ceil(b.getMaxX() - minX);
            for (int x = Math.max(0, from); x < Math.min(bins, to); x++) coverage[x]++;
        }
        int peak = 0;
        for (int c : coverage) peak = Math.max(peak, c);
        if (peak == 0) return single;
        double covThreshold = peak * GUTTER_COVERAGE_RATIO;
        boolean[] body = new boolean[bins];
        for (int x = 0; x < bins; x++) body[x] = coverage[x] > covThreshold;

        // bands are the body runs between internal low-coverage gutters (leading/trailing = margins).
        int firstBody = -1;
        for (int x = 0; x < bins; x++) if (body[x]) { firstBody = x; break; }
        if (firstBody < 0) return single;

        double minBandPeak = peak * MIN_COLUMN_PEAK_RATIO;
        List<double[]> bands = new ArrayList<>();
        int bandStart = firstBody;
        int i = firstBody;
        while (i < bins) {
            if (body[i]) {
                i++;
                continue;
            }
            int j = i;
            while (j < bins && !body[j]) j++;
            boolean internal = j < bins;                 // bounded by body on the right
            if (internal && (j - i) >= gutterMin
                    && !isBridged(lines, coverage, minX, width, bandStart, i, j)) {
                // a real gutter: close the current band
                addBand(bands, coverage, bandStart, i, minX, minBandPeak);
                bandStart = j;
            }
            i = j;
        }
        addBand(bands, coverage, bandStart, bins, minX, minBandPeak);
        return bands.size() >= 2 ? bands : single;
    }

    /**
     * True when the candidate gutter {@code [gutterStart, gutterEnd)} is <em>bridged</em>: enough
     * lines run straight across it that it can't be a column separator.
     * <p>
     * The coverage profile deliberately ignores lines wider than {@link #FULL_WIDTH_RATIO} of the
     * text width so that a handful of column-spanning headers can't paint over a gutter.  On a page
     * whose layout changes down the page &mdash; a wide single-column body above a narrow multi-column
     * list below &mdash; that exclusion hides the body entirely, and the ragged short lines of the
     * body then look like a column of their own next to the list's first column.  Counting the lines
     * that actually span the gutter tells the two apart: a real gutter is crossed by a few headers, a
     * phantom one by a whole paragraph's worth of body lines.
     * <p>
     * Page-wide rules and footers ({@code >= SPANNING_RATIO} of the text width) are never counted:
     * they legitimately cross every gutter on the page.
     *
     * @param lines       the page's lines
     * @param coverage    per-bin line-count profile
     * @param minX        page-space x of bin 0
     * @param width       text width in page space
     * @param bandStart   first bin of the band to the left of the gutter
     * @param gutterStart first bin of the gutter (exclusive end of the left band)
     * @param gutterEnd   first bin of the band to the right of the gutter
     * @return true when the gutter is bridged and the two bands should stay merged
     */
    private static boolean isBridged(List<LineText> lines, int[] coverage, double minX, double width,
                                     int bandStart, int gutterStart, int gutterEnd) {
        double spanning = width * SPANNING_RATIO;
        int crossings = 0;
        for (LineText line : lines) {
            Rectangle2D.Double b = line.getBounds();
            if (b.getWidth() >= spanning) continue;               // page-wide rule / footer
            double from = b.getMinX() - minX, to = b.getMaxX() - minX;
            if (from < gutterStart && to > gutterEnd) crossings++;
        }
        if (crossings <= BRIDGE_ALLOWANCE) return false;
        // Compare against the sparser side: the band being closed on the left, and everything to the
        // right of the gutter (its own gutters aren't known yet, which only makes this side denser and
        // so biases towards keeping the gutter).  Crossing more than a fraction of the sparser side's
        // lines means that side is really the ragged edge of the crossing lines' own column.
        int sparser = Math.min(peakBetween(coverage, bandStart, gutterStart),
                peakBetween(coverage, gutterEnd, coverage.length));
        return crossings > Math.max(BRIDGE_ALLOWANCE, sparser * BRIDGE_RATIO);
    }

    private static int peakBetween(int[] coverage, int from, int to) {
        int peak = 0;
        for (int x = Math.max(0, from); x < Math.min(coverage.length, to); x++) {
            peak = Math.max(peak, coverage[x]);
        }
        return peak;
    }

    /** Adds {@code [bandStart, bandEnd)} as a column band only if it is dense enough to be a real
     *  column (peak coverage &ge; {@code minPeak}); a sparse fragment strip is dropped. */
    private static void addBand(List<double[]> bands, int[] coverage, int bandStart, int bandEnd,
                                double minX, double minPeak) {
        int bandPeak = 0;
        for (int x = bandStart; x < bandEnd && x < coverage.length; x++) {
            bandPeak = Math.max(bandPeak, coverage[x]);
        }
        if (bandPeak >= minPeak && bandPeak >= MIN_COLUMN_PEAK) {
            // trim the band to its covered extent (drop leading/trailing sub-threshold bins).
            bands.add(new double[]{minX + bandStart, minX + bandEnd - 1});
        }
    }

    /**
     * @return index of the band containing {@code x}, else the nearest band by <em>edge</em>
     * distance.  Edge distance, not centre distance: a point just outside a narrow band (a sub-column
     * too sparse to be a column of its own) belongs to that neighbour, whereas centre distance would
     * hand it to whichever band happens to be widest.
     */
    static int bandOf(double x, List<double[]> bands) {
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < bands.size(); i++) {
            double[] band = bands.get(i);
            if (x >= band[0] && x <= band[1]) return i;
            double d = Math.min(Math.abs(x - band[0]), Math.abs(x - band[1]));
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        return nearest;
    }

    private static double medianHeight(List<LineText> lines) {
        double[] h = new double[lines.size()];
        for (int i = 0; i < lines.size(); i++) h[i] = lines.get(i).getBounds().getHeight();
        java.util.Arrays.sort(h);
        return h[h.length / 2];
    }
}
