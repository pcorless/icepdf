package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.MarkupGlueAnnotation;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

/**
 * MarkupGluePainter allows for a visual associating between a markup annotation, and it's popup annotation
 * when open.  This painter code is called from the component used in the Viewer RI as well as painting popups
 * for printing purposes.
 *
 * @since 7.1
 */
public class MarkupGluePainter {

    protected MarkupAnnotation markupAnnotation;
    protected PopupAnnotation popupAnnotation;

    protected MarkupGlueAnnotation markupGlueAnnotation;

    public MarkupGluePainter(MarkupAnnotation markupAnnotation, PopupAnnotation popupAnnotation,
                             MarkupGlueAnnotation markupGlueAnnotation) {
        this.markupAnnotation = markupAnnotation;
        this.popupAnnotation = popupAnnotation;
        this.markupGlueAnnotation = markupGlueAnnotation;
    }

    public void paint(Graphics g) {
        Rectangle popupBounds = popupAnnotation.getUserSpaceRectangle().getBounds();
        Rectangle markupBounds = markupAnnotation.getUserSpaceRectangle().getBounds();
        Rectangle glueBounds = markupGlueAnnotation.getUserSpaceRectangle().getBounds();
        MarkupGluePainter.paintGlue(
                g, markupBounds, popupBounds, glueBounds, popupAnnotation.isOpen(), markupAnnotation.getColor());
    }

    public static void paintGlue(Graphics g, Rectangle markupBounds, Rectangle popupBounds, Rectangle glueBounds,
                                 boolean isOpen, Color color) {
        Graphics2D g2d = (Graphics2D) g;
        if (isOpen) {
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(1));
            GeneralPath path = new GeneralPath();
            path.moveTo(0, 0);

            // in order to draw the curvy shape we need to determine which of the 8 surrounding regions
            // the popup is relative to the markup annotation.
            int popupX = popupBounds.x;
            int popupY = popupBounds.y;
            int popupXC = (int) popupBounds.getCenterX();
            int popupYC = (int) popupBounds.getCenterY();
            int popupW = popupBounds.width;
            int popupH = popupBounds.height;

            int markupXC = (int) markupBounds.getCenterX();
            int markupYC = (int) markupBounds.getCenterY();

            float angle = (float) Math.toDegrees(Math.atan2(markupYC - popupYC, markupXC - popupXC));
            if (angle < 0) {
                angle += 360;
            }
            // N
            if (angle >= 67.5 && angle < 112.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupXC, popupY + popupH, popupX, popupY + popupH);
                path.lineTo(popupX + popupW, popupY + popupH);
                path.quadTo(popupXC, popupY + popupH, markupXC, markupYC);
            }
            // NE
            else if (angle >= 112.5 && angle < 157.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX, popupY + popupH, popupX, popupYC);
                path.lineTo(popupXC, popupY + popupH);
                path.quadTo(popupX, popupY + popupH, markupXC, markupYC);
            }
            // E
            else if (angle >= 157.5 && angle < 202.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX, popupYC, popupX, popupY);
                path.lineTo(popupX, popupY + popupH);
                path.quadTo(popupX, popupYC, markupXC, markupYC);
            }
            // SE
            else if (angle >= 202.5 && angle < 247.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX, popupY, popupXC, popupY);
                path.lineTo(popupX, popupYC);
                path.quadTo(popupX, popupY, markupXC, markupYC);
            }
            // S
            else if (angle >= 247.5 && angle < 292.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupXC, popupY, popupX, popupY);
                path.lineTo(popupX + popupW, popupY);
                path.quadTo(popupXC, popupY, markupXC, markupYC);
            } else if (angle >= 292.5 && angle < 315) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX + popupW, popupY, popupXC, popupY);
                path.lineTo(popupX + popupW, popupYC);
                path.quadTo(popupX + popupW, popupY, markupXC, markupYC);
            }
            // W
            else if (angle >= 315 || angle < 22.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX + popupW, popupYC, popupX + popupW, popupY);
                path.lineTo(popupX + popupW, popupY + popupH);
                path.quadTo(popupX + popupW, popupYC, markupXC, markupYC);
            }
            // NW
            else if (angle >= 22.5 && angle < 67.5) {
                path.moveTo(markupXC, markupYC);
                path.quadTo(popupX + popupW, popupY + popupH, popupX + popupW, popupYC);
                path.lineTo(popupXC, popupY + popupH);
                path.quadTo(popupX + popupW, popupY + popupH, markupXC, markupYC);
            }
            // translate to this components space.
            path.transform(new AffineTransform(1, 0, 0, 1, -glueBounds.x, -glueBounds.y));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            g2d.fill(path);
        }
    }
}
