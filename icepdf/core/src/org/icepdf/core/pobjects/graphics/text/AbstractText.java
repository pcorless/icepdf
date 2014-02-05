/*
 * Copyright 2006-2013 ICEsoft Technologies Inc.
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

import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * Abstarct text is the base class for all Text extraction data.  Its main
 * purpose to is hold common data for GeneralPath and Bounds and commong
 * contains and intersect calculations.
 * <p/>
 * Some paintable properties are also defined here, such as selected, has selected
 * highlight and hasHighlight which are used as queues to painting selected
 * or highlighted text.
 *
 * @since 4.0
 */
public abstract class AbstractText implements Text {

    // white space sentences etc.
    protected Rectangle2D.Float bounds;

    // selected states
    protected boolean selected;
    // highlight state
    protected boolean highlight;

    // highlight hint for quicker painting
    protected boolean hasSelected;
    // highlight hint for quicker painting
    protected boolean hasHighlight;

    /**
     * Gets the bounds of the respective text object.
     *
     * @return bounds of text object.
     */
    public abstract Rectangle2D.Float getBounds();

    public void clearBounds() {
        bounds = null;
    }

    /**
     * Creates a new instance of GeneralPath for this AbstractText object and
     * applies the current pageTransformation to it.  The containment
     * calculation is then applied the newly tranformed path for the given
     * rectangle.
     * <p/>
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
     * Is the AbstarctText selected, all of its children must also be selected.
     *
     * @return true if selected false otherwise.
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Sets the AbstractText as selected, if it child AbstractText object they
     * must also be selected.
     *
     * @param selected selected state.
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * Is the AbstarctText highlighted, all of its children must also be
     * highlighted.
     *
     * @return true if highlighted false otherwise.
     */
    public boolean isHighlighted() {
        return highlight;
    }

    /**
     * Sets the AbstractText as highlighted, if it child AbstractText object they
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
     *         state.
     */
    public boolean hasHighligh() {
        return hasHighlight;
    }

    /**
     * Indicates that at least this or one of the child instances of AbstractText
     * is selected.
     *
     * @return true if one or more root or parent elements are in a highlighted
     *         state.
     */
    public boolean hasSelected() {
        return hasSelected;
    }

    /**
     * Set the highlited state, meaning that this instance or one of the child
     * AbstractText objects has a highlighted state.
     *
     * @param hasHighlight true to indicates a highlighted states.
     */
    public void setHasHighlight(boolean hasHighlight) {
        this.hasHighlight = hasHighlight;
    }

    /**
     * Set the selected state, meaning that this instance or one of the child
     * AbstractText objects has a selected state.
     *
     * @param hasSelected true to indicates a selected states.
     */
    public void setHasSelected(boolean hasSelected) {
        this.hasSelected = hasSelected;
    }
}
