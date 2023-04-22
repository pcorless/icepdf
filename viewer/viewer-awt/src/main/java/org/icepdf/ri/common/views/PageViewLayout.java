package org.icepdf.ri.common.views;

import org.icepdf.ri.common.views.annotations.PopupAnnotationComponent;

import java.awt.*;

/**
 * Layout manager for centering and adding pages to single or facing pages contiguous and non-contiguous views.
 */
public class PageViewLayout implements LayoutManager2 {
    private final int vgap;
    private int minWidth = 0, minHeight = 0;
    private int preferredWidth = 0, preferredHeight = 0;
    private boolean sizeUnknown = true;

    public PageViewLayout() {
        this(5);
    }

    public PageViewLayout(int v) {
        vgap = v;
    }

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
            if (component.isVisible() && !(component instanceof PopupAnnotationComponent)) {
                Dimension d = component.getPreferredSize();

                // center the page or pagesPanel
                int xCord = (maxWidth - d.width) / 2;
                int yCord = (maxHeight - d.height) / 2;

                if (xCord < 0) xCord = 0;
                if (yCord < 0) yCord = 0;

                xCord += insets.left;
                yCord += insets.top;

                component.setBounds(xCord, yCord, d.width, d.height);


                // popup components
                // map page space coordinate to parent pages location
                // likely keep a cache of the page offsets to speed this up

                // worry about glue later
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
    private void setSizes(Container parent) {
        preferredWidth = 0;
        preferredHeight = 0;
        minWidth = 0;
        minHeight = 0;

        int nComps = parent.getComponentCount();
        Dimension dimension;

        for (int i = 0; i < nComps; i++) {
            Component component = parent.getComponent(i);
            if (component.isVisible()) {
                dimension = component.getPreferredSize();
                if (i > 0) {
                    preferredWidth += dimension.width / 2;
                    preferredHeight += vgap;
                } else {
                    preferredWidth = dimension.width;
                }
                preferredHeight += dimension.height;

                minWidth = Math.max(component.getMinimumSize().width, minWidth);
                minHeight = preferredHeight;
            }
        }
    }

    public String toString() {
        String str = "";
        return getClass().getName() + "[vgap=" + vgap + str + "]";
    }
}
