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

import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.AnnotationPopup;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.logging.Logger;

/**
 * The summary view is made up of annotation contents for markup annotations.  The view however is built independently
 * of the the page view and the component state may not be in correct state to use the default MarkupAnnotationPopupMenu
 * <p>
 * This class takes into account that the component state is not guaranteed.
 *
 * @since 6.3
 */
public class SummaryPopupMenu extends AnnotationPopup<MarkupAnnotationComponent> {

    private static final Logger logger =
            Logger.getLogger(SummaryPopupMenu.class.toString());

    protected MarkupAnnotation markupAnnotation;
    protected Frame frame;

    public SummaryPopupMenu(MarkupAnnotation markupAnnotation, MarkupAnnotationComponent annotationComponent,
                            Controller controller, Frame frame) {
        super(annotationComponent, controller, null);
        this.frame = frame;
        this.markupAnnotation = markupAnnotation;
        this.buildGui();
    }

    public void buildGui() {
        if (!(annotationComponent.getAnnotation() instanceof LinkAnnotation)) {
            destinationsMenuItem.setEnabled(false);
        }
        add(deleteMenuItem, -1);
        deleteMenuItem.addActionListener(this);
        addSeparator();
        add(propertiesMenuItem);
        propertiesMenuItem.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == null) return;

        if (source == propertiesMenuItem) {
            controller.showAnnotationProperties(annotationComponent, frame);
        } else if (source == deleteMenuItem) {
            controller.getDocumentViewController().deleteAnnotation(annotationComponent);
        }
    }
}
