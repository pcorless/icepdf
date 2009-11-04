package org.icepdf.core.views.common;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.util.ColorUtil;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.GraphicsRenderingHints;
import org.icepdf.core.views.DocumentViewController;
import org.icepdf.core.views.DocumentViewModel;
import org.icepdf.core.views.swing.AbstractPageViewComponent;

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>This Class takes care of handling annoation interaction
 * via the UI mouse.  This class is responsible for a few things:
 * </p>
 * <ul>
 * - changing the mouse icon to a pointer when an annotation is encountered
 * on a page.
 * - listener for mouse pressed events to handle annotation actions
 * - painting of annotation rollover effects.
 * </ul>
 * <p>Annotation mouse clicks will only be enabled when the regular pan tool
 * is selected, to activate an annotation using another tool (maginify, text
 * select, etc.) use the ctr key combined with a mouse click to activate the
 * annotaiton. </p>
 *
 * @since 4.0
 */
public class AnnotationHandler implements MouseInputListener {

    private static final Logger logger =
            Logger.getLogger(AnnotationHandler.class.toString());

    // disable/enable file caching, overrides fileCachingSize.
    private static boolean isInteractiveAnnotationsEnabled;
    private static Color annotationHighlightColor;
    private static float annotationHighlightAlpha;

    static {
        // enables interactive annotation support.
        isInteractiveAnnotationsEnabled =
                Defs.sysPropertyBoolean(
                        "org.icepdf.core.annotations.interactive.enabled", true);

        // sets annotation selected highlight colour
        try {
            String color = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.highlight.color", "#000000");
            int colorValue = ColorUtil.convertColor(color);
            annotationHighlightColor =
                    new Color(colorValue > 0 ? colorValue :
                            Integer.parseInt("000000", 16));

        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading page annotation highlight colour");
            }
        }

