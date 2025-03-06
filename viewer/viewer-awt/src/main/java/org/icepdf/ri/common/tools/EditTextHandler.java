package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.edit.content.TextContentEditor;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AbstractPageViewComponent;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.ArrayList;

import static org.icepdf.ri.common.tools.HighLightAnnotationHandler.getSelectedTextBounds;

public class EditTextHandler extends TextSelection
        implements ToolHandler {

    private final SwingController controller;

    public EditTextHandler(SwingController controller, AbstractPageViewComponent pageViewComponent) {
        super(controller.getDocumentViewController(), pageViewComponent);
        this.controller = controller;
    }

    public void editWord(Point selectionPoint) {
        try {
            // select the word under the mouse
            Page currentPage = pageViewComponent.getPage();
            // handle text selection mouse coordinates
            Point mouseLocation = (Point) selectionPoint.clone();
            wordSelectHandler(currentPage, mouseLocation);

            // get the bounds and text
            ArrayList<Shape> highlightBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());
            if (highlightBounds != null && !highlightBounds.isEmpty()) {
                GeneralPath highlightPath = convertTextShapesToBounds(highlightBounds);
                Rectangle textBounds = convertToPageSpace(highlightBounds, highlightPath);
                // todo we need to build a rule set for when editing would be allowed.
                //  is there a toUnicode mapping
                //  what font type is being used
                TextContentEditor.updateText(currentPage, textBounds, "new text");
                // repaint page
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void editLine(Point selectionPoint) {
        try {
            // select the word under the mouse
            Page currentPage = pageViewComponent.getPage();
            // handle text selection mouse coordinates
            Point mouseLocation = (Point) selectionPoint.clone();
            lineSelectHandler(currentPage, mouseLocation);

            // get the bounds and text
            ArrayList<Shape> highlightBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());
            String text = documentViewController.getSelectedText();
            System.out.println("Selected text: " + text);


            // open the dialog

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void paintTool(Graphics g) {

    }

    @Override
    public void installTool() {

    }

    @Override
    public void uninstallTool() {

    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {

    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseEntered(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseExited(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {

    }

    @Override
    public void mouseMoved(MouseEvent mouseEvent) {

    }
}
