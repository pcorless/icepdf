package org.icepdf.core.pobjects.graphics.text;

import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;

/**
 * Sprite interface which is base for all text sprite types.  Mainly line,
 * text, word and glyph.  They are used for managing text extraction.
 *
 * @since 4.0 
 */
public interface Text {

    public Rectangle2D.Float getBounds();

    public GeneralPath getGeneralPath();

    public boolean contains(AffineTransform pageTransform, Point2D point);

    public boolean intersects(AffineTransform pageTransform, Rectangle2D rect);

    public boolean isHighlighted();

    public boolean isSelected();

    public void setHighlighted(boolean highlight);

    public void setSelected(boolean selected);

    public boolean hasHighligh();

    public boolean hasSelected();

    public void setHasHighlight(boolean hasHighlight);

    public void setHasSelected(boolean hasSelected);

}
