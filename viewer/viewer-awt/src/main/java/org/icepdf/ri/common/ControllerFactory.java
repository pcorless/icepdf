package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;

import java.util.ResourceBundle;

/**
 * A Controller Factory
 */
public interface ControllerFactory {

    /**
     * @return A controller with no resource bundle
     */
    Controller create();

    /**
     * Creates a controller with the given resource bundle
     *
     * @param bundle The resource bundle
     * @return The new controller
     */
    Controller create(ResourceBundle bundle);
}
