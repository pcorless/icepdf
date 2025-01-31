package org.icepdf.ri.common.views.annotations.summary.colorpanel;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.PageComponentSelector;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryBox;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryGroup;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;
import org.icepdf.ri.common.views.annotations.summary.menu.BoxMenuFactory;
import org.icepdf.ri.common.views.annotations.summary.menu.GroupMenuFactory;
import org.icepdf.ri.common.views.annotations.summary.menu.MultiSelectedPopupMenu;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DraggablePanelController {
    private Component dragComponent;
    private boolean isDragging;

    protected final DraggableAnnotationPanel panel;
    private final Set<Component> selectedComponents;
    private final Frame frame;

    private boolean neverDragged = true;

    private final SummaryController summaryController;

    private static final Logger log = Logger.getLogger(DraggablePanelController.class.getName());

    public DraggablePanelController(final DraggableAnnotationPanel panel, final SummaryController summaryController, final Frame frame) {
        this.panel = panel;
        this.selectedComponents = new HashSet<>();
        this.summaryController = summaryController;
        this.frame = frame;
        final MouseHandler handler = new MouseHandler();
        panel.addMouseListener(handler);
        panel.addMouseMotionListener(handler);
        final InputMap inputMap = panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        final ActionMap actionMap = panel.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), KeyEvent.VK_LEFT);
        actionMap.put(KeyEvent.VK_LEFT, new BaseKeyAction() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                if (isValidState()) {
                    final Color newColor = summaryController.getLeftColor(panel);
                    if (newColor != null) {
                        final Set<Component> toRemove = new HashSet<>();
                        for (final Component comp : selectedComponents) {
                            if (comp instanceof AnnotationSummaryComponent) {
                                if (!(comp instanceof AnnotationSummaryGroup &&
                                        summaryController.getGroupManager().groupExists(newColor, comp.getName()))) {
                                    ((AnnotationSummaryComponent) comp).moveTo(newColor, true);
                                    comp.requestFocusInWindow();
                                    toRemove.add(comp);
                                }
                            }
                        }
                        selectedComponents.removeAll(toRemove);
                    }
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), KeyEvent.VK_RIGHT);
        actionMap.put(KeyEvent.VK_RIGHT, new BaseKeyAction() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                if (isValidState()) {
                    final Color newColor = summaryController.getRightColor(panel);
                    if (newColor != null) {
                        final Set<Component> toRemove = new HashSet<>();
                        for (final Component comp : selectedComponents) {
                            if (!(comp instanceof AnnotationSummaryGroup &&
                                    summaryController.getGroupManager().groupExists(newColor, comp.getName()))) {
                                ((AnnotationSummaryComponent) comp).moveTo(newColor, true);
                                comp.requestFocusInWindow();
                                toRemove.add(comp);
                            }
                        }
                        selectedComponents.removeAll(toRemove);
                    }
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), KeyEvent.VK_UP);
        actionMap.put(KeyEvent.VK_UP, new BaseKeyAction() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (isValidState()) {
                    final List<Container> parents = selectedComponents.stream().map(Component::getParent).collect(Collectors.toList());
                    final Container parent = parents.get(0);
                    if (parents.stream().allMatch(p -> p == parent)) {
                        if (parent instanceof AnnotationSummaryGroup) {
                            ((AnnotationSummaryGroup) parent).moveAll(selectedComponents, true);
                        } else if (parent == panel && neverDragged) {
                            panel.moveAll(selectedComponents, true);
                        }
                        focused.requestFocusInWindow();
                    }
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), KeyEvent.VK_DOWN);
        actionMap.put(KeyEvent.VK_DOWN, new BaseKeyAction() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                final Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (isValidState()) {
                    final List<Container> parents = selectedComponents.stream().map(Component::getParent).collect(Collectors.toList());
                    final Container parent = parents.get(0);
                    if (parents.stream().allMatch(p -> p == parent)) {
                        if (parent instanceof AnnotationSummaryGroup) {
                            ((AnnotationSummaryGroup) parent).moveAll(selectedComponents, false);
                        } else if (parent == panel && neverDragged) {
                            panel.moveAll(selectedComponents, false);
                        }
                    }
                    focused.requestFocusInWindow();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), KeyEvent.VK_DELETE);
        actionMap.put(KeyEvent.VK_DELETE, new BaseKeyAction() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                if (isValidState()) {
                    selectedComponents.forEach(c -> ((AnnotationSummaryComponent) c).delete());
                    clearSelectedComponents();
                }
            }
        });
    }

    public abstract class BaseKeyAction extends AbstractAction {

        @Override
        public abstract void actionPerformed(ActionEvent actionEvent);

        protected boolean isValidState() {
            return !selectedComponents.isEmpty();
        }

    }

    void addSelected(final Component c) {
        selectedComponents.add(c);
    }

    void removeSelected(final Component c) {
        selectedComponents.remove(c);
    }

    public DraggableAnnotationPanel getPanel() {
        return panel;
    }

    public void setNeverDragged(final boolean neverDragged) {
        this.neverDragged = neverDragged;
    }

    public boolean isNeverDragged() {
        return neverDragged;
    }

    public SummaryController getSummaryController() {
        return summaryController;
    }

    public void clearSelectedComponents() {
        selectedComponents.forEach(c -> {
            ((AnnotationSummaryComponent) c).setComponentSelected(false);
            summaryController.getDragAndLinkManager().removeComponentSelected((AnnotationSummaryComponent) c);
        });
        selectedComponents.clear();
    }

    private void addSelectedComponent(final Component comp, final boolean toggle) {
        final boolean select = !toggle || !selectedComponents.contains(comp);
        if (select && getComponentIndex(comp) != -1) {
            summaryController.getDragAndLinkManager().addComponentSelected((AnnotationSummaryComponent) comp);
        } else {
            summaryController.getDragAndLinkManager().removeComponentSelected((AnnotationSummaryComponent) comp);
        }
        if (comp instanceof AnnotationSummaryGroup) {
            final Set<Component> intersection = ((AnnotationSummaryGroup) comp).getSubComponents().stream()
                    .map(AnnotationSummaryComponent::asComponent).collect(Collectors.toSet());
            intersection.retainAll(selectedComponents);
            intersection.forEach(c -> ((AnnotationSummaryComponent) c).setComponentSelected(false));
            selectedComponents.removeAll(intersection);
            if (select) {
                selectedComponents.add(comp);
            } else {
                selectedComponents.remove(comp);
            }
            ((AnnotationSummaryComponent) comp).setComponentSelected(select);
        } else if (comp instanceof AnnotationSummaryBox) {
            if (!selectedComponents.contains(comp.getParent())) {
                ((AnnotationSummaryComponent) comp).setComponentSelected(select);
                if (select) {
                    selectedComponents.add(comp);
                } else {
                    selectedComponents.remove(comp);
                }
            }
        }
    }

    private class MouseHandler extends MouseAdapter {

        private Point dragOffset;
        private int lastSelectedComponentIdx = -1;

        @Override
        public void mouseClicked(final MouseEvent e) {
            final Component comp = panel.getComponentAt(e.getPoint());
            final Component deepestComponent = (Component) getDeepestComponentAt(panel, e.getPoint());
            comp.requestFocusInWindow();
            if (comp instanceof AnnotationSummaryComponent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    //If there are selected components and we're clicking on one of them, show a multi-select menu
                    if (selectedComponents.size() > 1 && (selectedComponents.contains(comp) || selectedComponents.contains(deepestComponent))) {
                        final JPopupMenu menu = new MultiSelectedPopupMenu(comp, selectedComponents.stream()
                                .map(c -> (AnnotationSummaryComponent) c).collect(Collectors.toList()),
                                summaryController, DraggablePanelController.this);
                        menu.show(e.getComponent(), e.getX(), e.getY());
                    } else {
                        //Otherwise just show a menu for the given component
                        JPopupMenu contextMenu = new JPopupMenu();
                        clearSelectedComponents();
                        final List<AnnotationSummaryComponent> allComponents = getAllComponentsAt(panel, e.getPoint(), new ArrayList<>());
                        if (allComponents.size() > 1) {
                            for (final AnnotationSummaryComponent c : allComponents) {
                                if (c instanceof AnnotationSummaryGroup) {
                                    final JMenu menu = GroupMenuFactory.createGroupMenu((AnnotationSummaryGroup) c, summaryController,
                                            DraggablePanelController.this, ((AnnotationSummaryGroup) c).getName());
                                    contextMenu.add(menu);
                                } else if (c instanceof AnnotationSummaryBox) {
                                    final JMenu menu = BoxMenuFactory.createBoxMenu((AnnotationSummaryBox) c,
                                            ((AnnotationSummaryBox) c).getMarkupAnnotationComponent(), frame,
                                            DraggablePanelController.this, summaryController, "Annotation");
                                    contextMenu.add(menu);
                                }
                            }
                        } else if (allComponents.size() == 1) {
                            if (comp instanceof AnnotationSummaryBox) {
                                contextMenu = BoxMenuFactory.createBoxPopupMenu((AnnotationSummaryBox) comp,
                                        ((AnnotationSummaryBox) comp).getMarkupAnnotationComponent(), frame,
                                        DraggablePanelController.this, summaryController);
                            } else if (comp instanceof AnnotationSummaryGroup) {
                                contextMenu = GroupMenuFactory.createGroupPopupMenu((AnnotationSummaryGroup) comp,
                                        summaryController, DraggablePanelController.this);
                            }
                        }
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    //If no component is selected or CTRL is pressed, toggle the component (or the deepest if ALT is pressed) on the selected list
                    if (selectedComponents.isEmpty() || e.isControlDown()) {
                        if (e.isAltDown()) {
                            if (deepestComponent != null) {
                                deepestComponent.requestFocusInWindow();
                                addSelectedComponent(deepestComponent, e.isControlDown());
                            }
                        } else {
                            addSelectedComponent(comp, e.isControlDown());
                            lastSelectedComponentIdx = getComponentIndex(comp);
                        }
                        //If a component is already selected and shif is down, select all the components in between
                    } else if (lastSelectedComponentIdx != -1 && e.isShiftDown()) {
                        final int newIdx = getComponentIndex(comp);
                        final int start = Math.min(lastSelectedComponentIdx, newIdx);
                        final int end = Math.max(lastSelectedComponentIdx, newIdx);
                        for (int i = start; i <= end; ++i) {
                            addSelectedComponent(panel.getComponent(i), false);
                        }
                        lastSelectedComponentIdx = getComponentIndex(comp);
                        //If simple click, clear the selected and add the clicked component to the selected list
                    } else if (!selectedComponents.contains(comp) || e.isAltDown() && !selectedComponents.contains(deepestComponent)) {
                        clearSelectedComponents();
                        if (e.isAltDown()) {
                            if (deepestComponent != null) {
                                deepestComponent.requestFocusInWindow();
                                addSelectedComponent(deepestComponent, false);
                            }
                        } else {
                            addSelectedComponent(comp, false);
                            lastSelectedComponentIdx = getComponentIndex(comp);
                        }
                    }
                    if (e.getClickCount() == 2) {
                        if (deepestComponent instanceof AnnotationSummaryBox) {
                            final Annotation annotation = ((AnnotationSummaryBox) deepestComponent).getAnnotation().getParent();
                            PageComponentSelector.SelectAnnotationComponent(summaryController.getController(), annotation);
                        }
                    }
                }
            } else {
                if (!e.isControlDown()) {
                    clearSelectedComponents();
                }
                summaryController.dispatch(e);
            }
        }

        @Override
        public void mousePressed(final MouseEvent e) {
            final Component comp = panel.getComponentAt(e.getPoint());
            if (SwingUtilities.isLeftMouseButton(e) && comp instanceof AnnotationSummaryComponent) {
                dragComponent = comp;
                dragComponent.requestFocus();
                // bring the component to the front.
                // setComponentZOrder(dragComponent, 0);
                // move to comp
                dragOffset = new Point();
                dragOffset.x = e.getPoint().x - comp.getX();
                dragOffset.y = e.getPoint().y - comp.getY();
                panel.repaint();
            }
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
            if (isDragging) moveComponent(dragComponent, !e.isControlDown());
            isDragging = false;
            dragComponent = null;
        }

        @Override
        public void mouseDragged(final MouseEvent e) {
            isDragging = true;
            if (dragComponent != null) {
                neverDragged = false;
                panel.setComponentZOrder(dragComponent, 0);
                final Point dragPoint = new Point();
                dragPoint.x = e.getPoint().x - dragOffset.x;
                dragPoint.y = e.getPoint().y - dragOffset.y;
                dragComponent.setLocation(dragPoint);
                ((AnnotationSummaryComponent) dragComponent).fireComponentMoved(false, false, UUID.randomUUID());
                summaryController.setHasManuallyChanged();
                panel.validate();
                panel.repaint();
            }
        }

        @Override
        public void mouseMoved(final MouseEvent e) {
            summaryController.dispatch(e);
        }
    }

    private void moveComponent(final Component draggedComponent, final boolean snap) {
        if (draggedComponent != null) {
            panel.sortComponents();
            innerMove(draggedComponent, snap);
        }
    }

    private void innerMove(final Component draggedComponent, final boolean snap) {
        final int padding = 10;
        final Component[] comps = panel.getComponents();
        final int draggedIndex = findComponentAt(comps, draggedComponent);
        final Rectangle dragBounds = draggedComponent.getBounds();
        final Collection<AnnotationSummaryComponent> moved = new ArrayList<>(comps.length);
        if (draggedIndex == 0 && dragBounds.getY() < padding) {
            draggedComponent.setLocation(draggedComponent.getX(), padding);
        } else if (draggedIndex > 0) {
            final Component previousComponent = comps[draggedIndex - 1];
            if (previousComponent instanceof AnnotationSummaryGroup && previousComponent.getBounds().contains(dragBounds)) {
                summaryController.getGroupManager().moveIntoGroup((AnnotationSummaryComponent) draggedComponent,
                        ((AnnotationSummaryGroup) previousComponent).getColor(), previousComponent.getName());
            } else if (previousComponent.getBounds().intersects(dragBounds)) {
                draggedComponent.setLocation(dragBounds.x, previousComponent.getY() + previousComponent.getHeight() + padding);
                moved.add((AnnotationSummaryComponent) draggedComponent);
            }
        }

        for (int i = draggedIndex + 1; i < comps.length; i++) {
            final Component currentComponent = comps[i];
            for (int j = draggedIndex; j < i; ++j) {
                final Component precedingComponent = comps[j];
                if (currentComponent.getBounds().intersects(precedingComponent.getBounds())) {
                    final Component previousComponent = comps[i - 1];
                    currentComponent.setLocation(previousComponent.getX(), previousComponent.getY() + previousComponent.getHeight() + padding);
                    moved.add((AnnotationSummaryComponent) currentComponent);
                }
            }
        }

        panel.revalidate();
        moved.forEach(c -> c.fireComponentMoved(snap, true, UUID.randomUUID()));
    }

    private int findComponentAt(final Component[] comps, final Component dragComponent) {
        for (int i = 0; i < comps.length; i++) {
            if (comps[i].equals(dragComponent)) {
                return i;
            }
        }
        return -1;
    }

    private int getComponentIndex(final Component component) {
        for (int i = 0; i < panel.getComponentCount(); i++) {
            if (component.equals(panel.getComponent(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the deepest component at a given Point
     *
     * @param c The current component we're processing
     * @param p The current point
     * @return The deepest component
     */
    private AnnotationSummaryComponent getDeepestComponentAt(final Component c, final Point p) {
        final Component child = c.getComponentAt(p);
        if (child instanceof AnnotationSummaryComponent) {
            final Point newP = SwingUtilities.convertPoint(c, p, child);
            if (child == c) {
                return (AnnotationSummaryComponent) child;
            } else {
                final AnnotationSummaryComponent deepest = getDeepestComponentAt(child, newP);
                return deepest == null ? (AnnotationSummaryComponent) child : deepest;
            }
        } else if (c instanceof AnnotationSummaryComponent) {
            return (AnnotationSummaryComponent) c;
        } else return null;
    }

    /**
     * Returns all the components at a given Point
     *
     * @param c    The current component we're processing
     * @param p    The current point
     * @param list The list of components
     * @return A list of components
     */
    private List<AnnotationSummaryComponent> getAllComponentsAt(final Component c, final Point p,
                                                                final List<AnnotationSummaryComponent> list) {
        final Component child = c.getComponentAt(p);
        if (child instanceof AnnotationSummaryComponent) {
            //Sometimes StackOverflow for some reason
            if (list.contains(child)) {
                return list;
            } else {
                list.add((AnnotationSummaryComponent) child);
                final Point newP = SwingUtilities.convertPoint(c, p, child);
                return getAllComponentsAt(child, newP, list);
            }
        } else return list;
    }

    public AnnotationSummaryComponent findComponentFor(final Predicate<AnnotationSummaryBox> filter) {
        final List<AnnotationSummaryComponent> components = Arrays.stream(panel.getComponents())
                .map(AnnotationSummaryComponent.class::cast).collect(Collectors.toList());
        final List<AnnotationSummaryComponent> found = components.stream().map(c -> {
            if (c instanceof AnnotationSummaryBox) {
                return filter.test((AnnotationSummaryBox) c) ? c : null;
            } else if (c instanceof AnnotationSummaryGroup) {
                return ((AnnotationSummaryGroup) c).findComponentFor(filter);
            } else return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
        if (found.size() > 1) {
            log.warning("Multiple components found");
        }
        return found.size() == 1 ? found.get(0) : null;
    }
}
