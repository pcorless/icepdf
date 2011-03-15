/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2011 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.ri.common.annotation;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.AnnotationState;
import org.icepdf.core.pobjects.annotations.BorderStyle;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.views.swing.AnnotationComponentImpl;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AbstractDocumentViewModel;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ResourceBundle;

/**
 * Link Annotation panel intended use is for the manipulation of LinkAnnotation
 * appearance prooperties.  This could be used with other annotation types but
 * it's not suggested.
 *
 * @since 4.0
 */
public class LinkAnnotationPanel extends AnnotationPanelAdapter implements ItemListener,
        ActionListener {

    // default list values.
    private static final int DEFAULT_LINK_TYPE = 1;
    private static final int DEFAULT_HIGHLIGHT_STYLE = 1;
    private static final int DEFAULT_LINE_THICKNESS = 0;
    private static final int DEFAULT_LINE_STYLE = 0;
    private static final Color DEFAULT_BORDER_COLOR = Color.BLACK;

    // link types.
    private final ValueLabelItem[] LINK_TYPE_LIST = new ValueLabelItem[]{
            new ValueLabelItem(Annotation.VISIBLE_RECTANGLE, "Visible Rectangle"),
            new ValueLabelItem(Annotation.INVISIBLE_RECTANGLE, "Invisible Rectangle")};

    // highlight states styles.
    private final ValueLabelItem[] HIGHLIGHT_STYLE_LIST = new ValueLabelItem[]{
            new ValueLabelItem(LinkAnnotation.HIGHLIGHT_NONE, "None"),
            new ValueLabelItem(LinkAnnotation.HIGHLIGHT_INVERT, "Invert"),
            new ValueLabelItem(LinkAnnotation.HIGHLIGHT_OUTLINE, "Outline"),
            new ValueLabelItem(LinkAnnotation.HIGHLIGHT_PUSH, "Push")};

    // line thicknesses.
    private final ValueLabelItem[] LINE_THICKNESS_LIST = new ValueLabelItem[]{
            new ValueLabelItem(1f, "1"),
            new ValueLabelItem(2f, "2"),
            new ValueLabelItem(3f, "3"),
            new ValueLabelItem(4f, "4"),
            new ValueLabelItem(5f, "5"),
            new ValueLabelItem(6f, "10"),
            new ValueLabelItem(7f, "15")};

    // line styles.
    private final ValueLabelItem[] LINE_STYLE_LIST = new ValueLabelItem[]{
            new ValueLabelItem(BorderStyle.BORDER_STYLE_SOLID, "Solid"),
            new ValueLabelItem(BorderStyle.BORDER_STYLE_DASHED, "Dashed"),
            new ValueLabelItem(BorderStyle.BORDER_STYLE_BEVELED, "Beveled"),
            new ValueLabelItem(BorderStyle.BORDER_STYLE_INSET, "Inset"),
            new ValueLabelItem(BorderStyle.BORDER_STYLE_UNDERLINE, "Underline")};

    private SwingController controller;
    private ResourceBundle messageBundle;

    // action instance that is being edited
    private AnnotationComponentImpl currentAnnotationComponent;

    // link action appearance properties.
    private JComboBox linkTypeBox;
    private JComboBox highlightStyleBox;
    private JComboBox lineThicknessBox;
    private JComboBox lineStyleBox;
    private JButton colorButton;

    // appearance properties to take care of.
    private int linkType;
    private String highlightStyle;
    private float lineThickness;
    private String lineStyle;
    private Color color;

    public LinkAnnotationPanel(SwingController controller) {
        super(new GridLayout(5, 2, 5, 2), true);

        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

        // Setup the basics of the panel
        setFocusable(true);
//        setBorder(new EmptyBorder(10, 5, 1, 5));

        // Add the tabbed pane to the overall panel
        createGUI();

        // Start the panel disabled until an action is clicked
        setEnabled(false);

        revalidate();
    }

    /**
     * Method that should be called when a new AnnotationComponent is selected by the user
     * The associated object will be stored locally as currentAnnotation
     * Then all of it's properties will be applied to the UI pane
     * For example if the border was red, the color of the background button will
     * be changed to red
     *
     * @param newAnnotation to set and apply to this UI
     */
    public void setAnnotationComponent(AnnotationComponentImpl newAnnotation) {

        if (newAnnotation == null || newAnnotation.getAnnotation() == null ||
                !(newAnnotation.getAnnotation() instanceof LinkAnnotation)) {
            setEnabled(false);
            return;
        }
        // assign the new action instance.
        this.currentAnnotationComponent = newAnnotation;

        // For convenience grab the Annotation object wrapped by the component
        LinkAnnotation linkAnnotation =
                (LinkAnnotation) currentAnnotationComponent.getAnnotation();

        // apply values to appears
        linkType = linkAnnotation.getLinkType();
        highlightStyle = linkAnnotation.getHighlightMode();
        lineThickness = linkAnnotation.getLineThickness();
        lineStyle = linkAnnotation.getLineStyle();
        color = linkAnnotation.getColor();

        applySelectedValue(linkTypeBox, linkType);
        applySelectedValue(highlightStyleBox, highlightStyle);
        applySelectedValue(lineThicknessBox, lineThickness);
        applySelectedValue(lineStyleBox, lineStyle);
        colorButton.setBackground(color);

        // disable appearance input if we have a invisible rectangle
        enableAppearanceInputComponents(linkAnnotation.getLinkType());
    }

    public void itemStateChanged(ItemEvent e) {
        ValueLabelItem item = (ValueLabelItem) e.getItem();
        if (e.getStateChange() == ItemEvent.SELECTED) {
            if (e.getSource() == linkTypeBox) {
                linkType = (Integer) item.getValue();
                // enable/disable fields based on types
                enableAppearanceInputComponents(linkType);
            } else if (e.getSource() == highlightStyleBox) {
                highlightStyle = (String) item.getValue();
            } else if (e.getSource() == lineThicknessBox) {
                lineThickness = (Float) item.getValue();
            } else if (e.getSource() == lineStyleBox) {
                lineStyle = (String) item.getValue();
            }
            // save the action state back to the document structure.
            updateAnnotationState();

            currentAnnotationComponent.repaint();
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == colorButton) {
            Color chosenColor =
                    JColorChooser.showDialog(colorButton,
                            messageBundle.getString("viewer.utilityPane.link.colorChooserTitle"),
                            colorButton.getBackground());
            if (chosenColor != null) {
                // change the colour of the button background
                colorButton.setBackground(chosenColor);
                color = chosenColor;

                // save the action state back to the document structure.
                updateAnnotationState();
                currentAnnotationComponent.repaint();
            }
        }
    }

    /**
     * Method to create link annotation GUI.
     */
    private void createGUI() {

        // Create and setup an Appearance panel
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.link.appearanceTitle"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));
        // link type box
        linkTypeBox = new JComboBox(LINK_TYPE_LIST);
        linkTypeBox.setSelectedIndex(DEFAULT_LINK_TYPE);
        linkTypeBox.addItemListener(this);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.link.linkType")));
        add(linkTypeBox);
        // highlight style box.
        highlightStyleBox = new JComboBox(HIGHLIGHT_STYLE_LIST);
        highlightStyleBox.setSelectedIndex(DEFAULT_HIGHLIGHT_STYLE);
        highlightStyleBox.addItemListener(this);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.link.highlightType")));
        add(highlightStyleBox);
        // line thickness
        lineThicknessBox = new JComboBox(LINE_THICKNESS_LIST);
        lineThicknessBox.setSelectedIndex(DEFAULT_LINE_THICKNESS);
        lineThicknessBox.addItemListener(this);
        add(new JLabel(messageBundle.getString(
                "viewer.utilityPane.link.lineThickness")));
        add(lineThicknessBox);
        // line style
        lineStyleBox = new JComboBox(LINE_STYLE_LIST);
        lineStyleBox.setSelectedIndex(DEFAULT_LINE_STYLE);
        lineStyleBox.addItemListener(this);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.link.lineStyle")));
        add(lineStyleBox);
        // line colour
        colorButton = new JButton();
        colorButton.addActionListener(this);
        colorButton.setOpaque(true);
        colorButton.setBackground(DEFAULT_BORDER_COLOR);
        add(new JLabel(
                messageBundle.getString("viewer.utilityPane.link.colorLabel")));
        add(colorButton);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        safeEnable(linkTypeBox, enabled);
        safeEnable(highlightStyleBox, enabled);
        safeEnable(lineThicknessBox, enabled);
        safeEnable(lineStyleBox, enabled);
        safeEnable(colorButton, enabled);
    }

    private void updateAnnotationState() {
        // store old state
        AnnotationState oldState = new AnnotationState(currentAnnotationComponent);
        // store new state from panel
        AnnotationState newState = new AnnotationState(currentAnnotationComponent);
        AnnotationState changes = new AnnotationState(
                linkType, highlightStyle, lineThickness, lineStyle, color);
        // apply new properties to the action and the component
        newState.apply(changes);

        // update thickness control as it might have changed
        lineThickness = currentAnnotationComponent.getAnnotation()
                .getLineThickness();
        applySelectedValue(lineThicknessBox, lineThickness);

        // Add our states to the undo caretaker
        ((AbstractDocumentViewModel) controller.getDocumentViewController().
                getDocumentViewModel()).getAnnotationCareTaker()
                .addState(oldState, newState);

        // Check with the controller whether we can enable the undo/redo menu items
        controller.reflectUndoCommands();
    }

    /**
     * Method to enable appearence input fields for an invisible rectangle
     *
     * @param linkType invisible rectangle or visible, your pick.
     */
    private void enableAppearanceInputComponents(int linkType) {
        if (linkType == Annotation.INVISIBLE_RECTANGLE) {
            // everything but highlight style and link type
            safeEnable(linkTypeBox, true);
            safeEnable(highlightStyleBox, true);
            safeEnable(lineThicknessBox, false);
            safeEnable(lineStyleBox, false);
            safeEnable(colorButton, false);
        } else {
            // enable all fields.
            safeEnable(linkTypeBox, true);
            safeEnable(highlightStyleBox, true);
            safeEnable(lineThicknessBox, true);
            safeEnable(lineStyleBox, true);
            safeEnable(colorButton, true);
        }
    }

    /**
     * Convenience method to ensure a component is safe to toggle the enabled state on
     *
     * @param comp    to toggle
     * @param enabled the status to use
     * @return true on success
     */
    protected boolean safeEnable(JComponent comp, boolean enabled) {
        if (comp != null) {
            comp.setEnabled(enabled);
            return true;
        }
        return false;
    }

    private void applySelectedValue(JComboBox comboBox, Object value) {
        comboBox.removeItemListener(this);
        ValueLabelItem currentItem;
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            currentItem = (ValueLabelItem) comboBox.getItemAt(i);
            if (currentItem.getValue().equals(value)) {
                comboBox.setSelectedIndex(i);
                break;
            }
        }
        comboBox.addItemListener(this);
    }

    /**
     * Class to associate with a JComboBox
     * Used to allow us to display different text to the user than we set in the backend
     */
    private class ValueLabelItem {
        private Object value;
        private String label;

        public ValueLabelItem(Object value, String label) {
            this.value = value;
            this.label = label;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String toString() {
            return label;
        }
    }
}
