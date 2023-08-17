package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.common.views.annotations.RedactionAnnotationComponent;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
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
            GeneralPath highlightPath = new GeneralPath();
            for (Shape bounds : redactionBounds) {
                highlightPath.append(bounds, false);
            }
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
            // finalized the appearance properties.
            annotation.resetAppearanceStream(pageTransform);
            // pass outline shapes and bounds to create the highlight shapes
            annotation.setContents(contents != null && enableHighlightContents ? contents : markupSubType.toString());

            // create new annotation given the general path
            RedactionAnnotationComponent comp = (RedactionAnnotationComponent)
                    AnnotationComponentFactory.buildAnnotationComponent(
                            annotation, documentViewController, pageViewComponent);

            // add the main highlight annotation
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
