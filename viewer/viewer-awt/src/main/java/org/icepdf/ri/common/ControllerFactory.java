package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;

import java.util.ResourceBundle;

/**
 * A Controller Factory
 */
@FunctionalInterface
public interface ControllerFactory {

    /**
     * @return A controller with no resource bundle
     */
    default Controller create(){
        return create(null);
    }

    /**
     * Creates a controller with the given resource bundle
     *
     * @param bundle The resource bundle
     * @return The new controller
     */
    Controller create(ResourceBundle bundle);
}
