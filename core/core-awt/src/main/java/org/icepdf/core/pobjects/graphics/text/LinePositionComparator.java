/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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

import java.util.Comparator;
import java.util.List;

/**
 * The LinePositionComparator is optionally called by text extraction algorithms
 * to help insure text lines found on a page are ordered using the y coordinates
 * of the bounding box in the cartesian plane's fourth quadrant.  The sorting
 * tries to order the line blocks via the coordinate system rather then the order
 * that they were plotted in.
 * <br>
 * It's assumed that all LineText that is a child of PageText will not be
 * sorted on the x access.  The class WordPositionComparator will be used
 * to insure that words are ordered correctly in the parent PageText array.
 *
 * @since 5.0.6
 */
public class LinePositionComparator implements
        Comparator<AbstractText> {

    /**
     * Compares the y coordinates of the AbstractText bounding box's y coordinate.
     *
     * @param lt1 word text object to compare
     * @param lt2 word text object to compare
     * @return the value 0 if lt1.y is numerically equal to lt2.y; a value less
     *         than 0 if lt1.y is numerically less than lt2.y; and a value greater than 0
     *         if lt1.y is numerically greater than lt2.y.
     */
    public int compare(AbstractText lt1, AbstractText lt2) {
        int comp = Double.compare(lt2.getBounds().y, lt1.getBounds().y);
        if (comp == 0) {
            comp = Double.compare(lt1.getBounds().x, lt2.getBounds().x);
        }
        return comp;
    }

    /**
     * Orders {@code lines} top-to-bottom, then left-to-right within each visual line.
     * <p>
     * A plain sort on this comparator is not enough on its own: fragments that share a visual line
     * routinely differ in y by a tiny amount, because a superscript, a trademark glyph or a font
     * change shifts the fragment's bounding box a fraction of a point off its neighbour's baseline.
     * Such a fragment loses the exact-tie test in {@link #compare} and sorts ahead of the text it
     * belongs beside, reversing the line (e.g. {@code "tm software..."} emitted before
     * {@code "FLEXenabled"}).
     * <p>
     * So lines are first grouped into horizontal bands using a tolerance derived from line height,
     * and only then sorted by x within each band.  The banding is deliberately not folded into
     * {@link #compare} as a fuzzy y-comparison: a tolerance-based comparator is not transitive
     * (a~b and b~c does not give a~c) and {@code TimSort} rejects it at runtime with
     * "Comparison method violates its general contract!".
     *
     * @param lines lines to order; sorted in place
     */
    public static void orderTopLeft(List<LineText> lines) {
        if (lines.size() < 2) {
            return;
        }
        lines.sort(new LinePositionComparator());
        // tolerance of half a line height matches the slicing rule used by
        // PageText.sortLinesVertically, and stays well inside normal leading.
        double tolerance = medianHeight(lines) * 0.5;
        if (tolerance <= 0) {
            return;
        }
        // walk the y-sorted lines, sorting each band of near-equal y by x.  Band membership is
        // measured against the band's first line, not the previous one, so a run of slightly
        // decreasing y cannot chain into one oversized band.
        int start = 0;
        for (int i = 1; i <= lines.size(); i++) {
            boolean endOfBand = i == lines.size()
                    || lines.get(start).getBounds().y - lines.get(i).getBounds().y > tolerance;
            if (endOfBand) {
                if (i - start > 1) {
                    // sort on getBounds() throughout; WordPositionComparator works off
                    // getTextExtractionBounds(), a different bounds system on these nodes.
                    lines.subList(start, i).sort(
                            (a, b) -> Double.compare(a.getBounds().x, b.getBounds().x));
                }
                start = i;
            }
        }
    }

    /** Median bounding-box height of {@code lines}, used to scale the band tolerance. */
    private static double medianHeight(List<LineText> lines) {
        double[] heights = new double[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            heights[i] = lines.get(i).getBounds().height;
        }
        java.util.Arrays.sort(heights);
        return heights[heights.length / 2];
    }
}
