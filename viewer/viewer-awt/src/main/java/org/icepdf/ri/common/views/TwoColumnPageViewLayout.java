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

        double systemScaling = documentViewModel.getSystemScaling();

        PageViewDecorator[] pages = Arrays.stream(parent.getComponents())
                .filter(component -> component instanceof PageViewDecorator && component.isVisible())
                .toArray(PageViewDecorator[]::new);

        int count = 0;
        Dimension previousDimension = new Dimension();
        for (PageViewDecorator pageViewDecorator : pages) {
            int pageIndex = pageViewDecorator.getPageViewComponent().getPageIndex();
            Dimension d = pageViewDecorator.getPreferredSize();
            int boundsWidth = d.width;
            int boundsHeight = d.height;
            
            d.width = (int)Math.round(d.width / systemScaling);
            d.height = (int)Math.round(d.height / systemScaling);

            // apply left to right reading
            if (viewType == DocumentView.RIGHT_VIEW && pageIndex == 0 &&
                    (pages.length != 2)) {
                // offset to the right side
                xCord = ((maxWidth - preferredWidth) / 2) + d.width + PAGE_SPACING_HORIZONTAL;
                if (preferredHeight < maxHeight) {
                    yCord = (maxHeight - preferredHeight) / 2;
                }
                xCord += insets.left;
            } else if (count == 0) {
                xCord = (maxWidth - preferredWidth) / 2;
                xCord += insets.left;
                yCord += previousDimension.height + PAGE_SPACING_VERTICAL;
                if (preferredHeight < maxHeight) {
                    yCord = (maxHeight - preferredHeight) / 2;
                    yCord += PAGE_SPACING_VERTICAL;
                }
                count++;
            } else {
                count = 0;
                xCord += previousDimension.width + PAGE_SPACING_HORIZONTAL;
            }
            previousDimension = d;
            pageViewDecorator.setBounds(xCord, yCord, boundsWidth, boundsHeight);
            updatePopupAnnotationComponents(pageViewDecorator);
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

        for (int i = 0, max = pages.length; i < max; i += 2) {
            Component component = pages[i];
            dimension = component.getPreferredSize();
            preferredWidth = Math.max((dimension.width * 2 + PAGE_SPACING_HORIZONTAL), preferredWidth);
            preferredHeight += dimension.height + PAGE_SPACING_VERTICAL;

            minWidth = Math.max(component.getMinimumSize().width * 2, minWidth);
            minHeight += preferredHeight;
        }
        // add height of right side page height for even number documents, otherwise height will be off.
        if (pages.length > 2 && pages.length % 2 == 0) {
            preferredHeight += pages[0].getPreferredSize().height + PAGE_SPACING_VERTICAL;
            minHeight += preferredHeight;
        }

        double systemScaling = documentViewModel.getSystemScaling();
        minWidth = (int)Math.round(minWidth / systemScaling);
        minHeight = (int)Math.round(minHeight / systemScaling);
        preferredWidth = (int)Math.round(preferredWidth / systemScaling);
        preferredHeight = (int)Math.round(preferredHeight / systemScaling);
    }
}
