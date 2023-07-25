/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.views.annotations.summary;

import org.icepdf.ri.common.MutableDocument;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.AnnotationSummaryPanel;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.MessageFormat;
import java.util.ResourceBundle;

public class AnnotationSummaryFrame extends JFrame implements MutableDocument {

    protected final Controller controller;
    protected final ResourceBundle messageBundle;
    protected AnnotationSummaryPanel annotationSummaryPanel;

    public AnnotationSummaryFrame(final Controller controller) {
        this.controller = controller;
        messageBundle = controller.getMessageBundle();
        setIconImage(new ImageIcon(Images.get("icepdf-app-icon-64x64.png")).getImage());
        setTitle(messageBundle.getString("viewer.window.annotationSummary.title.default"));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                saveChanges();
            }
        });
    }

    public void saveChanges() {
        if (annotationSummaryPanel.getController().hasChanged()) {
            final MessageFormat formatter = new MessageFormat(
                    messageBundle.getString("viewer.summary.dialog.saveOnClose.noUpdates.msg").replace("'", "''"));
            final String dialogMessage = formatter.format(new Object[]{controller.getDocument().getDocumentOrigin()});

            final int res = JOptionPane.showConfirmDialog(this,
                    dialogMessage,
                    messageBundle.getString("viewer.summary.dialog.saveOnClose.noUpdates.title"),
                    JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.OK_OPTION) {
                annotationSummaryPanel.getController().save();
            }
            annotationSummaryPanel.getController().setHasManuallyChanged(false);
        }
    }

    @Override
    public void refreshDocumentInstance() {
        if (controller.getDocument() != null) {
            final Object[] messageArguments = {controller.getDocument().getDocumentOrigin()};
            final MessageFormat formatter = new MessageFormat(
                    messageBundle.getString("viewer.window.annotationSummary.title.open.default"));
            setTitle(formatter.format(messageArguments));
            getContentPane().removeAll();
            annotationSummaryPanel = createAnnotationSummaryPanel(controller);
            getContentPane().add(annotationSummaryPanel);
            annotationSummaryPanel.getController().refreshDocumentInstance();
        }
    }

    protected AnnotationSummaryPanel createAnnotationSummaryPanel(final Controller controller) {
        return new AnnotationSummaryPanel(this, controller);
    }

    public AnnotationSummaryPanel getAnnotationSummaryPanel() {
        return annotationSummaryPanel;
    }


    @Override
    public void disposeDocument() {
        saveChanges();
        getContentPane().removeAll();
        invalidate();
        revalidate();
        repaint();
    }
}
