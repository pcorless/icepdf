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

import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;

import java.awt.*;

/**
 * The PolyLineAnnotationComponent encapsulates a PolyLineAnnotation objects.
 * <br>
 * NOTE: this component is has no handler and can only be created if the parent
 * Page object contains a PolygonLineAnnotation.
 *
 * @since 5.0
 */
@SuppressWarnings("serial")
public class PolyLineAnnotationComponent extends MarkupAnnotationComponent {

    public PolyLineAnnotationComponent(MarkupAnnotation annotation, DocumentViewController documentViewController,
                                       AbstractPageViewComponent pageViewComponent) {
        super(annotation, documentViewController, pageViewComponent);
        isShowInvisibleBorder = false;
        isEditable = false;
        isRollover = false;
        isMovable = false;
        isResizable = false;
    }

    @Override
    public void resetAppearanceShapes() {
        super.resetAppearanceShapes();
    }

    @Override
    public void paintComponent(Graphics g) {

    }
}

