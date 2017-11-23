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

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.AnnotationSelector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class DraggableAnnotationPanel extends JPanel {

    private static final int DEFAULT_GAP = 8;

    private boolean firstLoad = true;

    private Component dragComponent;

    private boolean isDragging;

    protected Frame frame;

    public DraggableAnnotationPanel(Frame frame, int layout, int hGap, int vHap) {
        setLayout(new ColumnLayoutManager(vHap));

        MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public DraggableAnnotationPanel(Frame frame) {
        this(frame, FlowLayout.CENTER, DEFAULT_GAP, DEFAULT_GAP);
    }

    public class MouseHandler extends MouseAdapter {

        private Point dragOffset;

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON3) {
                Component comp = getComponentAt(e.getPoint());
                if (comp != null && comp instanceof AnnotationSummaryBox) {
                    // todo rethink for null components.
                    AnnotationSummaryBox annotationSummaryBox = (AnnotationSummaryBox) comp;
                    JPopupMenu contextMenu = annotationSummaryBox.getContextMenu(frame);
                    contextMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            if (e.getButton() == MouseEvent.BUTTON1 &&
                    e.getClickCount() == 2) {
                Component comp = getComponentAt(e.getPoint());
                if (comp != null && comp instanceof AnnotationSummaryBox) {
                    AnnotationSummaryBox annotationSummaryBox = (AnnotationSummaryBox) comp;
                    Annotation annotation = annotationSummaryBox.getAnnotation().getParent();
                    AnnotationSelector.SelectAnnotationComponent(annotationSummaryBox.getController(), annotation);
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            Component comp = getComponentAt(e.getPoint());
            if (comp != null && comp instanceof AnnotationSummaryBox) {
                dragComponent = comp;
                dragComponent.requestFocus();
                // bring the component to the front.
                setComponentZOrder(dragComponent, 0);
                // move to comp
                dragOffset = new Point();
                dragOffset.x = e.getPoint().x - comp.getX();
                dragOffset.y = e.getPoint().y - comp.getY();
                repaint();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (isDragging) moveComponent(dragComponent);
            isDragging = false;
        }

        protected void moveComponent(Component dragComponent) {
            if (dragComponent != null) {
                int padding = 10;
                Component[] comps = getComponents();
                Arrays.sort(comps, new ComponentBoundsCompare());
                int draggedIndex = findComponentAt(comps, dragComponent);
                Rectangle dragBounds = dragComponent.getBounds();
                Component comp;
                // adjust for any overlap
                for (int i = 0; i < comps.length; i++) {
                    comp = comps[i];
                    if (i == draggedIndex) {
                        continue;
                    }
                    if (comp.getBounds().intersects(dragBounds)) {
                        // over top but just below the top so we shift it back down.
                        if (comp.getY() < dragBounds.y) {
                            dragComponent.setLocation(dragBounds.x, comp.getY() + comp.getHeight() + padding);
                            moveComponent(dragComponent);
                        } else {
                            comp.setLocation(dragBounds.x, dragComponent.getY() + dragComponent.getHeight() + padding);
                            moveComponent(comp);
                        }
                    }
                }

                if (draggedIndex == 0) {
                    // make sure the component y > padding
                    if (dragComponent.getY() < padding) {
                        dragComponent.setLocation(dragComponent.getX(), padding);
                        moveComponent(dragComponent);
                    }
                }
                if (draggedIndex >= 1) {
                    comp = comps[draggedIndex - 1];
                    int offset = dragComponent.getY() - (comp.getY() + comp.getHeight());
                    if (offset < padding) {
                        dragComponent.setLocation(dragComponent.getX(), dragComponent.getY() + (padding - offset));
                        moveComponent(dragComponent);
                    }
                }

                revalidate();
            }
        }


        private int findComponentAt(Component[] comps, Component dragComponent) {
            for (int i = 0; i < comps.length; i++) {
                if (comps[i].equals(dragComponent)) {
                    return i;
                }
            }
            return -1;
        }


        @Override
        public void mouseDragged(MouseEvent e) {
            isDragging = true;
            if (dragComponent != null) {
                firstLoad = false;
                Point dragPoint = new Point();
                dragPoint.x = e.getPoint().x - dragOffset.x;
                dragPoint.y = e.getPoint().y - dragOffset.y;
                dragComponent.setLocation(dragPoint);
                revalidate();
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

        public int padding = 10;

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
            int width = 0;
            int previousHeight = 0;
            int paddingTwo = padding * 2;
            // find last/tallest width
            for (Component comp : children) {
                width = target.getWidth() - paddingTwo;
                height = Math.max(previousHeight, comp.getLocation().y + comp.getHeight() + padding);
                previousHeight = height;
            }
            height += padding;
            width += padding;
            return new Dimension(width, height);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return maximumLayoutSize(parent);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return maximumLayoutSize(parent);
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
        public void layoutContainer(Container parent) {
            if (firstLoad) {
                evenLayout(parent);
            } else {
                stickyLayout(parent);
            }
        }

        private void stickyLayout(Container parent) {
            Rectangle previousComp = new Rectangle();
            Rectangle currentComp;
            int doublePadding = padding * 2;
            Dimension preferredSize;
            // sort the annotation by y coordinates.
            Component[] comps = parent.getComponents();
            for (Component comp : comps) {
                currentComp = comp.getBounds();
                preferredSize = comp.getSize();
                int x = padding;
                int y = currentComp.y;
                // size width
                if (preferredSize.width > parent.getWidth()) {
                    comp.setBounds(x, y, comp.getWidth(), comp.getHeight());
                } else {
                    // stretch to fill
                    comp.setBounds(x, y, parent.getWidth() - doublePadding, preferredSize.height);
                }
                previousComp.setRect(currentComp);
            }

        }

        private void evenLayout(Container parent) {
            Point previousPoint = new Point();
            int doublePadding = padding * 2;
            Dimension preferredSize;
            for (Component comp : parent.getComponents()) {
                int x = previousPoint.x + padding;
                int y = previousPoint.y + padding;
                preferredSize = comp.getPreferredSize();
                if (preferredSize.width > parent.getWidth()) {
                    comp.setBounds(x, y, comp.getWidth(), comp.getHeight());
                } else {
                    // stretch to fill
                    comp.setBounds(x, y, parent.getWidth() - doublePadding, preferredSize.height);
                }
                previousPoint.y = y + preferredSize.height;
            }
        }
    }

    class ComponentBoundsCompare implements Comparator<Component> {
        @Override
        public int compare(Component o1, Component o2) {
            return Integer.compare(o1.getY(), o2.getY());
        }
    }
}
