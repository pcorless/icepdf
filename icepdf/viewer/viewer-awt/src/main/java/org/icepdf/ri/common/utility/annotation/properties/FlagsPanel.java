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

package org.icepdf.ri.common.utility.annotation.properties;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * The flags panel allows a user to set common properties of an annotation like
 * readonly, no rotate, no zoom and printable.  This panel can be removed from
 * the viewer ri with the
 * PropertiesManager.PROPERTY_SHOW_UTILITYPANE_ANNOTATION_FLAGS=false.
 *
 * @since 5.0.4
 */
@SuppressWarnings("serial")
public class FlagsPanel extends AnnotationPanelAdapter implements ItemListener {

    private JComboBox<ValueLabelItem> readOnlyComboBox;
    private JComboBox<ValueLabelItem> noRotateComboBox;
    private JComboBox<ValueLabelItem> noZoomComboBox;
    private JComboBox<ValueLabelItem> printableComboBox;

    public FlagsPanel(Controller controller) {
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

    public void setAnnotationComponent(AnnotationComponent annotationComponent) {
        if (annotationComponent == null || annotationComponent.getAnnotation() == null) {
            setEnabled(false);
            return;
        }
        // assign the new action instance.
        // assign the new action instance.
        this.currentAnnotationComponent = annotationComponent;

        // For convenience grab the Annotation object wrapped by the component
        Annotation annotation = currentAnnotationComponent.getAnnotation();

        // apply flag state to swing components.
        noRotateComboBox.setSelectedIndex(annotation.getFlagNoRotate() ? 0 : 1);
        noZoomComboBox.setSelectedIndex(annotation.getFlagNoZoom() ? 0 : 1);
        readOnlyComboBox.setSelectedIndex(annotation.getFlagReadOnly() ? 0 : 1);
        printableComboBox.setSelectedIndex(annotation.getFlagPrint() ? 0 : 1);

    }

    public void itemStateChanged(ItemEvent e) {
        Object source = e.getItemSelectable();
        if (source == noRotateComboBox) {
            currentAnnotationComponent.getAnnotation().setFlag(Annotation.FLAG_NO_ROTATE,
                    noRotateComboBox.getSelectedIndex() == 0);
        } else if (source == noZoomComboBox) {
            currentAnnotationComponent.getAnnotation().setFlag(Annotation.FLAG_NO_ZOOM,
                    noZoomComboBox.getSelectedIndex() == 0);
        } else if (source == readOnlyComboBox) {
            currentAnnotationComponent.getAnnotation().setFlag(Annotation.FLAG_READ_ONLY,
                    readOnlyComboBox.getSelectedIndex() == 0);
        } else if (source == printableComboBox) {
            currentAnnotationComponent.getAnnotation().setFlag(Annotation.FLAG_PRINT,
                    printableComboBox.getSelectedIndex() == 0);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        noRotateComboBox.setEnabled(enabled);
        noZoomComboBox.setEnabled(enabled);
        // leaving this always enabled just so users can change it back in editor mode.
        // does this make sense?  not sure could argue either way.
        readOnlyComboBox.setEnabled(true);
        printableComboBox.setEnabled(enabled);
    }

    /**
     * Method to create link annotation GUI.
     */
    private void createGUI() {
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.annotation.flags.title"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(1, 2, 1, 2);

        // build out the yes no list.
        ValueLabelItem[] flagList = new ValueLabelItem[]{
                new ValueLabelItem(Boolean.TRUE,
                        messageBundle.getString("viewer.utilityPane.annotation.flags.enabled")),
                new ValueLabelItem(Boolean.FALSE,
                        messageBundle.getString("viewer.utilityPane.annotation.flags.disabled"))};

        // no rotate
        noRotateComboBox = new JComboBox<>(flagList);
        noRotateComboBox.addItemListener(this);
        addGB(this, new JLabel(messageBundle.getString("viewer.utilityPane.annotation.flags.noRotate")),
                0, 0, 1, 1);
        addGB(this, noRotateComboBox, 1, 0, 1, 1);
        // no zoom
        noZoomComboBox = new JComboBox<>(flagList);
        noZoomComboBox.addItemListener(this);
        addGB(this, new JLabel(messageBundle.getString("viewer.utilityPane.annotation.flags.noZoom")),
                0, 1, 1, 1);
        addGB(this, noZoomComboBox, 1, 1, 1, 1);
        // read only
        readOnlyComboBox = new JComboBox<>(flagList);
        readOnlyComboBox.addItemListener(this);
        addGB(this, new JLabel(messageBundle.getString("viewer.utilityPane.annotation.flags.readOnly")),
                0, 2, 1, 1);
        addGB(this, readOnlyComboBox, 1, 2, 1, 1);
        // read only
        printableComboBox = new JComboBox<>(flagList);
        printableComboBox.addItemListener(this);
        addGB(this, new JLabel(messageBundle.getString("viewer.utilityPane.annotation.flags.printable")),
                0, 3, 1, 1);
        addGB(this, printableComboBox, 1, 3, 1, 1);

        // little spacer
        constraints.weighty = 1.0;
        addGB(this, new Label(" "), 0, 4, 0, 1);
    }
}
