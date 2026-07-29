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

import java.awt.geom.Point2D;
import java.util.Comparator;

/**
 * The WordPositionComparator is optionally called by text extraction algorithms
 * to help insure words found in a line are ordered using the x coordinates
 * of the bounding box in the cartesian plane's fourth quadrant.  The sorting
 * tries to order the word blocks via the coordinate system rather then the order
 * that they were plotted in and thus shouldn't effect LTR or RTL writing formats.
 * <br>
 * It's assumed that all WordText that is a child of LineText will not be
 * sorted on the y access.  The class LinePositionComparator will be used
 * to insure that lines are ordered correctly in the parent PageText array.
 *
 * @since 5.0.6
 */
public class WordPositionComparator implements
        Comparator<AbstractText> {

    private final boolean directional;
    private final double dirX, dirY;

    /**
     * Orders words left-to-right by the x coordinate of their bounding box (the default for horizontal text).
     */
    public WordPositionComparator() {
        this.directional = false;
        this.dirX = 1;
        this.dirY = 0;
    }

    /**
     * Orders words along an arbitrary writing direction by projecting each word's bounding-box centre onto the
     * direction vector.  Used for rotated/vertical lines where an x-only comparison scrambles reading order.
     *
     * @param writeDirection page-space writing-direction vector (need not be unit length).
     */
    public WordPositionComparator(Point2D writeDirection) {
        this.directional = true;
        this.dirX = writeDirection.getX();
        this.dirY = writeDirection.getY();
    }

    /**
     * Compares two words by position: by x for horizontal text, or by projection onto the writing direction when a
     * direction was supplied.
     *
     * @param lt1 word text object to compare
     * @param lt2 word text object to compare
     * @return negative, zero or positive as lt1 reads before, same as, or after lt2 along the writing direction.
     */
    public int compare(AbstractText lt1, AbstractText lt2) {
        if (!directional) {
            return Double.compare(lt1.getTextExtractionBounds().x,
                    lt2.getTextExtractionBounds().x);
        }
        return Double.compare(projection(lt1), projection(lt2));
    }

    private double projection(AbstractText text) {
        java.awt.geom.Rectangle2D.Double b = text.getTextExtractionBounds();
        double cx = b.x + b.width / 2;
        double cy = b.y + b.height / 2;
        return cx * dirX + cy * dirY;
    }
}
