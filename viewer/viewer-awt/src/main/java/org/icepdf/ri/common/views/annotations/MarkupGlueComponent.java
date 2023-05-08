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

import org.icepdf.core.pobjects.annotations.MarkupGluePainter;

import javax.swing.*;
import java.awt.*;

/**
 * MarkupGlueComponent allows for a visual associating between a markup annotation and it's popup annotation
 * when open.
 *
 * @since 6.3
 */
public class MarkupGlueComponent extends JComponent {

    protected final MarkupAnnotationComponent markupAnnotationComponent;
    protected final PopupAnnotationComponent popupAnnotationComponent;

    public MarkupGlueComponent(MarkupAnnotationComponent markupAnnotationComponent, PopupAnnotationComponent popupAnnotationComponent) {
        this.markupAnnotationComponent = markupAnnotationComponent;
        this.popupAnnotationComponent = popupAnnotationComponent;
        if (popupAnnotationComponent != null) {
            Rectangle bound = markupAnnotationComponent.getBounds().union(popupAnnotationComponent.getBounds());
            setBounds(bound);
            setPreferredSize(bound.getSize());
            setSize(bound.getSize());
        }
    }

    public MarkupAnnotationComponent getMarkupAnnotationComponent() {
        return markupAnnotationComponent;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (popupAnnotationComponent.getAnnotation().isOpen()) {
            Rectangle popupBounds = popupAnnotationComponent.getBounds();
            Rectangle markupBounds = markupAnnotationComponent.getBounds();
            Rectangle glueBounds = markupAnnotationComponent.getBounds().union(popupAnnotationComponent.getBounds());
            setBounds(glueBounds);
            setPreferredSize(glueBounds.getSize());
            setSize(glueBounds.getSize());

            MarkupGluePainter.paintGlue(
                    g, markupBounds, popupBounds, glueBounds,
                    markupAnnotationComponent.getAnnotation().getColor());
        }
    }
}
