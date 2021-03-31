package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.annotations.Annotation;

@FunctionalInterface
public interface AnnotationFilter {
    /**
     * Filters an annotation
     *
     * @param a The annotation
     * @return Whether the annotation passes the filter or not
     */
    boolean filter(Annotation a);

    /*
     * @return The opposite filter of this filter
     */
    default AnnotationFilter invertFilter() {
        return a -> !filter(a);
    }
}
