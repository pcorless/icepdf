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
 * The Original Code is ICEpdf 3.0 open source software code, released
 * May 1st, 2009. The Initial Developer of the Original Code is ICEsoft
 * Technologies Canada, Corp. Portions created by ICEsoft are Copyright (C)
 * 2004-2009 ICEsoft Technologies Canada, Corp. All Rights Reserved.
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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.Memento;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.views.AnnotationComponent;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

/**
 * Stores state paramaters for annotation objects to be used in conjuction
 * with a care taker as part of the memento pattern.
 *
 * @since 4.0
 */
public class AnnotationState implements Memento {

    // simple normalized version of properties
    protected Integer linkType;
    protected String highlightStyle;
    protected float lineThickness;
    protected String lineStyle;
    protected Color color;

    // annotation bounding rectangle in user space.
    protected Rectangle2D.Float userSpaceRectangle;

    // original rectangle reference.
    protected AnnotationComponent annotationComponent;

    /**
     * Stores the annotation state associated with the AnnotationComponents
     * annotation object.  When a new instance of this object is created
     * the annotation's proeprties are saved.
     *
     * @param annotationComponent annotation component who's state will be stored.
     */
    public AnnotationState(AnnotationComponent annotationComponent) {
        // reference to component so we can apply the state parameters if
        // restore() is called.
        this.annotationComponent = annotationComponent;
        // test to store previous border color, more properties to follow.
        if (this.annotationComponent != null &&
                this.annotationComponent.getAnnotation() != null) {

            Annotation annotation = this.annotationComponent.getAnnotation();
            // link type, visible, invisible
            linkType = annotation.getLinkType();
            if (annotation instanceof LinkAnnotation) {
                highlightStyle = ((LinkAnnotation) annotation).getHighlightMode();
            }
            lineThickness = annotation.getLineThickness();
            lineStyle = annotation.getLineStyle();
            Color tmpColor = annotation.getColor();
            if (tmpColor != null) {
                color = new Color(tmpColor.getRGB());
            }
            // store user space rectangle SpaceRectangle.
            Rectangle2D.Float rect = annotation.getUserSpaceRectangle();
            userSpaceRectangle = new Rectangle2D.Float(rect.x, rect.y,
                    rect.width, rect.height);
        }
    }

    public AnnotationState(Integer linkType, String highlightStyle,
                           float lineThickness, String lineStyle, Color color) {
        this.linkType = linkType;
        this.highlightStyle = highlightStyle;
        this.lineThickness = lineThickness;
        this.lineStyle = lineStyle;
        this.color = new Color(color.getRGB());
    }

    public void apply(AnnotationState applyState) {

        // apply the new state vars.
        this.linkType = applyState.linkType;
        this.highlightStyle = applyState.highlightStyle;
        this.lineThickness = applyState.lineThickness;
        this.lineStyle = applyState.lineStyle;
        this.color = new Color(applyState.color.getRGB());

        // store user space rectangle SpaceRectangle.
        Rectangle2D.Float rect = applyState.userSpaceRectangle;
        if (rect != null) {
            userSpaceRectangle = new Rectangle2D.Float(rect.x, rect.y,
                    rect.width, rect.height);
        }

        // apply the new state to the annotation and schedule a sync
        restore();

    }

    /**
     * Restores the AnnotationComponents state to the state stored during the
     * construction of this object.
     */
    public void restore() {
        if (annotationComponent.getAnnotation() != null) {
            // get reference to annotation
            Annotation annotation = annotationComponent.getAnnotation();

            restore(annotation);

            // update the document with current state.
            synchronizeState();
        }
    }

    /**
     * Restores the annotation state in this instance to the Annotation
     * specified as a param. This method is ment to bue used in
     * {@link #AnnotationState(Integer, String, float, String, java.awt.Color)} 
     * @param annotation  annotation to retore state to.
     */
    public void restore(Annotation annotation){
        // create a new Border style entry as an inline dictionary
        if (annotation.getBorderStyle() == null) {
            annotation.setBorderStyle(new BorderStyle());
        }

        // get the easy stuff out of the way
        // apply old border color
        if (color != null) {
            annotation.setColor(color);
        }
        // apply old user rectangle
        annotation.setUserSpaceRectangle(userSpaceRectangle);

        restoreLineThickness(annotation);
        restoreHighlightStyle(annotation);
        restoreLineStyle(annotation);

        // we do this last as it set the line thickness to zero regardless
        // of restore values if linkType == Annotation.INVISIBLE_RECTANGLE
        applyInvisibleLinkType(annotation);
    }

