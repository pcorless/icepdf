package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.ri.common.utility.annotation.acroform.AcroFormTreeNode;

import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * AnnotationTreeNode is used by the annotation utility tab tree.  The class is pretty straight forward and is
 * mainly used to set the label of a node based on the annotation subtype.
 */
@SuppressWarnings("serial")
public class AnnotationTreeNode extends AbstractAnnotationTreeNode<Annotation> {

    private static final Logger logger =
            Logger.getLogger(AcroFormTreeNode.class.toString());

    private Annotation annotation;

    public AnnotationTreeNode(Annotation annotation, ResourceBundle messageBundle) {
        this.annotation = annotation;
        String message = null;
        // setup label.
        if (annotation instanceof TextAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.textComment.empty.label");
        } else if (annotation instanceof LinkAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.link.empty.label");
        } else if (annotation instanceof FreeTextAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.freeText.empty.label");
        } else if (annotation instanceof LineAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.line.empty.label");
        } else if (annotation instanceof SquareAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.square.empty.label");
        } else if (annotation instanceof CircleAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.circle.empty.label");
        } else if (annotation instanceof TextMarkupAnnotation) {
            if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT)) {
                message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.highlight.empty.label");
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_SQUIGGLY)) {
                message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.squiggly.empty.label");
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT)) {
                message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.strike.empty.label");
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_UNDERLINE)) {
                message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.underline.empty.label");
            }
        } else if (annotation instanceof InkAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.ink.empty.label");
        } else if (annotation instanceof PopupAnnotation) {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.popup.empty.label");
        } else {
            message = applyMessage(annotation, messageBundle, "viewer.utilityPane.annotation.tab.tree.unknown.empty.label");
        }
        setUserObject(message);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    /**
     * Utility for setting the label via a message bundle resource.
     *
     * @param annotation    annotation to check name value.
     * @param messageBundle ri message bundle
     * @param message       message bundle resource name for fall back label if annotation name is null.
     * @return name of annotation, if null then an internationalized subtype name is returned.
     */
    private String applyMessage(Annotation annotation, ResourceBundle messageBundle,
                                String message) {
        String text = annotation.getName();
        if (text == null || text.length() == 0) {
            text = messageBundle.getString(message);
        }
        return text;
    }
}
