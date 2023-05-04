package org.icepdf.ri.common.views.annotations;

import org.icepdf.ri.common.views.AbstractPageViewComponent;

/**
 * Common interface for handling the position  of annotation components painted at the document view level.
 */
public interface PageViewAnnotationComponent {

    void setParentPageComponent(AbstractPageViewComponent pageViewComponent);

    void refreshDirtyBounds();
    void repaint();
}
