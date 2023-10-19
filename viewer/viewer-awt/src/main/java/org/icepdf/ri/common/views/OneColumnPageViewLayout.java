package org.icepdf.ri.common.views;

import java.awt.*;
import java.util.Arrays;

public class OneColumnPageViewLayout extends OnePageViewLayout {


    public OneColumnPageViewLayout(DocumentViewModel documentViewModel) {
        super(documentViewModel);
    }

    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int maxWidth = parent.getWidth() - (insets.left + insets.right);
        int maxHeight = parent.getHeight() - (insets.top + insets.bottom);
        int xCord = 0, yCord= 0;
        int previousHeight = 0;
        if (sizeUnknown) {
            setSizes(parent);
        }

        double systemScaling = documentViewModel.getSystemScaling();

        PageViewDecorator[] pages = Arrays.stream(parent.getComponents())
                .filter(component -> component instanceof PageViewDecorator && component.isVisible())
                .toArray(PageViewDecorator[]::new);

        int index = 0;
        for (PageViewDecorator pageViewDecorator : pages) {
            Dimension d = pageViewDecorator.getPreferredSize();
            int boundsWidth = d.width;
            int boundsHeight = d.height;
            
            d.width = (int)Math.round(d.width / systemScaling);
            d.height = (int)Math.round(d.height / systemScaling);

            // set starting position for page, everything else flows from there
            if(index == 0){
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
                yCord += previousHeight + PAGE_SPACING_VERTICAL;
            }
            previousHeight = d.height;
            index++;
            xCord += insets.left;

            pageViewDecorator.setBounds(xCord, yCord, boundsWidth, boundsHeight);
            updatePopupAnnotationComponents(pageViewDecorator);
        }
    }
}
