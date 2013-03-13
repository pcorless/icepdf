/*
 * Copyright 2006-2012 ICEsoft Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either * express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.icepdf.ri.common.utility.annotation;

import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.PageTree;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.ri.common.views.AnnotationComponent;

import javax.swing.*;
import java.awt.*;

/**
 * All annotation and action property panels have a common method for
 * assigning the current annotation component.
 *
 * @since 4.0
 */
public abstract class AnnotationPanelAdapter extends JPanel
        implements AnnotationProperties {

    // action instance that is being edited
    protected AnnotationComponent currentAnnotationComponent;

    protected AnnotationPanelAdapter(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
    }

    /**
     * Utility to update the action annotation when changes have been made to
     * 'Dest' which has the same notation as 'GoTo'.  It's the pre action way
     * of doign things and is still very common of link Annotations. .
     *
     * @param annotation annotation to update/sync with parent page object.
     */
    protected void updateCurrentAnnotation(Annotation annotation) {
        int pageIndex = currentAnnotationComponent.getPageIndex();
        PageTree pageTree = currentAnnotationComponent.getDocument().getPageTree();
        Page page = pageTree.getPage(pageIndex);
        // update the altered annotation.
        page.updateAnnotation(annotation);
    }
}
