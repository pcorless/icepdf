package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;

import java.util.ResourceBundle;

public class SwingControllerFactory implements ControllerFactory {
    @Override
    public Controller create() {
        return new SwingController();
    }

    @Override
    public Controller create(ResourceBundle bundle) {
        return new SwingController(bundle);
    }
}
