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

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.pobjects.annotations.*;
import org.icepdf.ri.common.views.AbstractPageViewComponent;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.annotations.acroform.ButtonComponent;
import org.icepdf.ri.common.views.annotations.acroform.ChoiceComponent;
import org.icepdf.ri.common.views.annotations.acroform.SignatureComponent;
import org.icepdf.ri.common.views.annotations.acroform.TexComponent;

import java.awt.*;
import java.util.logging.Logger;

/**
 * AnnotationComponentFactory is responsible for building an annotation component
 * for given Annotation object.  Generally this factor is only used by the annotation
 * handlers during the creation of new annotations.  When a PageComponent is
 * initialized a pages Annotation list is iterated over and this class is used
 * to generate the annotations components.
 *
 * @since 5.0
 */
public class AnnotationComponentFactory {

    protected static final Logger logger =
            Logger.getLogger(AnnotationComponentFactory.class.toString());

    private AnnotationComponentFactory() {
    }

    /**
     * Creates an annotation component for the given annotation object subtype.
     *
     * @param annotation             annotation to encapsulate with a component instance
     * @param documentViewController document view controller
     * @param pageViewComponent      parent pageViewComponent
     * @return annotation component of the type specified by annotation subtype
     */
    public synchronized static AbstractAnnotationComponent buildAnnotationComponent(
            Annotation annotation,
            DocumentViewController documentViewController,
            AbstractPageViewComponent pageViewComponent) {
        Name subtype = annotation.getSubType();
        if (subtype != null) {
            if (Annotation.SUBTYPE_LINK.equals(subtype)) {
                return new LinkAnnotationComponent((LinkAnnotation) annotation, documentViewController, pageViewComponent);
            } else if (TextMarkupAnnotation.isTextMarkupAnnotation(subtype)) {
                return new TextMarkupAnnotationComponent((TextMarkupAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_LINE.equals(subtype)) {
                return new LineAnnotationComponent((LineAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_CIRCLE.equals(subtype)) {
                return new CircleAnnotationComponent((CircleAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_POLYGON.equals(subtype)) {
                return new PolygonAnnotationComponent((MarkupAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_POLYLINE.equals(subtype)) {
                return new PolyLineAnnotationComponent((MarkupAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_SQUARE.equals(subtype)) {
                return new SquareAnnotationComponent((SquareAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_POPUP.equals(subtype)) {
                return new PopupAnnotationComponent((PopupAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_TEXT.equals(subtype)) {
                return new TextAnnotationComponent((TextAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_INK.equals(subtype)) {
                return new InkAnnotationComponent((InkAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_FREE_TEXT.equals(subtype)) {
                return new FreeTextAnnotationComponent((FreeTextAnnotation) annotation, documentViewController,
                        pageViewComponent);
            } else if (Annotation.SUBTYPE_WIDGET.equals(subtype)) {
                AbstractWidgetAnnotation widgetAnnotation = (AbstractWidgetAnnotation) annotation;
                Name fieldType = widgetAnnotation.getFieldDictionary().getFieldType();
                if (FieldDictionaryFactory.TYPE_BUTTON.equals(fieldType)) {
                    return new ButtonComponent(
                            (ButtonWidgetAnnotation) annotation, documentViewController, pageViewComponent);
                } else if (FieldDictionaryFactory.TYPE_CHOICE.equals(fieldType)) {
                    return new ChoiceComponent(
                            (ChoiceWidgetAnnotation) annotation, documentViewController, pageViewComponent);
                } else if (FieldDictionaryFactory.TYPE_TEXT.equals(fieldType)) {
                    return new TexComponent(
                            (TextWidgetAnnotation) annotation, documentViewController, pageViewComponent);
                } else if (FieldDictionaryFactory.TYPE_SIGNATURE.equals(fieldType)) {
                    return new SignatureComponent((SignatureWidgetAnnotation) annotation, documentViewController,
                            pageViewComponent);
                }
                // load basic widget support, selection, rendering.
                else {
                    return new WidgetAnnotationComponent(
                            (AbstractWidgetAnnotation) annotation, documentViewController, pageViewComponent);
                }
            } else {
                return new AbstractAnnotationComponent<Annotation>(annotation, documentViewController, pageViewComponent) {
                    private static final long serialVersionUID = 409696785049691125L;

                    @Override
                    public void resetAppearanceShapes() {

                    }

                    @Override
                    public void paintComponent(Graphics g) {

                    }

                    public boolean isActive() {
                        return false;
                    }
                };
            }
        }
        return null;
    }
}
