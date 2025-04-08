package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.annotations.AnnotationFactory;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.ri.common.ViewModel;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.AbstractAnnotationComponent;
import org.icepdf.ri.common.views.annotations.AnnotationComponentFactory;
import org.icepdf.ri.util.ViewerPropertiesManager;

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;

/**
 * Creates a placeholder for a digital signature.   Placeholder can be used to add signatures fields to a document,
 * signers can then add there certificate to the signature on a user by user basis.
 */
public class SignatureAnnotationHandler extends SelectionBoxHandler
        implements ToolHandler, MouseInputListener {

    private static final Logger logger = Logger.getLogger(SignatureAnnotationHandler.class.toString());

    public SignatureAnnotationHandler(DocumentViewController documentViewController,
                                      AbstractPageViewComponent pageViewComponent) {
        super(documentViewController, pageViewComponent);
        selectionBoxColour = Color.GRAY;
    }

    public void mouseClicked(MouseEvent e) {
        if (pageViewComponent != null) {
            pageViewComponent.requestFocus();
        }
    }

    public void mousePressed(MouseEvent e) {
        // annotation selection box.
        int x = e.getX();
        int y = e.getY();
        currentRect = new Rectangle(x, y, 0, 0);
        updateDrawableRect(pageViewComponent.getWidth(),
                pageViewComponent.getHeight());
        pageViewComponent.repaint();
    }

    public void mouseReleased(MouseEvent e) {
        updateSelectionSize(e.getX(), e.getY(), pageViewComponent);

        // check the bounds on rectToDraw to try and avoid creating
        // an annotation that is very small.
        if (rectToDraw.getWidth() < 15 || rectToDraw.getHeight() < 15) {
            rectToDraw.setSize(new Dimension(15, 15));
        }

        Rectangle tBbox = convertToPageSpace(rectToDraw).getBounds();

        // create annotations types that are rectangle based;
        // which is actually just link annotations
        SignatureWidgetAnnotation annotation = (SignatureWidgetAnnotation) AnnotationFactory.buildWidgetAnnotation(
                documentViewController.getDocument().getPageTree().getLibrary(),
                FieldDictionaryFactory.TYPE_SIGNATURE,
                tBbox);
        // setup widget highlighting
        ViewModel viewModel = documentViewController.getParentController().getViewModel();
        annotation.setEnableHighlightedWidget(viewModel.isWidgetAnnotationHighlight());

        // Add the signatureWidget to catalog
        InteractiveForm interactiveForm =
                documentViewController.getDocument().getCatalog().getOrCreateInteractiveForm();
        interactiveForm.addField(annotation);

        // create the annotation object.
        AbstractAnnotationComponent comp =
                AnnotationComponentFactory.buildAnnotationComponent(
                        annotation, documentViewController, pageViewComponent);
        comp.setBounds(rectToDraw);
        comp.refreshAnnotationRect();

        // add them to the container, using absolute positioning.
        documentViewController.addNewAnnotation(comp);

        // set the annotation tool to the given tool
        documentViewController.getParentController().setDocumentToolMode(
                preferences.getInt(ViewerPropertiesManager.PROPERTY_ANNOTATION_SIGNATURE_SELECTION_TYPE, 0));

        // clear the rectangle
        clearRectangle(pageViewComponent);

    }

    protected void checkAndApplyPreferences() {

    }

    public void mouseEntered(MouseEvent e) {

    }

    public void mouseExited(MouseEvent e) {

    }

    public void mouseDragged(MouseEvent e) {
        updateSelectionSize(e.getX(), e.getY(), pageViewComponent);
    }

    public void mouseMoved(MouseEvent e) {

    }

    public void installTool() {

    }

    public void uninstallTool() {

    }

    @Override
    public void setSelectionRectangle(Point cursorLocation, Rectangle selection) {

    }

    public void paintTool(Graphics g) {
        paintSelectionBox(g, rectToDraw);
    }

}