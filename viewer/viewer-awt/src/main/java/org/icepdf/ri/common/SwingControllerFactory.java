package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;

import java.util.ResourceBundle;

/**
 * A factory of SwingController
 */
public final class SwingControllerFactory implements ControllerFactory {

    private static final SwingControllerFactory INSTANCE = new SwingControllerFactory();

    private SwingControllerFactory() {

    }

    /**
     * @return The factory instance
     */
    public static SwingControllerFactory getInstance(){
        return INSTANCE;
    }

    @Override
    public Controller create() {
        return new SwingController();
    }

    @Override
    public Controller create(final ResourceBundle bundle) {
        return new SwingController(bundle);
    }
}
