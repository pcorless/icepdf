package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.pobjects.graphics.Shapes;
import org.icepdf.core.pobjects.graphics.commands.DrawCmd;
import org.icepdf.core.pobjects.graphics.commands.ImageDrawCmd;
import org.icepdf.core.pobjects.graphics.commands.ShapesDrawCmd;
import org.icepdf.core.pobjects.graphics.images.ImageStream;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.RedactionAnnotationComponent;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.ArrayList;

/**
 * RedactionAnnotationHandler tool extends HighlightSelectionPageHandler which
 * takes care visually selected text as the mouse is dragged across text on the
 * current page as well as double and triple word selection.
 * <br>
 * Redaction annotation type only marks section of the PDF document for redaction.
 * For content to be removed the document must be exported using the DocumentBuilder
 * WriteMode.FULL_UPDATE mode.  During the export all content is removed from the PDF.
 *
 * @since 7.2
 */
public class TextRedactionAnnotationHandler extends HighLightAnnotationHandler {

    private boolean isOverImage = false;

    public TextRedactionAnnotationHandler(DocumentViewController documentViewController,
                                          AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
        markupSubType = Annotation.SUBTYPE_REDACT;
        selectionBoxColour = Color.BLACK;
    }

    protected void createMarkupAnnotationFromTextSelection(MouseEvent e) {
        // get the selection bounds
        ArrayList<Shape> highlightBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());

        // create the text markup annotation.
        createRedactionAnnotation(highlightBounds);

