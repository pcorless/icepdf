package org.icepdf.core.views;

import org.icepdf.core.pobjects.annotations.Annotation;

/**
 * AnnotationComponent interfaces.  Oulines two main methods needed for
 * management and state saving but avoids having to load the Swing/awt libraries
 * unless necessary.
 *
 * @since 4.0
 */
public interface AnnotationComponent {

    /**
     * Gets wrapped annotation object.
     *
     * @return annotation that this component wraps.
     */
    public Annotation getAnnotation();

    /**
     * Refreshs the annotations bounds rectangle.  This method insures that
     * the bounds have been correctly adjusted for the current page transformation
     * In a none visual representation this method may not have to do anything.
     */
    public void refreshDirtyBounds();

    /**
     * Component has focus.
     *
     * @return true if has focus, false otherwise.
     */
    public boolean hasFocus();

     public boolean isEditable() ;

    public boolean isRollover();

    public boolean isLinkAnnot();

    public boolean isBorderStyle() ;

    public boolean isSelected();
}
