/*
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * "The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations under
 * the License.
 *
 * The Original Code is ICEpdf 4.1 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2010 ICEsoft Technologies Canada, Corp. All Rights Reserved.
 *
 * Contributor(s): _____________________.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"
 * License), in which case the provisions of the LGPL License are
 * applicable instead of those above. If you wish to allow use of your
 * version of this file only under the terms of the LGPL License and not to
 * allow others to use your version of this file under the MPL, indicate
 * your decision by deleting the provisions above and replace them with
 * the notice and other provisions required by the LGPL License. If you do
 * not delete the provisions above, a recipient may use your version of
 * this file under either the MPL or the LGPL License."
 *
 */
package org.icepdf.ri.common.annotation;

import org.icepdf.core.pobjects.NameTree;
import org.icepdf.ri.common.SwingController;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ResourceBundle;

/**
 * Create a dialog containg a name tree for the open document.  If no name
 * tree exists then no tree is shown.
 *
 * @since 4.0
 */
public class NameTreeDialog extends JDialog
        implements ActionListener, TreeSelectionListener {

    private SwingController controller;
    private ResourceBundle messageBundle;

    private JTree nameJTree;
    private NameTreeNode selectedName;
    private JLabel destinationName;
    private JButton okButton;
    private JButton cancelButton;

    private GridBagConstraints constraints;

    public NameTreeDialog(SwingController controller, boolean modal, NameTree nameTree)
            throws HeadlessException {
        super(controller.getViewerFrame(), modal);
        this.controller = controller;
        this.messageBundle = this.controller.getMessageBundle();

        setGui(nameTree);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == okButton) {
            // assign the selected name. 
            if (selectedName != null) {
                destinationName.setText(selectedName.getName().toString());
            }
            setVisible(false);
            dispose();
        } else if (e.getSource() == cancelButton) {
            setVisible(false);
            dispose();
        }
    }

    // Listen for selected tree items
    public void valueChanged(TreeSelectionEvent e) {
        // jump to the page stored in the JTree
        if (nameJTree.getLastSelectedPathComponent() != null) {
            NameTreeNode selectedNode = ((NameTreeNode)
                    nameJTree.getLastSelectedPathComponent());
            // make sure we have name leaf and not a intermediate node.
            if (selectedNode.getReference() != null) {
                selectedName = selectedNode;
            } else {
                // null the selection. 
                nameJTree.setSelectionPath(null);
                selectedName = null;
            }
        }
    }

    private void setGui(NameTree nameTree) {

         // dialog title
         setTitle(messageBundle.getString(
                "viewer.utilityPane.action.dialog.goto.nameTree.title"));

        // build the name tree. 
        nameJTree = new NameJTree();
        nameJTree.setModel(new DefaultTreeModel(
                new NameTreeNode(nameTree.getRoot(), messageBundle)));
        nameJTree.setRootVisible(!nameTree.getRoot().isEmpty());
        nameJTree.addTreeSelectionListener(this);
        JScrollPane nameTreeScroller = new JScrollPane(nameJTree);
        nameTreeScroller.setPreferredSize(new Dimension(325, 225));

        // ok / cancel layout.
        okButton = new JButton(messageBundle.getString("viewer.button.ok.label"));
        okButton.setMnemonic(messageBundle.getString("viewer.button.ok.mnemonic").charAt(0));
        okButton.addActionListener(this);
        cancelButton = new JButton(messageBundle.getString("viewer.button.cancel.label"));
        cancelButton.setMnemonic(messageBundle.getString("viewer.button.cancel.mnemonic").charAt(0));
        cancelButton.addActionListener(this);
        // panel for OK and cancel
        JPanel okCancelPanel = new JPanel(new FlowLayout());
        okCancelPanel.add(okButton);
        okCancelPanel.add(cancelButton);

        JPanel nameTreePanel = new JPanel();
        nameTreePanel.setAlignmentY(JPanel.TOP_ALIGNMENT);
        GridBagLayout layout = new GridBagLayout();
        nameTreePanel.setLayout(layout);

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(5, 5, 5, 5);

        constraints.anchor = GridBagConstraints.CENTER;
        addGB(nameTreePanel, nameTreeScroller, 0, 0, 1, 1);
        addGB(nameTreePanel, okCancelPanel, 0, 1, 1, 1);

        this.getContentPane().add(nameTreePanel);

//        pack();
        setSize(new Dimension(375, 350));
        validate();
        setLocationRelativeTo(controller.getViewerFrame());

    }

    public void setDestinationName(JLabel destinationName) {
        this.destinationName = destinationName;
    }

    /**
     * Override createRootPane so that "escape" key can be used to
     * close this window.
     */
    protected JRootPane createRootPane() {
        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                setVisible(false);
                dispose();
            }
        };
        JRootPane rootPane = new JRootPane();
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        rootPane.registerKeyboardAction(actionListener, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
        return rootPane;
    }


    private void addGB(JPanel layout, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = rowSpan;
        constraints.gridheight = colSpan;
        layout.add(component, constraints);
    }
}