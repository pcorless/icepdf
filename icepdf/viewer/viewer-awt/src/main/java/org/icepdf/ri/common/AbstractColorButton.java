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
package org.icepdf.ri.common;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.metal.MetalComboBoxIcon;
import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Custom drop down button base which is based on the "Swing Hacks" tips & tools for Building Killer GUI's,
 * Joshua Marinacci & Chris Adamson.
 *
 * @since 6.3
 */
public abstract class AbstractColorButton extends AbstractButton
        implements ActionListener, AncestorListener {

    private static final Logger logger = Logger.getLogger(AbstractColorButton.class.toString());

    protected AnnotationColorPropertyPanel annotationColorPropertyPanel;
    protected AbstractButton colorButton;
    protected JButton dropDownArrowButton;
    protected JWindow popup;

    protected SwingController swingController;

    public AbstractColorButton(SwingController swingController,
                               ResourceBundle messageBundle) {
        super();
        this.swingController = swingController;

        dropDownArrowButton = new JButton(new MetalComboBoxIcon());
        dropDownArrowButton.setBorder(BorderFactory.createEmptyBorder());
        dropDownArrowButton.setContentAreaFilled(false);
        dropDownArrowButton.setRolloverEnabled(false);
        dropDownArrowButton.setFocusPainted(false);

        // assign the drop down window and setup a properties change event.
        this.annotationColorPropertyPanel =
                new AnnotationColorPropertyPanel(swingController, messageBundle);
        this.annotationColorPropertyPanel.setCallback(this);

        Insets insets = dropDownArrowButton.getMargin();
        dropDownArrowButton.setMargin(new Insets(insets.top, 0, insets.bottom, 0));

        setPreferredSize(new Dimension(48, 32));

        dropDownArrowButton.addActionListener(this);
        addAncestorListener(this);
    }

    protected void setupLayout() {
        GridBagLayout gbl = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        setLayout(gbl);

        c.weightx = 0.0;
        c.weighty = 0.0;
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        gbl.setConstraints(colorButton, c);
        add(colorButton);

        c.weightx = 0;
        c.gridx++;
        gbl.setConstraints(dropDownArrowButton, c);
        add(dropDownArrowButton);
    }

    public abstract void setColor(Color newColor, boolean fireChangeEvent);

    public void refreshColorPanel() {
        annotationColorPropertyPanel.refreshColorPanel();
    }

    public void actionPerformed(ActionEvent evt) {
        // set the button as selected
        colorButton.setSelected(true);

        // build popup window
        popup = new JWindow(getFrame(null));
        popup.getContentPane().add(annotationColorPropertyPanel);
        popup.addWindowFocusListener(new WindowAdapter() {
            public void windowLostFocus(WindowEvent evt) {
                popup.setVisible(false);
            }
        });
        popup.pack();

        // show the popup window
        Point pt = colorButton.getLocationOnScreen();
        pt.translate(colorButton.getWidth() - popup.getWidth(), colorButton.getHeight());
        popup.setLocation(pt);
        popup.toFront();
        popup.setVisible(true);
        popup.requestFocusInWindow();
    }

    protected Frame getFrame(Component comp) {
        if (comp == null) {
            comp = this;
        }
        if (comp.getParent() instanceof Frame) {
            return (Frame) comp.getParent();
        }
        return getFrame(comp.getParent());
    }

    public void ancestorAdded(AncestorEvent event) {
        hidePopup();
    }

    public void ancestorRemoved(AncestorEvent event) {
        hidePopup();
    }

    public void ancestorMoved(AncestorEvent event) {
        if (event.getSource() != popup) {
            hidePopup();
        }
    }

    public void hidePopup() {
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
        }
    }

    public void setEnabled(boolean enabled) {
        if (annotationColorPropertyPanel != null) annotationColorPropertyPanel.setEnabled(enabled);
        if (colorButton != null) colorButton.setEnabled(enabled);
        if (dropDownArrowButton != null) dropDownArrowButton.setEnabled(enabled);
    }

    @Override
    public boolean isSelected() {
        return colorButton.isSelected();
    }

    @Override
    public void setSelected(boolean b) {
        colorButton.setSelected(b);
    }

    @Override
    public ButtonModel getModel() {
        return colorButton.getModel();
    }

    @Override
    public void addItemListener(ItemListener l) {
        colorButton.addItemListener(l);
    }

    @Override
    public boolean equals(Object obj) {
        return obj.equals(colorButton) || super.equals(obj);
    }

}