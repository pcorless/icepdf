package org.icepdf.ri.common.views;

import java.awt.*;
import java.util.Arrays;

public class TwoColumnPageViewLayout extends TwoPageViewLayout{

    public TwoColumnPageViewLayout(int viewType, DocumentViewModel documentViewModel) {
        super(viewType, documentViewModel);
    }

    /*
     * This is called when the panel is first displayed, and every time its size changes.
     */
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int maxWidth = parent.getWidth() - (insets.left + insets.right);
        int maxHeight = parent.getHeight() - (insets.top + insets.bottom);
        int xCord = 0, yCord = 0;

        if (sizeUnknown) {
            setSizes(parent);
        }

        PageViewDecorator[] pages = Arrays.stream(parent.getComponents())
                .filter(component -> component instanceof PageViewDecorator && component.isVisible())
                .toArray(PageViewDecorator[]::new);

        int count = 0;
        Dimension previousDimension = null;
        if (viewType == DocumentView.RIGHT_VIEW) {
            for (PageViewDecorator pageViewDecorator : pages) {
                int pageIndex = pageViewDecorator.getPageViewComponent().getPageIndex();
                Dimension d = pageViewDecorator.getPreferredSize();
                // apply left to right reading
                if (pageIndex == 0) {
                    // offset to the right side
                    if (pages.length > 2 ){
                        xCord = ((maxWidth - preferredWidth) / 2) + d.width + PAGE_SPACING_HORIZONTAL + insets.left;
                    } else {
                        xCord = (maxWidth - preferredWidth) / 2;
                        count++;
                    }
                    if (preferredHeight < maxHeight){
                        yCord = (maxHeight - preferredHeight) / 2;
                    }
                    xCord += insets.left;
                    yCord += insets.top;
                } else if (count == 0) {
                    // start layout left to right
                    xCord = (maxWidth - preferredWidth) / 2;
                    xCord += insets.left;
                    yCord += previousDimension.height + PAGE_SPACING_VERTICAL;
                    count++;
                } else {
                    count = 0;
                    xCord += previousDimension.width + PAGE_SPACING_HORIZONTAL;
                }
                previousDimension = d;
                pageViewDecorator.setBounds(xCord, yCord, d.width, d.height);
                updatePopupAnnotationComponents(pageViewDecorator);
            }
        }
        else {
            PageViewDecorator pageViewDecorator, nexPageViewDecorator;
            for (int i = 0; i < pages.length; i++) {
                pageViewDecorator = pages[i];
                int pageIndex = pageViewDecorator.getPageViewComponent().getPageIndex();
                Dimension currentPageDimension = pageViewDecorator.getPreferredSize();
                // apply right to left reading
                if ((pageIndex == 0)) {
                    if (minWidth < maxWidth){
                        xCord = (maxWidth - preferredWidth) / 2;
                    }
                    if (minHeight < maxHeight){
                        yCord = (maxHeight - preferredHeight) / 2;
                    }
                    if (xCord < 0) xCord = 0;
                    if (yCord < 0) yCord = 0;

                    xCord += insets.left;
                    yCord += insets.top;

                    // otherwise move to right
                    if (pages.length == 2){
                        xCord += currentPageDimension.width + PAGE_SPACING_HORIZONTAL;
                        count++;
                    }

                } else if (count == 0) {
                    xCord += previousDimension.width + PAGE_SPACING_HORIZONTAL;
                    yCord += previousDimension.height + PAGE_SPACING_VERTICAL;
                    count++;
                } else {
                    xCord -= currentPageDimension.width + PAGE_SPACING_HORIZONTAL;
                    count = 0;
                }

                previousDimension = currentPageDimension;

                pageViewDecorator.setBounds(xCord, yCord, currentPageDimension.width, currentPageDimension.height);
            }
        }
    }

    protected void setSizes(Container parent) {
        preferredWidth = 0;
        preferredHeight = 0;
        minWidth = 0;
        minHeight = 0;

        Dimension dimension;

        PageViewDecorator[] pages = Arrays.stream(parent.getComponents())
                .filter(component -> component instanceof PageViewDecorator && component.isVisible())
                .toArray(PageViewDecorator[]::new);

        for (int i = 0, max = pages.length / 2 ; i < max; i++) {
            Component component = pages[i];
            dimension = component.getPreferredSize();
            preferredWidth = Math.max((dimension.width * 2 + PAGE_SPACING_HORIZONTAL), preferredWidth);
            preferredHeight += dimension.height + PAGE_SPACING_VERTICAL;

            minWidth = Math.max(component.getMinimumSize().width * 2, minWidth);
            minHeight += preferredHeight;
        }
        if (pages.length > 0 && pages.length != 2) {
            preferredHeight += pages[0].getPreferredSize().height + PAGE_SPACING_VERTICAL;
            minHeight += preferredHeight;
        }
    }
}
