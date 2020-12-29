package org.icepdf.qa.viewer.project;

import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.icepdf.qa.viewer.common.Mediator;

/**
 * Core functionality for custom dialogs.
 */
public class AbstractDialog<T> extends Dialog<T> {

    public AbstractDialog(Mediator mediator) {

        initStyle(StageStyle.UTILITY);
        setResizable(true);

        Stage primaryStage = mediator.getPrimaryStage();
        double x = primaryStage.getX() + primaryStage.getWidth() / 2 - 100;
        double y = primaryStage.getY() + primaryStage.getHeight() / 2 - 100;
        setX(x);
        setY(y);
    }
}
