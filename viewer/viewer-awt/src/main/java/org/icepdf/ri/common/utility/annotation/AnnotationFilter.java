package org.icepdf.ri.common.utility.annotation;


import org.icepdf.core.pobjects.annotations.Annotation;

/**
 * An annotation filter filters annotations given specific conditions
 */
@FunctionalInterface
public interface AnnotationFilter {

    /**
     * Filters an annotation
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