        // set the annotation tool to the given tool
        documentViewController.getParentController().setDocumentToolMode(
                preferences.getInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_REDACTION_SELECTION_TYPE, 0));
    }

    public void mouseMoved(MouseEvent e) {
        // apply respective pointers when hovering over text or images.
        // text selection has priority over image.
        boolean foundSelectableText = selectionTextSelectIcon(e.getPoint(), pageViewComponent);
        if (!foundSelectableText) {
            selectionImageSelectIcon(e.getPoint(), pageViewComponent);
        } else {
            isOverImage = false;
        }
    }

    public void mousePressed(MouseEvent e) {
        if (!isOverImage) {
            super.mousePressed(e);
        } else {
            isClearSelection = false;
            this.pageViewComponent.requestFocus();
            int x = e.getX();
            int y = e.getY();
            currentRect = new Rectangle(x, y, 0, 0);
            updateDrawableRect(pageViewComponent.getWidth(),
                    pageViewComponent.getHeight());
            pageViewComponent.repaint();
        }
    }

    public void mouseReleased(MouseEvent e) {
        if (!isOverImage) {
            super.mouseReleased(e);
        } else {
            updateSelectionSize(e.getX(), e.getY(), pageViewComponent);
            createMarkupAnnotationFromSelectionBox();
        }
    }

    public void mouseDragged(MouseEvent e) {
        if (isClearSelection) {
            return;
        }
        if (!isOverImage) {
            super.mouseDragged(e);
        } else {
            updateSelectionSize(e.getX(), e.getY(), pageViewComponent);
        }
    }

    @Override
    public void paintTool(Graphics g) {
        paintSelectionBox(g, rectToDraw);
    }

    protected void paintRectangle(Graphics2D gg, Rectangle rectToDraw) {
        if (isOverImage) {
            gg.fillRect(rectToDraw.x, rectToDraw.y, rectToDraw.width - 1, rectToDraw.height - 1);
        }
    }

    public void selectionImageSelectIcon(Point mouseLocation, AbstractPageViewComponent pageViewComponent) {
        try {
            Page currentPage = pageViewComponent.getPage();
            Point2D.Float pageMouseLocation = convertToPageSpace(mouseLocation);
            if (currentPage != null) {
                Shapes pageShapes = currentPage.getShapes();
                ArrayList<DrawCmd> shapes = pageShapes.getShapes();
                isOverImage = isCursorOverImage(pageMouseLocation, shapes);
            }
        } catch (Exception e) {
            logger.fine("Image selection page access interrupted");
        }
    }

    private boolean isCursorOverImage(Point2D.Float pageSpaceMousePoint, ArrayList<DrawCmd> shapes) {
        for (DrawCmd object : shapes) {
            if (object instanceof ImageDrawCmd) {
                ImageDrawCmd imageDrawCmd = (ImageDrawCmd) object;
                ImageStream imageStream = imageDrawCmd.getImageStream();
                Rectangle2D bounds = imageStream.getNormalizedBounds();
                if (bounds.contains(pageSpaceMousePoint)) {
                    documentViewController.setViewCursor(DocumentViewController.CURSOR_CROSSHAIR);
//                    System.out.println(imageStream.getPObjectReference() + " " +
//                            imageStream.getWidth() + "x" + imageStream.getHeight());
                    return true;
                }
            } else if (object instanceof ShapesDrawCmd) {
                // todo still might need some work here and apply the xObject matrix, need to find a simple test case.
                Shapes xObjectShapes = ((ShapesDrawCmd) object).getShapes();
                return isCursorOverImage(pageSpaceMousePoint, xObjectShapes.getShapes());
            }
        }
        return false;
    }

    public void createMarkupAnnotationFromSelectionBox() {

        // check the bounds on rectToDraw to try and avoid creating
        // an annotation that is very small.
        if (rectToDraw.getWidth() < 5 || rectToDraw.getHeight() < 5) {
            rectToDraw.setSize(new Dimension(15, 25));
        }

        Rectangle tBbox = convertToPageSpace(rectToDraw).getBounds();

        // create annotations types that are rectangle based;
        // which is actually just link annotations
        annotation = (RedactionAnnotation)
                AnnotationFactory.buildAnnotation(
                        documentViewController.getDocument().getPageTree().getLibrary(),
                        markupSubType,
                        tBbox);

        if (annotation != null) {

            GeneralPath highlightPath = new GeneralPath();
            highlightPath.append(rectToDraw, false);

            ArrayList<Shape> redactionBounds = new ArrayList<>(1);
            redactionBounds.add(rectToDraw);

            convertToPageSpace(redactionBounds, highlightPath);

            // todo cleanup duplication

            AffineTransform pageTransform = getToPageSpaceTransform();
            annotation.setColor(Color.BLACK);
            annotation.setMarkupBounds(redactionBounds);
            annotation.setMarkupPath(highlightPath);
            annotation.setBBox(tBbox);
            annotation.resetAppearanceStream(pageTransform);

            RedactionAnnotationComponent comp = (RedactionAnnotationComponent)
                    AnnotationComponentFactory.buildAnnotationComponent(
                            annotation, documentViewController, pageViewComponent);
            if (comp != null) {
                documentViewController.addNewAnnotation(comp);
                comp.setBounds(rectToDraw);
                // avoid a potential rounding error in comp.refreshAnnotationRect(), stead we simply
                // set the bbox to the rect which is just fine for highlight annotations.
                Rectangle2D rect = annotation.getUserSpaceRectangle();
                annotation.syncBBoxToUserSpaceRectangle(rect);
            }
        }

        // clear the selection
        rectToDraw = null;
    }

    public void createRedactionAnnotation(ArrayList<Shape> redactionBounds) {
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        if (documentViewModel.isSelectAll()) {
            documentViewController.clearSelectedText();
            return;
        }

        // get the geometric path of the selected text
        if (redactionBounds == null) {
            redactionBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());
        }

        // clear the selected text
        documentViewController.clearSelectedText();

        if (redactionBounds != null && !redactionBounds.isEmpty()) {

            // bound of the selected text
            Rectangle2D shapeBounds;
            double padding;
            // padding out the bound, so we get a better hits when looking for redacted text. Since the redaction
            // box is derived from the glyph bounds we can get rounding errors when a contains call is made and
            // a glyph will be just slightly outside the redaction bounds and contains will return false
            Area area = new Area();
            for (Shape bounds : redactionBounds) {
                shapeBounds = bounds.getBounds2D();
                padding = shapeBounds.getHeight() * 0.025;
                shapeBounds.setRect(
                        shapeBounds.getX() - padding,
                        shapeBounds.getY() - padding,
                        shapeBounds.getWidth() + (padding * 2),
                        shapeBounds.getHeight() + (padding * 2));
                // area is important here as we want a union of the shapes, not multiple separate paths.
                area.add(new Area(shapeBounds));
            }
            GeneralPath highlightPath = new GeneralPath();
            highlightPath.append(area, false);
            // get the bounds before convert to page space
            Rectangle bounds = highlightPath.getBounds();

            Rectangle tBbox = convertToPageSpace(redactionBounds, highlightPath);

            AffineTransform pageTransform = getToPageSpaceTransform();

            // create annotations types that are rectangle based;
            // which is actually just link annotations
            annotation = (RedactionAnnotation)
                    AnnotationFactory.buildAnnotation(
                            documentViewModel.getDocument().getPageTree().getLibrary(),
                            markupSubType,
                            tBbox);
            if (annotation != null) {
                annotation.setColor(Color.BLACK);
                annotation.setMarkupBounds(redactionBounds);
                annotation.setMarkupPath(highlightPath);
                annotation.setBBox(tBbox);
                annotation.resetAppearanceStream(pageTransform);

                RedactionAnnotationComponent comp = (RedactionAnnotationComponent)
                        AnnotationComponentFactory.buildAnnotationComponent(
                                annotation, documentViewController, pageViewComponent);
                if (comp != null) {
                    documentViewController.addNewAnnotation(comp);
                    comp.setBounds(bounds);
                    // avoid a potential rounding error in comp.refreshAnnotationRect(), stead we simply
                    // set the bbox to the rect which is just fine for highlight annotations.
                    Rectangle2D rect = annotation.getUserSpaceRectangle();
                    annotation.syncBBoxToUserSpaceRectangle(rect);
                }
            }
        }
        pageViewComponent.repaint();
    }

}
