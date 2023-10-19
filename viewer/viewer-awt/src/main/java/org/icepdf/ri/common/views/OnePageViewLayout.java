package org.icepdf.ri.common.views;

import java.awt.*;
import java.util.Arrays;

/**
 * Layout manager for centering and adding pages to single or facing pages contiguous and non-contiguous views.
 */
public class OnePageViewLayout extends BasePageViewLayout implements LayoutManager2 {

    protected int minWidth = 0, minHeight = 0;
    protected int preferredWidth = 0, preferredHeight = 0;
    protected boolean sizeUnknown = true;

    public OnePageViewLayout(DocumentViewModel documentViewModel) {
        super(documentViewModel);
    }

    /*
     * This is called when the panel is first displayed, and every time its size changes.
     */
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int maxWidth = parent.getWidth() - (insets.left + insets.right);
        int maxHeight = parent.getHeight() - (insets.top + insets.bottom);
        
        if (sizeUnknown) {
            setSizes(parent);
        }

        double systemScaling = documentViewModel.getSystemScaling();

        PageViewDecorator[] pages = Arrays.stream(parent.getComponents())
                .filter(component -> component instanceof PageViewDecorator && component.isVisible())
                .toArray(PageViewDecorator[]::new);

        for (PageViewDecorator pageViewDecorator : pages) {
            Dimension d = pageViewDecorator.getPreferredSize();
            // center the page or pagesPanel
            int xCord = (int)Math.round((maxWidth - d.width / systemScaling) / 2);
            int yCord = (int)Math.round((maxHeight - d.height / systemScaling) / 2);

            if (xCord < 0) xCord = 0;
            if (yCord < 0) yCord = 0;

            xCord += insets.left;
            yCord += insets.top;

            pageViewDecorator.setBounds(xCord, yCord, d.width, d.height);
            updatePopupAnnotationComponents(pageViewDecorator);
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

        for (PageViewDecorator pageViewDecorator : pages) {
            dimension = pageViewDecorator.getPreferredSize();
            preferredWidth = dimension.width;
            preferredHeight += dimension.height + PAGE_SPACING_VERTICAL;

            minWidth = Math.max(pageViewDecorator.getMinimumSize().width, minWidth);
        }

        preferredHeight += pages.length * PAGE_SPACING_VERTICAL;
        minHeight = preferredHeight;

        double systemScaling = documentViewModel.getSystemScaling();
        minWidth = (int)Math.round(minWidth / systemScaling);
        minHeight = (int)Math.round(minHeight / systemScaling);
        preferredWidth = (int)Math.round(preferredWidth / systemScaling);
        preferredHeight = (int)Math.round(preferredHeight / systemScaling);
    }

    public String toString() {
        return getClass().getName();
    }
}
