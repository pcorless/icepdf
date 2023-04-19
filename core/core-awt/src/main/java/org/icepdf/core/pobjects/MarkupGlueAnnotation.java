package org.icepdf.core.pobjects;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupGluePainter;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;

public class MarkupGlueAnnotation extends Annotation {

    protected MarkupAnnotation markupAnnotation;
    protected PopupAnnotation popupAnnotation;

    public MarkupGlueAnnotation(Library l, HashMap h) {
        super(l, h);
    }

    public MarkupGlueAnnotation(Library l, MarkupAnnotation markupAnnotation, PopupAnnotation popupAnnotation) {
        super(l, new HashMap());
        this.markupAnnotation = markupAnnotation;
        this.popupAnnotation = popupAnnotation;
    }

    protected void renderAppearanceStream(Graphics2D g2d) {
        if (this.popupAnnotation == null || this.markupAnnotation == null) return;

        GraphicsConfiguration graphicsConfiguration = g2d.getDeviceConfiguration();
        boolean isPrintingAllowed = this.markupAnnotation.getFlagPrint();
        if (graphicsConfiguration.getDevice().getType() == GraphicsDevice.TYPE_PRINTER &&
                this.popupAnnotation.isOpen() && isPrintingAllowed) {
            AffineTransform oldTransform = g2d.getTransform();
            new MarkupGluePainter(markupAnnotation, popupAnnotation, this).paint(g2d);
            g2d.setTransform(oldTransform);
        }
    }

    public Rectangle2D.Float getUserSpaceRectangle() {
        // make sure we always update this to get the correct clip during painting
        if (this.markupAnnotation != null && this.popupAnnotation != null) {
            Rectangle rect = this.markupAnnotation.getUserSpaceRectangle().getBounds().union(
                    this.popupAnnotation.getUserSpaceRectangle().getBounds());
            userSpaceRectangle = new Rectangle2D.Float(rect.x, rect.y, rect.width, rect.height);
        }
        return userSpaceRectangle;
    }

    public boolean allowPrintNormalMode() {
        return allowScreenOrPrintRenderingOrInteraction() && this.markupAnnotation.getFlagPrint();
    }

    @Override
    public void resetAppearanceStream(double dx, double dy, AffineTransform pageSpace, boolean isNew) {

    }

    public MarkupAnnotation getMarkupAnnotation() {
        return markupAnnotation;
    }
}
