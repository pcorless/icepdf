/*
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
package org.icepdf.ri.common.utility.annotation;


import org.icepdf.core.pobjects.annotations.Annotation;

/**
 * An annotation filter filters annotations given specific conditions
 */
@FunctionalInterface
public interface AnnotationFilter {

    /**
     * Filters an annotation
     *
     * @param a The annotation
     * @return True if the annotation fulfills the filter condition, false otherwise
     */
    boolean filter(Annotation a);

    /**
     * @return A filter filtering the opposite of the current filter
     */
    default AnnotationFilter invertFilter() {
        return a -> !filter(a);
    }
}