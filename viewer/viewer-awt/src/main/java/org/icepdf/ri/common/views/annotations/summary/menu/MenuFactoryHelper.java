package org.icepdf.ri.common.views.annotations.summary.menu;

import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryGroup;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggablePanelController;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.DragAndLinkManager;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.GroupManager;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;
import org.icepdf.ri.common.widgets.DragDropColorList;

import javax.swing.*;
import java.awt.*;
import java.text.MessageFormat;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for creating menus
 */
public class MenuFactoryHelper {
    private final Controller controller;
    private final ResourceBundle messageBundle;
    private final SummaryController summaryController;
    private final DraggablePanelController draggablePanelController;
    private final Collection<AnnotationSummaryComponent> components;
    private final GroupManager groupManager;
    private final UUID uuid;
    private final boolean canEdit;

    MenuFactoryHelper(final SummaryController summaryController, final DraggablePanelController draggablePanelController,
                      final Collection<AnnotationSummaryComponent> components) {
        this.controller = summaryController.getController();
        this.summaryController = summaryController;
        this.draggablePanelController = draggablePanelController;
        this.messageBundle = controller.getMessageBundle();
        this.components = components;
        assert !components.isEmpty();
        this.groupManager = summaryController.getGroupManager();
        this.uuid = UUID.randomUUID();
        canEdit = components.stream().allMatch(AnnotationSummaryComponent::canEdit);
    }

    private void setManuallyChanged() {
        summaryController.setHasManuallyChanged();
    }

    JMenuItem getCreateGroupMenuItem() {
        final JMenuItem createGroupMenuItem = new JMenuItem(messageBundle.getString("viewer.summary.popup.group.label"));
        createGroupMenuItem.addActionListener(e -> {
            setManuallyChanged();
            groupManager.createGroup(draggablePanelController.getPanel().getSortedList(components.stream()
                                    .map(c -> (Component) c).collect(Collectors.toList()), true)
                            .stream().map(c -> (AnnotationSummaryComponent) c).collect(Collectors.toList()),
                    draggablePanelController.getPanel().getFirstPosForComponents(components.stream()
                            .map(c -> (Component) c).collect(Collectors.toList())));
        });
        return createGroupMenuItem;
    }

    JMenu getMoveInMenu() {
        final Map<Color, Set<String>> colorGroups = summaryController.getGroupManager().getGroupNames();
        if (!colorGroups.isEmpty()) {
            final JMenu moveInMenu = new JMenu(messageBundle.getString("viewer.summary.popup.group.movein"));
            final AnnotationSummaryGroup parent = groupManager.getParentOf(components.iterator().next());
            final Map<Color, Set<String>> invalidNames = new HashMap<>();
            final Set<String> allSubnames = new HashSet<>();
            components.forEach(c -> {
                if (c instanceof AnnotationSummaryGroup) {
                    final Set<String> subnames = invalidNames.getOrDefault(c.getColor(), new HashSet<>());
                    subnames.addAll(((AnnotationSummaryGroup) c).getAllSubnames());
                    allSubnames.addAll(subnames);
                    invalidNames.put(c.getColor(), subnames);
                }
            });
            colorGroups.forEach((color, names) -> {
                if (!color.equals(components.iterator().next().getColor()) && names.stream().anyMatch(allSubnames::contains)) {
                    invalidNames.put(color, names);
                }
            });
            colorGroups.forEach((color, names) -> {
                final Set<String> subnames = invalidNames.getOrDefault(color, Collections.emptySet());
                final List<String> groups = new ArrayList<>(names);
                groups.sort(Comparator.naturalOrder());
                final JMenu colorMenu = new JMenu(summaryController.getColorLabelFor(color).getLabel());
                groups.forEach(name -> {
                    if (!subnames.contains(name) && (parent == null || !parent.getName().equals(name))) {
                        final JMenuItem moveTo = new JMenuItem(name);
                        moveTo.addActionListener(e -> {
                            setManuallyChanged();
                            components.forEach(c -> groupManager.moveIntoGroup(c, color, name));
                        });
                        colorMenu.add(moveTo);
                    }
                });
                if (!canEdit && !components.iterator().next().getColor().equals(color)) {
                    colorMenu.setEnabled(false);
                }
                if (colorMenu.getMenuComponentCount() > 0) {
                    moveInMenu.add(colorMenu);
                }
            });
            return moveInMenu.getMenuComponentCount() > 0 ? moveInMenu : null;
        } else return null;
    }

    JMenuItem getMoveOutMenuItem() {
        final JMenuItem moveOutItem = new JMenuItem(messageBundle.getString("viewer.summary.popup.group.moveout"));
        moveOutItem.addActionListener(e -> {
            setManuallyChanged();
            components.forEach(groupManager::moveOut);
        });
        return moveOutItem;
    }

