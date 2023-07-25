package org.icepdf.ri.common.views.annotations.summary.mainpanel;

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryBox;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryGroup;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.ColorLabelPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class managing group operations
 */
public class GroupManager {

    private final ResourceBundle messageBundle;
    private final SummaryController controller;
    private final Map<Reference, AnnotationSummaryGroup> annotationGroups;
    private final Map<AnnotationSummaryComponent, AnnotationSummaryGroup> groups;
    private final Map<Color, Map<String, AnnotationSummaryGroup>> namedGroups;

    public GroupManager(final SummaryController controller) {
        this.controller = controller;
        this.annotationGroups = new HashMap<>();
        this.groups = new HashMap<>();
        this.namedGroups = new HashMap<>();
        this.messageBundle = controller.getController().getMessageBundle();
    }

    private Collection<AnnotationSummaryGroup> getAllGroups() {
        return namedGroups.values().stream().flatMap(m -> m.values().stream()).collect(Collectors.toList());
    }

    /**
     * Refresh all the groups
     */
    public void refreshGroups() {
        for (final AnnotationSummaryGroup group : getAllGroups()) {
            group.clear();
        }
        for (final AnnotationSummaryGroup group : getAllGroups()) {
            if (groups.get(group) != null) {
                groups.get(group).addComponent(group);
            }
        }
    }

    /**
     * Adds the groups to their suitable panel
     *
     * @param colorLabelPanel The panel to add to
     */
    public void addGroupsToPanel(final ColorLabelPanel colorLabelPanel) {
        for (final AnnotationSummaryGroup group : namedGroups.getOrDefault(colorLabelPanel.getColorLabel().getColor(),
                Collections.emptyMap()).values()) {
            if (!groups.containsKey(group)) {
                colorLabelPanel.addGroup(group, -1);
            }
        }
    }

    /**
     * Checks that a group exists
     *
     * @param c    The color of the group
     * @param name The name of the group
     * @return True if it exists
     */
    public boolean groupExists(final Color c, final String name) {
        return namedGroups.containsKey(c) && namedGroups.get(c).containsKey(name);
    }

    /**
     * Gets a group given a color and a name
     *
     * @param c    The color
     * @param name The group name
     * @return The group (or null)
     */
    public AnnotationSummaryGroup getGroup(final Color c, final String name) {
        return namedGroups.get(c).get(name);
    }


    public AnnotationSummaryGroup createGroup(final Collection<AnnotationSummaryComponent> components, final int y) {
        if (components.stream().allMatch(c -> groups.get(c) == groups.get(components.iterator().next()))) {
            final String name = showInsertGroupNameDialog(components.iterator().next().getColor(), "");
            return createGroup(components, y, name);
        } else {
            return null;
        }
    }

    public AnnotationSummaryGroup createGroup(final Collection<AnnotationSummaryComponent> components) {
        return createGroup(components, -1);
    }

    public AnnotationSummaryGroup createGroup(final Collection<AnnotationSummaryComponent> components, final String name) {
        return createGroup(components, -1, name);
    }

    /**
     * Creates a group
     *
     * @param components The components composing the group
     * @param y          The y coordinate to insert the group at
     * @param name       The name of the group
     */
    public AnnotationSummaryGroup createGroup(final Collection<AnnotationSummaryComponent> components, final int y, final String name) {
        if (name != null) {
            controller.setHasManuallyChanged();
            controller.getDragAndLinkManager().unlinkAll(components, false);
            final AnnotationSummaryGroup group = new AnnotationSummaryGroup(components, name, controller);
            final Map<String, AnnotationSummaryGroup> colorGroups = namedGroups.getOrDefault(group.getColor(), new HashMap<>());
            colorGroups.put(name, group);
            namedGroups.put(group.getColor(), colorGroups);
            components.stream().filter(c -> c instanceof AnnotationSummaryBox).flatMap(c ->
                    c.getAnnotations().stream()).forEach(a -> annotationGroups.put(a.getPObjectReference(), group));
            final AnnotationSummaryGroup oldParent = groups.get(components.iterator().next());
            components.forEach(c -> groups.put(c, group));
            if (oldParent != null) {
                groups.put(group, oldParent);
                oldParent.addComponent(group);
            } else {
                final ColorLabelPanel panel = controller.getColorPanelFor(group);
                if (panel != null) {
                    panel.addGroup(group, y);
                }
            }
            components.forEach(c -> {
                if (oldParent != null) {
                    oldParent.removeComponent(c);
                }
            });
            controller.refreshPanelLayout();
            return group;
        } else {
            return null;
        }
    }

    /**
     * Deletes a group
     *
     * @param group The group to delete
     */
    public void deleteGroup(final AnnotationSummaryGroup group) {
        group.delete();
        disbandGroup(group);
        controller.refreshPanelLayout();
    }

    /**
     * Disbands a group
     *
     * @param group The group to disband
     */
    public void disbandGroup(final AnnotationSummaryGroup group) {
        controller.setHasManuallyChanged();
        namedGroups.getOrDefault(group.getColor(), new HashMap<>()).remove(group.getName());
        final ColorLabelPanel panel = controller.getColorPanelFor(group);
        final AnnotationSummaryGroup parent = groups.get(group);
        controller.getDragAndLinkManager().unlinkComponent(group, false);
        if (parent != null) {
            group.getSubComponents().forEach(c -> {
                groups.put(c, parent);
                if (c instanceof AnnotationSummaryBox) {
                    c.getAnnotations().forEach(a -> annotationGroups.put(a.getPObjectReference(), parent));
                }
                parent.addComponent(c);
            });
        } else {
            final List<AnnotationSummaryComponent> components = group.getSubComponents();
            final int pos = panel.getDraggableAnnotationPanel().getPositionFor(group);
            for (int i = 0; i < components.size(); ++i) {
                final AnnotationSummaryComponent c = components.get(i);
                groups.remove(c);
                panel.addComponent(c, pos + i);
                if (c instanceof AnnotationSummaryBox) {
                    c.getAnnotations().forEach(a -> annotationGroups.remove(a.getPObjectReference()));
                }
            }
        }
        removeFromGroup(group);
        controller.refreshPanelLayout();
    }


