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

import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import javax.swing.*;
import java.awt.*;

public class AnnotationSummaryBox extends PopupAnnotationComponent {

    public AnnotationSummaryBox(PopupAnnotation annotation, DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent, true);

        setFocusable(false);
        removeFocusListener(this);

        commentPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        // hides a bunch of the controls.
        commentPanel.removeMouseListener(popupListener);
        commentPanel.removeMouseListener(this);
        commentPanel.removeMouseMotionListener(this);

        minimizeButton.setVisible(false);
        privateToggleButton.setVisible(false);
        textArea.setEditable(false);
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        commentPanel.getInsets().set(10, 10, 10, 10);
        setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        // add property change events for font and font size
    }


}
