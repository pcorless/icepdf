/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
import org.icepdf.ri.images.Images;

import javax.swing.*;
import java.text.MessageFormat;
import java.util.ResourceBundle;

public class AnnotationSummaryFrame extends JFrame implements MutableDocument {

    protected Controller controller;
    protected ResourceBundle messageBundle;
    protected AnnotationSummaryPanel annotationSummaryPanel;

    public AnnotationSummaryFrame(Controller controller) {
        this.controller = controller;
        messageBundle = controller.getMessageBundle();

        setIconImage(new ImageIcon(Images.get("icepdf-app-icon-64x64.png")).getImage());
        setTitle(messageBundle.getString("viewer.window.annotationSummary.title.default"));
    }

    @Override
    public void refreshDocumentInstance() {
        if (controller.getDocument() != null) {

            Object[] messageArguments = new Object[]{controller.getDocument().getDocumentOrigin()};
            MessageFormat formatter = new MessageFormat(
                    messageBundle.getString("viewer.window.annotationSummary.title.open.default"));
            setTitle(formatter.format(messageArguments));

            annotationSummaryPanel = new AnnotationSummaryPanel(controller);
            getContentPane().add(annotationSummaryPanel);
            annotationSummaryPanel.refreshDocumentInstance();
        }
    }

    public AnnotationSummaryPanel getAnnotationSummaryPanel() {
        return annotationSummaryPanel;
    }

    @Override
    public void disposeDocument() {
        getContentPane().removeAll();
        invalidate();
        revalidate();
        repaint();
    }
}
