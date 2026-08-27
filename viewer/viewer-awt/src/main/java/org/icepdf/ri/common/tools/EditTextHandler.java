/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.ri.common.tools;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.edit.content.TextContentEditor;
import org.icepdf.core.util.edit.content.TextEditCapability;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ResourceBundle;

import static org.icepdf.ri.common.tools.HighLightAnnotationHandler.getSelectedTextBounds;

/**
 * EditTextHandler is a tool handler that allows the user to edit text.  Text selection can be specified by either
 * work or line.  The text is selected and a dialog is shown to allow the user to edit the text.  The edited text
 * is then used to update the content stream of the page.
 * <p>
 * Editing success depends on how the text was encoded in the PDF.  If the text has been encoded using a sub font that
 * does not contain the glyphs for the text, then the text will be editable but may not be displayed correctly.  In
 * such a case the dialog will show warning message that the text may not be displayed correctly.
 */
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
        ArrayList<Shape> highlightBounds = getSelectedTextBounds(pageViewComponent, getPageTransform());
        if (highlightBounds != null && !highlightBounds.isEmpty()) {
            GeneralPath highlightPath = convertTextShapesToBounds(highlightBounds);
            Rectangle textBounds = convertToPageSpace(highlightBounds, highlightPath);
            Page currentPage = pageViewComponent.getPage();
            String selectedText = currentPage.getViewText().getSelected().toString().trim();

            // Refused outright only when the text is not made of characters in a font at all - a
            // Type 3 glyph is a content stream the page draws, and there is nothing to write a
            // replacement as.  Everything else can be edited: where the document's own font has no
            // character for what was typed, the text is written in a substitute.
            String unsupported = TextEditCapability.unsupportedReason(currentPage, textBounds);
            if (unsupported != null) {
                JOptionPane.showMessageDialog(controller.getViewerFrame(),
                        controller.getMessageBundle().getString(
                                "viewer.dialog.textEdit.unsupported." + unsupported + ".msg"),
                        controller.getMessageBundle().getString("viewer.dialog.textEdit.unsupported.title"),
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            TextEditDialog textEditDialog = new TextEditDialog(controller,
                    controller.getMessageBundle(), selectedText);
            textEditDialog.setVisible(true);
            if (!textEditDialog.isCancelled()) {
                String newText = textEditDialog.getText();
                // The document's font cannot write some of what was typed, so it will be written in
                // a similar one instead.  A visible change to the page, so it is offered rather than
                // done quietly.
                if (TextEditCapability.requiresSubstitution(currentPage, textBounds, newText)
                        && JOptionPane.showConfirmDialog(controller.getViewerFrame(),
                        controller.getMessageBundle().getString("viewer.dialog.textEdit.substitute.msg"),
                        controller.getMessageBundle().getString("viewer.dialog.textEdit.substitute.title"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
                    return;
                }
                // update the text in the content stream
                TextContentEditor.updateText(pageViewComponent.getPage(), selectedText, textBounds, newText);
            }
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

    /**
     * TextEditDialog is a simple dialog that allows the user to edit text that was selected by teh edit text tool.
     * Edited text is then used to update the content stream of the page.
     */
    public static class TextEditDialog extends EscapeJDialog {

        private GridBagConstraints constraints;
        private boolean cancelled;
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
            addGB(layout, editTextField, 0, 0, 1, 2);

            JButton cancelButton = new JButton(messageBundle.getString("viewer.dialog.textEdit.cancel.label"));
            cancelButton.addActionListener(e -> {
                cancelled = true;
                setVisible(false);
                dispose();
            });
            addGB(layout, cancelButton, 0, 2, 1, 1);

            JButton okButton = new JButton(messageBundle.getString("viewer.dialog.textEdit.save.label"));
            okButton.addActionListener(e -> {
                setVisible(false);
                dispose();
            });
            addGB(layout, okButton, 1, 2, 1, 1);

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

        public boolean isCancelled() {
            return cancelled;
        }
    }
}
