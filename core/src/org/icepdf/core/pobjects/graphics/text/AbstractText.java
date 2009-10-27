package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
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

    // glyph path used for painting highlighted text using 2d.float.
    protected GeneralPath generalPath;

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

    public abstract GeneralPath getGeneralPath();

    public abstract Rectangle2D.Float getBounds();

    public void clearBounds() {
        generalPath = null;
        bounds = null;
    }

    /**
     * Creates a new instance of GeneralPath for this AbstractText object and
     * applies the current pageTransformation to it.  The containment
     * calculation is then applied the newly tranformed path for the given
     * point.
     * <p/>
     * This method is usually used for text selection via a mouse click interact
     * for word and sentance selection.
     *
     * @param pageTransform page user induced page transform
     * @param point         point to check containment of in page.
     * @return true if the point is contained with in this Text instance.
     */
    public boolean contains(AffineTransform pageTransform, Point2D point) {
        GeneralPath shapePath = new GeneralPath(getGeneralPath());
        shapePath.transform(pageTransform);
        return shapePath.contains(point);
    }

    /**
     * Creates a new instance of GeneralPath for this AbstractText object and
     * applies the current pageTransformation to it.  The containment
     * calculation is then applied the newly tranformed path for the given
     * rectangle.
     * <p/>
     * This method is usually used for text selection via a selection box.
     *
     * @param pageTransform page user induced page transform
     * @param rect          rectangle to check intersection of in page.
     * @return true if the point is contained with in this Text instance.
     */
    public boolean intersects(AffineTransform pageTransform, Rectangle2D rect) {
        GeneralPath shapePath = new GeneralPath(getGeneralPath());
        shapePath.transform(pageTransform);
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
