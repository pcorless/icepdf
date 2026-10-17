/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.icepdf.fx.view.internal.annot;

import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;

/** An annotation and the page it is on. */
public record AnnotationHit(int pageIndex, Annotation annotation) {

    /** A click follows it: a link, or a non-markup annotation with an action. */
    public boolean isActionable() {
        return annotation instanceof LinkAnnotation
                || (annotation.getAction() != null && !(annotation instanceof MarkupAnnotation));
    }
}
