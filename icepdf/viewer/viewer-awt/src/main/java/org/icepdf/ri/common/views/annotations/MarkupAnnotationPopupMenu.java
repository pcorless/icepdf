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
package org.icepdf.ri.common.views.annotations;

import org.icepdf.core.pobjects.annotations.FreeTextAnnotation;
import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.ri.common.tools.DestinationHandler;
import org.icepdf.ri.common.tools.FreeTextAnnotationHandler;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.PageViewComponentImpl;
import org.icepdf.ri.images.Images;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Markup specific annotation context menu support, includes delete and properties commands,
 * status and reply command and minimize and show all popups.
 *
 * @since 6.3
 */
@SuppressWarnings("serial")
public class MarkupAnnotationPopupMenu extends AnnotationPopup<MarkupAnnotationComponent> {

    private static final Logger logger =
            Logger.getLogger(MarkupAnnotationPopupMenu.class.toString());

    // reply commands
    protected JMenuItem replyMenuItem;
    // status change commands.
    protected JMenuItem statusNoneMenuItem;
    protected JMenuItem statusAcceptedItem;
    protected JMenuItem statusCancelledMenuItem;
    protected JMenuItem statusCompletedMenuItem;
    protected JMenuItem statusRejectedMenuItem;
    // generic commands, open/minimize all
    protected JMenuItem openAllMenuItem;
    protected JMenuItem minimizeAllMenuItem;
    // add/create annotation shortcuts
    protected JMenuItem addDestinationMenuItem;
    protected JMenuItem addFreeTextMenuItem1, addFreeTextMenuItem2;

    // delete root annotation and all child popup annotations.
    protected boolean deleteRoot;

    public MarkupAnnotationPopupMenu(MarkupAnnotationComponent markupAnnotationComponent, Controller controller,
                                     AbstractPageViewComponent pageViewComponent, boolean deleteRoot) {
        super(markupAnnotationComponent, controller, pageViewComponent);
        this.deleteRoot = deleteRoot;
        this.annotationComponent = markupAnnotationComponent;

        buildGui();
    }

