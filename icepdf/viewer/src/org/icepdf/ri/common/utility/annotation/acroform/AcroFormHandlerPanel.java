package org.icepdf.ri.common.utility.annotation.acroform;

import org.icepdf.core.pobjects.Reference;
import org.icepdf.core.pobjects.acroform.FieldDictionary;
import org.icepdf.core.pobjects.acroform.InteractiveForm;
import org.icepdf.core.pobjects.annotations.AbstractWidgetAnnotation;
import org.icepdf.core.util.Library;
import org.icepdf.ri.common.SwingController;
import org.icepdf.ri.common.utility.annotation.AbstractWorkerPanel;
import org.icepdf.ri.common.views.AnnotationSelector;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * The AcroFormHandlerPanel list all form widget in a document and arranges them using the hierarchy defined by the
 * document, no attempts are mode to flatten the form structure or change order.
 */
@SuppressWarnings("serial")
public class AcroFormHandlerPanel extends AbstractWorkerPanel {

    private static final Logger logger =
            Logger.getLogger(AcroFormHandlerPanel.class.toString());

    public AcroFormHandlerPanel(SwingController controller) {
        super(controller);
        nodeSelectionListener = new AcroFormNodeSelectionListener();
        cellRenderer = new AcroFormCellRender();
        rootNodeLabel = messageBundle.getString("viewer.utilityPane.acroform.tab.title");
        // finally construct the acroForm tree of nodes.
        buildUI();
    }

    /**
     * Builds the AcroForm annotation tree.  Currently this process is handle on teh AWT thread as it typically
     * happens very quickly.
     */
    @Override
    protected void buildWorkerTaskUI() {
        if (this.currentDocument != null &&
                currentDocument.getCatalog().getInteractiveForm() != null) {
            InteractiveForm interactiveForm = currentDocument.getCatalog().getInteractiveForm();
            final ArrayList<Object> widgets = interactiveForm.getFields();
            // build out the tree
            if (widgets != null) {
                Library library = currentDocument.getCatalog().getLibrary();
                for (Object widget : widgets) {
                    descendFormTree(library, rootTreeNode, widget);
                }
            }
            tree.expandPath(new TreePath(rootTreeNode));
            revalidate();
        }
    }

    /**
     * Recursively set highlight on all the form fields.
     *
     * @param formNode root form node.
     */
    private void descendFormTree(Library library, DefaultMutableTreeNode currentRoot, Object formNode) {

        if (formNode instanceof AbstractWidgetAnnotation) {
            AcroFormTreeNode unsignedFieldNode = new AcroFormTreeNode((AbstractWidgetAnnotation) formNode, messageBundle);
            treeModel.insertNodeInto(unsignedFieldNode, currentRoot, currentRoot.getChildCount());
        } else if (formNode instanceof FieldDictionary) {
            // iterate over the kid's array.
            FieldDictionary child = (FieldDictionary) formNode;
            formNode = child.getKids();
            if (formNode != null) {
                ArrayList kidsArray = (ArrayList) formNode;
                for (Object kid : kidsArray) {
                    if (kid instanceof Reference) {
                        kid = library.getObject((Reference) kid);
                    }
                    if (kid instanceof AbstractWidgetAnnotation) {
                        AcroFormTreeNode unsignedFieldNode = new AcroFormTreeNode((AbstractWidgetAnnotation) kid, messageBundle);
                        treeModel.insertNodeInto(unsignedFieldNode, currentRoot, currentRoot.getChildCount());
                    } else if (kid instanceof FieldDictionary) {
                        AcroFormTreeNode unsignedFieldNode = new AcroFormTreeNode((FieldDictionary) kid, messageBundle);
                        treeModel.insertNodeInto(unsignedFieldNode, currentRoot, currentRoot.getChildCount());
                        descendFormTree(library, unsignedFieldNode, kid);
                    }
                }
            }

        }
    }

    /**
     * NodeSelectionListener handles the root node context menu creation display and command execution.
     */
    private class AcroFormNodeSelectionListener extends NodeSelectionListener {
        @Override
        public void mouseClicked(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            int row = tree.getRowForLocation(x, y);
            TreePath path = tree.getPathForRow(row);
            if (path != null) {
                Object node = path.getLastPathComponent();
                if (node instanceof AcroFormTreeNode) {
                    AcroFormTreeNode formNode = (AcroFormTreeNode) node;
                    // on double click, navigate to page and set focus of component.
                    if (e.getClickCount() == 2) {
                        AbstractWidgetAnnotation widgetAnnotation = formNode.getAnnotation();
                        AnnotationSelector.SelectAnnotationComponent(controller, widgetAnnotation);
                    }
                }
            }
        }
    }

}
