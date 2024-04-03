/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
import org.icepdf.ri.images.IconPack;
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

    protected final Controller controller;

    protected final JPopupMenu popupMenu;

    protected final String imageName;
    protected final Images.IconSize imageSize;

    public DropDownButton(Controller controller,
                          String title, String toolTip, String imageName,
                          final Images.IconSize imageSize, java.awt.Font font) {
        super(title);
        this.controller = controller;

        setFont(font);
        setToolTipText(toolTip);
        setRolloverEnabled(true);
        this.imageName = imageName;
        this.imageSize = imageSize;

        if (imageName != null) {
            Images.applyIcons (this, imageName, imageSize);
        }

        popupMenu = new JPopupMenu();

        addMouseListener(this);

    }

    @Override
    public void setEnabled(boolean b) {
        super.setEnabled(b);
        popupMenu.setEnabled(b);
    }

    @Override
    public void setSelected(boolean b) {
        super.setSelected(b);
        Images.applyIcon (this, imageName, IconPack.Variant.SELECTED, imageSize);
    }

    public void add(JMenuItem menuItem, int idx) {
        popupMenu.add(menuItem, idx);
    }

    public void add(JMenuItem menuItem) {
        popupMenu.add(menuItem);
    }

    public void addSeparator() {
        popupMenu.addSeparator();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && isEnabled()) {
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