    JMenu getMoveToMenu() {
        final JMenu moveMenu = new JMenu(messageBundle.getString("viewer.annotation.popup.move.label"));
        summaryController.getAnnotationNamedColorPanels().forEach(panel -> {
            if (components.stream().noneMatch(c -> c instanceof AnnotationSummaryGroup
                    && summaryController.getGroupManager().groupExists(panel.getColorLabel().getColor(), ((AnnotationSummaryGroup) c).getName()))
                    && !panel.getColorLabel().getColor().equals(components.iterator().next().getColor())) {
                moveMenu.addSeparator();
                final JMenuItem menuItem = new JMenuItem(panel.getColorLabel().getLabel());
                menuItem.setForeground(Color.BLACK);
                final Color color = panel.getColorLabel().getColor();
                menuItem.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), TextMarkupAnnotation.HIGHLIGHT_ALPHA));
                menuItem.setOpaque(true);
                moveMenu.add(menuItem);
                menuItem.addActionListener(actionEvent -> {
                    setManuallyChanged();
                    components.forEach(c -> c.moveTo(panel.getColorLabel().getColor(), true));
                });
            }
        });
        moveMenu.setEnabled(canEdit);
        return moveMenu;
    }

    JMenuItem getDeleteMenuItem() {
        final JMenuItem deleteMenuItem = new JMenuItem(messageBundle.getString("viewer.annotation.popup.delete.label"));
        deleteMenuItem.setEnabled(controller.havePermissionToModifyDocument());
        deleteMenuItem.addActionListener(e -> {
            setManuallyChanged();
            components.forEach(AnnotationSummaryComponent::delete);
        });
        deleteMenuItem.setEnabled(canEdit);
        return deleteMenuItem;
    }

    JMenuItem getShowTextBlockMenuItem() {
        final JMenuItem showHideTextBlockMenuItem = new JCheckBoxMenuItem(
                messageBundle.getString("viewer.annotation.popup.showHidTextBlock.label"));
        final boolean isVisible = isShowTextBlockVisible();
        showHideTextBlockMenuItem.setSelected(isVisible);
        showHideTextBlockMenuItem.addItemListener(e -> {
            components.forEach(c -> c.setTextBlockVisibility(!isVisible));
            SwingUtilities.invokeLater(() -> draggablePanelController.getPanel().checkForOverlap(uuid));
        });
        return showHideTextBlockMenuItem;
    }

    JMenuItem getShowHeaderMenuItem() {
        final JMenuItem showHeaderMenuItem = new JCheckBoxMenuItem(messageBundle.getString("viewer.annotation.popup.showHideHeader.label"));
        final boolean isVisible = isHeaderVisible();
        showHeaderMenuItem.setSelected(isVisible);
        showHeaderMenuItem.addItemListener(e -> {
            components.forEach(c -> c.setHeaderVisibility(!isVisible));
            SwingUtilities.invokeLater(() -> draggablePanelController.getPanel().checkForOverlap(uuid));
        });
        return showHeaderMenuItem;
    }

    JMenu getLinkWithMenu() {
        final DragAndLinkManager dragAndLinkManager = summaryController.getDragAndLinkManager();
        final JMenu linkMenu = new JMenu(messageBundle.getString("viewer.summary.popup.link.single"));
        final AnnotationSummaryComponent component = components.iterator().next();
        final Map<DragDropColorList.ColorLabel, AnnotationSummaryComponent> neighborLabels = dragAndLinkManager.getSuitableNeighbors(component);
        for (final Map.Entry<DragDropColorList.ColorLabel, AnnotationSummaryComponent> entry : neighborLabels.entrySet()) {
            final JMenuItem item = new JMenuItem(MessageFormat.format(messageBundle.getString(
                    "viewer.summary.popup.link.neighbor.single"), entry.getKey().getLabel()));
            item.addActionListener(actionEvent -> dragAndLinkManager.linkWithNeighbor(component, entry.getValue()));
            linkMenu.add(item);
        }
        if (neighborLabels.size() > 1) {
            final JMenuItem item = new JMenuItem(messageBundle.getString("viewer.summary.popup.link.neighbor.all"));
            item.addActionListener(actionEvent -> neighborLabels.forEach((cl, annotationSummaryComponent) ->
                    dragAndLinkManager.linkWithNeighbor(component, annotationSummaryComponent)));
            linkMenu.add(item);
        }
        final List<DragDropColorList.ColorLabel> colorLabels = summaryController.getDragAndLinkManager().getSuitableSelected(component);
        for (final DragDropColorList.ColorLabel colorLabel : colorLabels) {
            final JMenuItem item = new JMenuItem(MessageFormat.format(messageBundle.getString(
                    "viewer.summary.popup.link.selected.single"), colorLabel.getLabel()));
            item.addActionListener(actionEvent -> dragAndLinkManager.linkWithSelected(component, colorLabel.getColor()));
            linkMenu.add(item);
        }
        if (colorLabels.size() > 1) {
            final JMenuItem item = new JMenuItem(messageBundle.getString("viewer.summary.popup.link.selected.all"));
            item.addActionListener(actionEvent -> colorLabels.forEach(cl -> dragAndLinkManager.linkWithSelected(component, cl.getColor())));
            linkMenu.add(item);
        }
        return linkMenu.getMenuComponentCount() > 0 ? linkMenu : null;
    }

    JMenu getLinkAllWithMenu(final int selectedIdx) {
        final DragAndLinkManager dragAndLinkManager = summaryController.getDragAndLinkManager();
        final JMenu linkMenu = new JMenu(messageBundle.getString("viewer.summary.popup.link.all"));
        final AnnotationSummaryComponent component = new ArrayList<>(components).get(selectedIdx);
        final Map<DragDropColorList.ColorLabel, AnnotationSummaryComponent> neighborLabels = dragAndLinkManager.getSuitableNeighbors(component);
        for (final Map.Entry<DragDropColorList.ColorLabel, AnnotationSummaryComponent> entry : neighborLabels.entrySet()) {
            final JMenuItem item = new JMenuItem(MessageFormat.format(messageBundle.getString(
                    "viewer.summary.popup.link.neighbor.single"), entry.getKey().getLabel()));
            item.addActionListener(actionEvent -> dragAndLinkManager.linkWithNeighbor(components, entry.getValue(), selectedIdx));
            linkMenu.add(item);
        }
        if (neighborLabels.size() > 1) {
            final JMenuItem item = new JMenuItem(messageBundle.getString("viewer.summary.popup.link.neighbor.all"));
            item.addActionListener(actionEvent -> neighborLabels.forEach((cl, annotationSummaryComponent) ->
                    dragAndLinkManager.linkWithNeighbor(component, annotationSummaryComponent)));
            //TODO linkMenu.add(item);
        }
        final List<DragDropColorList.ColorLabel> colorLabels = summaryController.getDragAndLinkManager().getSuitableSelected(component);
        for (final DragDropColorList.ColorLabel colorLabel : colorLabels) {
            final JMenuItem item = new JMenuItem(MessageFormat.format(messageBundle.getString(
                    "viewer.summary.popup.link.selected.single"), colorLabel.getLabel()));
            item.addActionListener(actionEvent -> dragAndLinkManager.linkWithSelected(components, colorLabel.getColor(), selectedIdx));
            linkMenu.add(item);
        }
        if (colorLabels.size() > 1) {
            final JMenuItem item = new JMenuItem(messageBundle.getString("viewer.summary.popup.link.selected.all"));
            item.addActionListener(actionEvent -> colorLabels.forEach(cl -> dragAndLinkManager.linkWithSelected(component, cl.getColor())));
            //TODO linkMenu.add(item);
        }
        return components.size() > 1 && linkMenu.getMenuComponentCount() > 0 ? linkMenu : null;
    }

    JMenuItem getUnlinkMenuItem() {
        final JMenuItem item = new JMenuItem(messageBundle.getString("viewer.summary.popup.unlink"));
        item.addActionListener(actionEvent -> components.forEach(c -> summaryController.getDragAndLinkManager().unlinkComponent(c, true)));
        return item;
    }

    void addCommonMenu(final List<JMenuItem> list) {
        final JMenu moveInMenu = getMoveInMenu();
        if (moveInMenu != null) list.add(moveInMenu);
        final JMenu moveMenu = getMoveToMenu();
        if (moveMenu.getMenuComponentCount() > 0) list.add(moveMenu);
        final JMenuItem showHideTextBlockMenuItem = getShowTextBlockMenuItem();
        list.add(showHideTextBlockMenuItem);
        final JMenuItem showHeaderMenuItem = getShowHeaderMenuItem();
        list.add(showHeaderMenuItem);
        final JMenuItem deleteMenuItem = getDeleteMenuItem();
        list.add(deleteMenuItem);
    }

    List<JMenuItem> addLinkMenu(final List<JMenuItem> list, final AnnotationSummaryGroup parentOf, final boolean linked) {
        if (parentOf == null) {
            final JMenu linkMenu = getLinkWithMenu();
            if (linkMenu != null) list.add(linkMenu);
            final JMenu linkAllMenu = getLinkAllWithMenu(0);
            if (linkAllMenu != null) list.add(linkAllMenu);
            if (linked)
                list.add(getUnlinkMenuItem());
        }
        return list;
    }

    private boolean isShowTextBlockVisible() {
        return components.stream().allMatch(AnnotationSummaryComponent::isShowTextBlockVisible);
    }

    private boolean isHeaderVisible() {
        return components.stream().allMatch(AnnotationSummaryComponent::isHeaderVisible);
    }
}
