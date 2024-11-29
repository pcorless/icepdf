package org.icepdf.ri.common.views.annotations.summary.menu;

import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggablePanelController;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * PopupMenu when multiple elements are selected
 */
public class MultiSelectedPopupMenu extends JPopupMenu {

    private final List<AnnotationSummaryComponent> components;

    private final AnnotationSummaryComponent clicked;
    private final SummaryController summaryController;
    private final DraggablePanelController draggablePanelController;

    public MultiSelectedPopupMenu(final Component clicked, final Collection<AnnotationSummaryComponent> components,
                                  final SummaryController summaryController, final DraggablePanelController draggablePanelController) {
        this.components = new ArrayList<>(components);
        this.summaryController = summaryController;
        this.draggablePanelController = draggablePanelController;
        this.clicked = (AnnotationSummaryComponent) clicked;
        buildGui();
    }

    private boolean isShowTextBlockVisible() {
        return components.stream().allMatch(AnnotationSummaryComponent::isShowTextBlockVisible);
    }

    public void buildGui() {
        final MenuFactoryHelper helper = new MenuFactoryHelper(summaryController, draggablePanelController, components);
        final boolean allSameParent = components.stream().allMatch(c -> ((Component) c).getParent() == ((Component) components.get(0)).getParent());
        //Dont show moveout if not all components are in the same group
        if (components.stream().anyMatch(c -> summaryController.getGroupManager().getParentOf(c) != null) && allSameParent) {
            final JMenuItem moveOutItem = helper.getMoveOutMenuItem();
            addUnselectListener(moveOutItem);
            add(moveOutItem);
        }
        final JMenu moveInMenu = helper.getMoveInMenu();
        if (moveInMenu != null && allSameParent) {
            addUnselectListener(moveInMenu);
            add(moveInMenu);
        }
        final JMenu moveMenu = helper.getMoveToMenu();
        addUnselectListener(moveMenu);
        if (moveMenu.getMenuComponentCount() > 0) add(moveMenu);
        final JMenuItem showHideTextBlockMenuItem = helper.getShowTextBlockMenuItem();
        add(showHideTextBlockMenuItem);
        final JMenuItem showHeaderMenuItem = helper.getShowHeaderMenuItem();
        add(showHeaderMenuItem);
        addSeparator();
        final JMenuItem deleteMenuItem = helper.getDeleteMenuItem();
        addUnselectListener(deleteMenuItem);
        add(deleteMenuItem);
        addSeparator();
        // Dont show create group if components are in different groups or not in root
        if (allSameParent && summaryController.getGroupManager().getParentOf(components.get(0)) == null) {
            final JMenuItem createGroupMenuItem = helper.getCreateGroupMenuItem();
            addUnselectListener(createGroupMenuItem);
            add(createGroupMenuItem);
            final JMenu linkMenu = helper.getLinkWithMenu();
            if (linkMenu != null) {
                addUnselectListener(linkMenu);
                add(linkMenu);
            }
            final JMenu linkAllMenu = helper.getLinkAllWithMenu(components.indexOf(clicked));
            if (linkAllMenu != null) {
                addUnselectListener(linkAllMenu);
                add(linkAllMenu);
            }
            if (summaryController.getDragAndLinkManager().isLinked(clicked)) {
                add(helper.getUnlinkMenuItem());
            }
        }
    }

    private void addUnselectListener(final Component menuItem) {
        if (menuItem instanceof JMenu) {
            Arrays.stream(((JMenu) menuItem).getMenuComponents()).forEach(this::addUnselectListener);
        } else if (menuItem instanceof JMenuItem) {
            ((JMenuItem) menuItem).addActionListener(actionEvent -> draggablePanelController.clearSelectedComponents());
        }
    }
}
