package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.common.views.DocumentViewController;
import org.icepdf.ri.common.views.DocumentViewControllerImpl;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;

/**
 * Factory for creating SwingViewBuilders
 */
public final class SwingViewBuilderFactory implements ViewBuilderFactory {

    private static final SwingViewBuilderFactory INSTANCE = new SwingViewBuilderFactory();

    private SwingViewBuilderFactory() {

    }

    /**
     * @return The factory instance
     */
    public static SwingViewBuilderFactory getInstance(){
        return INSTANCE;
    }

    @Override
    public ViewBuilder create(final Controller c) {
        return create(c, null);
    }

    @Override
    public ViewBuilder create(final Controller c, final ViewerPropertiesManager properties) {
        return create(c, properties, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED, null,
                DocumentViewControllerImpl.ONE_PAGE_VIEW,
                DocumentViewController.PAGE_FIT_WINDOW_HEIGHT, 0);
    }

    @Override
    public ViewBuilder create(final Controller c, final int documentViewType, final int documentPageFitMode) {
        return create(c, documentViewType, documentPageFitMode, 0);
    }

    @Override
    public ViewBuilder create(final Controller c, final int documentViewType, final int documentPageFitMode, final float rotation) {
        return create(c, null, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED,
                null, documentViewType, documentPageFitMode, rotation);
    }

    @Override
    public ViewBuilder create(final Controller c, final Font bf, final boolean bt, final int ts, final float[] zl,
                              final int documentViewType, final int documentPageFitMode) {
        return create(c, null, bf, bt, ts, zl, documentViewType, documentPageFitMode, 0);
    }

    @Override
    public ViewBuilder create(final Controller c, final ViewerPropertiesManager properties, final Font bf, final boolean bt,
                              final int ts, final float[] zl, final int documentViewType, final int documentPageFitMode, final float rotation) {
        return c instanceof SwingController ?
                new SwingViewBuilder((SwingController) c, properties, bf, bt, ts, zl, documentViewType, documentPageFitMode, rotation) : null;
    }
}
