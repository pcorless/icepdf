package org.icepdf.ri.common.views.annotations.summary.mainpanel;

import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryGroup;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.ColorLabelPanel;
import org.icepdf.ri.common.widgets.DragDropColorList;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Class managing the dragging and linking of the components
 */
public class DragAndLinkManager {
    private final Map<AnnotationSummaryComponent, Set<AnnotationSummaryComponent>> potentialLinks;
    private final Map<AnnotationSummaryComponent, Set<AnnotationSummaryComponent>> linkedComponents;
    private final Map<Color, Set<Component>> colorToComps;
    private final Map<Color, Set<AnnotationSummaryComponent>> selectedComponents;
    private final Map<UUID, Set<AnnotationSummaryComponent>> movedForUUID;
    private final Map<UUID, Long> timeForUUID;
    private static final long UUID_PURGE_TIME = 5000000000L; // 5sec
    private static final long UUID_TIMER_SCHEDULE = 10000; //10sec

    private final SummaryController controller;

    public DragAndLinkManager(final SummaryController controller) {
        this.controller = controller;
        this.colorToComps = new HashMap<>();
        this.linkedComponents = new HashMap<>();
        this.potentialLinks = new HashMap<>();
        this.selectedComponents = new HashMap<>();
        this.movedForUUID = new ConcurrentHashMap<>();
        this.timeForUUID = new ConcurrentHashMap<>();
        final Timer clearTimer = new Timer();
        clearTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final long now = System.nanoTime();
                final Set<UUID> toDelete = new HashSet<>();
                timeForUUID.forEach((uuid, time) -> {
                    if (now - time > UUID_PURGE_TIME) {
                        toDelete.add(uuid);
                    }
                });
                toDelete.forEach(uuid -> {
                    timeForUUID.remove(uuid);
                    movedForUUID.remove(uuid);
                });
            }
        }, UUID_TIMER_SCHEDULE, UUID_TIMER_SCHEDULE);
    }

    /**
     * Checks if a component is linked
     *
     * @param comp The component to check
     * @return true if it is linked
     */
    public boolean isLinked(final AnnotationSummaryComponent comp) {
        final Set<AnnotationSummaryComponent> linked = linkedComponents.get(comp);
        return linked != null && !linked.isEmpty();
    }

    /**
     * Checks if a component is linked to a given color
     *
     * @param comp The component
     * @param c    The color
     * @return true if they are linked
     */
    public boolean areLinked(final AnnotationSummaryComponent comp, final Color c) {
        final Set<AnnotationSummaryComponent> linked = linkedComponents.get(comp);
        if (linked != null) {
            for (final Color color : linked.stream().map(AnnotationSummaryComponent::getColor).collect(Collectors.toList())) {
                if (color.equals(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if two components are linked
     *
     * @param comp1 The first component
     * @param comp2 The second component
     * @return true if they are linked
     */
    public boolean areLinked(final AnnotationSummaryComponent comp1, final AnnotationSummaryComponent comp2) {
        final Set<AnnotationSummaryComponent> linked = linkedComponents.get(comp1);
        return linked != null && linked.contains(comp2);
    }

    /**
     * Clears the links
     */
    public void clear() {
        linkedComponents.clear();
        potentialLinks.clear();
        selectedComponents.values().forEach(s -> s.forEach(comp -> comp.setComponentSelected(false)));
        selectedComponents.clear();
    }

    /**
     * Checks if a component is linked to a component on its left
     *
     * @param comp The component
     * @return true if the component is linked to its left
     */
    public boolean isLeftLinked(final AnnotationSummaryComponent comp) {
        final Set<AnnotationSummaryComponent> linked = linkedComponents.get(comp);
        if (linked != null) {
            for (final Color color : linked.stream().map(AnnotationSummaryComponent::getColor).collect(Collectors.toList())) {
                if (controller.isLeftOf(color, comp.getColor())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void linkComponents(final Collection<AnnotationSummaryComponent> components) {
        if (components.stream().map(AnnotationSummaryComponent::getColor).distinct().count() == components.size()) {
            addAllRelationsTo(components, linkedComponents);
        }
    }

    /**
     * Links two components
     *
     * @param c1 The first component
     * @param c2 The second component
     */
    public void linkWithNeighbor(final AnnotationSummaryComponent c1, final AnnotationSummaryComponent c2) {
        if (!c1.getColor().equals(c2.getColor()) && c1.asComponent().getY() == c2.asComponent().getY()) {
            linkComponents(c1, c2);
        }
    }

    /**
     * Links a group of component with a component
     *
     * @param comps The components to create the group with
     * @param comp  The component
     * @param idx   The idx of the component in the group that will determine its position
     */
    public void linkWithNeighbor(final Collection<AnnotationSummaryComponent> comps, final AnnotationSummaryComponent comp, final int idx) {
        final AnnotationSummaryGroup group = createAndGetGroup(comps, idx);
        linkWithNeighbor(group, comp);
    }

    /**
     * Links a component with the selected components in a column
     *
     * @param c     The component
     * @param color The color of the column
     */
    public void linkWithSelected(final AnnotationSummaryComponent c, final Color color) {
        final Set<AnnotationSummaryComponent> selected = selectedComponents.get(color);
        final UUID uuid = UUID.randomUUID();
        if (selected != null) {
            if (selected.size() > 1) {
                final List<AnnotationSummaryComponent> selectedComponents = new ArrayList<>(selected);
                AnnotationSummaryGroup selectedGroup = null;
                for (int i = 0; i < selectedComponents.size() && selectedGroup == null; ++i) {
                    if (selectedComponents.get(i).asComponent().getY() == c.asComponent().getY()) {
                        selectedGroup = createAndGetGroup(selectedComponents, i);
                    }
                }
                if (selectedGroup == null) {
                    selectedGroup = createAndGetGroupWithY(selectedComponents, c.asComponent().getY());
                }
                if (selectedGroup != null) {
                    linkComponents(c, selectedGroup);
                    controller.getColorPanelFor(selectedGroup).getDraggableAnnotationPanel().moveComponentToY(
                            selectedGroup.asComponent(), c.asComponent().getY(), uuid);
                }
            } else {
                selected.forEach(comp -> {
                    linkComponents(c, comp);
                    controller.getColorPanelFor(comp).getDraggableAnnotationPanel().moveComponentToY(
                            comp.asComponent(), c.asComponent().getY(), uuid);
                });
            }
        }
    }

    /**
     * Links a group of component with the selected components in a column
     *
     * @param comps The components
     * @param color The color of the column
     * @param idx   The idx that will determine the position of the group
     */
    public void linkWithSelected(final Collection<AnnotationSummaryComponent> comps, final Color color, final int idx) {
        final AnnotationSummaryGroup group = createAndGetGroup(comps, idx);
        linkWithSelected(group, color);
    }

    private AnnotationSummaryGroup createAndGetGroupWithY(final Collection<AnnotationSummaryComponent> comps, final int y) {
        final List<AnnotationSummaryComponent> components = new ArrayList<>(comps);
        final AnnotationSummaryComponent c = components.get(0);
        final GroupManager gm = controller.getGroupManager();
        gm.createGroup(components, y);
        final AnnotationSummaryGroup group = gm.getParentOf(c);
        updateRelation(c, group, potentialLinks);
        updateRelation(c, group, linkedComponents);
        comps.forEach(comp -> {
            if (comp != c) {
                deleteFromMap(comp, linkedComponents);
                deleteFromMap(comp, potentialLinks);
            }
        });
        return group;
    }

    private AnnotationSummaryGroup createAndGetGroup(final Collection<AnnotationSummaryComponent> comps, final int idx) {
        final List<AnnotationSummaryComponent> components = new ArrayList<>(comps);
        final AnnotationSummaryComponent c = components.get(idx);
        final GroupManager gm = controller.getGroupManager();
        gm.createGroup(components, c.asComponent().getY());
        final AnnotationSummaryGroup group = gm.getParentOf(c);
        updateRelation(c, group, potentialLinks);
        updateRelation(c, group, linkedComponents);
        comps.forEach(comp -> {
            if (comp != c) {
                deleteFromMap(comp, linkedComponents);
                deleteFromMap(comp, potentialLinks);
            }
        });
        return group;
    }

    private <T> void deleteFromMap(final T value, final Map<T, Set<T>> map) {
        if (map.containsKey(value)) {
            final Set<T> values = map.remove(value);
            values.forEach(v -> {
                final Set<T> vValues = map.get(v);
                if (vValues != null) {
                    vValues.remove(value);
                }
            });
        }
    }

    private <T> void updateRelation(final T oldV, final T newV, final Map<T, Set<T>> map) {
        if (map.containsKey(oldV)) {
            final Set<T> values = map.remove(oldV);
            values.forEach(v -> {
                final Set<T> vValues = map.get(v);
                vValues.remove(oldV);
                vValues.add(newV);
            });
            map.put(newV, values);
        }
    }

    /**
     * Link two components
     *
     * @param c1 the first component
     * @param c2 the second component
     */
    public void linkComponents(final AnnotationSummaryComponent c1, final AnnotationSummaryComponent c2) {
        if (!c1.getColor().equals(c2.getColor())) {
            controller.setHasManuallyChanged();
            addRelation(c1, c2, linkedComponents);
            removeRelation(c1, c2, potentialLinks);
            controller.refreshLinks();
        }
    }

    /**
     * Unlink two components
     *
     * @param c1 The first component
     * @param c2 The second component
     */
    public void unlinkComponents(final AnnotationSummaryComponent c1, final AnnotationSummaryComponent c2) {
        controller.setHasManuallyChanged();
        removeRelation(c1, c2, linkedComponents);
        controller.refreshLinks();
    }

    private static <T> void addRelation(final T t1, final T t2, final Map<T, Set<T>> map) {
        if (map.containsKey(t1)) {
            final Set<T> rel1 = new HashSet<>(map.get(t1));
            if (map.containsKey(t2)) {
                final Set<T> rel2 = new HashSet<>(map.get(t2));
                rel1.add(t2);
                rel2.add(t1);
                map.put(t1, rel1);
                map.put(t2, rel2);
            } else {
                final Set<T> rel2 = new HashSet<>();
                rel1.add(t2);
                rel2.add(t1);
                map.put(t1, rel1);
                map.put(t2, rel2);
            }
        } else {
            if (map.containsKey(t2)) {
                final Set<T> rel2 = new HashSet<>(map.get(t2));
                final Set<T> rel1 = new HashSet<>();
                rel1.add(t2);
                rel2.add(t1);
                map.put(t1, rel1);
                map.put(t2, rel2);
            } else {
                final Set<T> set1 = new HashSet<>();
                set1.add(t2);
                final Set<T> set2 = new HashSet<>();
                set2.add(t1);
                map.put(t1, set1);
                map.put(t2, set2);
            }
        }
    }

    private static <T> void removeRelation(final T t1, final T t2, final Map<T, Set<T>> map) {
        if (map.containsKey(t1)) {
            final Set<T> rel1 = new HashSet<>(map.get(t1));
            if (map.containsKey(t2)) {
                final Set<T> rel2 = new HashSet<>(map.get(t2));
                rel1.remove(t2);
                rel2.remove(t1);
                map.put(t1, rel1);
                map.put(t2, rel2);
            } else {
                final Set<T> rel2 = new HashSet<>();
                rel1.remove(t2);
                map.put(t1, rel1);
                map.put(t2, rel2);
            }
        } else {
            if (map.containsKey(t2)) {
                final Set<T> rel2 = new HashSet<>(map.get(t2));
                final Set<T> rel1 = new HashSet<>(rel2);
                rel1.remove(t2);
                rel2.remove(t1);
                map.put(t1, rel1);
                map.put(t2, rel2);
            }
        }
    }

    private static <T> void addAllRelationsTo(final Collection<T> toAdd, final Map<T, Set<T>> map) {
        toAdd.forEach(t -> {
            final Set<T> rel = new HashSet<>(toAdd);
            rel.remove(t);
            map.put(t, rel);
        });
    }

    /**
     * @return The map of linked components
     */
    public Map<AnnotationSummaryComponent, Set<AnnotationSummaryComponent>> getLinkedComponents() {
        return linkedComponents;
    }

    /**
     * Unlink a component
     *
     * @param c    The component to unlink
     * @param keep If the component will still exists
     */
    public void unlinkComponent(final AnnotationSummaryComponent c, final boolean keep) {
        if (keep) {
            transferValues(c, linkedComponents, potentialLinks);
        } else {
            deleteFromMap(c, linkedComponents);
        }
        controller.refreshLinks();
    }

    /**
     * Unlink all components in the list
     *
     * @param components The components to unlink
     * @param keep       If the components will still exist
     */
    public void unlinkAll(final Collection<AnnotationSummaryComponent> components, final boolean keep) {
        components.forEach(c -> unlinkComponent(c, keep));
    }

    private <T> void transferValues(final T key, final Map<T, Set<T>> origin, final Map<T, Set<T>> dest) {
        final Set<T> values = origin.get(key);
        deleteFromMap(key, origin);
        dest.put(key, values);
        values.forEach(v -> {
            final Set<T> vValues = new HashSet<>(values);
            vValues.remove(v);
            vValues.add(key);
            dest.put(v, vValues);
        });
    }

    public void unlinkAllComponents(final AnnotationSummaryComponent c) {
        if (linkedComponents.containsKey(c)) {
            final Set<AnnotationSummaryComponent> components = linkedComponents.remove(c);
            components.forEach(linkedComponents::remove);
        }
    }

    /**
     * Notifies that a component has been moved
     *
     * @param c     The component that moved
     * @param snap  If the component is to be snapped in position
     * @param check If the column needs to be checked for overlap
     * @param uuid  The uuid of the operation (to avoid infinite loop of checking/snapping)
     */
    public void componentMoved(final AnnotationSummaryComponent c, final boolean snap, final boolean check, final UUID uuid) {
        if (!movedForUUID.containsKey(uuid) || movedForUUID.get(uuid) == null || !movedForUUID.get(uuid).contains(c)) {
            if (!timeForUUID.containsKey(uuid)) {
                timeForUUID.put(uuid, System.nanoTime());
            }
            Set<AnnotationSummaryComponent> comps = movedForUUID.get(uuid);
            if (comps == null) {
                comps = new HashSet<>();
                comps.add(c);
                movedForUUID.put(uuid, comps);
            } else {
                comps.add(c);
            }
            deleteFromMap(c, potentialLinks);
            final Component cComp = (Component) c;
            final int y = cComp.getY();
            Set<Component> colorComponents = colorToComps.get(c.getColor());
            if (colorComponents == null) {
                colorComponents = new HashSet<>();
            }
            colorComponents.add(cComp);
            colorToComps.put(c.getColor(), colorComponents);
            if (snap) {
                final ColorLabelPanel panel = controller.getColorPanelFor(c);
                if (panel != null) {
                    for (final Map.Entry<Color, Set<Component>> entry : colorToComps.entrySet()) {
                        if (!entry.getKey().equals(c.getColor())) {
                            for (final Component component : entry.getValue()) {
                                final int compY = component.getY();
                                final int snapHeight = 30;
                                if (!linkedComponents.getOrDefault(c, new HashSet<>()).contains(component)
                                        && y != compY && y > compY - snapHeight && y < compY + snapHeight) {
                                    panel.getDraggableAnnotationPanel().moveComponentToY(cComp, compY, uuid);
                                    addRelation(c, (AnnotationSummaryComponent) component, potentialLinks);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (linkedComponents.containsKey(c)) {
                linkedComponents.get(c).forEach(comp -> {
                    final ColorLabelPanel panel = controller.getColorPanelFor(comp);
                    //Don't reuse y as it may have changed after snapping
                    if (panel != null && (cComp.getY() != ((Component) comp).getY() || check)) {
                        panel.getDraggableAnnotationPanel().moveComponentToY((Component) comp, cComp.getY(), check, uuid);
                    }
                });
            }
            controller.componentMoved(c);
        }
    }

    /**
     * Indicates if a component is selected
     *
     * @param component The component
     * @param selected  The selection status
     */
    public void setComponentSelected(final AnnotationSummaryComponent component, final boolean selected) {
        final Color color = component.getColor();
        Set<AnnotationSummaryComponent> components = selectedComponents.get(color);
        if (selectedComponents.get(color) == null) {
            components = new HashSet<>();
            if (selected) {
                components.add(component);
            }
            selectedComponents.put(color, components);
        } else {
            if (selected) {
                components.add(component);
            } else {
                components.remove(component);
            }
        }
    }

    /**
     * Indicates that a component is selected
     *
     * @param c The component
     */
    public void addComponentSelected(final AnnotationSummaryComponent c) {
        setComponentSelected(c, true);
    }

    /**
     * Indicates that a component is unselected
     *
     * @param c The component
     */
    public void removeComponentSelected(final AnnotationSummaryComponent c) {
        setComponentSelected(c, false);
    }

    /**
     * Returns a map of color to component which indicates neighbor components which are at the same height
     *
     * @param component The component to check against
     * @return The map of components
     */
    public Map<DragDropColorList.ColorLabel, AnnotationSummaryComponent> getSuitableNeighbors(final AnnotationSummaryComponent component) {
        final Map<DragDropColorList.ColorLabel, AnnotationSummaryComponent> neighbors = new HashMap<>();
        for (final ColorLabelPanel panel : controller.getAnnotationNamedColorPanels()) {
            if (!panel.getColorLabel().getColor().equals(component.getColor())) {
                for (final Component c : panel.getDraggableAnnotationPanel().getComponents()) {
                    if (c.getY() == component.asComponent().getY() && !areLinked(component, panel.getColorLabel().getColor())) {
                        neighbors.put(panel.getColorLabel(), (AnnotationSummaryComponent) c);
                    }
                }
            }
        }
        return neighbors;
    }

    /**
     * Returns a list of columns which have suitable selected components
     *
     * @param component The component to check against
     * @return The list of colors
     */
    public List<DragDropColorList.ColorLabel> getSuitableSelected(final AnnotationSummaryComponent component) {
        final List<DragDropColorList.ColorLabel> components = new ArrayList<>();
        final Color color = component.getColor();
        for (final Map.Entry<Color, Set<AnnotationSummaryComponent>> entry : selectedComponents.entrySet()) {
            final Color c = entry.getKey();
            if (!c.equals(color) && !areLinked(component, c)) {
                final Set<AnnotationSummaryComponent> selected = entry.getValue();
                final GroupManager gm = controller.getGroupManager();
                if (selected != null && selected.stream().anyMatch(comp -> !areLinked(comp, color)) &&
                        selected.stream().allMatch(comp ->
                                gm.getParentOf(comp) == gm.getParentOf(selected.iterator().next()))
                        && gm.getParentOf(selected.iterator().next()) == null) {
                    components.add(controller.getColorLabelFor(c));
                }
            }
        }
        return components;
    }

}
