package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.annotations.Annotation;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * AbstractAnnotationTreeNode allows the specification of a DefaultMutableTreeNode that can return a specific
 * annotation type.
 */
public abstract class AbstractAnnotationTreeNode<T extends Annotation> extends DefaultMutableTreeNode {

    /**
     * Gets an instance of the Annotation instance that is encapsulated/represented by this node.
     *
     * @return instance of Annotation object.
     */
    public abstract T getAnnotation();
}
