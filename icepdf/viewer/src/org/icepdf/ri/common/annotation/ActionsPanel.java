package org.icepdf.ri.common.annotation;

import org.icepdf.core.pobjects.actions.ActionFactory;
import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.views.swing.AnnotationComponentImpl;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Actions panel manages an annotations actions as annotation can have zero
 * or more annotations.  The pannel allows a user  add, edit and remove
 * actions for the selected annotation.
 *
 * @since 4.0
 */
public class ActionsPanel extends AnnotationPanelAdapter
        implements ListSelectionListener, ActionListener {

    private static final Logger logger =
            Logger.getLogger(ActionsPanel.class.toString());

    private SwingController controller;
    private ResourceBundle messageBundle;

    // current annotation pointer
    private AnnotationComponentImpl currentAnnotaiton;

    // actionList of action actions
    private DefaultListModel actionListModel;
    private JList actionList;

    // add, edit, remove buttons.
    private JButton addAction;
    private JButton editAction;
    private JButton removeAction;

    // action type descriptions
    private String destinationLabel;
    private String uriActionLabel;
    private String goToActionLabel;

    public ActionsPanel(SwingController controller) {
        super(new GridLayout(2, 1, 5, 5), true);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

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
    }

    /**
     * Sets the current annotation component.  The current annotation component
     * is used to build the associated action list and of which all action
     * edits act on.
     *
     * @param annotaiton current action, should not be null.
     */
    public void setAnnotationComponent(AnnotationComponentImpl annotaiton) {

        currentAnnotaiton = annotaiton;

        // remove previous old annotations
        actionListModel.removeAllElements();

        // get annotations from action
        if (annotaiton.getAnnotation() != null &&
                annotaiton.getAnnotation().getAction() != null) {
            addAnnotationToList(annotaiton.getAnnotation().getAction());
        }
        // check to see if the link annotation "dest" key is present.
        else if (annotaiton.getAnnotation() != null &&
                annotaiton.getAnnotation() instanceof LinkAnnotation) {
            LinkAnnotation linkAnnotaiton = (LinkAnnotation)
                    annotaiton.getAnnotation();
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

        if (currentAnnotaiton == null) {
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
    }

    /**
     * Changes events that occur whena user selects an annotation's action in
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
        Object[] possibilities = {
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.type.goToAction.label"),
                        ActionFactory.GOTO_ACTION),
                new ActionChoice(
                        messageBundle.getString(
                                "viewer.utilityPane.action.type.uriAction.label"),
                        ActionFactory.URI_ACTION)};
        ActionChoice actionType = (ActionChoice) JOptionPane.showInputDialog(
                controller.getViewerFrame(),
                messageBundle.getString("viewer.utilityPane.action.dialog.new.msgs"),
                messageBundle.getString("viewer.utilityPane.action.dialog.new.title"),
                JOptionPane.PLAIN_MESSAGE, null,
                possibilities, null);
        // create and show a new GOTO action
        if (actionType != null &&
                actionType.getActionType() == ActionFactory.GOTO_ACTION) {

        } // create and show a new URI action
        else if (actionType != null &&
                actionType.getActionType() == ActionFactory.URI_ACTION) {
            // show URI dialog
            String uriString = showURIActionDialog(null);
            // finally do all the lifting for adding a new action for the curren
            // action
            if (uriString != null && currentAnnotaiton != null) {
                // create a new instance of the action type
                org.icepdf.core.pobjects.actions.URIAction uriAction = (URIAction)
                        ActionFactory.buildAction(
                                currentAnnotaiton.getAnnotation().getLibrary(),
                                ActionFactory.URI_ACTION);
                // get action and add the new action
                uriAction.setURI(uriString);
                currentAnnotaiton.getAnnotation().addAction(uriAction);
                // add the new action to the list.
                actionListModel.addElement(new ActionEntry(
                        messageBundle.getString(
                                "viewer.utilityPane.action.type.uriAction.label"),
                        uriAction));
            }
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
                currentAnnotaiton.getAnnotation().updateAction(uriAction);
            }
        }
        // todo add checks for goto action and dest attributes.
    }

    /**
     * Shows a confirmation dialog and if confirmed the action will be removed
     * from the current annotation.
     */
    private void removeAction() {
        ActionEntry actionEntry = (ActionEntry) actionListModel.getElementAt(
                actionList.getSelectedIndex());
        org.icepdf.core.pobjects.actions.Action action =
                actionEntry.getAction();
        if (action != null) {
            boolean success =
                    currentAnnotaiton.getAnnotation().deleteAction(action);
            if (success) {
                actionListModel.removeElementAt(actionList.getSelectedIndex());
                actionList.setSelectedIndex(-1);
            }
        }
        // todo remove dest entry.
    }

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
        actionListModel = new DefaultListModel();
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

    private void addAnnotationToList(org.icepdf.core.pobjects.actions.Action action) {
        if (action instanceof GoToAction) {
            actionListModel.addElement(new ActionEntry(goToActionLabel, action));
        } else if (action instanceof URIAction) {
            actionListModel.addElement(new ActionEntry(uriActionLabel, action));
        }
        // todo check for an next entry
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


        ActionChoice(String title, int actionType) {
            super();
            this.actionType = actionType;
            this.title = title;
        }

        int getActionType() {
            return actionType;
        }

        public String toString() {
            return title;
        }
    }
}