    private void updateGroupName(final AnnotationSummaryGroup group, final String oldname, final String newname) {
        controller.setHasManuallyChanged();
        namedGroups.get(group.getColor()).put(newname, group);
        namedGroups.get(group.getColor()).remove(oldname);
    }

    private String showInsertGroupNameDialog(final Color color, final String basename) {
        final String name = controller.showInputDialog(messageBundle.getString("viewer.summary.creategroup.dialog.label"),
                messageBundle.getString("viewer.summary.creategroup.dialog.title"), JOptionPane.QUESTION_MESSAGE, basename, null);
        if (name == null) {
            return null;
        } else if (namedGroups.getOrDefault(color, new HashMap<>()).containsKey(name) || name.isEmpty()) {
            controller.showMessageDialog(messageBundle.getString("viewer.summary.creategroup.dialog.error.label"),
                    messageBundle.getString("viewer.summary.creategroup.dialog.error.title"), JOptionPane.ERROR_MESSAGE);
            return showInsertGroupNameDialog(color, basename);
        } else {
            return name;
        }
    }

    /**
     * Clears all the groups
     */
    public void clear() {
        final List<AnnotationSummaryGroup> allGroups = new ArrayList<>(getAllGroups());
        allGroups.forEach(this::disbandGroup);
        annotationGroups.clear();
        groups.clear();
        namedGroups.clear();
    }

    /**
     * Renames a group
     *
     * @param group The group to rename
     */
    public void renameGroup(final AnnotationSummaryGroup group) {
        final String oldname = group.getName();
        final String newname = showInsertGroupNameDialog(group.getColor(), oldname);
        if (newname != null) {
            updateGroupName(group, oldname, newname);
            group.setName(newname);
        }
    }

    /**
     * Returns the parent group of a component
     *
     * @param c The component
     * @return The group containing the component, or null if it's at the root
     */
    public AnnotationSummaryGroup getParentOf(final AnnotationSummaryComponent c) {
        return groups.get(c);
    }

    /**
     * Returns the parent group of an annotation
     *
     * @param annot The annotation
     * @return The group containing the annotation component, or null if it's at the root
     */
    public AnnotationSummaryGroup getParentOf(final MarkupAnnotation annot) {
        return annotationGroups.get(annot.getPObjectReference());
    }

    /**
     * Removes a component from a group
     *
     * @param c The component to remove
     */
    public void removeFromGroup(final AnnotationSummaryComponent c) {
        final AnnotationSummaryGroup group = groups.remove(c);
        if (c != null) {
            if (group != null) {
                group.removeComponent(c);
            } else {
                final ColorLabelPanel panel = controller.getColorPanelFor(c);
                if (panel != null) panel.removeComponent(c);
            }
        }
        if (c instanceof AnnotationSummaryBox) {
            c.getAnnotations().forEach(a -> annotationGroups.remove(a.getPObjectReference()));
        }
        controller.refreshPanelLayout();
    }

    /**
     * Moves a component out of a group
     *
     * @param c The component
     */
    public void moveOut(final AnnotationSummaryComponent c) {
        removeFromGroup(c);
        final ColorLabelPanel panel = controller.getColorPanelFor(c);
        if (panel != null) {
            panel.addComponent(c);
        }
        controller.refreshPanelLayout();
    }

    /**
     * Moves a component into a group
     *
     * @param c    The component to move
     * @param name The name of the group to put the component in
     */
    public void moveIntoGroup(final AnnotationSummaryComponent c, final Color color, final String name) {
        controller.setHasManuallyChanged();
        final AnnotationSummaryGroup oldGroup = groups.remove(c);
        final AnnotationSummaryGroup group = namedGroups.get(color).get(name);
        controller.getDragAndLinkManager().unlinkComponent(c, false);
        if (!c.getColor().equals(group.getColor())) {
            c.moveTo(group.getColor(), true);
        }
        groups.put(c, group);
        if (c instanceof AnnotationSummaryBox) {
            c.getAnnotations().forEach(a -> annotationGroups.put(a.getPObjectReference(), group));
        }
        if (oldGroup != null) oldGroup.removeComponent(c);
        group.addComponent(c);
        controller.refreshPanelLayout();
    }

    public boolean canMoveTo(final AnnotationSummaryGroup group, final Color newColor) {
        return !namedGroups.getOrDefault(newColor, Collections.emptyMap()).containsKey(group.getName());
    }

    public void moveTo(final AnnotationSummaryGroup group, final Color oldColor, final Color newColor) {
        controller.setHasManuallyChanged();
        namedGroups.get(oldColor).remove(group.getName());
        final Map<String, AnnotationSummaryGroup> namedGroup = namedGroups.getOrDefault(newColor, new HashMap<>());
        namedGroup.put(group.getName(), group);
        namedGroups.put(newColor, namedGroup);
    }

    /**
     * @return all the group names
     */
    public Map<Color, Set<String>> getGroupNames() {
        return namedGroups.entrySet().stream().map(s -> new AbstractMap.SimpleEntry<>(s.getKey(), s.getValue().keySet()))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
    }

    public Set<String> getGroupNames(final Color c) {
        return namedGroups.getOrDefault(c, new HashMap<>()).keySet();
    }
}
