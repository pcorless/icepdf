/*
 * Copyright 2006-2014 ICEsoft Technologies Inc.
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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.TextMarkupAnnotation;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewModel;

import java.awt.*;

/**
 * AnnotationComponentFactory is responsible for building an annotation component
 * for given Annotation object.  Generaly this factor is only used by the annotation
 * handlers during the creation of new annotatoins.  When a PageComponent is
 * initialized a pages Annotation list is iterated over and this class is used
 * to generate the annotations components.
 *
 * @since 5.0
 */
@SuppressWarnings("serial")
public class AnnotationComponentFactory {

    private AnnotationComponentFactory() {
    }

    /**
     * Creates an annotation component for the given annotation object subtype.
     *
     * @param annotation             annotation to encapsulate with a component instance
     * @param documentViewController document view controller
     * @param pageViewComponent      parent pageViewComponent
     * @param documentViewModel      document view model.
     * @return annotation component of the type specified by annotation subtype
     */
    public static AbstractAnnotationComponent buildAnnotationComponent(
            Annotation annotation,
            DocumentViewController documentViewController,
            AbstractPageViewComponent pageViewComponent,
            DocumentViewModel documentViewModel) {
        Name subtype = annotation.getSubType();
        if (subtype != null) {
            if (Annotation.SUBTYPE_LINK.equals(subtype)) {
                return new LinkAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (TextMarkupAnnotation.isTextMarkupAnnotation(subtype)) {
                return new TextMarkupAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_LINE.equals(subtype)) {
                return new LineAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_CIRCLE.equals(subtype)) {
                return new CircleAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_POLYGON.equals(subtype)) {
                return new PolygonAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_POLYLINE.equals(subtype)) {
                return new PolyLineAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_SQUARE.equals(subtype)) {
                return new SquareAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_POPUP.equals(subtype)) {
                return new PopupAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_TEXT.equals(subtype)) {
                return new TextAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_INK.equals(subtype)) {
                return new InkAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_FREE_TEXT.equals(subtype)) {
                return new FreeTextAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else if (Annotation.SUBTYPE_WIDGET.equals(subtype)) {
                return new WidgetAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel);
            } else {
                return new AbstractAnnotationComponent(annotation, documentViewController,
                        pageViewComponent, documentViewModel) {
                    @Override
                    public void resetAppearanceShapes() {

                    }

                    @Override
                    public void paintComponent(Graphics g) {

                    }
                };
            }
        }
        return null;
    }
}
