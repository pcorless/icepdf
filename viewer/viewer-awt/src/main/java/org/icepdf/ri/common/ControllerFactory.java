package org.icepdf.ri.common;

import org.icepdf.ri.common.views.Controller;

import java.util.ResourceBundle;

public interface ControllerFactory {

    Controller create();

    Controller create(ResourceBundle bundle);
}
