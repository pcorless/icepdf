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

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.logging.Logger;

/**
 * Custom drop down button allows for the easy addition of JMenuItem classes.
 *
 * @since 6.3
 */
public class DropDownButton extends JButton
        implements MouseListener {

    private static final Logger logger = Logger.getLogger(DropDownButton.class.toString());


    protected Controller controller;

    protected JPopupMenu popupMenu;

    protected String imageName;
    protected String imageSize;

    public DropDownButton(Controller controller,
                          String title, String toolTip, String imageName,
                          final String imageSize, java.awt.Font font) {
        super(title);
        this.controller = controller;

        setFont(font);
        setToolTipText(toolTip);
        setRolloverEnabled(true);
        this.imageName = imageName;
        this.imageSize = imageSize;

        if (imageName != null) {
            try {
                ImageIcon image = new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png"));
                setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
                setPressedIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
                setRolloverIcon(new ImageIcon(Images.get(imageName + "_r" + imageSize + ".png")));
                setDisabledIcon(new ImageIcon(Images.get(imageName + "_i" + imageSize + ".png")));
                // set size based on imageIcon size.
                setPreferredSize(new Dimension(image.getIconWidth(), image.getIconHeight()));
            } catch (NullPointerException e) {
                logger.warning("Failed to load dropdown image button images: " + imageName + "_i" + imageSize + ".png");
            }
        }

        popupMenu = new JPopupMenu();

        addMouseListener(this);

    }

    @Override
    public void setSelected(boolean b) {
        super.setSelected(b);
        try {
            if (b) {
                setIcon(new ImageIcon(Images.get(imageName + "_selected_a" + imageSize + ".png")));
            } else {
                setIcon(new ImageIcon(Images.get(imageName + "_a" + imageSize + ".png")));
            }
        } catch (Exception e) {
            logger.warning("Could not load icon" + imageName + "_a" + imageSize + ".png");
        }
    }

    public void add(JMenuItem menuItem) {
        popupMenu.add(menuItem);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            popupMenu.show(e.getComponent(), 5, getHeight() - 5);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    //    @Override
//    public boolean isSelected() {
//        return dropdownButton.isSelected();
//    }
//
//    @Override
//    public void setSelected(boolean b) {
//        dropdownButton.setSelected(b);
//    }
//
//    @Override
//    public ButtonModel getModel() {
//        return dropdownButton.getModel();
//    }
//
//    @Override
//    public void addItemListener(ItemListener l) {
//        dropdownButton.addItemListener(l);
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        return dropdownButton.equals(obj);
//    }
}
