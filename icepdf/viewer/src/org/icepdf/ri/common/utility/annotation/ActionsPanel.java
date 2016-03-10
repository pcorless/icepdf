/*
 * Copyright 2006-2016 ICEsoft Technologies Inc.
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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.actions.*;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.ButtonWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.views.AnnotationComponent;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.logging.Logger;

/**
 * Actions panel manages an annotations actions as annotation can have zero
 * or more annotations.  The pannel allows a user  add, edit and remove
 * actions for the selected annotation.
 *
 * @since 4.0
 */
@SuppressWarnings("serial")
public class ActionsPanel extends AnnotationPanelAdapter
        implements ListSelectionListener, ActionListener {

    private static final Logger logger =
            Logger.getLogger(ActionsPanel.class.toString());

    // actionList of action actions
    private DefaultListModel<ActionEntry> actionListModel;
    private JList actionList;

    // add, edit, remove buttons.
    private JButton addAction;
    private JButton editAction;
    private JButton removeAction;

    // action type descriptions
    private String destinationLabel;
    private String uriActionLabel;
    private String goToActionLabel;
    private String launchActionLabel;
    private String resetFormActionLabel;
    private String goToRActionLabel;
    private String javaScriptActionLabel;
    private String namedActionLabel;
    private String submitFormLabel;

    // Goto action dialog
    private GoToActionDialog goToActionDialog;
    private SubmitFormActionDialog submitFormActionDialog;
    private ResetFormActionDialog resetFormActionDialog;

    public ActionsPanel(SwingController controller) {
        super(controller);
        setLayout(new GridLayout(2, 1, 5, 5));

        // Setup the basics of the panel
        setFocusable(true);

        // Add the tabbed pane to the overall panel
        createGUI();

        // Start the panel disabled until an action is clicked
        this.setEnabled(false);

        // assign language values for supported action types
        destinationLabel = messageBundle.getString("viewer.utilityPane.action.type.destination.label");
        uriActionLabel = messageBundle.getString("viewer.utilityPane.action.type.uriAction.label");
        goToActionLabel = messageBundle.getString("viewer.utilityPane.action.type.goToAction.label");
        launchActionLabel = messageBundle.getString("viewer.utilityPane.action.type.launchAction.label");
        resetFormActionLabel = messageBundle.getString("viewer.utilityPane.action.type.resetFormAction.label");
        goToRActionLabel = messageBundle.getString("viewer.utilityPane.action.type.goToRAction.label");
        javaScriptActionLabel = messageBundle.getString("viewer.utilityPane.action.type.javaScriptAction.label");
        namedActionLabel = messageBundle.getString("viewer.utilityPane.action.type.namedAction.label");
        submitFormLabel = messageBundle.getString("viewer.utilityPane.action.type.submitAction.label");

    }

    /**
     * Sets the current annotation component.  The current annotation component
     * is used to build the associated action list and of which all action
     * edits act on.
     *
     * @param annotation current action, should not be null.
     */
    public void setAnnotationComponent(AnnotationComponent annotation) {

        currentAnnotationComponent = annotation;

        // remove previous old annotations
        actionListModel.clear();

        // get annotations from action
        if (annotation.getAnnotation() != null &&
                annotation.getAnnotation().getAction() != null) {
            addActionToList(annotation.getAnnotation().getAction());
            // select first item in list. 
            if (actionListModel.size() > 0) {
                actionList.setSelectedIndex(0);
            }
        }
        // check to see if the link annotation "dest" key is present. as
        // we'll edit this field with the goToAction dialog
        else if (annotation.getAnnotation() != null &&
                annotation.getAnnotation() instanceof LinkAnnotation) {
            LinkAnnotation linkAnnotaiton = (LinkAnnotation)
                    annotation.getAnnotation();
            if (linkAnnotaiton.getDestination() != null) {
                actionListModel.addElement(new ActionEntry(destinationLabel, null));
            }
        }
        // refresh add/edit/remove buttons.
        refreshActionCrud();
    }

    /**
     * Handlers for add, edit and delete commands.
     *
     * @param e awt action event
     */
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (currentAnnotationComponent == null) {
            logger.warning("No annotation was selected, edit is not possible.");
            return;
        }

        if (source == addAction) {
            // does all dialog work for adding new action. 
            addAction();
        } else if (source == editAction) {
            // show the correct panel for the selected annotation
            editAction();
        } else if (source == removeAction) {
            // confirmation dialog
            int option = JOptionPane.showConfirmDialog(controller.getViewerFrame(),
                    messageBundle.getString("viewer.utilityPane.action.dialog.delete.msgs"),
                    messageBundle.getString("viewer.utilityPane.action.dialog.delete.title"),
                    JOptionPane.YES_NO_OPTION);
            // delete command.
            if (JOptionPane.YES_OPTION == option) {
                // start the delete process.
                removeAction();
            }
            // refresh button states
            refreshActionCrud();
        }
        updateCurrentAnnotation();
    }

    /**
     * Changes events that occur when a user selects an annotation's action in
     * the list actionList.
     *
     * @param e awt list event.
     */
    public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            if (actionList.getSelectedIndex() == -1) {
                //No selection, disable fire button.
                addAction.setEnabled(false);
                editAction.setEnabled(false);
                removeAction.setEnabled(false);
            } else {
                // we only can add one action to an annotation for now. 
                refreshActionCrud();
            }
        }
    }

    /**
     * Shows new action selection dialog and then the appropriate action type
     * dialog for creating/adding new actions to the current annotation.
     */
    private void addAction() {
        // show new action select dialog to select annotation type
        // and ultimately  the dialog edit panels.
        Object[] possibilities = buildActionChoices();
        // show the jOptionPane dialog
        ActionChoice actionType = (ActionChoice) JOptionPane.showInputDialog(
                controller.getViewerFrame(),
                messageBundle.getString("viewer.utilityPane.action.dialog.new.msgs"),
                messageBundle.getString("viewer.utilityPane.action.dialog.new.title"),
                JOptionPane.PLAIN_MESSAGE, null,
                possibilities, null);
        // use the action type to select the outcome for the action creation dialog.
        // create and show a new GOTO action
        if (actionType != null &&
                actionType.getActionType() == ActionFactory.GOTO_ACTION) {
            // create new instance of dialog if it hasn't been created.
            showGoToActionDialog();
        }
        // create and show a new URI action
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.URI_ACTION) {
            // show URI dialog
            String uriString = showURIActionDialog(null);
            // finally do all the lifting for adding a new action for the
            // current action
            if (uriString != null && currentAnnotationComponent != null) {
                // create a new instance of the action type
                org.icepdf.core.pobjects.actions.URIAction uriAction = (URIAction)
                        ActionFactory.buildAction(
                                currentAnnotationComponent.getAnnotation().getLibrary(),
                                ActionFactory.URI_ACTION);
                // get action and add the new action
                if (uriAction != null) {
                    uriAction.setURI(uriString);
                    currentAnnotationComponent.getAnnotation().addAction(uriAction);
                    // add the new action to the list.
                    actionListModel.addElement(new ActionEntry(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.uriAction.label"),
                            uriAction));
                }
            }
        }
        // create and show a new launch action
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.LAUNCH_ACTION) {
            // show URI dialog
            String fileString = showLaunchActionDialog(null);
            // finally do all the lifting for adding a new action for the
            // current action
            if (fileString != null && currentAnnotationComponent != null) {
                // create a new instance of the action type
                LaunchAction launchAction = (LaunchAction)
                        ActionFactory.buildAction(
                                currentAnnotationComponent.getAnnotation().getLibrary(),
                                ActionFactory.LAUNCH_ACTION);
                if (launchAction != null) {
                    // get action and add the new action
                    launchAction.setExternalFile(fileString);
                    currentAnnotationComponent.getAnnotation().addAction(launchAction);
                    // add the new action to the list.
                    actionListModel.addElement(new ActionEntry(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.launchAction.label"),
                            launchAction));
                }
            }
        }
        // create and show a new goto resource action
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.GOTO_R_ACTION) {
            // implement in the near future.
        }
        // create and show a JavaScript action dialog
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.JAVA_SCRIPT_ACTION) {
            // implement in the near future.
        }
        // create and show a named action dialog
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.NAMED_ACTION) {
            showNamedActionDialog();
        }
        // create and show a submit action dialog
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.SUBMIT_ACTION) {
            showSubmitFormActionDialog();
        }
        // create and show a reset action dialog
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.RESET_ACTION) {
            showResetFormActionDialog();
        }

    }

    /**
     * Shows the appropriate action type edit dialog.
     */
    private void editAction() {
        ActionEntry actionEntry = (ActionEntry) actionListModel.getElementAt(
                actionList.getSelectedIndex());
        org.icepdf.core.pobjects.actions.Action action =
                actionEntry.getAction();
        // show URI edit pane
        if (action instanceof URIAction) {
            URIAction uriAction = (URIAction) action;
            String oldURIValue = uriAction.getURI();
            String newURIValue = showURIActionDialog(oldURIValue);
            // finally do all the lifting to edit a uri change.
            if (newURIValue != null &&
                    !oldURIValue.equals(newURIValue)) {
                // create a new instance of the action type
                uriAction.setURI(newURIValue);
                currentAnnotationComponent.getAnnotation().updateAction(uriAction);
            }
        }
        // show goto dialog for goToAction or link annotation dest
        else if (action instanceof GoToAction || action == null) {
            // goToAction dialog handles the save action processing
            showGoToActionDialog();
        }
        // show URI edit pane
        if (action instanceof LaunchAction) {
            LaunchAction launchAction = (LaunchAction) action;
            String oldLaunchValue = launchAction.getExternalFile();
            String newLaunchValue = showLaunchActionDialog(oldLaunchValue);
            // finally do all the lifting to edit a launch change.
            if (newLaunchValue != null &&
                    !oldLaunchValue.equals(newLaunchValue)) {
                // create a new instance of the action type
                launchAction.setExternalFile(newLaunchValue);
                currentAnnotationComponent.getAnnotation().updateAction(launchAction);
            }
        } else if (action instanceof SubmitFormAction || action == null) {
            // goToAction dialog handles the save action processing
            showSubmitFormActionDialog();
        } else if (action instanceof ResetFormAction || action == null) {
            // goToAction dialog handles the save action processing
            showResetFormActionDialog();
        } else if (action instanceof NamedAction || action == null) {
            // goToAction dialog handles the save action processing
            showNamedActionDialog();
        }
    }

    /**
     * Shows a confirmation dialog and if confirmed the action will be removed
     * from the current annotation.
     */
    private void removeAction() {
        ActionEntry actionEntry = actionListModel.getElementAt(
                actionList.getSelectedIndex());
        org.icepdf.core.pobjects.actions.Action action =
                actionEntry.getAction();
        if (action != null) {
            boolean success =
                    currentAnnotationComponent.getAnnotation().deleteAction(action);
            if (success) {
                actionListModel.removeElementAt(actionList.getSelectedIndex());
                actionList.setSelectedIndex(-1);
            }
        }
        // we must have a destination and will try and delete it.
        else if (currentAnnotationComponent.getAnnotation() instanceof LinkAnnotation) {
            LinkAnnotation linkAnnotation = (LinkAnnotation)
                    currentAnnotationComponent.getAnnotation();
            // remove the dest key and save the action, currently we don't
            // use the annotationState object to reflect his change.
            linkAnnotation.getEntries().remove(LinkAnnotation.DESTINATION_KEY);
            updateCurrentAnnotation();
            // update the list
            actionListModel.removeElementAt(actionList.getSelectedIndex());
            actionList.setSelectedIndex(-1);
        }
    }

    private Object[] buildActionChoices() {
        // action types for non-form annotations.
        if (currentAnnotationComponent != null &&
                !(currentAnnotationComponent.getAnnotation() instanceof AbstractWidgetAnnotation)) {
            return new Object[]{
                    new ActionChoice(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.goToAction.label"),
                            ActionFactory.GOTO_ACTION),
                    new ActionChoice(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.launchAction.label"),
                            ActionFactory.LAUNCH_ACTION),
//                new ActionChoice(
//                        messageBundle.getString(
//                                "viewer.utilityPane.action.type.goToRAction.label"),
//                        ActionFactory.GOTO_R_ACTION),
                    new ActionChoice(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.uriAction.label"),
                            ActionFactory.URI_ACTION)};
        } else if (currentAnnotationComponent != null &&
                currentAnnotationComponent.getAnnotation() instanceof ButtonWidgetAnnotation) {
            return new Object[]{
//                new ActionChoice(
//                        messageBundle.getString(
//                                "viewer.utilityPane.action.type.javaScriptAction.label"),
//                        ActionFactory.JAVA_SCRIPT_ACTION),
                    new ActionChoice(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.namedAction.title"),
                            ActionFactory.NAMED_ACTION),
                    new ActionChoice(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.resetFormAction.label"),
                            ActionFactory.RESET_ACTION),
                    new ActionChoice(
                            messageBundle.getString(
                                    "viewer.utilityPane.action.type.submitAction.label"),
                            ActionFactory.SUBMIT_ACTION)};
        } else {
            return new Object[]{
//                new ActionChoice(
//                        messageBundle.getString(
//                                "viewer.utilityPane.action.type.javaScriptAction.label"),
//                        ActionFactory.JAVA_SCRIPT_ACTION)
            };
        }
    }

    /**
     * Utility to show the URIAction dialog for add and edits.
     *
     * @param oldURIValue default value to show in dialog text field.
     * @return new values typed by user.
     */
    private String showURIActionDialog(String oldURIValue) {
        return (String) JOptionPane.showInputDialog(
                controller.getViewerFrame(),
                messageBundle.getString(
                        "viewer.utilityPane.action.dialog.uri.msgs"),
                messageBundle.getString(
                        "viewer.utilityPane.action.dialog.uri.title"),
                JOptionPane.PLAIN_MESSAGE, null, null,
                oldURIValue);
    }

    /**
     * Utility to show the LaunchAction dialog for add and edits.
     *
     * @param oldLaunchValue default value to show in dialog text field.
     * @return new values typed by user.
     */
    private String showLaunchActionDialog(String oldLaunchValue) {
        return (String) JOptionPane.showInputDialog(
                controller.getViewerFrame(),
                messageBundle.getString(
                        "viewer.utilityPane.action.dialog.launch.msgs"),
                messageBundle.getString(
                        "viewer.utilityPane.action.dialog.launch.title"),
                JOptionPane.PLAIN_MESSAGE, null, null,
                oldLaunchValue);
    }

    private void showNamedActionDialog() {
        ActionChoice[] namedActions = {
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.named.type.nextPage.label"),
                        NamedAction.NEXT_PAGE_KEY),
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.named.type.prevPage.label"),
                        NamedAction.PREV_PAGE_KEY),
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.named.type.firstPage.label"),
                        NamedAction.FIRST_PAGE_KEY),
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.named.type.lastPage.label"),
                        NamedAction.LAST_PAGE_KEY),
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.named.type.saveAs.label"),
                        NamedAction.SAVE_AS_KEY),
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.dialog.named.type.print.label"),
                        NamedAction.PRINT_KEY)
        };
        // check for previous value, in case we are editing.
        ActionChoice oldValue = null;
        if (currentAnnotationComponent.getAnnotation().getAction() != null) {
            NamedAction namedAction = (NamedAction) currentAnnotationComponent.getAnnotation().getAction();
            for (ActionChoice action : namedActions) {
                if (action.equals(namedAction.getNamedAction())) {
                    oldValue = action;
                    break;
                }
            }
        }
        // show the jOptionPane dialog
        ActionChoice namedActionType = (ActionChoice) JOptionPane.showInputDialog(
                controller.getViewerFrame(),
                messageBundle.getString("viewer.utilityPane.action.dialog.named.msgs"),
                messageBundle.getString("viewer.utilityPane.action.dialog.named.title"),
                JOptionPane.PLAIN_MESSAGE, null,
                namedActions, oldValue);
        // finally do all the lifting for adding a new action for the
        // current action
        if (namedActionType != null && currentAnnotationComponent != null) {

            if (currentAnnotationComponent.getAnnotation().getAction() == null) {
                // create a new instance of the action type
                NamedAction namedAction = (NamedAction)
                        ActionFactory.buildAction(
                                currentAnnotationComponent.getAnnotation().getLibrary(),
                                ActionFactory.NAMED_ACTION);
                if (namedAction != null) {
                    namedAction.setName(namedActionType.getActionName());
                    currentAnnotationComponent.getAnnotation().addAction(namedAction);
                    // add the new action to the list.
                    MessageFormat formatter = new MessageFormat(messageBundle.getString(
                            "viewer.utilityPane.action.type.namedAction.label"));
                    actionListModel.addElement(new ActionEntry(
                            formatter.format(new Object[]{namedAction.getNamedAction()}),
                            namedAction));
                    // disable add button.
                    addAction.setEnabled(false);
                }
            } else {
                NamedAction namedAction = (NamedAction) currentAnnotationComponent.getAnnotation().getAction();
                namedAction.setName(namedActionType.getActionName());
                currentAnnotationComponent.getAnnotation().updateAction(namedAction);

            }
        }

    }

    private void showGoToActionDialog() {
        // create new instance of dialog if it hasn't been created.
        if (goToActionDialog != null) {
            goToActionDialog.dispose();
        }
        goToActionDialog = new GoToActionDialog(controller, this);
        // set the new annotation
        goToActionDialog.setAnnotationComponent(currentAnnotationComponent);
        // make it visible.
        goToActionDialog.setVisible(true);
    }

    private void showSubmitFormActionDialog() {
        // create new instance of dialog if it hasn't been created.
        if (submitFormActionDialog != null) {
            submitFormActionDialog.dispose();
        }
        submitFormActionDialog = new SubmitFormActionDialog(controller, this);
        // set the new annotation
        submitFormActionDialog.setAnnotationComponent(currentAnnotationComponent);
        // make it visible.
        submitFormActionDialog.setVisible(true);
    }

    public void showResetFormActionDialog() {
        // create new instance of dialog if it hasn't been created.
        if (resetFormActionDialog != null) {
            resetFormActionDialog.dispose();
        }
        resetFormActionDialog = new ResetFormActionDialog(controller, this);
        // set the new annotation
        resetFormActionDialog.setAnnotationComponent(currentAnnotationComponent);
        // make it visible.
        resetFormActionDialog.setVisible(true);
    }

    /**
     * Refreshes add,edit and remove button state based on the number of actions
     * currently in the action list.
     */
    private void refreshActionCrud() {
        // we only can add one action to an annotation for now.
        addAction.setEnabled(actionListModel.getSize() == 0);
        editAction.setEnabled(actionListModel.getSize() > 0);
        removeAction.setEnabled(actionListModel.getSize() > 0);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // apply to child components.
        actionList.setEnabled(enabled);
        actionList.setSelectedIndex(-1);
        // only enable if there is a selected index.
        boolean isSelectedIndex = actionList.getSelectedIndex() != -1;
        addAction.setEnabled(enabled && actionListModel.getSize() == 0);
        editAction.setEnabled(enabled && isSelectedIndex);
        removeAction.setEnabled(enabled && isSelectedIndex);
    }

    /**
     * Method to create link annotation GUI.
     */
    private void createGUI() {

        // border for container.
        setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED),
                messageBundle.getString("viewer.utilityPane.action.selectionTitle"),
                TitledBorder.LEFT,
                TitledBorder.DEFAULT_POSITION));

        // create the new list
        actionListModel = new DefaultListModel<ActionEntry>();
        actionList = new JList(actionListModel);
        actionList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        actionList.setVisibleRowCount(-1);
        actionList.addListSelectionListener(this);
        JScrollPane listScroller = new JScrollPane(actionList);
        listScroller.setPreferredSize(new Dimension(150, 50));
        add(listScroller);

        // create action manipulator buttons.
        addAction = new JButton(messageBundle.getString(
                "viewer.utilityPane.action.addAction"));
        addAction.setEnabled(false);
        addAction.addActionListener(this);
        editAction = new JButton(messageBundle.getString(
                "viewer.utilityPane.action.editAction"));
        editAction.setEnabled(false);
        editAction.addActionListener(this);
        removeAction = new JButton(messageBundle.getString(
                "viewer.utilityPane.action.removeAction"));
        removeAction.setEnabled(false);
        removeAction.addActionListener(this);
        // panel for buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(addAction);
        buttonPanel.add(editAction);
        buttonPanel.add(removeAction);
        add(buttonPanel);

        revalidate();
    }

    /**
     * Clear the action list of all action items.
     */
    public void clearActionList() {
        actionListModel.clear();
    }

    /**
     * Add an action to the list.
     *
     * @param action action object to add.
     */
    public void addActionToList(org.icepdf.core.pobjects.actions.Action action) {
        if (action instanceof GoToAction) {
            actionListModel.addElement(new ActionEntry(goToActionLabel, action));
        } else if (action instanceof URIAction) {
            actionListModel.addElement(new ActionEntry(uriActionLabel, action));
        } else if (action instanceof LaunchAction) {
            actionListModel.addElement(new ActionEntry(launchActionLabel, action));
        } else if (action instanceof ResetFormAction) {
            actionListModel.addElement(new ActionEntry(resetFormActionLabel, action));
        } else if (action instanceof GoToRAction) {
            actionListModel.addElement(new ActionEntry(goToRActionLabel, action));
        } else if (action instanceof JavaScriptAction) {
            actionListModel.addElement(new ActionEntry(javaScriptActionLabel, action));
        } else if (action instanceof NamedAction) {
            actionListModel.addElement(new ActionEntry(namedActionLabel, action));
        } else if (action instanceof SubmitFormAction) {
            actionListModel.addElement(new ActionEntry(submitFormLabel, action));
        }
        // todo check for an next entry
        // todo add a "none" entry
    }

    /**
     * Action entries used with the actionList component.
     */
    class ActionEntry {

        // The text to be displayed on the screen for this item.
        String title;

        // The destination to be displayed when this item is activated
        org.icepdf.core.pobjects.actions.Action action;

        /**
         * Creates a new instance of a FindEntry.
         *
         * @param title of found entry
         */
        ActionEntry(String title) {
            super();
            this.title = title;
        }

        ActionEntry(String title, org.icepdf.core.pobjects.actions.Action action) {
            this.action = action;
            this.title = title;
        }

        org.icepdf.core.pobjects.actions.Action getAction() {
            return action;
        }

        public String toString() {
            return title;
        }
    }

    /**
     * An Entry objects for the different action types, used in dialog
     * for creating/adding new actions.
     */
    class ActionChoice {

        // The text to be displayed on the screen for this item.
        String title;

        // The destination to be displayed when this item is activated
        int actionType;

        Name actionName;


        ActionChoice(String title, int actionType) {
            super();
            this.actionType = actionType;
            this.title = title;
        }

        ActionChoice(String title, Name actionName) {
            super();
            this.actionName = actionName;
            this.title = title;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Name) {
                return actionName.equals((Name) obj);
            }
            return super.equals(obj);
        }

        int getActionType() {
            return actionType;
        }

        public Name getActionName() {
            return actionName;
        }

        public String toString() {
            return title;
        }
    }
}
