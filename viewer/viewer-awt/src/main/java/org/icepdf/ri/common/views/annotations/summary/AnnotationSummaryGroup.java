package org.icepdf.ri.common.views.annotations.summary;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.AnnotationComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggablePanelController;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;
import org.icepdf.ri.common.views.annotations.summary.menu.GroupMenuFactory;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a group of AnnotationSummaryComponent
 */
public class AnnotationSummaryGroup extends MoveableComponentsPanel implements AnnotationSummaryComponent {

    private List<AnnotationSummaryComponent> components;
    private String name;
    private TitledBorder border;
    private CompoundBorder groupSelectedBorder;
    private final SummaryController summaryController;
    private boolean isSelected = false;
    private Color color;
    private final UUID id;

    public AnnotationSummaryGroup(final Collection<AnnotationSummaryComponent> components, final String name, final SummaryController summaryController) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.components = new ArrayList<>(components);
        this.name = name;
        this.summaryController = summaryController;
        this.id = UUID.randomUUID();
        setRequestFocusEnabled(true);
        setFocusable(true);
        getColor();
        refreshBorder();
        refreshComponents();
    }

    public Color getColor() {
        if (!components.isEmpty()) {
            color = components.get(0).getColor();
        }
        return color;
    }

    @Override
    public void moveTo(final Color c, final boolean isTopComponent) {
        final Color oldColor = getColor();
        components.forEach(a -> a.moveTo(c, false));
        refreshColor();
        summaryController.getGroupManager().moveTo(this, oldColor, c);
        if (isTopComponent) {
            summaryController.moveTo(this, c, oldColor);
        }
    }

    /**
     * Clears the group
     */
    public void clear() {
        removeAll();
        components.clear();
    }

    @Override
    public void setComponentSelected(final boolean b) {
        isSelected = b;
        setBorder(b ? groupSelectedBorder : border);
    }

    @Override
    public boolean isComponentSelected() {
        return isSelected;
    }

    /**
     * @return all the names of the groups contained in this group recursively
     */
    public Set<String> getAllSubnames() {
        return getAllSubnames(this, new HashSet<>());
    }

    private Set<String> getAllSubnames(final AnnotationSummaryGroup g, final Set<String> names) {
        g.getSubComponents().forEach(subComp -> {
            if (subComp instanceof AnnotationSummaryGroup) {
                getAllSubnames((AnnotationSummaryGroup) subComp, names);
            }
        });
        names.add(g.name);
        return names;
    }

    @Override
    public boolean delete() {
        int undeletable = 0;
        while (!components.isEmpty() && undeletable != components.size()) {
            summaryController.getDragAndLinkManager().unlinkComponent(components.get(undeletable), false);
            if (!components.get(undeletable).delete()) {
                undeletable++;
            }
        }
        Arrays.stream(getPropertyChangeListeners()).forEach(this::removePropertyChangeListener);
        summaryController.getGroupManager().disbandGroup(this);
        return true;
    }

    @Override
    public void refresh() {
        components.forEach(AnnotationSummaryComponent::refresh);
        refreshComponents();
        refreshBorder();
    }

    @Override
    public Collection<Annotation> getAnnotations() {
        return components.stream().flatMap(c -> c.getAnnotations().stream()).collect(Collectors.toList());
    }

    @Override
    public Collection<AnnotationComponent> getAnnotationComponents() {
        return components.stream().flatMap(c -> c.getAnnotationComponents().stream()).collect(Collectors.toList());
    }


    private void refreshBorder() {
        this.border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.BLACK, 2, true), name, TitledBorder.LEFT, TitledBorder.TOP);
        this.groupSelectedBorder = BorderFactory.createCompoundBorder(border, SELECTED_BORDER);
        setComponentSelected(isSelected);
    }

    private void refreshColor() {
        refreshBorder();
    }

    /**
     * @return The list of AnnotationSummaryComponents contained in this group
     */
    public List<AnnotationSummaryComponent> getSubComponents() {
        return Collections.unmodifiableList(components);
    }

    public void setComponents(final List<AnnotationSummaryComponent> components) {
        this.components = new ArrayList<>(components);
        refreshComponents();
    }

    private void refreshComponents() {
        removeAll();
        components.forEach(c -> add(c.asComponent()));
        revalidate();
        repaint();
    }

    public void addComponent(final AnnotationSummaryComponent component) {
        if (!components.contains(component)) {
            components.add(component);
            add(component.asComponent());
        }
        revalidate();
        repaint();
    }

    public void insertComponent(final int idx, final AnnotationSummaryComponent component) {
        if (!components.contains(component)) {
            components.add(idx, component);
            add(component.asComponent(), idx);
        }
        revalidate();
        repaint();
    }

    public void removeComponent(final AnnotationSummaryComponent component) {
        components.remove(component);
        if (components.isEmpty()) {
            summaryController.getGroupManager().disbandGroup(this);
        }
        remove(component.asComponent());
        revalidate();
        repaint();
    }

    @Override
    public void move(final Component component, final int idx) {
        final AnnotationSummaryComponent asComp = (AnnotationSummaryComponent) component;
        if (components.contains(asComp)) {
            components.remove(asComp);
            components.add(idx, asComp);
            remove(component);
            add(component, idx);
        }
        revalidate();
        repaint();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
        refreshBorder();
    }

    @Override
    public void setTextBlockVisibility(final boolean visible) {
        components.forEach(c -> c.setTextBlockVisibility(visible));
        validate();
    }

    @Override
    public void toggleTextBlockVisibility() {
        setTextBlockVisibility(!isShowTextBlockVisible());
    }

    @Override
    public boolean isShowTextBlockVisible() {
        return components.stream().allMatch(AnnotationSummaryComponent::isShowTextBlockVisible);
    }

    @Override
    public void toggleHeaderVisibility() {
        components.forEach(AnnotationSummaryComponent::toggleHeaderVisibility);
    }

    @Override
    public void setHeaderVisibility(final boolean visible) {
        components.forEach(c -> c.setHeaderVisibility(visible));
    }

    @Override
    public boolean isHeaderVisible() {
        return components.stream().allMatch(AnnotationSummaryComponent::isHeaderVisible);
    }

    @Override
    public JPopupMenu getContextMenu(final Frame frame, final DraggablePanelController panel) {
        return GroupMenuFactory.createGroupPopupMenu(this, summaryController, panel);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(final Object that) {
        return that instanceof AnnotationSummaryGroup && this.id.equals(((AnnotationSummaryGroup) that).id);
    }

    @Override
    public String getDebuggable() {
        return name;
    }

    @Override
    public void setFontSize(final float size) {
        components.forEach(c -> c.setFontSize(size));
    }

    @Override
    public void setFontFamily(final String family) {
        components.forEach(c -> c.setFontFamily(family));
    }

    @Override
    public void fireComponentMoved(final boolean snap, final boolean check, final UUID uuid) {
        summaryController.getDragAndLinkManager().componentMoved(this, snap, check, uuid);
    }

    @Override
    public Component asComponent() {
        return this;
    }

    @Override
    public Container asContainer() {
        return this;
    }

    @Override
    public boolean canEdit() {
        return components.stream().allMatch(AnnotationSummaryComponent::canEdit);
    }

    /**
     * Search the group for an annotation with the given arguments
     *
     * @param filter The annotation box filter
     * @return The annotation if found, null otherwise
     */
    public AnnotationSummaryComponent findComponentFor(final Predicate<AnnotationSummaryBox> filter) {
        for (final AnnotationSummaryComponent comp : getSubComponents()) {
            if (comp instanceof AnnotationSummaryBox && filter.test(((AnnotationSummaryBox) comp))) {
                return comp;
            } else if (comp instanceof AnnotationSummaryGroup) {
                final AnnotationSummaryComponent found = ((AnnotationSummaryGroup) comp).findComponentFor(filter);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}