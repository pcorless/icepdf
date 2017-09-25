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

import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * AnnotationTreeNode is used by the annotation utility tab tree.  The class is pretty straight forward and is
 * mainly used to set the label of a node based on the annotation subtype.
 *
 * @since 6.3
 */
@SuppressWarnings("serial")
public class AnnotationTreeNode extends AbstractAnnotationTreeNode<Annotation> {

    private static final Logger logger =
            Logger.getLogger(AnnotationTreeNode.class.toString());

    private Annotation annotation;

    public AnnotationTreeNode(Annotation annotation, ResourceBundle messageBundle) {
        this.annotation = annotation;
        // setup label.
        applyMessage(annotation, messageBundle);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    /**
     * Utility for setting the label via a message bundle resource.
     *
     * @param messageBundle ri message bundle
     * @return name of annotation, if null then an internationalized subtype name is returned.
     */
    public void applyMessage(Annotation markupAnnotation, ResourceBundle messageBundle) {
        String text = markupAnnotation.getContents();
        this.annotation = markupAnnotation;
        // todo trim to a specific width.
        if (text == null || text.length() == 0) {
            text = getNullMessage(messageBundle);
        }
        setUserObject(text);
    }

    protected String getNullMessage(ResourceBundle messageBundle) {
        String message = null;
        if (annotation instanceof TextAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.textComment.empty.label");
        } else if (annotation instanceof LinkAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.link.empty.label");
        } else if (annotation instanceof FreeTextAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.freeText.empty.label");
        } else if (annotation instanceof LineAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.line.empty.label");
        } else if (annotation instanceof SquareAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.square.empty.label");
        } else if (annotation instanceof CircleAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.circle.empty.label");
        } else if (annotation instanceof TextMarkupAnnotation) {
            if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_HIGHLIGHT)) {
                message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.highlight.empty.label");
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_SQUIGGLY)) {
                message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.squiggly.empty.label");
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_STRIKE_OUT)) {
                message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.strike.empty.label");
            } else if (annotation.getSubType().equals(TextMarkupAnnotation.SUBTYPE_UNDERLINE)) {
                message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.underline.empty.label");
            }
        } else if (annotation instanceof InkAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.ink.empty.label");
        } else if (annotation instanceof PopupAnnotation) {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.popup.empty.label");
        } else {
            message = messageBundle.getString("viewer.utilityPane.markupAnnotation.view.tree.unknown.empty.label");
        }
        return message;
    }
}
