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

import org.icepdf.core.pobjects.annotations.TextAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;
import org.icepdf.ri.common.views.PageViewComponentImpl;
import org.icepdf.ri.util.PropertiesManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * The MarkupAnnotationPopup is common JPopup menu that can be used by any annotation component.
 *
 * @since 6.3
 */
@SuppressWarnings("serial")
public class MarkupAnnotationPopup extends JPopupMenu implements ActionListener {

    protected PopupAnnotationComponent popupAnnotationComponent;
    // add and remove commands
    protected JMenuItem replyMenuItem;
    protected JMenuItem deleteMenuItem;
    // status change commands.
    protected JMenuItem statusNoneMenuItem;
    protected JMenuItem statusAcceptedItem;
    protected JMenuItem statusCancelledMenuItem;
    protected JMenuItem statusCompletedMenuItem;
    protected JMenuItem statusRejectedMenuItem;
    // generic commands, open/minimize all
    protected JMenuItem openAllMenuItem;
    protected JMenuItem minimizeAllMenuItem;

    protected PageViewComponentImpl pageViewComponent;
    protected DocumentViewController documentViewController;
    protected DocumentViewModel documentViewModel;
    protected ResourceBundle messageBundle;

    // delete root annot and all child popup annotatinos.
    protected boolean deletRoot;

    public MarkupAnnotationPopup(PopupAnnotationComponent popupAnnotationComponent, DocumentViewController documentViewController,
                                 AbstractPageViewComponent pageViewComponent, DocumentViewModel documentViewModel, boolean deleteRoot) {
        this.popupAnnotationComponent = popupAnnotationComponent;
        this.pageViewComponent = (PageViewComponentImpl) pageViewComponent;
        this.documentViewModel = documentViewModel;
        this.documentViewController = documentViewController;
        this.deletRoot = deleteRoot;
        messageBundle = documentViewController.getParentController().getMessageBundle();

        buildGui();
    }

    protected void buildGui() {
        PropertiesManager propertiesManager = PropertiesManager.getInstance();

        if (propertiesManager.checkAndStoreBooleanProperty(
                PropertiesManager.PROPERTY_SHOW_ANNOTATION_MARKUP_REPLY_TO)) {
            replyMenuItem = new JMenuItem(
                    messageBundle.getString("viewer.annotation.popup.reply.label"));
            // build out reply and delete
            replyMenuItem.addActionListener(this);
            add(replyMenuItem);
        }
        deleteMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.delete.label"));
        // status change commands.
        statusNoneMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.none.label"));
        statusAcceptedItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.accepted.label"));
        statusCancelledMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.cancelled.label"));
        statusCompletedMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.completed.label"));
        statusRejectedMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.status.rejected.label"));
        // generic commands, open/minimize all
        openAllMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.openAll.label"));
        minimizeAllMenuItem = new JMenuItem(
                messageBundle.getString("viewer.annotation.popup.minimizeAll.label"));

        // build out delete
        deleteMenuItem.addActionListener(this);
        add(deleteMenuItem);
        addSeparator();

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
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == null) return;

        if (source == replyMenuItem) {
            popupAnnotationComponent.replyToSelectedMarkupExecute();
        } else if (source == deleteMenuItem) {
            popupAnnotationComponent.deleteSelectedMarkupExecute(deletRoot);
        } else if (source == statusNoneMenuItem) {
            popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.none.title"),
                    messageBundle.getString("viewer.annotation.popup.status.none.msg"),
                    TextAnnotation.STATE_REVIEW_NONE);
        } else if (source == statusAcceptedItem) {
            popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.accepted.title"),
                    messageBundle.getString("viewer.annotation.popup.status.accepted.msg"),
                    TextAnnotation.STATE_ACCEPTED);
        } else if (source == statusCancelledMenuItem) {
            popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.cancelled.title"),
                    messageBundle.getString("viewer.annotation.popup.status.cancelled.msg"),
                    TextAnnotation.STATE_CANCELLED);
        } else if (source == statusCompletedMenuItem) {
            popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.completed.title"),
                    messageBundle.getString("viewer.annotation.popup.status.completed.msg"),
                    TextAnnotation.STATE_COMPLETED);
        } else if (source == statusRejectedMenuItem) {
            popupAnnotationComponent.setStatusSelectedMarkupExecute(
                    messageBundle.getString("viewer.annotation.popup.status.rejected.title"),
                    messageBundle.getString("viewer.annotation.popup.status.rejected.msg"),
                    TextAnnotation.STATE_REJECTED);
        } else if (source == openAllMenuItem) {
            popupAnnotationComponent.showHidePopupAnnotations(true);
        } else if (source == minimizeAllMenuItem) {
            popupAnnotationComponent.showHidePopupAnnotations(false);
        }
    }
}
