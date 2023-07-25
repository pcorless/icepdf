package org.icepdf.ri.common.views.annotations.summary.menu;

import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryGroup;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggablePanelController;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.GroupManager;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Creates a JPopupMenu or a JMenu for a group depending on the context
 */
public final class GroupMenuFactory {

    private GroupMenuFactory() {
    }

    public static JMenu createGroupMenu(final AnnotationSummaryGroup group, final SummaryController summaryController,
                                        final DraggablePanelController panel, final String name) {
        final JMenu menu = new JMenu(name);
        buildGui(group, summaryController, panel).forEach(menu::add);
        return menu;
    }

    public static JPopupMenu createGroupPopupMenu(final AnnotationSummaryGroup group, final SummaryController summaryController,
                                                  final DraggablePanelController panel) {
        final JPopupMenu menu = new JPopupMenu();
        buildGui(group, summaryController, panel).forEach(menu::add);
        return menu;
    }

    public static List<JMenuItem> buildGui(final AnnotationSummaryGroup group, final SummaryController summaryController,
                                           final DraggablePanelController panel) {
        final MenuFactoryHelper helper = new MenuFactoryHelper(summaryController, panel, Arrays.asList(new AnnotationSummaryGroup[]{group}));
        final List<JMenuItem> list = new ArrayList<>();
        final ResourceBundle messageBundle = summaryController.getController().getMessageBundle();
        final GroupManager groupManager = summaryController.getGroupManager();
        final JMenuItem renameMenuItem = new JMenuItem(messageBundle.getString("viewer.summary.popup.group.rename"));
        renameMenuItem.addActionListener(e -> groupManager.renameGroup(group));
        list.add(renameMenuItem);
        final JMenuItem disbandMenuItem = new JMenuItem(messageBundle.getString("viewer.summary.popup.group.disband"));
        disbandMenuItem.addActionListener(e -> groupManager.disbandGroup(group));
        list.add(disbandMenuItem);
        if (groupManager.getParentOf(group) != null) {
            final JMenuItem moveOutItem = helper.getMoveOutMenuItem();
            list.add(moveOutItem);
        }
        helper.addCommonMenu(list);
        return helper.addLinkMenu(list, summaryController.getGroupManager().getParentOf(group),
                summaryController.getDragAndLinkManager().isLinked(group));
    }
}
