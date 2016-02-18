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
package org.icepdf.ri.common.utility.acroform;

import org.icepdf.core.pobjects.acroform.ButtonFieldDictionary;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * Renders a form elements icons that represent the given element.
 */
public class AcroFormCellRender extends DefaultTreeCellRenderer {

    /**
     * Text widget annotation icon.
     */
    public static final ImageIcon FORM_TEXT_WIDGET_ICON = new ImageIcon(Images.get("form_text_tree.png"));

    /**
     * Choice widget annotation icon
     */
    public static final ImageIcon FORM_CHOICE_ICON = new ImageIcon(Images.get("form_choice_tree.png"));

    /**
     * Radio button widget annotation icon
     */
    public static final ImageIcon FORM_RADIO_ICON = new ImageIcon(Images.get("form_radio_tree.png"));

    /**
     * Checkbox button widget.
     */
    public static final ImageIcon FORM_CHECK_ICON = new ImageIcon(Images.get("form_check_tree.png"));

    /**
     * Button widget icon.
     */
    public static final ImageIcon FORM_BUTTON_ICON = new ImageIcon(Images.get("form_btn_tree.png"));

    /**
     * Signature widget annotation icon.
     */
    public static final ImageIcon FORM_SIGNATURE_ICON = new ImageIcon(Images.get("form_sig_tree.png"));

    public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean sel,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus) {

        super.getTreeCellRendererComponent(
                tree, value, sel,
                expanded, leaf, row,
                hasFocus);
        Object currentUserObject = null;
        if (value instanceof AcroFormTreeNode) {
            currentUserObject = ((AcroFormTreeNode) value).getWidgetAnnotation();
        }
        // check user object for type and assign the appropriate icon.
        if (currentUserObject instanceof TextWidgetAnnotation) {
            setIcon(FORM_TEXT_WIDGET_ICON);
        } else if (currentUserObject instanceof ChoiceWidgetAnnotation) {
            setIcon(FORM_CHOICE_ICON);
        } else if (currentUserObject instanceof ButtonWidgetAnnotation) {
            ButtonWidgetAnnotation buttonWidgetAnnotation = (ButtonWidgetAnnotation) currentUserObject;
            ButtonFieldDictionary fieldDictionary = buttonWidgetAnnotation.getFieldDictionary();
            // check for which type of button.
            if (fieldDictionary.getButtonFieldType() == ButtonFieldDictionary.ButtonFieldType.CHECK_BUTTON) {
                setIcon(FORM_CHECK_ICON);
            } else if (fieldDictionary.getButtonFieldType() == ButtonFieldDictionary.ButtonFieldType.RADIO_BUTTON) {
                setIcon(FORM_RADIO_ICON);
            } else {
                setIcon(FORM_BUTTON_ICON);
            }
        } else if (currentUserObject instanceof SignatureWidgetAnnotation) {
            setIcon(FORM_SIGNATURE_ICON);
        } else {
            setIcon(null);
        }
        return this;
    }

}