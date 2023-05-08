package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;
import org.icepdf.ri.util.ViewerPropertiesManager;

import java.awt.*;

/**
 * Factory for ViewBuilders
 */
public interface ViewBuilderFactory {

    /**
     * Creates a ViewBuilder for the given controller
     *
     * @param c The controller
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c);

    /**
     * Creates a ViewBuilder for the given controller and properties
     *
     * @param c          The controller
     * @param properties The properties
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, ViewerPropertiesManager properties);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, int documentViewType,
                       int documentPageFitMode);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @param rotation            The initial view rotation
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, int documentViewType,
                       int documentPageFitMode, float rotation);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, Font bf, boolean bt, int ts,
                       float[] zl, final int documentViewType,
                       final int documentPageFitMode);

    /**
     * Creates a ViewBuilder for the given controller and arguments
     *
     * @param c                   The controller
     * @param properties          The initial properties
     * @param documentViewType    The initial view type
     * @param documentPageFitMode The initial fit mode
     * @param rotation            The initial document rotation
     * @return The viewbuilder
     */
    ViewBuilder create(Controller c, ViewerPropertiesManager properties,
                       Font bf, boolean bt, int ts,
                       float[] zl, final int documentViewType,
                       final int documentPageFitMode, final float rotation);
}
