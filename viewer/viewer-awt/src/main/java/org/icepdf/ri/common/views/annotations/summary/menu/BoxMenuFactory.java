package org.icepdf.ri.common.views.annotations.summary.menu;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.annotations.MarkupAnnotationComponent;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryBox;
import org.icepdf.ri.common.views.annotations.summary.AnnotationSummaryComponent;
import org.icepdf.ri.common.views.annotations.summary.colorpanel.DraggablePanelController;
import org.icepdf.ri.common.views.annotations.summary.mainpanel.SummaryController;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;


/**
 * The summary view is made up of annotation contents for markup annotations.  The view however is built independently
 * of the the page view and the component state may not be in correct state to use the default MarkupAnnotationPopupMenu
 * <p>
 * This class takes into account that the component state is not guaranteed.
 *
 * @since 6.3
 * <p>
 * <p>
 * Allows creating a JPopupMenu or a JMenu depending on the context
 */
public final class BoxMenuFactory {

    private BoxMenuFactory() {
    }

    public static JMenu createBoxMenu(final AnnotationSummaryBox annotationSummaryBox,
                                      final MarkupAnnotationComponent annotationComponent, final Frame frame,
                                      final DraggablePanelController panel, final SummaryController summaryController,
                                      final String name) {
        final JMenu menu = new JMenu(name);
        buildGui(annotationSummaryBox, annotationComponent, frame, panel, summaryController).forEach(menu::add);
        return menu;
    }

    public static JPopupMenu createBoxPopupMenu(final AnnotationSummaryBox annotationSummaryBox,
                                                final MarkupAnnotationComponent annotationComponent, final Frame frame,
                                                final DraggablePanelController panel, final SummaryController summaryController) {
        final JPopupMenu menu = new JPopupMenu();
        buildGui(annotationSummaryBox, annotationComponent, frame, panel, summaryController).forEach(menu::add);
        return menu;
    }

    public static List<JMenuItem> buildGui(final AnnotationSummaryBox annotationSummaryBox, final MarkupAnnotationComponent annotationComponent,
                                           final Frame frame, final DraggablePanelController panel, final SummaryController summaryController) {
        final List<JMenuItem> list = new ArrayList<>();
        final Controller controller = summaryController.getController();
        final MenuFactoryHelper helper = new MenuFactoryHelper(summaryController, panel, Arrays.asList(new AnnotationSummaryBox[]{annotationSummaryBox}));
        final ResourceBundle messageBundle = controller.getMessageBundle();
        if (summaryController.getGroupManager().getParentOf(annotationSummaryBox) != null) {
            final JMenuItem moveOutItem = helper.getMoveOutMenuItem();
            list.add(moveOutItem);
        }
        helper.addCommonMenu(list);
        final JMenuItem propertiesMenuItem = new JMenuItem(messageBundle.getString("viewer.annotation.popup.properties.label"));
        propertiesMenuItem.addActionListener(e -> controller.showAnnotationProperties(annotationComponent));
        list.add(propertiesMenuItem);
        propertiesMenuItem.addActionListener(e -> {
            controller.showAnnotationProperties(annotationComponent, frame);
            summaryController.refreshDocumentInstance();
        });
        final JMenuItem createGroupMenuItem = new JMenuItem(messageBundle.getString("viewer.summary.popup.group.label"));
        createGroupMenuItem.addActionListener(e -> {
            final List<AnnotationSummaryComponent> components = new ArrayList<>();
            components.add(annotationSummaryBox);
            final List<Component> cComponents = components.stream().map(AnnotationSummaryComponent::asComponent).collect(Collectors.toList());
            summaryController.getGroupManager().createGroup(components, panel.getPanel().getFirstPosForComponents(cComponents));
        });
        list.add(createGroupMenuItem);
        return helper.addLinkMenu(list, summaryController.getGroupManager().getParentOf(annotationSummaryBox), summaryController.getDragAndLinkManager().isLinked(annotationSummaryBox));
    }


}
