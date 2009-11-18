package org.icepdf.core.pobjects.annotations;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Stores state paramaters for annotation objects.
 * todo: work in progress.
 *
 * @since 4.0
 */
public class AnnotationState {

    protected BorderStyle borderStyle;
    // border color of annotation.
    protected Color borderColor;
    // annotation bounding rectangle in user space.
    protected Rectangle2D.Float userSpaceRectangle;

    // original rectangle reference.
    protected Annotation annotation;

    public AnnotationState(Annotation annotation) {
        this.annotation = annotation;

        // test to store previous border color, more properties to follow.
        if (this.annotation != null){
            Color tmp = annotation.getBorderColor();
            borderColor = new Color(tmp.getRGB());

            // test to store old user SpaceRectangle.
            Rectangle2D.Float rect = annotation.getUserSpaceRectangle();
            userSpaceRectangle = new Rectangle2D.Float(rect.x, rect.y,
                    rect.width, rect.height);
        }
    }

    public void restore(){
        if (annotation != null){
            annotation.setBorderColor(borderColor);
            annotation.getUserSpaceRectangle().setRect(userSpaceRectangle);
        }
    }
}
