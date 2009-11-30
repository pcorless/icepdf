package org.icepdf.ri.common.annotation;

import org.icepdf.core.pobjects.actions.GoToAction;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.views.swing.AnnotationComponentImpl;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.border.TitledBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ResourceBundle;

/**
 * Actions panel manages an annotations actions as annotation can have zero
 * or more annotations.  The pannel allows a user  add, edit and remove
 * actions for the selected annotation.
 *
 * @since 4.0
 */
public class ActionsPanel extends AnnotationPanelAdapter
        implements ListSelectionListener, ActionListener {

    private SwingController controller;
    private ResourceBundle messageBundle;

    // actionList of action actions
    private DefaultListModel actionListModel;
    private JList actionList;

    // add, edit, remove buttons.
    private JButton addAction;
    private JButton editAction;
    private JButton removeAction;

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

    }

    public AnnotationPanelAdapter buildActionPropertyPanel(AnnotationComponentImpl annotationComp) {
        if (annotationComp != null && annotationComp.getAnnotation() != null) {
            // check action type
            org.icepdf.core.pobjects.actions.Action action =
                    annotationComp.getAnnotation().getAction();
            if (action != null && action instanceof URIAction) {
                return new URIActionPanel(controller);
            } else if (action != null && action instanceof GoToAction) {
                return new GoToActionPanel(controller);
            }
            // todo add other panels for other action types.
        }
        // default panel
        return new LinkAnnotationPanel(controller);
    }

    public void setAnnotationComponent(AnnotationComponentImpl annotaiton) {
        // remove previous old annotations 
        actionListModel.removeAllElements();

        // get annotations from action
        if (annotaiton.getAnnotation() != null &&
                annotaiton.getAnnotation().getAction() != null){
            addAnnotationToList(annotaiton.getAnnotation().getAction());
        }
        // check to see if the link annotation "dest" key is present.
        else if(annotaiton.getAnnotation() != null &&
                annotaiton.getAnnotation() instanceof LinkAnnotation){
            LinkAnnotation linkAnnotaiton = (LinkAnnotation)
                    annotaiton.getAnnotation();
            if (linkAnnotaiton.getDestination() != null){
                actionListModel.addElement(new ActionEntry("Dest", null));
            }
        }

    }

    public void actionPerformed(ActionEvent e) {
        // todo handle add edit and remove
    }


    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() == false) {

            if (actionList.getSelectedIndex() == -1) {
                //No selection, disable fire button.
                addAction.setEnabled(false);
                editAction.setEnabled(false);
                removeAction.setEnabled(false);

            } else {
                //Selection, enable the fire button.
                addAction.setEnabled(true);
                editAction.setEnabled(true);
                removeAction.setEnabled(true);

                // todo get pointer to current action.
            }
        }
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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        // apply to child components.
        actionList.setEnabled(enabled);
        // only enable if there is a selected index.
        boolean isSelectedIndex = actionList.getSelectedIndex() != -1;
        addAction.setEnabled(enabled && isSelectedIndex);
        editAction.setEnabled(enabled && isSelectedIndex);
        removeAction.setEnabled(enabled && isSelectedIndex);
    }

    private void addAnnotationToList(org.icepdf.core.pobjects.actions.Action action){
        if (action instanceof GoToAction){
            actionListModel.addElement(new ActionEntry("GoTo Action", action));
        }
        else if(action instanceof URIAction){
            actionListModel.addElement(new ActionEntry("URI Action", action));
        }
        // todo check for an next entry
    }

    /**
     * An Entry objects represents the found pages
     */
    class ActionEntry extends DefaultMutableTreeNode {

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
            setUserObject(title);
        }

        ActionEntry(String title, org.icepdf.core.pobjects.actions.Action action) {
            super();
            this.action = action;
            this.title = title;
            setUserObject(title);
        }

        org.icepdf.core.pobjects.actions.Action getAction() {
            return action;
        }
    }
}
