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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.metal.MetalComboBoxIcon;
import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Custom drop down button wrapper based on the "Swing Hacks" tips & tools for Building Killer GUI's,
 * Joshua Marinacci & Chris Adamson.
 *
 * @since 6.3
 */
public class AnnotationColorButton extends AbstractButton
        implements ActionListener, AncestorListener {

    private static final Logger logger = Logger.getLogger(AnnotationColorButton.class.toString());

    protected Name annotationSubType;
    protected AnnotationColorPropertyPanel annotationColorPropertyPanel;
    protected ColorToggleButton toggleComponent;
    protected JButton dropDownArrowButton;
    protected JWindow popup;

    protected SwingController swingController;

    public AnnotationColorButton(SwingController swingController,
                                 ResourceBundle messageBundle,
                                 Name annotationSubType,
                                 String title, String toolTip, String imageName,
                                 final String imageSize, java.awt.Font font) {
        super();
        this.swingController = swingController;
        this.annotationSubType = annotationSubType;

        toggleComponent = new ColorToggleButton();
        toggleComponent.setColorBound(new Rectangle(5, 9, 12, 13));
        toggleComponent.setFont(font);
        toggleComponent.setToolTipText(toolTip);
        toggleComponent.setPreferredSize(new Dimension(32, 32));
        toggleComponent.setRolloverEnabled(true);

        try {
            toggleComponent.setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
            toggleComponent.setPressedIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
            toggleComponent.setRolloverIcon(new ImageIcon(Images.get(imageName + "_r" + imageSize + ".png")));
            toggleComponent.setDisabledIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
        } catch (NullPointerException e) {
            logger.warning("Failed to load toolbar toggle drop down button images: " + imageName + "_i" + imageSize + ".png");
        }
        toggleComponent.setBorder(BorderFactory.createEmptyBorder());
        toggleComponent.setContentAreaFilled(false);
        toggleComponent.setFocusPainted(true);
        // apply the settings colour
        Color color = null;
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        if (TextMarkupAnnotation.SUBTYPE_HIGHLIGHT.equals(annotationSubType) &&
                preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_COLOR, 0);
            color = new Color(rgb);
        } else if (TextMarkupAnnotation.SUBTYPE_STRIKE_OUT.equals(annotationSubType) &&
                preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_STRIKE_OUT_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_STRIKE_OUT_COLOR, 0);
            color = new Color(rgb);
        } else if (TextMarkupAnnotation.SUBTYPE_UNDERLINE.equals(annotationSubType) &&
                preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_UNDERLINE_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_UNDERLINE_COLOR, 0);
            color = new Color(rgb);
        } else if (TextMarkupAnnotation.SUBTYPE_SQUIGGLY.equals(annotationSubType) &&
                preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_SQUIGGLY_COLOR, -1) != -1) {
            int rgb = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_SQUIGGLY_COLOR, 0);
            color = new Color(rgb);
        }
        // apply the settings or system property base colour for the given subtype.
        if (color != null) {
            toggleComponent.setColor(color);
        }

        dropDownArrowButton = new JButton(new MetalComboBoxIcon());
        dropDownArrowButton.setBorder(BorderFactory.createEmptyBorder());
        dropDownArrowButton.setContentAreaFilled(false);
        dropDownArrowButton.setRolloverEnabled(false);
        dropDownArrowButton.setFocusPainted(false);

        // assign the drop down window and setup a properties change event.
        this.annotationColorPropertyPanel =
                new AnnotationColorPropertyPanel(swingController, messageBundle, annotationSubType);
        this.annotationColorPropertyPanel.setCallback(this);

        Insets insets = dropDownArrowButton.getMargin();
        dropDownArrowButton.setMargin(new Insets(insets.top, 0, insets.bottom, 0));
        setupLayout();

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
        gbl.setConstraints(toggleComponent, c);
        add(toggleComponent);

        c.weightx = 0;
        c.gridx++;
        gbl.setConstraints(dropDownArrowButton, c);
        add(dropDownArrowButton);
    }

    public void setColor(Color newColor) {
        toggleComponent.setColor(newColor);
        toggleComponent.repaint();
        Preferences preferences = PropertiesManager.getInstance().getPreferences();
        if (annotationSubType.equals(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT)) {
            preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_HIGHLIGHT_COLOR, newColor.getRGB());
        } else if (annotationSubType.equals(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT)) {
            preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_STRIKE_OUT_COLOR, newColor.getRGB());
        } else if (annotationSubType.equals(TextMarkupAnnotation.SUBTYPE_UNDERLINE)) {
            preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_UNDERLINE_COLOR, newColor.getRGB());
        } else if (annotationSubType.equals(TextMarkupAnnotation.SUBTYPE_SQUIGGLY)) {
            preferences.putInt(PropertiesManager.PROPERTY_ANNOTATION_SQUIGGLY_COLOR, newColor.getRGB());
        }
        // TODO add other annotation button types.
        popup.setVisible(false);
    }

    public void actionPerformed(ActionEvent evt) {
        // set the button as selected
        toggleComponent.setSelected(true);

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
        Point pt = toggleComponent.getLocationOnScreen();
        pt.translate(toggleComponent.getWidth() - popup.getWidth(), toggleComponent.getHeight());
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
        if (toggleComponent != null) toggleComponent.setEnabled(enabled);
        if (dropDownArrowButton != null) dropDownArrowButton.setEnabled(enabled);
    }

    @Override
    public boolean isSelected() {
        return toggleComponent.isSelected();
    }

    @Override
    public void setSelected(boolean b) {
        toggleComponent.setSelected(b);
    }

    @Override
    public ButtonModel getModel() {
        return toggleComponent.getModel();
    }

    @Override
    public void addItemListener(ItemListener l) {
        toggleComponent.addItemListener(l);
    }

    @Override
    public boolean equals(Object obj) {
        return toggleComponent.equals(obj);
    }
}