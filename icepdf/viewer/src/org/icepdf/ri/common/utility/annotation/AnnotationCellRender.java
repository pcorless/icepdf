package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * AnnotationCellRender takes care of building a tree node's appearance for annotation nodes.  If an annotation type
 * is not supported by the editing tools an icon for the note is set to null.
 */
public class AnnotationCellRender extends DefaultTreeCellRenderer {

    /**
     * Comment/text annotation icon.
     */
    public static final ImageIcon ANNOTATION_TEXT_ICON = new ImageIcon(Images.get("annot_text_tree.png"));

    /**
     * Markup highlight annotation icon.
     */
    public static final ImageIcon ANNOTATION_HIGHLIGHT_ICON = new ImageIcon(Images.get("annot_highlight_tree.png"));

    /**
     * Markup cross out annotation icon.
     */
    public static final ImageIcon ANNOTATION_CROSS_OUT_ICON = new ImageIcon(Images.get("annot_cross_out_tree.png"));

    /**
     * Markup underline annotation icon.
     */
    public static final ImageIcon ANNOTATION_UNDERLINE_ICON = new ImageIcon(Images.get("annot_underline_tree.png"));

    /**
     * Free text annotation icon.
     */
    public static final ImageIcon ANNOTATION_FREE_TEXT_ICON = new ImageIcon(Images.get("annot_free_text_tree.png"));

    /**
     * Line annotation icon.
     */
    public static final ImageIcon ANNOTATION_LINE_ICON = new ImageIcon(Images.get("annot_line_tree.png"));

    /**
     * Circle annotation icon.
     */
    public static final ImageIcon ANNOTATION_CIRCLE_ICON = new ImageIcon(Images.get("annot_circle_tree.png"));

    /**
     * Square annotation icon.
     */
    public static final ImageIcon ANNOTATION_SQUARE_ICON = new ImageIcon(Images.get("annot_square_tree.png"));

    /**
     * Ink annotation icon.
     */
    public static final ImageIcon ANNOTATION_INK_ICON = new ImageIcon(Images.get("annot_ink_tree.png"));

    /**
     * Link annotation icon.
     */
    public static final ImageIcon ANNOTATION_LINK_ICON = new ImageIcon(Images.get("annot_link_tree.png"));

    public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean sel,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus) {

        super.getTreeCellRendererComponent(
                tree, value, sel,
                expanded, leaf, row,
                hasFocus);

        Annotation annotation = null;
        if (value instanceof AnnotationTreeNode) {
            annotation = ((AnnotationTreeNode) value).getAnnotation();
        }
        // dynamic as the validator status changes, so will the icon.
        if (annotation instanceof TextAnnotation) {
            setIcon(ANNOTATION_TEXT_ICON);
        } else if (annotation instanceof LinkAnnotation) {
            setIcon(ANNOTATION_LINK_ICON);
        } else if (annotation instanceof FreeTextAnnotation) {
            setIcon(ANNOTATION_FREE_TEXT_ICON);
        } else if (annotation instanceof LineAnnotation) {
            setIcon(ANNOTATION_LINE_ICON);
        } else if (annotation instanceof SquareAnnotation) {
            setIcon(ANNOTATION_SQUARE_ICON);
        } else if (annotation instanceof CircleAnnotation) {
            setIcon(ANNOTATION_CIRCLE_ICON);
        } else if (annotation instanceof TextMarkupAnnotation) {
            if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT)) {
                setIcon(ANNOTATION_HIGHLIGHT_ICON);
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_SQUIGGLY)) {
                setIcon(ANNOTATION_UNDERLINE_ICON);
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT)) {
                setIcon(ANNOTATION_CROSS_OUT_ICON);
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_UNDERLINE)) {
                setIcon(ANNOTATION_UNDERLINE_ICON);
            }
        } else if (annotation instanceof InkAnnotation) {
            setIcon(ANNOTATION_INK_ICON);
        } else if (annotation != null) {
            setLeafIcon(null);
            setOpenIcon(null);
            setClosedIcon(null);
        } else if (value instanceof DefaultMutableTreeNode) {
            setOpenIcon(new ImageIcon(Images.get("page.gif")));
            setClosedIcon(new ImageIcon(Images.get("page.gif")));
            setLeafIcon(new ImageIcon(Images.get("page.gif")));
        }
        return this;
    }

}

