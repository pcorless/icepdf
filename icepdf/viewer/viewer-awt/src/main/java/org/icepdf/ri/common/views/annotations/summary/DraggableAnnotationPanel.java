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
package org.icepdf.ri.common.views.annotations.summary;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class DraggableAnnotationPanel extends JPanel {

    private static final int DEFAULT_GAP = 10;


    public DraggableAnnotationPanel(int layout, int hGap, int vHap) {
        setLayout(new ColumnLayoutManager(vHap));

        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public DraggableAnnotationPanel(int vgap) {
        setLayout(new FlowLayout(vgap));
        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }


    public DraggableAnnotationPanel() {
        this(FlowLayout.CENTER, DEFAULT_GAP, DEFAULT_GAP);
    }

    public class MouseHandler extends MouseAdapter {

        private Component dragComponent;
        private int lastMovedCompIndex = -1;
        private Point dragOffset;

        @Override
        public void mousePressed(MouseEvent e) {
            Component comp = getComponentAt(e.getPoint());
            if (comp != null && comp instanceof AnnotationSummaryBox) {
                dragComponent = comp;
                dragComponent.getLocation();
                lastMovedCompIndex = getComponentIndex(dragComponent);
                dragOffset = new Point();
                dragOffset.x = e.getPoint().x - comp.getX();
                dragOffset.y = e.getPoint().y - comp.getY();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragComponent != null) {
                dragComponent = null;
                doLayout();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragComponent != null) {
                Component[] comps = getComponents();
                Component comp;
                for (int i = 0; i < getComponentCount(); i++) {
                    comp = comps[i];
                    if (!comp.equals(dragComponent) && !comp.equals(this) &&
                            comp.getBounds().contains(e.getPoint())) {
                        // shift comps left or right,  depends on the drag
                        if (i < lastMovedCompIndex) {
                            // shift all the components to the right.
                            for (int j = lastMovedCompIndex - 1; j == i; j--) {
                                Component tmp = getComponent(j);
                                remove(j);
                                add(tmp, j + 1);
                            }

                        } else {
                            // shift all the components to the left
                            for (int j = lastMovedCompIndex + 1; j <= i; j++) {
                                Component tmp = getComponent(j);
                                remove(j);
                                add(tmp, j - 1);
                            }
                        }
                        // place the dragged component
                        add(dragComponent, i);
                        lastMovedCompIndex = i;
                        revalidate();
                        break;
                    }
                }

                Point dragPoint = new Point();
                dragPoint.x = e.getPoint().x - dragOffset.x;
                dragPoint.y = e.getPoint().y - dragOffset.y;
                dragComponent.setLocation(dragPoint);
                repaint();
            }
        }

        private int getComponentIndex(Component component) {
            for (int i = 0; i < getComponentCount(); i++) {
                if (component.equals(getComponent(i))) {
                    return i;
                }
            }
            return -1;
        }
    }

    public class ColumnLayoutManager implements LayoutManager2 {

        private int padding = 10;

        private ArrayList<Component> children;

        public ColumnLayoutManager(int padding) {
            this();
            this.padding = padding;

        }

        public ColumnLayoutManager() {
            children = new ArrayList<>(25);
        }

        @Override
        public void addLayoutComponent(Component comp, Object constraints) {
            children.add(comp);
        }

        @Override
        public Dimension maximumLayoutSize(Container target) {
            int height = padding;
            for (Component comp : children) {
                height += comp.getHeight() + padding;
            }
            height += padding;
            return new Dimension(target.getWidth(), height);
        }

        @Override
        public float getLayoutAlignmentX(Container target) {
            return 0.5f;
        }

        @Override
        public float getLayoutAlignmentY(Container target) {
            return 0.5f;
        }

        @Override
        public void invalidateLayout(Container target) {
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            children.remove(comp);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            int height = padding;
            int width = padding * 2;
            for (Component comp : children) {
                height += comp.getHeight() + padding;
                width = Math.max(width, comp.getWidth());
            }
            height += padding;
            return new Dimension(width, height);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            int height = padding;
            for (Component comp : children) {
                height += comp.getHeight() + padding;
            }
            height += padding;
            return new Dimension(parent.getWidth(), height);
        }

        @Override
        public void layoutContainer(Container parent) {
//            Point offset = ((Chess.Board) parent).getBoardOffset();
            Point previousPoint = new Point();
            for (Component comp : parent.getComponents()) {
                int x = previousPoint.x + padding;
                int y = previousPoint.y + padding;
                comp.setBounds(x, y, comp.getWidth(), comp.getHeight());
                previousPoint.y = y + comp.getHeight();
            }
        }
    }
}
