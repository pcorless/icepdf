/*
 * Copyright 2006-2017 ICEsoft Technologies Canada Corp.
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
package org.icepdf.ri.common.views;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.Annotation;

/**
 * AnnotationComponent interfaces.  Outlines two main methods needed for
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
    Annotation getAnnotation();

    /**
     * Refresh the annotations bounds rectangle.  This method insures that
     * the bounds have been correctly adjusted for the current page transformation
     * In a none visual representation this method may not have to do anything.
     */
    void refreshDirtyBounds();

    /**
     * Refreshed the annotation rectangle by inverting the components current
     * bounds with the current page transformation.
     */
    void refreshAnnotationRect();

    /**
     * Component has focus.
     *
     * @return true if has focus, false otherwise.
     */
    boolean hasFocus();

    /**
     * Component is editable, contents can be updated in ui
     * @return true if has editable, false otherwise.
     */
    boolean isEditable();

    /**
     * Component is editable, contents can be updated in ui
     * @return true if show invisible border, false otherwise.
     */
    boolean isShowInvisibleBorder();

    /**
     * Component highlight/select border is draw on mouse over.
     * @return true if is rollover, false otherwise.
     */
    boolean isRollover();

    /**
     * Component is movable.
     * @return true if movable, false otherwise.
     */
    boolean isMovable();

    /**
     * Component is resizable.
     * @return true if resizable, false otherwise.
     */
    boolean isResizable();

    /**
     * border has defined style.
     *
     * @return true if has border style, false otherwise.
     */
    boolean isBorderStyle();

    /**
     * Annotation is in a selected state. Used for drawing a highlighted state.
     *
     * @return is selected
     */
    boolean isSelected();

    /**
     * Document that annotation belows too.
     *
     * @return document parent.
     */
    Document getDocument();

    /**
     * Page index that annotation component resides on.
     *
     * @return page index of parent page
     */
    int getPageIndex();

    /**
     * Sets the selected state
     *
     * @param selected selected state.
     */
    void setSelected(boolean selected);

    /**
     * Repaints this component
     */
    void repaint();

    /**
     * Rest the annotation appearance stream.
     */
    void resetAppearanceShapes();

    /**
     * Gets the parent page view that displays this component.
     *
     * @return parent component.
     */
    PageViewComponent getPageViewComponent();

    /**
     * Dispose this component resources.
     */
    void dispose();

}
