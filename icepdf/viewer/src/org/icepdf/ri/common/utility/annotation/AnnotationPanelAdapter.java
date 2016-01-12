/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.DocumentViewController;

import javax.swing.*;
import java.util.ResourceBundle;

/**
 * All annotation and action property panels have a common method for
 * assigning the current annotation component.
 *
 * @since 4.0
 */
public abstract class AnnotationPanelAdapter extends JPanel
        implements AnnotationProperties {

    // action instance that is being edited
    protected AnnotationComponent currentAnnotationComponent;
    protected DocumentViewController documentViewController;

    protected SwingController controller;
    protected ResourceBundle messageBundle;

    // border styles types.
    protected static ValueLabelItem[] VISIBLE_TYPE_LIST;
    protected static ValueLabelItem[] LINE_THICKNESS_LIST;
    // line styles.
    protected static ValueLabelItem[] LINE_STYLE_LIST;

    protected AnnotationPanelAdapter(
            SwingController controller) {
        setDoubleBuffered(true);
        this.controller = controller;
        this.documentViewController = controller.getDocumentViewController();
        this.messageBundle = controller.getMessageBundle();

        // common selection lists.
        // line thicknesses.
        if (LINE_THICKNESS_LIST == null) {
            LINE_THICKNESS_LIST = new ValueLabelItem[]{
                    new ValueLabelItem(1f,
                            messageBundle.getString("viewer.common.number.one")),
                    new ValueLabelItem(2f,
                            messageBundle.getString("viewer.common.number.two")),
                    new ValueLabelItem(3f,
                            messageBundle.getString("viewer.common.number.three")),
                    new ValueLabelItem(4f,
                            messageBundle.getString("viewer.common.number.four")),
                    new ValueLabelItem(5f,
                            messageBundle.getString("viewer.common.number.five")),
                    new ValueLabelItem(10f,
                            messageBundle.getString("viewer.common.number.ten")),
                    new ValueLabelItem(15f,
                            messageBundle.getString("viewer.common.number.fifteen"))};
        }
        // setup the menu
        if (VISIBLE_TYPE_LIST == null) {
            VISIBLE_TYPE_LIST = new ValueLabelItem[]{
                    new ValueLabelItem(true,
                            messageBundle.getString("viewer.utilityPane.annotation.border.borderType.visibleRectangle")),
                    new ValueLabelItem(false,
                            messageBundle.getString("viewer.utilityPane.annotation.border.borderType.invisibleRectangle"))};
        }
        if (LINE_STYLE_LIST == null) {
            LINE_STYLE_LIST = new ValueLabelItem[]{
                    new ValueLabelItem(BorderStyle.BORDER_STYLE_SOLID,
                            messageBundle.getString("viewer.utilityPane.annotation.border.solid")),
                    new ValueLabelItem(BorderStyle.BORDER_STYLE_DASHED,
                            messageBundle.getString("viewer.utilityPane.annotation.border.dashed"))};
        }
    }

    /**
     * Utility to update the action annotation when changes have been made to
     * 'Dest' which has the same notation as 'GoTo'.  It's the pre action way
     * of doing things and is still very common of link Annotations. .
     */
    protected void updateCurrentAnnotation() {

        if (documentViewController.getAnnotationCallback() != null) {
            documentViewController.getAnnotationCallback()
                    .updateAnnotation(currentAnnotationComponent);
        }
    }
}
