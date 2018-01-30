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
package org.icepdf.ri.common.views.destinations;

import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Destination;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.util.Defs;
import org.icepdf.core.util.PropertyConstants;
import org.icepdf.ri.common.utility.annotation.destinations.NameTreeEditDialog;
import org.icepdf.ri.common.views.*;
import org.icepdf.ri.common.views.annotations.AnnotationState;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Component representing a a destination on a given page.
 *
 * @since 6.2
 */
public class DestinationComponent extends JComponent implements FocusListener, MouseInputListener, ResizeableComponent,
        ActionListener {
    protected static final Logger logger =
            Logger.getLogger(DestinationComponent.class.toString());
    protected static boolean isInteractiveAnnotationsEnabled;

    static {
        // enables interactive annotation support.
        isInteractiveAnnotationsEnabled =
                Defs.sysPropertyBoolean(
                        "org.icepdf.core.annotations.interactive.enabled", true);
    }

    private static int WIDTH = 18;
    private static int HEIGHT = 24;
    private static int OFFSET = 1;

    protected float currentZoom;
    protected float currentRotation;

    // reusable border
    public static final int resizeBoxSize = 4;
    protected static ResizableBorder resizableBorder =
            new ResizableBorder(resizeBoxSize);

    protected Destination destination;
    protected DocumentViewController documentViewController;
    protected AbstractPageViewComponent pageViewComponent;

    protected boolean isSelected;
    protected boolean isMousePressed;
    protected boolean resized;
    protected boolean wasResized;

    // drag offset
    protected int dx;
    protected int dy;

    // selection, move and resize handling.
    protected int cursor;
    protected Point startPos;
    protected AnnotationState previousAnnotationState;
    // total distance moved on mouse down/up.
    protected Point startOfMousePress;
    protected Point endOfMousePress;

    private JPopupMenu contextMenu;
    private JMenuItem deleteNameTreeNode;
    private JMenuItem editNameTreeNode;

    public DestinationComponent(Destination destination, DocumentViewController documentViewController,
                                AbstractPageViewComponent pageViewComponent) {
        this.destination = destination;
        this.documentViewController = documentViewController;
        this.pageViewComponent = pageViewComponent;

        if (destination.getNamedDestination() != null) {
            setToolTipText(destination.getNamedDestination().toString());
        }
        setFocusable(true);
        setBorder(resizableBorder);

        addMouseMotionListener(this);
        addMouseListener(this);
        addFocusListener(this);

        refreshBounds();

        // update zoom and rotation state
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        currentRotation = documentViewModel.getViewRotation();
        currentZoom = documentViewModel.getViewZoom();
        resizableBorder.setZoom(currentZoom);

        // create context menu
        ResourceBundle messageBundle = documentViewController.getParentController().getMessageBundle();

        contextMenu = new JPopupMenu();
        editNameTreeNode = new JMenuItem(messageBundle.getString(
                "viewer.utilityPane.destinations.view.contextMenu.edit.label"));
        editNameTreeNode.addActionListener(this);
        contextMenu.add(editNameTreeNode);
        contextMenu.addSeparator();
        deleteNameTreeNode = new JMenuItem(messageBundle.getString(
                "viewer.utilityPane.destinations.view.contextMenu.delete.label"));
        deleteNameTreeNode.addActionListener(this);
        contextMenu.add(deleteNameTreeNode);
    }

    public void updateDestination(Destination destination) {
        this.destination = destination;
        if (destination.getNamedDestination() != null) {
            setToolTipText(destination.getNamedDestination().toString());
        }
        refreshBounds();
    }

    public Destination getDestination() {
        return destination;
    }

    public void validate() {
        DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
        if (currentZoom != documentViewModel.getViewZoom() ||
                currentRotation != documentViewModel.getViewRotation()) {
            refreshBounds();
            currentRotation = documentViewModel.getViewRotation();
            currentZoom = documentViewModel.getViewZoom();
            resizableBorder.setZoom(currentZoom);
        }
    }

    /**
     * Refreshes/transforms the page space bounds back to user space.  This
     * must be done in order refresh the annotation user space rectangle after
     * UI manipulation, otherwise the annotation will be incorrectly located
     * on the next repaint.
     */
    public void refreshBounds() {
        if (destination.getLeft() != null && destination.getTop() != null) {
            Page currentPage = pageViewComponent.getPage();
            DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
            AffineTransform at = currentPage.getPageTransform(
                    documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(),
                    documentViewModel.getViewZoom());
            int x = destination.getLeft().intValue() - WIDTH;
            int y = destination.getTop().intValue();
            Rectangle rect = new Rectangle(x + OFFSET, y - OFFSET, WIDTH, HEIGHT);

            // store the new annotation rectangle in its original user space
            Shape shape = at.createTransformedShape(rect);
            setBounds(shape.getBounds());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        Controller controller = documentViewController.getParentController();
        if (source == editNameTreeNode) {
            NameTreeEditDialog nameTreeEditDialog = new NameTreeEditDialog(controller, destination);
            nameTreeEditDialog.setVisible(true);
        } else if (source == deleteNameTreeNode) {
            controller.getDocumentViewController().firePropertyChange(PropertyConstants.DESTINATION_DELETED,
                    destination, null);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (documentViewController.getParentController().getViewModel().isAnnotationEditingMode()) {
            isSelected = true;
        }
        repaint();
    }

    @Override
    public void focusLost(FocusEvent e) {
        isSelected = false;
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocus();
        if (documentViewController.getDocumentViewModel().getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON3) {
            contextMenu.show(e.getComponent(), e.getX(), e.getY());
        }
        repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocus();
        // setup visual effect when the mouse button is pressed or held down
        // inside the active area of the annotation.
        isMousePressed = true;
        Point point = new Point();
        if (e != null) {
            point = e.getPoint();
        }
        startOfMousePress = point;
        endOfMousePress = new Point(point);

        if (documentViewController.getDocumentViewModel().getViewToolMode() ==
                DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                documentViewController.getParentController().getViewModel().isAnnotationEditingMode()) {
            Border border = getBorder();
            if (border != null && border instanceof ResizableBorder) {
                cursor = ((ResizableBorder) border).getCursor(e);
            }
            startPos = e.getPoint();
        }
        repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        startPos = null;
        isMousePressed = false;

        if (wasResized) {
            wasResized = false;
        }
        repaint();
    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (startPos != null) {
            dx = e.getX() - startPos.x;
            dy = e.getY() - startPos.y;
            if (endOfMousePress != null) {
                endOfMousePress.setLocation(endOfMousePress.x + dx, endOfMousePress.y + dy);
            }

            Rectangle bounds = getBounds();
            bounds.translate(dx, dy);
            setBounds(bounds);
            resize();
            setCursor(Cursor.getPredefinedCursor(cursor));

            // move the destination
            Page currentPage = pageViewComponent.getPage();
            DocumentViewModel documentViewModel = documentViewController.getDocumentViewModel();
            AffineTransform af = currentPage.getToPageSpaceTransform(
                    documentViewModel.getPageBoundary(),
                    documentViewModel.getViewRotation(),
                    documentViewModel.getViewZoom());
            Point location = getLocation();
            location.setLocation(location.x + getWidth(), location.y + getHeight());
            Point2D point = af.transform(location, null);
            destination.setLocation((int) point.getX(), (int) point.getY());
            // add the change to the state manager.
            Catalog catalog = documentViewController.getParentController().getDocument().getCatalog();
            String name = destination.getNamedDestination();
            catalog.updateNamedDestination(name, name, destination);
            refreshBounds();
            repaint();
        }
    }

    protected void resize() {
        if (getParent() != null) {
            getParent().validate();
        }
        resized = true;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        int toolMode = documentViewController.getDocumentViewModel().getViewToolMode();
        if (toolMode == DocumentViewModel.DISPLAY_TOOL_SELECTION &&
                documentViewController.getParentController().getViewModel().isAnnotationEditingMode()) {
            Border border = getBorder();
            if (border instanceof ResizableBorder) {
                setCursor(Cursor.getPredefinedCursor(((ResizableBorder) border).getCursor(e)));
            }
        } else {
            // set cursor back to the hand cursor.
            setCursor(documentViewController.getViewCursor(
                    DocumentViewController.CURSOR_DEFAULT));
        }
    }

    @Override
    public boolean isEditable() {
        return true;
    }

    @Override
    public boolean isResizable() {
        return false;
    }

    @Override
    public boolean isMovable() {
        return true;
    }

    @Override
    public boolean isSelected() {
        return isSelected;
    }

    @Override
    public boolean isBorderStyle() {
        return false;
    }

    @Override
    public boolean isRollover() {
        return false;
    }

    @Override
    public boolean isShowInvisibleBorder() {
        return false;//documentViewController.getParentController().getViewModel().isAnnotationEditingMode();
    }

    public static void paintDestination(Destination dest, Graphics2D g2d) {
        // paint destination
        if (dest.getLeft() != null && dest.getTop() != null) {
            int x = dest.getLeft().intValue();
            int y = dest.getTop().intValue();
            int dim = HEIGHT;
            int xBack = x - (dim / 3);
            int yBack = y + (dim / 2);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g2d.setColor(Color.GRAY);
            g2d.drawLine(x, y, xBack, yBack);
            g2d.setColor(Color.RED);
            g2d.fillOval(xBack - 5, yBack - 5, 10, 10);
            g2d.setStroke(new BasicStroke(0.4f));
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.drawOval(xBack - 5, yBack - 5, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            g2d.fillOval(xBack - 3, yBack - 1, 4, 4);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
    }
}
