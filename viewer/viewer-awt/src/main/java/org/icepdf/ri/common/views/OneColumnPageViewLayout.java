package org.icepdf.ri.common.views;

import org.icepdf.ri.common.views.annotations.MarkupGlueComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import java.awt.*;

public class OneColumnPageViewLayout extends OnePageViewLayout {
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int maxWidth = parent.getWidth() - (insets.left + insets.right);
        int maxHeight = parent.getHeight() - (insets.top + insets.bottom);
        int xCord = 0, yCord= 0;
        int nComps = parent.getComponentCount();

        if (sizeUnknown) {
            setSizes(parent);
        }

        for (int i = 0, count = 0; i < nComps; i++) {
            Component component = parent.getComponent(i);
            if (component.isVisible() &&
                    !(component instanceof PopupAnnotationComponent || component instanceof MarkupGlueComponent)) {
                Dimension d = component.getPreferredSize();
                // set starting position for page, everything else flows from there
                if(count == 0){
                    // detect if we should be centering
                    if (minWidth < maxWidth){
                        xCord = (maxWidth - d.width) / 2;
                    }
                    if (minHeight < maxHeight){
                        yCord = (maxHeight - minHeight) / 2;
                    }
                    if (xCord < 0) xCord = 0;
                    if (yCord < 0) yCord = 0;

                    yCord += insets.top;
                } else {
                    xCord = (maxWidth - d.width) / 2;
                    yCord += d.height + PAGE_SPACING_VERTICAL;
                }
                xCord += insets.left;
                count++;

                component.setBounds(xCord, yCord, d.width, d.height);
            }
        }
    }
}
