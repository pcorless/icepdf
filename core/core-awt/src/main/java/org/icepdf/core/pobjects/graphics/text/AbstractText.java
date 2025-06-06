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

import org.icepdf.core.pobjects.Page;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Abstract text is the base class for all Text extraction data.  Its main
 * purpose to is hold common data for GeneralPath and Bounds and common
 * contains and intersect calculations.
 * <br>
 * Some paintable properties are also defined here, such as selected, has selected
 * highlight and hasHighlight which are used as queues to painting selected
 * or highlighted text.
 *
 * @since 4.0
 */
public abstract class AbstractText implements Text {

    // Bounds of text converted to page space.
    protected Rectangle2D.Double bounds;

    // original bounds as plotted by the PDF, this is used for painting selection as calculating mouse interactions
    protected Rectangle2D.Double textSelectionBounds;
    // Can be used for space and line break detection.
    protected Rectangle2D.Double textExtractionBounds;

    // selected states
    protected boolean selected;
    // highlight state
    protected boolean highlight;
    protected boolean highlightCursor;
    private Color highlightColor = Page.highlightColor;

    // highlight hint for quicker painting
    protected boolean hasSelected;
    // highlight hint for quicker painting
    protected boolean hasHighlight;
    // highlight cursor hint for quicker painting
    protected boolean hasHighlightCursor;

    protected float pageRotation;

    /**
     * Gets the bounds of the respective text object normalized to page
     * space.  This is mainly used for text selection calculations.
     *
     * @return bounds of text object.
     */
    public abstract Rectangle2D.Double getBounds();

    public void clearBounds() {
        bounds = null;
    }

    /**
     * Creates a new instance of GeneralPath for this AbstractText object and
     * applies the current pageTransformation to it.  The containment
     * calculation is then applied the newly transformed path for the given
     * rectangle.
     * <br>
     * This method is usually used for text selection via a selection box.
     *
     * @param rect rectangle to check intersection of in page.
     * @return true if the point is contained with in this Text instance.
     */
    public boolean intersects(Rectangle2D rect) {
        // bounds is lazy loaded so getBounds is need to get the value correctly.
        GeneralPath shapePath = new GeneralPath(getBounds());
        return shapePath.intersects(rect);
    }

    /**
     * Tests if the point intersects the text bounds.
     *
     * @param point point to test for intersection.
     * @return true if the point intersects the text bounds,  otherwise false.
     */
    public boolean intersects(Point2D.Float point) {
        // bounds is lazy loaded so getBounds is need to get the value correctly.
        GeneralPath shapePath = new GeneralPath(getBounds());
        return shapePath.contains(point);
    }

    /**
     * Is the AbstractText selected, all of its children must also be selected.
     *
     * @return true if selected false otherwise.
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Sets the AbstractText as selected, if its child AbstractText object they
     * must also be selected.
     *
     * @param selected selected state.
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }


    /**
     * Sets the color to display if the text is highlighted
     * @param c The color to highlight with
     */
    public void setHighlightColor(Color c){
        this.highlightColor=c;
    }

    /**
     * @return The highlight color
     */
    public Color getHighlightColor() {
        return highlightColor;
    }

    /**
     * Is the AbstractText highlighted, all of its children must also be
     * highlighted.
     *
     * @return true if highlighted false otherwise.
     */
    public boolean isHighlighted() {
        return highlight;
    }

    /**
     * Sets the AbstractText as highlighted, if its child AbstractText object they
     * must also be highlighted.
     *
     * @param highlight selected state.
     */
    public void setHighlighted(boolean highlight) {
        this.highlight = highlight;
    }

    /**
     * Indicates that at least this or one of the child instances of AbstractText
     * is highlighted.
     *
     * @return true if one or more root or parent elements are in a highlighted
     * state.
     */
    public boolean hasHighligh() {
        return hasHighlight;
    }

    /**
     * Indicates that at least this or one of the child instances of AbstractText
     * is selected.
     *
     * @return true if one or more root or parent elements are in a highlighted
     * state.
     */
    public boolean hasSelected() {
        return hasSelected;
    }

    /**
     * Set the highlighted state, meaning that this instance or one of the child
     * AbstractText objects has a highlighted state.
     *
     * @param hasHighlight true to indicate a highlighted states.
     */
    public void setHasHighlight(boolean hasHighlight) {
        this.hasHighlight = hasHighlight;
    }

    /**
     * Set the selected state, meaning that this instance or one of the child
     * AbstractText objects has a selected state.
     *
     * @param hasSelected true to indicate a selected states.
     */
    public void setHasSelected(boolean hasSelected) {
        this.hasSelected = hasSelected;
    }

    public boolean isHighlightCursor() {
        return highlightCursor;
    }

    public void setHighlightCursor(boolean highlightCursor) {
        this.highlightCursor = highlightCursor;
    }

    public boolean hasHighlightCursor() {
        return hasHighlightCursor;
    }

    public void setHasHighlightCursor(boolean hasHighlightCursor) {
        this.hasHighlightCursor = hasHighlightCursor;
    }

    /**
     * Gets the original bounds of the text unit, this value is not normalized
     * to page space and represents the raw layout coordinates of the text as
     * defined in the Post Script notation. This is primarily used for text
     * selection contains calculations.
     *
     * @return text bounds.
     */
    public Rectangle2D.Double getTextSelectionBounds() {
        return textSelectionBounds;
    }

    /**
     * Gets the bounds of the text unit, this value is normalized to page space and removes any page
     * level rotations.  This is primarily used text coordinate sorting for extraction and searching.
     * Where the getTextSelectionBounds represent the raw layout coordinates of the text as defined in the document
     * which is good for text selection calculations but breaks down when trying to do text extraction sorting.
     *
     * @return text bounds.
     */
    public Rectangle2D.Double getTextExtractionBounds() {
        if (textExtractionBounds == null) {
            if (pageRotation != 0) {
                AffineTransform transform = new AffineTransform();
                // rotation without centering as we want the margin to be the same
                transform.rotate(Math.toRadians(pageRotation));
                textExtractionBounds =
                        (Rectangle2D.Double) transform.createTransformedShape(textSelectionBounds).getBounds2D();
            } else {
                textExtractionBounds = textSelectionBounds;
            }
        }
        return textExtractionBounds;
    }
}
