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
package org.icepdf.core.pobjects.annotations;

import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.acroform.FieldDictionaryFactory;
import org.icepdf.core.util.Library;

import java.awt.*;
import java.util.logging.Logger;

/**
 * Factory for build annotations.
 * <br>
 * Note: Currently only Link annotations are supported.
 *
 * @since 4.0
 */
public class AnnotationFactory {

    private static final Logger logger =
            Logger.getLogger(AnnotationFactory.class.toString());

    /**
     * Creates a new Annotation object using properties from the annotationState
     * parameter.  If no annotationState is provided a LinkAnnotation is returned
     * with a black border.  The rect specifies where the annotation should
     * be located in user space.
     * <br>
     * This call adds the new Annotation object to the document library as well
     * as the document StateManager.
     *
     * @param library library to register annotation with
     * @param subType type of annotation to create
     * @param rect    bounds of new annotation specified in user space.
     * @return new annotation object with the same properties as the one
     * specified in annotation state.
     */
    public static Annotation buildAnnotation(Library library,
                                             final Name subType,
                                             Rectangle rect) {
        // build up a link annotation
        if (subType.equals(Annotation.SUBTYPE_LINK)) {
            return LinkAnnotation.getInstance(library, rect);
        }
        // highlight version of a TextMarkup annotation.
        else if (TextMarkupAnnotation.isTextMarkupAnnotation(subType)) {
            return TextMarkupAnnotation.getInstance(library, rect,
                    subType);
        } else if (subType.equals(Annotation.SUBTYPE_LINE)) {
            return LineAnnotation.getInstance(library, rect);
        } else if (subType.equals(Annotation.SUBTYPE_SQUARE)) {
            return SquareAnnotation.getInstance(library, rect);
        } else if (subType.equals(Annotation.SUBTYPE_CIRCLE)) {
            return CircleAnnotation.getInstance(library, rect);
        } else if (subType.equals(Annotation.SUBTYPE_INK)) {
            return InkAnnotation.getInstance(library, rect);
        } else if (subType.equals(Annotation.SUBTYPE_FREE_TEXT)) {
            return FreeTextAnnotation.getInstance(library, rect);
        } else if (subType.equals(Annotation.SUBTYPE_TEXT)) {
            return TextAnnotation.getInstance(library, rect);
        } else if (subType.equals(Annotation.SUBTYPE_POPUP)) {
            return PopupAnnotation.getInstance(library, rect);
        } else if (subType.equals(Annotation.SUBTYPE_REDACT)) {
            return RedactionAnnotation.getInstance(library, rect);
        } else {
            logger.warning("Unsupported Annotation type. ");
            return null;
        }
    }

    /**
     * Creates a new Widget Annotation object using properties from the annotationState
     * parameter.
     * <br>
     * This call adds the new Annotation object to the document library as well
     * as the document StateManager.
     *
     * @param library   library to register annotation with
     * @param fieldType field type to create
     * @param rect      bounds of new annotation specified in user space.
     * @return new annotation object
     */
    public static Annotation buildWidgetAnnotation(Library library,
                                                   final Name fieldType,
                                                   Rectangle rect) {
        // build up a link annotation
        if (fieldType.equals(FieldDictionaryFactory.TYPE_SIGNATURE)) {
            return SignatureWidgetAnnotation.getInstance(library, rect);
        } else {
            logger.warning("Unsupported Annotation type. ");
            return null;
        }
    }
}