        // set the annotation alpha value.
        // sets annotation selected highlight colour
        try {
            String alpha = Defs.sysProperty(
                    "org.icepdf.core.views.page.annotation.highlight.alpha", "0.4");
            annotationHighlightAlpha = Float.parseFloat(alpha);

        } catch (NumberFormatException e) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Error reading page annotation highlight alpha");
            }
            annotationHighlightAlpha = 0.4f;
        }
    }

    // parent page component
    private AbstractPageViewComponent pageViewComponent;
    private DocumentViewController documentViewController;
    private DocumentViewModel documentViewModel;
    // annotation support
    private Annotation currentAnnotation;
    private boolean isMousePressed = false;

    public AnnotationHandler(AbstractPageViewComponent pageViewComponent,
                             DocumentViewModel documentViewModel) {
        this.pageViewComponent = pageViewComponent;
        this.documentViewModel = documentViewModel;
    }

    /**
     * Is the given mouse event over an annotation.  Annotation detection
     * is actually handled by the mouse moved event and a currentAnnotation
     * is set to represent the current annotation.
     *  
     * @return true if there is a current annotation false otherwise.
     */
    public boolean isCurrentAnnotation(){
        return currentAnnotation != null;
    }

    public void setDocumentViewController(
            DocumentViewController documentViewController) {
        this.documentViewController = documentViewController;
    }

    public void mouseClicked(MouseEvent e) {
        // if cuurrentAnnotation exists, we want to process the click.
        if (currentAnnotation != null &&
                documentViewController.getAnnotationCallback() != null) {
            documentViewController.getAnnotationCallback()
                    .proccessAnnotationAction(currentAnnotation);
        }
    }

    public void mousePressed(MouseEvent e) {
       
        // setup visual effect when the mouse button is pressed or held down
        // inside the active area of the annotation.
        isMousePressed = true;
        if (currentAnnotation != null) {
            pageViewComponent.repaint();
        }
    }

    public void mouseDragged(MouseEvent e) {

    }

    public void mouseReleased(MouseEvent e) {

        isMousePressed = false;
        if (currentAnnotation != null) {
            pageViewComponent.repaint();
        }
    }

    public void mouseMoved(MouseEvent e) {

        Page currentPage = pageViewComponent.getPageLock(this);
        // handle annotation mouse coordinates
        annotationMouseMoveHandler(currentPage, e.getPoint());

        pageViewComponent.releasePageLock(currentPage, this);
    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void paintAnnotations(Graphics g) {
        Page currentPage = pageViewComponent.getPageLock(this);
        if (currentPage != null && currentPage.isInitiated()) {
            Vector annotations = currentPage.getAnnotations();
            if (annotations != null) {

                Graphics2D gg2 = (Graphics2D) g;

                // save draw state.
                AffineTransform prePaintTransform = gg2.getTransform();
                Color oldColor = gg2.getColor();
                Stroke oldStroke = gg2.getStroke();

                AffineTransform at = currentPage.getPageTransform(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());
                gg2.transform(at);

                // paint all annotations on top of the content buffer
                Object tmp;
                Annotation annotation;
                for (Object annotation1 : annotations) {
                    tmp = annotation1;
                    if (tmp instanceof Annotation) {
                        annotation = (Annotation) tmp;
                        annotation.render(gg2, GraphicsRenderingHints.SCREEN,
                                documentViewModel.getViewRotation(), documentViewModel.getViewZoom(), false);
                    }
                }

                // annotation appearance dictionary, rollover, down appearance.
                if (currentAnnotation != null &&
                        currentAnnotation.allowScreenRolloverMode() &&
                        isMousePressed) {
                    if (currentAnnotation instanceof LinkAnnotation) {
                        LinkAnnotation linkAnnotation = (LinkAnnotation) currentAnnotation;
                        int highlightMode = linkAnnotation.getHighlightMode();
                        if (highlightMode == LinkAnnotation.HIGHLIGHT_INVERT) {
                            Rectangle2D rect = currentAnnotation.getUserSpaceRectangle();
                            gg2.setColor(annotationHighlightColor);
                            gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                    annotationHighlightAlpha));
                            gg2.fillRect((int) rect.getX(),
                                    (int) rect.getY(),
                                    (int) rect.getWidth(),
                                    (int) rect.getHeight());
                        } else if (highlightMode == LinkAnnotation.HIGHLIGHT_OUTLINE) {
                            Rectangle2D rect = currentAnnotation.getUserSpaceRectangle();
                            gg2.setColor(annotationHighlightColor);
                            gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                    annotationHighlightAlpha));
                            gg2.drawRect((int) rect.getX(),
                                    (int) rect.getY(),
                                    (int) rect.getWidth(),
                                    (int) rect.getHeight());
                        } else if (highlightMode == LinkAnnotation.HIGHLIGHT_PUSH) {
                            Rectangle2D rect = currentAnnotation.getUserSpaceRectangle();
                            gg2.setColor(annotationHighlightColor);
                            gg2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                                    annotationHighlightAlpha));
                            gg2.drawRect((int) rect.getX(),
                                    (int) rect.getY(),
                                    (int) rect.getWidth(),
                                    (int) rect.getHeight());
                        }
                    }
                }
                // post paint clean up.
                gg2.setColor(oldColor);
                gg2.setStroke(oldStroke);
                gg2.setTransform(prePaintTransform);
            }
        }
        pageViewComponent.releasePageLock(currentPage, this);
    }


    private void annotationMouseMoveHandler(Page currentPage,
                                            Point mouseLocation) {

        if (currentPage != null &&
                currentPage.isInitiated() &&
                isInteractiveAnnotationsEnabled) {
            Vector annotations = currentPage.getAnnotations();
            if (annotations != null) {
                Annotation annotation;
                Object tmp;
                AffineTransform at = currentPage.getPageTransform(
                        documentViewModel.getPageBoundary(),
                        documentViewModel.getViewRotation(),
                        documentViewModel.getViewZoom());

                try {
                    at.inverseTransform(mouseLocation, mouseLocation);
                } catch (NoninvertibleTransformException e1) {
                    e1.printStackTrace();
                }

                for (Object annotation1 : annotations) {
                    tmp = annotation1;
                    if (tmp instanceof Annotation) {
                        annotation = (Annotation) tmp;
                        // repaint an annotation.
                        if (annotation.getUserSpaceRectangle().contains(
                                mouseLocation.getX(), mouseLocation.getY())) {
                            currentAnnotation = annotation;
                            documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_ANNOTATION);
//                            repaint(annotation.getUserSpaceRectangle().getBounds());
                            pageViewComponent.repaint();
                            break;
                        } else {
                            currentAnnotation = null;
                        }
                    }
                }
                if (currentAnnotation == null) {
                    int toolMode = documentViewModel.getViewToolMode();
                    if (toolMode == DocumentViewModel.DISPLAY_TOOL_PAN) {
                        documentViewController.setViewCursor(DocumentViewController.CURSOR_HAND_OPEN);
                    } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_ZOOM_IN) {
                        documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_IN);
                    } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_ZOOM_OUT) {
                        documentViewController.setViewCursor(DocumentViewController.CURSOR_ZOOM_OUT);
                    } else if (toolMode == DocumentViewModel.DISPLAY_TOOL_TEXT_SELECTION) {
                        documentViewController.setViewCursor(DocumentViewController.CURSOR_SELECT);
                    }
                    pageViewComponent.repaint();
                }
            }
        }
    }

}
