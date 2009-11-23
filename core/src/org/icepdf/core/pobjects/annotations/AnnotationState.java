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

import org.icepdf.core.views.AnnotationComponent;
import org.icepdf.core.Memento;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * Stores state paramaters for annotation objects to be used in conjuction
 * with a care taker as part of the memento pattern.
 *
 * @since 4.0
 */
public class AnnotationState implements Memento {

    // border color of annotation.
    protected Color borderColor;
    // border style of annotation.
    protected String borderStyle;
    protected float borderWidth;
    // annotation bounding rectangle in user space.
    protected Rectangle2D.Float userSpaceRectangle;
    // todo keep mapping annotation state params. 

    // original rectangle reference.
    protected AnnotationComponent annotationComponent;

    /**
     * Stores the annotation state associated with the AnnotationComponents
     * annotation object.  When a new instance of this object is created
     * the annotation's proeprties are saved.
     *
     * @param annotation annotation component who's state will be stored.
     */
    public AnnotationState(AnnotationComponent annotation) {
        // reference to component so we can apply the state parameters if
        // restore() is called.
        this.annotationComponent = annotation;
        // test to store previous border color, more properties to follow.
        if (this.annotationComponent != null){
            // store userpace rectangle SpaceRectangle.
            Rectangle2D.Float rect = annotation.getAnnotation().getUserSpaceRectangle();
            userSpaceRectangle = new Rectangle2D.Float(rect.x, rect.y,
                    rect.width, rect.height);
            // store border color
            Color tmpColor = annotation.getAnnotation().getBorderColor();
            if (tmpColor != null) {
                borderColor = new Color(tmpColor.getRGB());
            }
            if (annotation.getAnnotation().getBorderStyle() != null) {
                String tmpStyle = annotation.getAnnotation().getBorderStyle().getBorderStyle();
                if (tmpStyle != null) {
                    // store border style
                    borderStyle = new String(tmpStyle);
                }
                float tmpWidth = annotation.getAnnotation().getBorderStyle().getStrokeWidth();
                borderWidth = new Float(tmpWidth);
            }
        }
    }

    /**
     * Restores the AnnotationComponents state to the state stored during the
     * construction of this object. 
     */
    public void restore(){
        if (annotationComponent.getAnnotation() != null){
            // get reference to annotation
            Annotation annotation = annotationComponent.getAnnotation();
            // apply old border color
            if (borderColor != null) {
                annotation.setBorderColor(borderColor);
            }
            if (annotation.getBorderStyle() != null) {
                if (borderStyle != null) {
                    annotation.getBorderStyle().setBorderStyle(borderStyle);
                }
                annotation.getBorderStyle().setStrokeWidth(borderWidth);
            }
            // apply old user rectangle
            annotation.getUserSpaceRectangle()
                    .setRect(userSpaceRectangle);
            // trigger the component to refresh and repaint its self with the
            // new 'restored' properties.
            annotationComponent.refreshDirtyBounds();
        }
    }
}
