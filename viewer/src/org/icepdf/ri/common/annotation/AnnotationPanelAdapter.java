package org.icepdf.ri.common.annotation;

import org.icepdf.core.views.swing.AnnotationComponentImpl;

import javax.swing.*;
import java.awt.*;

/**
 * All annotation and action property panels have a common method for
 * assigning the current annotation component.
 *
 * @since 4.0
 */
public abstract class AnnotationPanelAdapter extends JPanel{

    protected AnnotationPanelAdapter(LayoutManager layout, boolean isDoubleBuffered) {
        super(layout, isDoubleBuffered);
    }

    public abstract void setAnnotationComponent(AnnotationComponentImpl annotaiton);
}