    public void synchronizeState() {
        // update the document with this change.
        int pageIndex = annotationComponent.getPageIndex();
        Document document = annotationComponent.getDocument();
        Annotation annotation = annotationComponent.getAnnotation();
        PageTree pageTree = document.getPageTree();
        Page page = pageTree.getPage(pageIndex, this);

        // state behind draw state.
        if (!annotation.isDeleted()) {
            page.updateAnnotation(annotation);
            // refresh bounds for any resizes
            annotationComponent.refreshDirtyBounds();
            annotationComponent.refreshAnnotationRect();
        }
        // todo still some bug here, when undoing a delete, coordinates are one
        else {
            // mark it as not deleted
            annotation.setDeleted(false);
            // re-add it to the page
            annotation = page.addAnnotation(annotation);
            // finally update the pageComponent so we can see it again.
            annotationComponent.getParentPageView().addAnnotation(annotation);
            // refresh bounds for any resizes
            annotationComponent.refreshDirtyBounds();
            annotationComponent.refreshAnnotationRect();
        }
        pageTree.releasePage(page, this);
    }

    private void restoreLineThickness(Annotation annotation) {
        // check if we need to set line thickness to default value
        if (linkType == Annotation.VISIBLE_RECTANGLE &&
                lineThickness == 0) {
            lineThickness = 1f;
        }

        // update the border width line thickness
        Object border = annotation.getObject(Annotation.BORDER_KEY);
        if (border != null && border instanceof Vector) {
            Vector borderProps = (Vector) border;
            if (borderProps.size() >= 3) {
                borderProps.set(2, lineThickness);
            }
        }
        // check for a border style
        if (annotation.getBorderStyle() != null) {
            BorderStyle borderStyle = annotation.getBorderStyle();
            borderStyle.setStrokeWidth(lineThickness);
        }
    }

    private void restoreHighlightStyle(Annotation annotation) {
        if (annotation instanceof LinkAnnotation) {
            LinkAnnotation linkAnnotation = (LinkAnnotation) annotation;
            Object object = linkAnnotation.getObject(
                    LinkAnnotation.HIGHLIGHT_MODE_KEY);
            // update the entry
            if (object != null && object instanceof Name) {
                linkAnnotation.getEntries().put(
                        LinkAnnotation.HIGHLIGHT_MODE_KEY,
                        new Name(highlightStyle));
            } else {
                // add the new entry
                linkAnnotation.getEntries().put(
                        LinkAnnotation.HIGHLIGHT_MODE_KEY,
                        new Name(highlightStyle));
            }
        }
    }

    private void restoreLineStyle(Annotation annotation) {
        Object border = annotation.getObject(Annotation.BORDER_KEY);

        BorderStyle borderStyle = annotation.getBorderStyle();
        borderStyle.setBorderStyle(lineStyle);

        // remove the dashed border dashed entry if any as we will use
        // border style going forward and not the older border entry.
        // that said we will keep the border entry first 3 digits .
        if (border != null && border instanceof Vector) {
            Vector borderProps = (Vector) border;
            if (borderProps.size() == 4) {
                borderProps.remove(Annotation.BORDER_DASH);
            }
        }
    }

    private void applyInvisibleLinkType(Annotation annotation) {
        // clear border thickness
        if (linkType == Annotation.INVISIBLE_RECTANGLE) {
            Object border = annotation.getObject(Annotation.BORDER_KEY);
            if (border != null && border instanceof Vector) {
                Vector borderProps = (Vector) border;
                if (borderProps.size() >= 3) {
                    borderProps.set(2, 0);
                }
            }
            // check for a border style
            if (annotation.getBorderStyle() != null) {
                BorderStyle borderStyle = annotation.getBorderStyle();
                borderStyle.setStrokeWidth(0);
            }
        }
    }
}
