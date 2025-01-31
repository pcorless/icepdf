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
package org.icepdf.ri.common.views.annotations.summary.colorpanel;

import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryBox;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryGroup;
import org.icepdf.ri.common.views.annotations.summary.MoveableComponentsPanel;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class DraggableAnnotationPanel extends MoveableComponentsPanel {

    private static final Logger logger = Logger.getLogger(DraggableAnnotationPanel.class.getName());
    private static final int DEFAULT_GAP = 8;


    private Component dirtyC;
    protected final DraggablePanelController panelController;

    public DraggableAnnotationPanel(final Frame frame, final ColorLabelPanel colorPanel, final SummaryController summaryController) {
        this(frame, DEFAULT_GAP, summaryController);
    }

    public DraggableAnnotationPanel(final Frame frame, final int vGap, final SummaryController summaryController) {
        setLayout(new ColumnLayoutManager(vGap));
        this.panelController = createController(summaryController, frame);
    }

    protected DraggablePanelController createController(final SummaryController summaryController, final Frame frame) {
        return new DraggablePanelController(this, summaryController, frame);
    }

    public Component add(final AnnotationSummaryComponent c, final int pos, final boolean selected) {
        add(c.asComponent(), pos);
        if (selected) {
            panelController.addSelected(c.asComponent());
        }
        return c.asComponent();
    }

    public boolean contains(final MarkupAnnotation annotation) {
        return Arrays.stream(getComponents())
                .filter(AnnotationSummaryComponent.class::isInstance)
                .flatMap(c -> ((AnnotationSummaryComponent) c).getAnnotations().stream())
                .anyMatch(a -> a.getPObjectReference().equals(annotation.getPObjectReference()));
    }

    @Override
    public Component add(final Component c, final int pos) {
        if (pos >= getComponentCount()) {
            panelController.setNeverDragged(false);
        }
        if (panelController.isNeverDragged()) {
            if (pos == -1) {
                super.add(c);
            } else {
                super.add(c, pos);
            }
        } else {
            if (pos != -1) {
                super.add(c);
                c.setLocation(0, pos);
                sortComponents();
                //Size is not known at this time, check for overlap during next layout
                dirtyC = c;
            } else if (getComponentCount() > 0) {
                final Component lastComp = getComponent(getComponentCount() - 1);
                super.add(c);
                c.setLocation(0, lastComp.getY() + lastComp.getHeight() + 10);
            } else {
                super.add(c);
            }
        }
        ((AnnotationSummaryComponent) c).fireComponentMoved(false, true, UUID.randomUUID());
        revalidate();
        repaint();
        return c;
    }

    public int getFirstPosForComponents(final Collection<Component> components) {
        final List<Component> sorted = getSortedList(components, true);
        return sorted == null ? -1 : getPositionFor(sorted.get(0));
    }

    public int getPositionFor(final Component c) {
        return panelController.isNeverDragged() ? Arrays.asList(getComponents()).indexOf(c) : c.getY();
    }

    protected void sortComponents() {
        if (!panelController.isNeverDragged()) {
            final Component[] sorted = getComponents();
            Arrays.sort(sorted, new ComponentBoundsCompare());
            removeAll();
            Arrays.stream(sorted).forEach(this::add);
        }
    }

    /**
     * Removes a component given an annotation
     *
     * @param annot The annotation
     */
    public void remove(final MarkupAnnotation annot) {
        final Container c = findContainingComponent(annot);
        final Component annotComp = findComponentForAnnotation(annot);
        if (c != null && annotComp != null) {
            panelController.getSummaryController().getGroupManager().removeFromGroup((AnnotationSummaryComponent) annotComp);
            c.remove(annotComp);
            c.revalidate();
            c.repaint();
        }
    }

    /**
     * Update a component given an annotation
     *
     * @param annot The annotation
     */
    public void update(final MarkupAnnotation annot) {
        final AnnotationSummaryBox box = (AnnotationSummaryBox) findComponentForAnnotation(annot);
        if (box != null) {
            box.refresh();
            //colorPanel.getSummaryPanel().refreshDocumentInstance();
        }
    }

    private Container findContainingComponent(final MarkupAnnotation annot) {
        return findContainingComponent(annot, this);
    }

    private static Container findContainingComponent(final MarkupAnnotation annot, final Container parent) {
        for (final Component c : parent.getComponents()) {
            if (c instanceof AnnotationSummaryBox) {
                if (checkSameAnnotations(annot, c)) {
                    return parent;
                }
            } else if (c instanceof AnnotationSummaryGroup) {
                final Container result = findContainingComponent(annot, (Container) c);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private Component findComponentForAnnotation(final MarkupAnnotation annot) {
        return findComponentForAnnotation(annot, this);
    }

    private static Component findComponentForAnnotation(final MarkupAnnotation annot, final Container parent) {
        for (final Component c : parent.getComponents()) {
            if (c instanceof AnnotationSummaryBox) {
                if (checkSameAnnotations(annot, c)) {
                    return c;
                }
            } else if (c instanceof AnnotationSummaryGroup) {
                final Component result = findComponentForAnnotation(annot, (Container) c);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static boolean checkSameAnnotations(final MarkupAnnotation annot, final Component c) {
        if (c instanceof AnnotationSummaryBox) {
            final MarkupAnnotation boxAnnot = ((AnnotationSummaryBox) c).getAnnotation().getParent();
            return boxAnnot.getPObjectReference().equals(annot.getPObjectReference());
        } else return false;
    }


    public void moveComponentToY(final Component c, final int y, final UUID uuid) {
        moveComponentToY(c, y, true, uuid);
    }

    public void moveComponentToY(final Component c, final int y, final boolean checkOverlap, final UUID uuid) {
        panelController.setNeverDragged(false);
        c.setLocation(c.getX(), y);
        if (checkOverlap) {
            checkForOverlap(uuid);
        }
        ((AnnotationSummaryComponent) c).fireComponentMoved(false, false, uuid);
        validate();
    }

    public void checkForOverlap(final UUID uuid) {
        final Component[] comps = getComponents();
        Arrays.sort(comps, new ComponentBoundsCompare());
        if (comps.length > 1) {
            checkForOverlap(uuid, comps, 1);
        }
    }

    private static void checkForOverlap(final UUID uuid, final Component[] components, final int index) {
        if (index < components.length) {
            final Component refComp = components[index - 1];
            final Component curComp = components[index];
            final Rectangle bounds = curComp.getBounds();
            if (refComp.getBounds().intersects(bounds) || refComp.getY() + refComp.getHeight() + 10 > curComp.getY()) {
                // over top but just below the top so we shift it back down.
                curComp.setLocation(bounds.x, refComp.getY() + refComp.getHeight() + 10);
                ((AnnotationSummaryComponent) curComp).fireComponentMoved(false, true, uuid);
            }
            checkForOverlap(uuid, components, index + 1);
        }
    }

    public void compact(final UUID uuid) {
        final Component[] comps = getComponents();
        Arrays.sort(comps, new ComponentBoundsCompare());
        for (int i = 0; i < comps.length; ++i) {
            final Component comp = comps[i];
            if (!panelController.getSummaryController().getDragAndLinkManager().isLeftLinked((AnnotationSummaryComponent) comp)) {
                int y = -1;
                for (int j = i - 1; j >= 0 && y == -1; --j) {
                    final Component prevComp = comps[j];
                    if (!panelController.getSummaryController().getDragAndLinkManager().isLeftLinked((AnnotationSummaryComponent) prevComp)) {
                        final Component nextComp = comps[j + 1];
                        if (nextComp == comp || nextComp.getY() - prevComp.getY() - prevComp.getHeight() - 20 >= comp.getHeight()) {
                            y = prevComp.getY() + prevComp.getHeight() + 10;
                        }
                    }
                }
                if (y == -1) {
                    for (int j = i - 1; j >= 0 && y == -1; --j) {
                        final Component prevComp = comps[j];
                        final Component nextComp = comps[j + 1];
                        if (nextComp == comp || nextComp.getY() - prevComp.getY() - prevComp.getHeight() - 20 >= comp.getHeight()) {
                            y = prevComp.getY() + prevComp.getHeight() + 10;
                        }
                    }
                }
                if (y == -1) {
                    y = 10;
                }
                comp.setLocation(comp.getX(), y);
                ((AnnotationSummaryComponent) comp).fireComponentMoved(false, true, uuid);
            }
        }
        validate();
    }

    public AnnotationSummaryComponent findComponentFor(final Predicate<AnnotationSummaryBox> filter) {
        return panelController.findComponentFor(filter);
    }


    public class ColumnLayoutManager implements LayoutManager2 {

        public int padding = 10;

        private final List<Component> children;

        public ColumnLayoutManager(final int padding) {
            this();
            this.padding = padding;
        }

        public ColumnLayoutManager() {
            children = new ArrayList<>(25);
        }

        @Override
        public void addLayoutComponent(final Component comp, final Object constraints) {
            children.add(comp);
        }

        @Override
        public Dimension maximumLayoutSize(final Container target) {
            int height = padding;
            int width = 0;
            int previousHeight = 0;
            final int paddingTwo = padding * 2;
            // find last/tallest width
            for (final Component comp : children) {
                width = target.getWidth() - paddingTwo;
                height = Math.max(previousHeight, comp.getLocation().y + comp.getPreferredSize().height + padding);
                previousHeight = height;
            }
            height += padding;
            width += padding;
            return new Dimension(width, height);
        }

        @Override
        public Dimension preferredLayoutSize(final Container parent) {
            return maximumLayoutSize(parent);
        }

        @Override
        public Dimension minimumLayoutSize(final Container parent) {
            return maximumLayoutSize(parent);
        }

        @Override
        public float getLayoutAlignmentX(final Container target) {
            return 0.5f;
        }

        @Override
        public float getLayoutAlignmentY(final Container target) {
            return 0.5f;
        }

        @Override
        public void invalidateLayout(final Container target) {
        }

        @Override
        public void addLayoutComponent(final String name, final Component comp) {
            addLayoutComponent(comp, null);
        }

        @Override
        public void removeLayoutComponent(final Component comp) {
            children.remove(comp);
        }

        @Override
        public void layoutContainer(final Container parent) {
            if (panelController.isNeverDragged()) {
                evenLayout(parent);
            } else {
                stickyLayout(parent);
            }
        }

        private void stickyLayout(final Container parent) {
            final Rectangle previousComp = new Rectangle();
            Rectangle currentComp;
            final int doublePadding = padding * 2;
            Dimension preferredSize;
            // sort the annotation by y coordinates.
            final Component[] comps = parent.getComponents();
            for (final Component comp : comps) {
                currentComp = comp.getBounds();
                preferredSize = comp.getPreferredSize();
                final int x = padding;
                final int y = currentComp.y;
                // size width
                if (preferredSize.width > parent.getWidth()) {
                    comp.setBounds(x, y, parent.getWidth() - doublePadding, comp.getHeight());
                } else {
                    // stretch to fill
                    comp.setBounds(x, y, parent.getWidth() - doublePadding, preferredSize.height);
                }
                previousComp.setRect(currentComp);
            }
            if (dirtyC != null) {
                checkForOverlap(UUID.randomUUID());
                dirtyC = null;
            }
        }

        private void evenLayout(final Container parent) {
            final Point previousPoint = new Point();
            final int doublePadding = padding * 2;
            Dimension preferredSize;
            for (final Component comp : parent.getComponents()) {
                final int x = previousPoint.x + padding;
                final int y = previousPoint.y + padding;
                preferredSize = comp.getPreferredSize();
                if (preferredSize.width > parent.getWidth()) {
                    comp.setBounds(x, y, parent.getWidth() - doublePadding, comp.getHeight());
                } else {
                    // stretch to fill
                    comp.setBounds(x, y, parent.getWidth() - doublePadding, preferredSize.height);
                }
                previousPoint.y = y + preferredSize.height;
            }
        }

    }

    static class ComponentBoundsCompare implements Comparator<Component>, Serializable {
        private static final long serialVersionUID = 133414022655106139L;

        @Override
        public int compare(final Component o1, final Component o2) {
            return Integer.compare(o1.getY(), o2.getY());
        }
    }
}
