/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
