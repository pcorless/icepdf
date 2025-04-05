package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.edit.content.TextContentEditor;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ResourceBundle;

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
            wordSelectHandler(currentPage, selectionPoint);

            updateSelectedText();

            // reinitialize the page and repaint with new content stream
            pageViewComponent.reinitialize();
            pageViewComponent.repaint();

        } catch (InterruptedException | IOException e) {
            logger.severe("Error editing word: " + e);
        }
    }

    public void editLine(Point selectionPoint) {
        try {
            // select the word under the mouse
            Page currentPage = pageViewComponent.getPage();
            // handle text selection mouse coordinates
            lineSelectHandler(currentPage, selectionPoint);

            updateSelectedText();

            // reinitialize the page and repaint with new content stream
            pageViewComponent.reinitialize();
            pageViewComponent.repaint();

        } catch (InterruptedException | IOException e) {
            logger.severe("Error editing line: " + e);
        }
    }

    private void updateSelectedText() throws IOException, InterruptedException {
        // get the bounds and text
        ArrayList<Shape> highlightBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());

        if (highlightBounds != null && !highlightBounds.isEmpty()) {
            GeneralPath highlightPath = convertTextShapesToBounds(highlightBounds);
            Rectangle textBounds = convertToPageSpace(highlightBounds, highlightPath);
            // todo we need to build a rule set for when editing would be allowed.
            //  is there a toUnicode mapping
            //  what font type is being used
            //  would likely be a utility method on this class that would be called from the TextSelectionViewHandler
            Page currentPage = pageViewComponent.getPage();
            String selectedText = currentPage.getViewText().getSelected().toString().trim();

            TextEditDialog textEditDialog = new TextEditDialog(controller,
                    controller.getMessageBundle(),
                    selectedText);
            textEditDialog.setVisible(true);

            String newText = textEditDialog.getText();

            TextContentEditor.updateText(pageViewComponent.getPage(), selectedText, textBounds, newText);
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

    public class TextEditDialog extends EscapeJDialog implements ActionListener {

        private GridBagConstraints constraints;
        private JTextField editTextField;


        protected ResourceBundle messageBundle;

        public TextEditDialog(Controller controller, ResourceBundle messageBundle,
                              String selectedText) {
            super(controller.getViewerFrame(), true);
            this.messageBundle = messageBundle;
            buildUI(selectedText);
        }

        private void buildUI(String selectedText) {
            setTitle(messageBundle.getString("viewer.dialog.textEdit.title"));
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setLayout(new GridBagLayout());
            constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.insets = new Insets(5, 10, 10, 10);

            JPanel layout = new JPanel(new GridBagLayout());

            editTextField = new JTextField(50);
            editTextField.setText(selectedText);
            addGB(layout, editTextField, 0, 0, 1, 1);

            JButton okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
            okButton.addActionListener(e -> {
                setVisible(false);
                dispose();
            });
            addGB(layout, okButton, 0, 1, 1, 1);

            setContentPane(layout);

            pack();
            setLocationRelativeTo(this.getOwner());
        }

        private void addGB(JPanel layout, Component component,
                           int x, int y,
                           int rowSpan, int colSpan) {
            constraints.gridx = x;
            constraints.gridy = y;
            constraints.gridwidth = colSpan;
            constraints.gridheight = rowSpan;
            layout.add(component, constraints);
        }

        public String getText() {
            return editTextField.getText();
        }

        @Override
        public void actionPerformed(ActionEvent actionEvent) {

        }
    }
}
