package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;

import java.util.ResourceBundle;

/**
 * A factory of SwingController
 */
public final class SwingControllerFactory implements ControllerFactory {

    public static final SwingControllerFactory INSTANCE = new SwingControllerFactory();

    private SwingControllerFactory() {

    }

    @Override
    public Controller create() {
        return new SwingController();
    }

    @Override
    public Controller create(ResourceBundle bundle) {
        return new SwingController(bundle);
    }
}
