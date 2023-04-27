package org.icepdf.ri.common.views;

import org.icepdf.ri.common.views.annotations.MarkupGlueComponent;
import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import java.awt.*;

/**
 * Layout manager for centering and adding pages to single or facing pages contiguous and non-contiguous views.
 */
public class SinglePageViewLayout implements LayoutManager2 {

    protected static final int PAGE_SPACING_HORIZONTAL = 2;
    protected static final int PAGE_SPACING_VERTICAL = 2;
    protected int minWidth = 0, minHeight = 0;
    protected int preferredWidth = 0, preferredHeight = 0;
    protected boolean sizeUnknown = true;

    /*
     * This is called when the panel is first displayed, and every time its size changes.
     */
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int maxWidth = parent.getWidth() - (insets.left + insets.right);
        int maxHeight = parent.getHeight() - (insets.top + insets.bottom);
        int nComps = parent.getComponentCount();

        if (sizeUnknown) {
            setSizes(parent);
        }

        for (int i = 0; i < nComps; i++) {
            Component component = parent.getComponent(i);
            if (component.isVisible() &&
                    !(component instanceof PopupAnnotationComponent || component instanceof MarkupGlueComponent)) {
                Dimension d = component.getPreferredSize();
                // center the page or pagesPanel
                int xCord = (maxWidth - d.width) / 2;
                int yCord = (maxHeight - d.height) / 2;

                if (xCord < 0) xCord = 0;
                if (yCord < 0) yCord = 0;

                xCord += insets.left;
                yCord += insets.top;

                component.setBounds(xCord, yCord, d.width, d.height);
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

        int nComps = parent.getComponentCount();
        Dimension dimension;

        for (int i = 0; i < nComps; i++) {
            Component component = parent.getComponent(i);
            if (component.isVisible() &&
                    !(component instanceof PopupAnnotationComponent || component instanceof MarkupGlueComponent)) {
                dimension = component.getPreferredSize();
                preferredWidth = dimension.width;
                preferredHeight += dimension.height + PAGE_SPACING_VERTICAL;

                minWidth = Math.max(component.getMinimumSize().width, minWidth);
                minHeight = preferredHeight;
            }
        }
    }

    public String toString() {
        return getClass().getName();
    }
}
