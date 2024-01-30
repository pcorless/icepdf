/*
 * Copyright 2006-2019 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.views.annotations;

import java.awt.geom.Rectangle2D;

import static java.util.Objects.requireNonNull;

/**
 * Stores state parameters for popup annotation objects
 *
 */
public class PopupState {

    private final Rectangle2D.Float userSpaceRectangle;
    private final PopupAnnotationComponent annotationComponent;
    private final float headerTextSize;
    private final float commentTextSize;
    private final boolean visible;


    /**
     * Stores the state associated with the PopupAnnotationComponent object.
     * When a new instance of this object is created the annotation's properties are saved.
     *
     * @param annotationComponent annotation component whose state will be stored.
     */
    public PopupState(final PopupAnnotationComponent annotationComponent) {
        // reference to component so we can apply the state parameters if
        // restore() is called.
        this.annotationComponent = requireNonNull(annotationComponent);
        this.userSpaceRectangle = annotationComponent.getAnnotation().getUserSpaceRectangle();
        this.visible = annotationComponent.isVisible();
        this.commentTextSize = annotationComponent.getAnnotation().getTextAreaFontsize();
        this.headerTextSize = annotationComponent.getAnnotation().getHeaderLabelsFontSize();
    }

    public PopupAnnotationComponent getAnnotationComponent() {
        return annotationComponent;
    }

    public Rectangle2D.Float getUserSpaceRectangle() {
        return userSpaceRectangle;
    }

    public float getHeaderTextSize() {
        return headerTextSize;
    }

    public float getTextAreaFontSize() {
        return commentTextSize;
    }

    public boolean isVisible() {
        return visible;
    }
}
