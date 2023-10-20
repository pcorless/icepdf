package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.RedactionAnnotationComponent;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
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

    public TextRedactionAnnotationHandler(DocumentViewController documentViewController,
                                          AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
        markupSubType = Annotation.SUBTYPE_REDACT;
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

        // grab the selected text
        String contents = enableHighlightContents && redactionBounds != null ? getSelectedText() : "";

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

            annotation.setColor(Color.BLACK);
            annotation.setMarkupBounds(redactionBounds);
            annotation.setMarkupPath(highlightPath);
            annotation.setBBox(tBbox);
            annotation.resetAppearanceStream(pageTransform);

            RedactionAnnotationComponent comp = (RedactionAnnotationComponent)
                    AnnotationComponentFactory.buildAnnotationComponent(
                            annotation, documentViewController, pageViewComponent);
            documentViewController.addNewAnnotation(comp);

            // convert to user rect to page space along with the bounds.
            comp.setBounds(bounds);
            // avoid a potential rounding error in comp.refreshAnnotationRect(), stead we simply
            // set the bbox to the rect which is just fine for highlight annotations.
            Rectangle2D rect = annotation.getUserSpaceRectangle();
            annotation.syncBBoxToUserSpaceRectangle(rect);
        }
        pageViewComponent.repaint();
    }

}
