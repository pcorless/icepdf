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
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.ri.common.ImageColorIcon;
import org.icepdf.ri.images.Images;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

/**
 * AnnotationCellRender takes care of building a tree node's appearance for annotation nodes.  If an annotation type
 * is not supported by the editing tools an icon for the note is set to null.
 *
 * @since 6.3
 */
@SuppressWarnings("serial")
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

    public static GeneralPath squareColorOutline;
    public static GeneralPath circleColorOutline;
    public static GeneralPath textColorOutline;
    public static GeneralPath highlightColorOutline;
    public static GeneralPath inkColorOutline;
    public static GeneralPath strikeOutColorOutline;
    public static GeneralPath underlineColorOutline;
    public static GeneralPath lineColorOutline;

    static {
        squareColorOutline = new GeneralPath();
        squareColorOutline.moveTo(5, 2);
        squareColorOutline.lineTo(19, 2);
        squareColorOutline.lineTo(19, 15);
        squareColorOutline.lineTo(5, 15);
        squareColorOutline.closePath();

        circleColorOutline = new GeneralPath();
        circleColorOutline.moveTo(0, 0);
        circleColorOutline.curveTo(0, -4.265, -3.458, -7.723, -7.723, -7.723);
        circleColorOutline.curveTo(-11.988, -7.723, -15.446, -4.265, -15.446, 0);
        circleColorOutline.curveTo(-15.446, 4.265, -11.988, 7.723, -7.723, 7.723);
        circleColorOutline.curveTo(-3.458, 7.723, 0, 4.265, 0, 0);
        circleColorOutline.closePath();
        circleColorOutline.transform(new AffineTransform(1, 0, 0, -1, 19, 9));

        textColorOutline = new GeneralPath();
        textColorOutline.moveTo(0, 0);
        textColorOutline.curveTo(1.174, -0.335, 2.021, -0.404, 3.362, -0.404);
        textColorOutline.curveTo(8.999, -0.404, 13.569, 2.853, 13.569, 6.869);
        textColorOutline.curveTo(13.569, 10.886, 8.999, 14.142, 3.362, 14.142);
        textColorOutline.curveTo(-2.275, 14.142, -6.845, 10.886, -6.845, 6.869);
        textColorOutline.curveTo(-6.845, 4.847, -5.687, 3.017, -3.818, 1.699);
        textColorOutline.curveTo(
                (float) textColorOutline.getCurrentPoint().getX(),
                (float) textColorOutline.getCurrentPoint().getY(),
                -3.732, 0.684, -4.097, -0.404);
        textColorOutline.curveTo(-4.574, -1.822, -5.002, -2.235, -5.002, -2.235);
        textColorOutline.curveTo((float) textColorOutline.getCurrentPoint().getX(),
                (float) textColorOutline.getCurrentPoint().getY(), -3.801, -2.235, -2.107, -1.557);
        textColorOutline.curveTo(
                -0.571, -0.94, 0, 0, 0, 0);
        textColorOutline.transform(new AffineTransform(1, 0, 0, -1, 8, 16));
        textColorOutline.closePath();

        inkColorOutline = new GeneralPath();
        inkColorOutline.moveTo(7, 11);
        inkColorOutline.lineTo(15, 3);
        inkColorOutline.lineTo(18, 5);
        inkColorOutline.lineTo(8, 14);
        inkColorOutline.closePath();

        highlightColorOutline = new GeneralPath();
        highlightColorOutline.moveTo(3, 2);
        highlightColorOutline.lineTo(13, 2);
        highlightColorOutline.lineTo(13, 12);
        highlightColorOutline.lineTo(3, 12);
        highlightColorOutline.closePath();

        strikeOutColorOutline = new GeneralPath();
        strikeOutColorOutline.moveTo(3, 7);
        strikeOutColorOutline.lineTo(20, 7);
        strikeOutColorOutline.lineTo(20, 9);
        strikeOutColorOutline.lineTo(3, 9);
        strikeOutColorOutline.closePath();

        underlineColorOutline = new GeneralPath();
        underlineColorOutline.moveTo(4, 15);
        underlineColorOutline.lineTo(20, 15);
        underlineColorOutline.lineTo(20, 17);
        underlineColorOutline.lineTo(4, 17);
        underlineColorOutline.closePath();

        lineColorOutline = new GeneralPath();
        lineColorOutline.moveTo(20, 6);
        lineColorOutline.lineTo(16, 1);
        lineColorOutline.lineTo(9, 1);
        lineColorOutline.lineTo(3, 7);
        lineColorOutline.lineTo(7, 15);
        lineColorOutline.lineTo(11, 11);
    }


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
            ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_text_c_tree.png"));
            tmp.setColor(annotation.getColor());
            tmp.setColorBound(textColorOutline);
            setIcon(tmp);
        } else if (annotation instanceof LinkAnnotation) {
            setIcon(ANNOTATION_LINK_ICON);
        } else if (annotation instanceof FreeTextAnnotation) {
            setIcon(ANNOTATION_FREE_TEXT_ICON);
        } else if (annotation instanceof LineAnnotation) {
            ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_line_c_tree.png"));
            tmp.setColor(annotation.getColor(), 1f, false, false);
            tmp.setColorBound(lineColorOutline);
            setIcon(tmp);
        } else if (annotation instanceof SquareAnnotation) {
            ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_square_c_tree.png"));
            tmp.setColor(annotation.getColor(), 1f, false, false);
            tmp.setColorBound(squareColorOutline);
            setIcon(tmp);
        } else if (annotation instanceof CircleAnnotation) {
            ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_circle_c_tree.png"));
            tmp.setColor(annotation.getColor(), 1f, false, false);
            tmp.setColorBound(circleColorOutline);
            setIcon(tmp);
        } else if (annotation instanceof TextMarkupAnnotation) {
            if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT)) {
                ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_highlight_c_tree.png"));
                tmp.setColor(annotation.getColor());
                tmp.setBack(false);
                tmp.setColorBound(highlightColorOutline);
                setIcon(tmp);
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_SQUIGGLY)) {
                setIcon(ANNOTATION_UNDERLINE_ICON);
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT)) {
                ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_cross_out_c_tree.png"));
                tmp.setColor(annotation.getColor(), 1f, true, true);
                tmp.setColorBound(strikeOutColorOutline);
                setIcon(tmp);
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_UNDERLINE)) {
                ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_underline_c_tree.png"));
                tmp.setColor(annotation.getColor(), 1f, true, false);
                tmp.setColorBound(underlineColorOutline);
                setIcon(tmp);
            }
        } else if (annotation instanceof InkAnnotation) {
            ImageColorIcon tmp = new ImageColorIcon(Images.get("annot_ink_c_tree.png"));
            tmp.setColor(annotation.getColor());
            tmp.setColorBound(inkColorOutline);
            setIcon(tmp);
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