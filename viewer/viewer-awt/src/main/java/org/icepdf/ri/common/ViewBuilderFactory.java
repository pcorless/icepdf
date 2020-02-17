package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;

public interface ViewBuilderFactory {

    ViewBuilder create(Controller c);

    ViewBuilder create(Controller c, ViewerPropertiesManager properties);

    ViewBuilder create(Controller c, int documentViewType,
                       int documentPageFitMode);

    ViewBuilder create(Controller c, int documentViewType,
                       int documentPageFitMode, float rotation);

    ViewBuilder create(Controller c, Font bf, boolean bt, int ts,
                       float[] zl, final int documentViewType,
                       final int documentPageFitMode);

    ViewBuilder create(Controller c, ViewerPropertiesManager properties,
                       Font bf, boolean bt, int ts,
                       float[] zl, final int documentViewType,
                       final int documentPageFitMode, final float rotation);
}
