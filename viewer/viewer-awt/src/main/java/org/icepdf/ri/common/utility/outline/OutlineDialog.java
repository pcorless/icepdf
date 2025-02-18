package org.icepdf.ri.common.utility.outline;

import org.icepdf.core.pobjects.*;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.EscapeJDialog;
import org.icepdf.ri.common.NameJTree;
import org.icepdf.ri.common.NameTreeNode;
import org.icepdf.ri.common.utility.annotation.destinations.ImplicitDestinationPanel;
import org.icepdf.ri.common.utility.annotation.properties.ValueLabelItem;
import org.icepdf.ri.common.views.Controller;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ResourceBundle;

public class OutlineDialog extends EscapeJDialog implements ItemListener, TreeSelectionListener, ActionListener,
        FocusListener {

    private final ResourceBundle messageBundle;

    private static final String IMPLICIT_DESTINATION = "Implicit Destination";
    private static final int IMPLICIT_DESTINATION_INDEX = 0;
    private static final String NAMED_DESTINATION = "Named Destination";
    private static final int NAMED_DESTINATION_INDEX = 1;

    private final Controller controller;
    private boolean isNewOutlineItem = false;
    private final OutlineItemTreeNode outlineItemTreeNode;

    // copy of the destination for editing
    private JTree parentTree;
    private Destination outlineDestination;
    private String title;

    private JTextField titleTextField;
    private JComboBox<ValueLabelItem> destinationTypeComboBox;
    private JPanel destinationTypesCards;
    private ImplicitDestinationPanel implicitDestinationPanel;
    private JButton okButton;
    private JButton cancelButton;

    private GridBagConstraints constraints;


    public OutlineDialog(Controller controller, JTree parentTree, OutlineItemTreeNode outlineItemTreeNode) {
        this(controller, parentTree, outlineItemTreeNode, false);
    }

    public OutlineDialog(Controller controller, JTree parentTree, OutlineItemTreeNode outlineItemTreeNode,
                         boolean isNewOutlineItem)
            throws HeadlessException {
        super(controller.getViewerFrame());
        this.controller = controller;
        this.parentTree = parentTree;
        this.isNewOutlineItem = isNewOutlineItem;
        this.outlineItemTreeNode = outlineItemTreeNode;
        this.messageBundle = this.controller.getMessageBundle();

        // copy the destination for editing
        Destination destination = outlineItemTreeNode.getOutlineItem().getDest();
        if (destination != null) {
            outlineDestination = new Destination(destination.getLibrary(),
                    destination.getEntries().clone());
        } else {
            outlineDestination = new Destination(controller.getDocument().getCatalog().getLibrary(), "");
            outlineItemTreeNode.getOutlineItem().setDest(outlineDestination);
        }
        title = outlineItemTreeNode.getOutlineItem().getTitle();

        buildGui();

        implicitDestinationPanel.setDestination(outlineItemTreeNode.getOutlineItem().getDest());
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        if (actionEvent.getSource() == okButton) {
            // check the state of the destination type to see what should be updated.
            if (destinationTypeComboBox.getSelectedIndex() == NAMED_DESTINATION_INDEX) {
                // update the named destination
                outlineItemTreeNode.getOutlineItem().setDest(outlineDestination);
            } else {
                // get the updated destination
                Destination destination = implicitDestinationPanel.getDestination(outlineDestination.getLibrary());
                destination.clearNamedDestination();
                outlineItemTreeNode.getOutlineItem().setDest(destination);
            }
            // update the title
            outlineItemTreeNode.getOutlineItem().setTitle(title);
            outlineItemTreeNode.setUserObject(title);

            if (isNewOutlineItem) {
                // add the new node to the tree
                Library library = controller.getDocument().getCatalog().getLibrary();
                OutlineItem outline = outlineItemTreeNode.getOutlineItem();
                library.getStateManager().addChange(new PObject(outline, outline.getPObjectReference()));
            }

            // update the tree model
            DefaultTreeModel model = (DefaultTreeModel) parentTree.getModel();
            model.nodeChanged(outlineItemTreeNode);
            setVisible(false);
            dispose();
        } else if (actionEvent.getSource() == cancelButton) {
            outlineDestination = null;
            setVisible(false);
            dispose();
        } else if (actionEvent.getSource() == titleTextField) {
            title = titleTextField.getText();
        }
    }

    @Override
    public void focusGained(FocusEvent focusEvent) {

    }

    @Override
    public void focusLost(FocusEvent focusEvent) {
        if (focusEvent.getSource() == titleTextField) {
            title = titleTextField.getText();
        }
    }

    @Override
    public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
        NameJTree nameJTree = (NameJTree) treeSelectionEvent.getSource();
        TreePath selectedPath = nameJTree.getSelectionPath();
        if (selectedPath != null) {
            NameTreeNode selectedNode = (NameTreeNode) selectedPath.getLastPathComponent();
            outlineDestination.setNamedDestination(selectedNode.getName());
            implicitDestinationPanel.setDestination(outlineDestination);
        }
    }

    @Override
    public void itemStateChanged(ItemEvent itemEvent) {
        if (itemEvent.getStateChange() != ItemEvent.SELECTED) {
            return;
        }
        CardLayout cl = (CardLayout) destinationTypesCards.getLayout();
        ValueLabelItem item = (ValueLabelItem) itemEvent.getItem();
        cl.show(destinationTypesCards, item.getValue().toString());
    }

    private void buildGui() {
        setLayout(new GridBagLayout());
        setTitle(messageBundle.getString("viewer.utilityPane.outline.edit.title"));
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel outlinePanel = new JPanel();
        outlinePanel.setLayout(new GridBagLayout());
        outlinePanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.anchor = GridBagConstraints.NORTHEAST;

        // Title
        JLabel titleLabel = new JLabel(messageBundle.getString("viewer.utilityPane.outline.title.label"));
        constraints.insets = new Insets(0, 0, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(outlinePanel, titleLabel, 0, 0, 1, 1);
        // title input
        titleTextField = new JTextField();
        titleTextField.setColumns(35);
        titleTextField.addActionListener(this);
        titleTextField.addFocusListener(this);
        constraints.insets = new Insets(0, 0, 5, 0);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(outlinePanel, titleTextField, 1, 0, 1, 1);

        // destination type compo
        buildDestinationTypeComboBox();
        JLabel destinationLabel = new JLabel(
                messageBundle.getString("viewer.utilityPane.outline.destination.type.label"));
        constraints.insets = new Insets(0, 0, 5, 5);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(outlinePanel, destinationLabel, 0, 1, 1, 1);
        addGB(outlinePanel, destinationTypeComboBox, 1, 1, 1, 1);

        destinationTypesCards = new JPanel(new CardLayout());
        implicitDestinationPanel = new ImplicitDestinationPanel(controller);
        destinationTypesCards.add(implicitDestinationPanel, IMPLICIT_DESTINATION);
        Names names = controller.getDocument().getCatalog().getNames();
        if (names != null && names.getDestsNameTree() != null) {
            destinationTypesCards.add(buildNameTreePanel(), NAMED_DESTINATION);
        }

        // set up the two destination panels types.
        titleTextField.setText(outlineItemTreeNode.getOutlineItem().getTitle());
        Destination des = outlineItemTreeNode.getOutlineItem().getDest();
        CardLayout cl = (CardLayout) destinationTypesCards.getLayout();
        if (des != null && des.getNamedDestination() != null) {
            destinationTypeComboBox.setSelectedIndex(NAMED_DESTINATION_INDEX);
            cl.show(destinationTypesCards, NAMED_DESTINATION);
        } else {
            destinationTypeComboBox.setSelectedItem(IMPLICIT_DESTINATION_INDEX);
            cl.show(destinationTypesCards, IMPLICIT_DESTINATION);
        }

        addGB(outlinePanel, destinationTypesCards, 0, 2, 1, 2);

        // Buttons
        okButton = new JButton(messageBundle.getString("viewer.utilityPane.outline.ok.label"));
        okButton.addActionListener(this);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.anchor = GridBagConstraints.EAST;
        addGB(outlinePanel, okButton, 0, 3, 1, 1);

        cancelButton = new JButton(messageBundle.getString("viewer.utilityPane.outline.cancel.label"));
        cancelButton.addActionListener(this);
        constraints.gridwidth = 1;
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.anchor = GridBagConstraints.WEST;
        addGB(outlinePanel, cancelButton, 1, 3, 1, 1);

        add(outlinePanel);
        pack();
        setResizable(true);
        setLocationRelativeTo(controller.getViewerFrame());
    }

    private JComponent buildNameTreePanel() {
        Catalog catalog = controller.getDocument().getCatalog();
        if (catalog.getNames() != null && catalog.getNames().getDestsNameTree() != null) {
            NameTree nameTree = catalog.getNames().getDestsNameTree();
            NameJTree nameJTree = new NameJTree();
            DefaultTreeModel namesTreeModel = new DefaultTreeModel(new NameTreeNode(nameTree.getRoot(), messageBundle));
            nameJTree.setModel(namesTreeModel);
            nameJTree.setRootVisible(!nameTree.getRoot().isEmpty());
            nameJTree.setExpandsSelectedPaths(true);
            nameJTree.addTreeSelectionListener(this);
            // select the current destination
            String name = outlineItemTreeNode.getOutlineItem().getDest().getNamedDestination();
            if (name != null) {
                ((DefaultMutableTreeNode) namesTreeModel.getRoot()).depthFirstEnumeration().asIterator().forEachRemaining(node -> {
                    if (node instanceof NameTreeNode) {
                        NameTreeNode nameTreeNode = (NameTreeNode) node;
                        if (nameTreeNode.getName() != null &&
                                name.equals(nameTreeNode.getName().toString())) {
                            TreePath path = new TreePath(nameTreeNode.getPath());
                            SwingUtilities.invokeLater(() -> {
                                nameJTree.setSelectionPath(path);
                                nameJTree.scrollPathToVisible(path);
                                nameJTree.expandPath(path);
                            });
                        }
                    }
                });

            }
            JScrollPane nameTreeScroller = new JScrollPane(nameJTree);
            nameTreeScroller.setPreferredSize(new Dimension(325, 225));
            return nameTreeScroller;
        }
        return null;
    }

    private void buildDestinationTypeComboBox() {
        Names names = controller.getDocument().getCatalog().getNames();
        destinationTypeComboBox = new JComboBox<>();
        destinationTypeComboBox.addItem(new ValueLabelItem(IMPLICIT_DESTINATION,
                messageBundle.getString("viewer.utilityPane.outline.destination.type.implicit.label")));
        if (names != null && names.getDestsNameTree() != null) {
            destinationTypeComboBox.addItem(new ValueLabelItem(NAMED_DESTINATION,
                    messageBundle.getString("viewer.utilityPane.outline.destination.type.named.label")));
        }
        destinationTypeComboBox.addItemListener(this);
    }

    private void addGB(JPanel layout, Component component,
                       int x, int y,
                       int rowSpan, int colSpan) {
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = colSpan;
        constraints.gridheight = rowSpan;
        layout.add(component, constraints);
    }
}