    public void buildGui() {
        PropertiesManager propertiesManager = PropertiesManager.getInstance();
        boolean modifyDocument = controller.havePermissionToModifyDocument();

        // status change commands.
        statusNoneMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.none.label"));
        statusNoneMenuItem.setEnabled(modifyDocument);
        statusAcceptedItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.accepted.label"));
        statusAcceptedItem.setEnabled(modifyDocument);
        statusCancelledMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.cancelled.label"));
        statusCancelledMenuItem.setEnabled(modifyDocument);
        statusCompletedMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.completed.label"));
        statusCompletedMenuItem.setEnabled(modifyDocument);
        statusRejectedMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.rejected.label"));
        statusRejectedMenuItem.setEnabled(modifyDocument);
        // generic commands, open/minimize all
        openAllMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.openAll.label"));
        minimizeAllMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.minimizeAll.label"));

        // annotation and destination creation shortcuts.
        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_ANNOTATION_MARKUP_ADD_ANNOTATIONS)) {
            // annotation creation menus.
            addDestinationMenuItem = new JMenuItem(
                    messageBundle.getString("viewer.utilityPane.view.selectionTool.contextMenu.addDestination.label"));
            addDestinationMenuItem.setEnabled(modifyDocument);
            addDestinationMenuItem.addActionListener(this);
            addDestinationMenuItem.setIcon(new ImageIcon(Images.get("destination_20.png")));
            addFreeTextMenuItem1 = new JMenuItem(
                    messageBundle.getString("viewer.annotation.popup.addAnnotation.freeText.label"));
            addFreeTextMenuItem1.setEnabled(modifyDocument);
            addFreeTextMenuItem1.setIcon(new ImageIcon(Images.get("freetext_annot_a_20.png")));
            addFreeTextMenuItem1.addActionListener(this);
            addFreeTextMenuItem2 = new JMenuItem(
                    messageBundle.getString("viewer.annotation.popup.addAnnotation.freeText.label"));
            addFreeTextMenuItem2.setEnabled(modifyDocument);
            addFreeTextMenuItem2.setIcon(new ImageIcon(Images.get("freetext_annot_a_20.png")));
            addFreeTextMenuItem2.addActionListener(this);
            // addition of set status menu
            JMenu submenu = new JMenu(
                    messageBundle.getString("viewer.annotation.popup.addAnnotation.label"));
            addDestinationMenuItem.setEnabled(modifyDocument);
            submenu.add(addDestinationMenuItem);
            submenu.addSeparator();
            submenu.add(addFreeTextMenuItem2);
            add(addFreeTextMenuItem1);
            add(submenu);
            addSeparator();
        }

        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_ANNOTATION_MARKUP_REPLY_TO)) {
            replyMenuItem = new JMenuItem(
                    messageBundle.getString("viewer.annotation.popup.reply.label"));
            // build out reply and delete
            replyMenuItem.addActionListener(this);
            replyMenuItem.setEnabled(modifyDocument);
            add(replyMenuItem);
        }

        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_ANNOTATION_MARKUP_SET_STATUS)) {
            // addition of set status menu
            JMenu submenu = new JMenu(
                    messageBundle.getString("viewer.annotation.popup.status.label"));
            statusNoneMenuItem.addActionListener(this);
            submenu.add(statusNoneMenuItem);
            statusAcceptedItem.addActionListener(this);
            submenu.add(statusAcceptedItem);
            statusCancelledMenuItem.addActionListener(this);
            submenu.add(statusCancelledMenuItem);
            statusCompletedMenuItem.addActionListener(this);
            submenu.add(statusCompletedMenuItem);
            statusRejectedMenuItem.addActionListener(this);
            submenu.add(statusRejectedMenuItem);
            add(submenu);
            addSeparator();
        }

        // generic commands, open/minimize all
        openAllMenuItem.addActionListener(this);
        add(openAllMenuItem);
        minimizeAllMenuItem.addActionListener(this);
        add(minimizeAllMenuItem);

        // delete
        add(deleteMenuItem);
        deleteMenuItem.addActionListener(this);
        addSeparator();

        // properties
        add(propertiesMenuItem);
        propertiesMenuItem.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == null) return;

        if (annotationComponent == null) {
            logger.log(Level.WARNING, "Markup Annotation is null");
            return;
        }

        if (source == replyMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null) popupAnnotationComponent.replyToSelectedMarkupExecute();
        } else if (source == deleteMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null) popupAnnotationComponent.deleteSelectedMarkupExecute(deleteRoot);
        } else if (source == statusNoneMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null) popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.none.title"),
                    messageBundle.getString("viewer.annotation.popup.status.none.msg"),
                    TextAnnotation.STATE_REVIEW_NONE);
        } else if (source == statusAcceptedItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null) popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.accepted.title"),
                    messageBundle.getString("viewer.annotation.popup.status.accepted.msg"),
                    TextAnnotation.STATE_ACCEPTED);
        } else if (source == statusCancelledMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null) popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.cancelled.title"),
                    messageBundle.getString("viewer.annotation.popup.status.cancelled.msg"),
                    TextAnnotation.STATE_CANCELLED);
        } else if (source == statusCompletedMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.completed.title"),
                    messageBundle.getString("viewer.annotation.popup.status.completed.msg"),
                    TextAnnotation.STATE_COMPLETED);
        } else if (source == statusRejectedMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null) popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.rejected.title"),
                    messageBundle.getString("viewer.annotation.popup.status.rejected.msg"),
                    TextAnnotation.STATE_REJECTED);
        } else if (source == openAllMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            popupAnnotationComponent.showHidePopupAnnotations(true);
        } else if (source == minimizeAllMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null) popupAnnotationComponent.showHidePopupAnnotations(false);
        } else if (source == propertiesMenuItem) {
            PopupAnnotationComponent popupAnnotationComponent = annotationComponent.getPopupAnnotationComponent();
            if (popupAnnotationComponent != null)
                controller.showAnnotationProperties(popupAnnotationComponent.getAnnotationParentComponent());
        } else if (source == addDestinationMenuItem) {
            Point point = annotationComponent.getLocation();
            pageViewComponent = (PageViewComponentImpl) annotationComponent.getPageViewComponent();
            new DestinationHandler(controller.getDocumentViewController(),
                    pageViewComponent).createNewDestination(
                    annotationComponent.getAnnotation().getContents(), point.x, point.y);
        } else if (source == addFreeTextMenuItem1 ||
                source == addFreeTextMenuItem2) {
            Point point = annotationComponent.getLocation();
            Preferences preferences = PropertiesManager.getInstance().getPreferences();
            int fontSize = preferences.getInt(PropertiesManager.PROPERTY_ANNOTATION_FREE_TEXT_SIZE, 12) +
                    (FreeTextAnnotation.INSETS / 2);
            fontSize *= controller.getDocumentViewController().getZoom();
//            controller.setDocumentToolMode(DocumentViewModel.DISPLAY_TOOL_SELECTION);
            new FreeTextAnnotationHandler(controller.getDocumentViewController(), pageViewComponent)
                    .createFreeTextAnnotation(point.x, point.y - fontSize, false);
        }
    }
}
