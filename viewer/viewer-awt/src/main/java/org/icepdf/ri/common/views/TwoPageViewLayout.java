
package org.icepdf.ri.common.views;

import java.awt.*;
import java.util.Arrays;

/**
 * Layout manager for centering and adding pages to single or facing pages contiguous and non-contiguous views.
 */
public class TwoPageViewLayout implements LayoutManager2 {

    protected static final int PAGE_SPACING_HORIZONTAL = 2;
    protected static final int PAGE_SPACING_VERTICAL = 2;
    protected int minWidth = 0, minHeight = 0;
    protected int preferredWidth = 0, preferredHeight = 0;
    protected boolean sizeUnknown = true;

    protected int viewType;

    public TwoPageViewLayout(int viewType) {
        this.viewType = viewType;
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
        int previousWidth = 0;
        if (viewType == DocumentView.RIGHT_VIEW) {
            for (PageViewDecorator pageViewDecorator : pages) {
                int pageIndex = pageViewDecorator.getPageViewComponent().getPageIndex();
                Dimension d = pageViewDecorator.getPreferredSize();
                // apply left to right reading
                if (pageIndex == 0 && pages.length == 1) {
                    // offset to the right side
                    xCord = ((maxWidth - preferredWidth) / 2) + d.width + PAGE_SPACING_HORIZONTAL;
                } else if (count == 0) {
                    // start layout left to right
                    xCord += (maxWidth - preferredWidth) / 2;
                    previousWidth = d.width;
                    count++;
                } else {
                    xCord += previousWidth + PAGE_SPACING_HORIZONTAL;
                }
                yCord = (maxHeight - d.height) / 2;

                if (xCord < 0) xCord = 0;
                if (yCord < 0) yCord = 0;

                xCord += insets.left;
                yCord += insets.top;

                pageViewDecorator.setBounds(xCord, yCord, d.width, d.height);
            }
        } else {
            PageViewDecorator pageViewDecorator;
            for (int i = pages.length - 1; i >= 0; i--) {
                pageViewDecorator = pages[i];
                int pageIndex = pageViewDecorator.getPageViewComponent().getPageIndex();
                Dimension d = pageViewDecorator.getPreferredSize();
                // apply right to left reading
                if ((pageIndex == 0 && pages.length == 1) || count == 0) {
                    // left side
                    xCord += (maxWidth - preferredWidth) / 2;
                    previousWidth = d.width;
                    count++;
                } else {
                    xCord += previousWidth + PAGE_SPACING_HORIZONTAL;
                }
                yCord = (maxHeight - d.height) / 2;

                if (xCord < 0) xCord = 0;
                if (yCord < 0) yCord = 0;

                xCord += insets.left;
                yCord += insets.top;

                pageViewDecorator.setBounds(xCord, yCord, d.width, d.height);
            }
        }
    }

    public Dimension preferredLayoutSize(Container parent) {
        Dimension dimension = new Dimension(0, 0);

        setSizes(parent);

        Insets insets = parent.getInsets();
        dimension.width = preferredWidth + insets.left + insets.right;
        dimension.height = preferredHeight + insets.top + insets.bottom;
        sizeUnknown = false;

        return dimension;
    }

    public Dimension minimumLayoutSize(Container parent) {
        Dimension dimension = new Dimension(0, 0);
        Insets insets = parent.getInsets();
        dimension.width = minWidth + insets.left + insets.right;
        dimension.height = minHeight + insets.top + insets.bottom;
        sizeUnknown = false;
        return dimension;
    }

    public void addLayoutComponent(String name, Component comp) {
    }

    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public void addLayoutComponent(Component component, Object o) {

    }

    @Override
    public Dimension maximumLayoutSize(Container container) {
        return null;
    }

    @Override
    public float getLayoutAlignmentX(Container container) {
        return 0;
    }

    @Override
    public float getLayoutAlignmentY(Container container) {
        return 0;
    }

    @Override
    public void invalidateLayout(Container container) {

    }

    /**
     * Reset preferred/minimum width and height.
     *
     * @param parent parent container
     */
    protected void setSizes(Container parent) {
        preferredWidth = 0;
        preferredHeight = 0;
        minWidth = 0;
        minHeight = 0;

        Dimension dimension;

        PageViewDecorator[] pages = Arrays.stream(parent.getComponents())
                .filter(component -> component instanceof PageViewDecorator && component.isVisible())
                .toArray(PageViewDecorator[]::new);

        for (int i = 0; i < pages.length; i++) {
            Component component = pages[i];
            dimension = component.getPreferredSize();
            preferredWidth = Math.max((dimension.width * 2 + PAGE_SPACING_HORIZONTAL), preferredWidth);
            preferredHeight = Math.max(dimension.height + PAGE_SPACING_VERTICAL, preferredHeight);

            minWidth = Math.max(component.getMinimumSize().width * 2, minWidth);
            minHeight = preferredHeight;
        }
    }

    public String toString() {
        return getClass().getName();
    }
}

