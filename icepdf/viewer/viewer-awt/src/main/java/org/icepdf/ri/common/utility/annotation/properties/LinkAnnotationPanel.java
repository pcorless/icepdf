/*
 * Copyright 2006-2018 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.utility.annotation.properties;

import org.icepdf.core.pobjects.LiteralStringObject;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.NameTree;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import static org.icepdf.ri.common.utility.annotation.properties.GoToActionDialog.EMPTY_DESTINATION;

/**
 * Link Annotation panel intended use is for the manipulation of LinkAnnotation
 * appearance properties.  This could be used with other annotation types but
 * it's not suggested.
 *
 * @since 4.0
 */
@SuppressWarnings("serial")
public class LinkAnnotationPanel extends AnnotationPanelAdapter implements ItemListener, ActionListener {

    // default list values.
    private static final int DEFAULT_HIGHLIGHT_STYLE = 1;

    // link action appearance properties.
    private JComboBox<ValueLabelItem> highlightStyleBox;

    // named destination fields.
    private JLabel destinationName;
    private JButton viewNamedDesButton;
    private NameTreeDialog nameTreeDialog;

    // appearance properties to take care of.
    private Name highlightStyle;

    private LinkAnnotation linkAnnotation;

    public LinkAnnotationPanel(Controller controller) {
        super(controller);
        setLayout(new GridBagLayout());

        // Setup the basics of the panel
        setFocusable(true);

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
    public void setAnnotationComponent(AnnotationComponent newAnnotation) {

        if (newAnnotation == null || newAnnotation.getAnnotation() == null ||
                !(newAnnotation.getAnnotation() instanceof LinkAnnotation)) {
            setEnabled(false);
            return;
        }
        // assign the new action instance.
        this.currentAnnotationComponent = newAnnotation;

        // For convenience grab the Annotation object wrapped by the component
        linkAnnotation =
                (LinkAnnotation) currentAnnotationComponent.getAnnotation();

        // apply values to appears
        highlightStyle = linkAnnotation.getHighlightMode();
        applySelectedValue(highlightStyleBox, highlightStyle);

        // check for  destination key
        Object dest = linkAnnotation.getEntries().get(LinkAnnotation.DESTINATION_KEY);
        if (dest != null && dest instanceof LiteralStringObject) {
            destinationName.setText(((LiteralStringObject) dest).getDecryptedLiteralString(
                    controller.getDocument().getSecurityManager()));
        }

        // disable appearance input if we have a invisible rectangle
        enableAppearanceInputComponents(linkAnnotation.getBorderType());
    }

    public void itemStateChanged(ItemEvent e) {
        ValueLabelItem item = (ValueLabelItem) e.getItem();
        if (e.getStateChange() == ItemEvent.SELECTED) {
            if (e.getSource() == highlightStyleBox) {
                highlightStyle = (Name) item.getValue();
                linkAnnotation.setHighlightMode(highlightStyle);
            }
            // save the action state back to the document structure.
            updateCurrentAnnotation();
            currentAnnotationComponent.repaint();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == viewNamedDesButton) {
            // test implementation of a NameJTree for destinations.
            NameTree nameTree = controller.getDocument().getCatalog().getNames().getDestsNameTree();
            if (nameTree != null) {
                // create new dialog instance.
                nameTreeDialog = new NameTreeDialog(
                        controller,
                        true, nameTree);
                nameTreeDialog.setDestinationName(destinationName.getText());
                // add the nameTree instance.
                nameTreeDialog.setVisible(true);

                // apply the new names
                linkAnnotation.setNamedDestination(nameTreeDialog.getDestinationName());
                updateCurrentAnnotation();

                nameTreeDialog.dispose();
            }
        }
    }

    /**
     * Method to create link annotation GUI.
     */
    private void createGUI() {

        // highlight styles.
        ValueLabelItem[] highlightStyleList = new ValueLabelItem[]{
                new ValueLabelItem(LinkAnnotation.HIGHLIGHT_NONE,
                        messageBundle.getString("viewer.utilityPane.annotation.link.none")),
                new ValueLabelItem(LinkAnnotation.HIGHLIGHT_INVERT,
                        messageBundle.getString("viewer.utilityPane.annotation.link.invert")),
                new ValueLabelItem(LinkAnnotation.HIGHLIGHT_OUTLINE,
                        messageBundle.getString("viewer.utilityPane.annotation.link.outline")),
                new ValueLabelItem(LinkAnnotation.HIGHLIGHT_PUSH,
                        messageBundle.getString("viewer.utilityPane.annotation.link.push"))};

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(1, 2, 1, 2);

        // Create and setup an Appearance panel
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.annotation.link.appearance.title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        // highlight style box.
        highlightStyleBox = new JComboBox<>(highlightStyleList);
        highlightStyleBox.setSelectedIndex(DEFAULT_HIGHLIGHT_STYLE);
        highlightStyleBox.addItemListener(this);
        addGB(this, new JLabel(messageBundle.getString("viewer.utilityPane.annotation.link.highlightType")),
                0, 0, 1, 1);
        addGB(this, highlightStyleBox,
                1, 0, 2, 1);
        // destination link
        addGB(this, new JLabel(messageBundle.getString("viewer.utilityPane.annotation.link.destination")),
                0, 1, 1, 1);
        destinationName = new JLabel(EMPTY_DESTINATION);
        addGB(this, destinationName, 1, 1, 1, 1);
        // browse button to show named destination tree.
        viewNamedDesButton = new JButton(messageBundle.getString("viewer.utilityPane.action.dialog.goto.browse"));
        viewNamedDesButton.addActionListener(this);
        addGB(this, viewNamedDesButton, 2, 1, 1, 1);

        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 2, 1, 1);

    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        safeEnable(highlightStyleBox, enabled);
    }

    /**
     * Method to enable appearance input fields for an invisible rectangle
     *
     * @param linkType invisible rectangle or visible, your pick.
     */
    private void enableAppearanceInputComponents(int linkType) {
        if (linkType == Annotation.INVISIBLE_RECTANGLE) {
            // everything but highlight style and link type
            safeEnable(highlightStyleBox, true);
        } else {
            // enable all fields.
            safeEnable(highlightStyleBox, true);
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
}
