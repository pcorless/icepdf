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

    public static final SwingViewBuilderFactory INSTANCE = new SwingViewBuilderFactory();

    private SwingViewBuilderFactory() {

    }

    @Override
    public ViewBuilder create(Controller c) {
        return create(c, null);
    }

    @Override
    public ViewBuilder create(Controller c, ViewerPropertiesManager properties) {
        return create(c, properties, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED, null,
                DocumentViewControllerImpl.ONE_PAGE_VIEW,
                DocumentViewController.PAGE_FIT_WINDOW_HEIGHT, 0);
    }

    @Override
    public ViewBuilder create(Controller c, int documentViewType, int documentPageFitMode) {
        return create(c, documentViewType, documentPageFitMode, 0);
    }

    @Override
    public ViewBuilder create(Controller c, int documentViewType, int documentPageFitMode, float rotation) {
        return create(c, null, null, false, SwingViewBuilder.TOOL_BAR_STYLE_FIXED,
                null, documentViewType, documentPageFitMode, rotation);
    }

    @Override
    public ViewBuilder create(Controller c, Font bf, boolean bt, int ts, float[] zl, int documentViewType, int documentPageFitMode) {
        return create(c, null, bf, bt, ts, zl, documentViewType, documentPageFitMode, 0);
    }

    @Override
    public ViewBuilder create(Controller c, ViewerPropertiesManager properties, Font bf, boolean bt, int ts, float[] zl, int documentViewType, int documentPageFitMode, float rotation) {
        return c instanceof SwingController ? new SwingViewBuilder((SwingController) c, properties, bf, bt, ts, zl, documentViewType, documentPageFitMode, rotation) : null;
    }
}
