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
package org.icepdf.fx.ri.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * A short message near the bottom of the viewer that fades away by itself ("Saved", "Printed") -
 * in place of a status bar.  Add it on top of the viewer; it never takes the pointer.
 */
public final class Toast extends StackPane {

    private final Label label = new Label();
    private SequentialTransition running;

    public Toast() {
        getStyleClass().add("toast-layer");
        setPickOnBounds(false);
        setMouseTransparent(true);
        label.getStyleClass().add("toast");
        label.setOpacity(0);
        StackPane.setAlignment(label, Pos.BOTTOM_CENTER);
        StackPane.setMargin(label, new javafx.geometry.Insets(0, 0, 32, 0));
        getChildren().add(label);
    }

    /** Shows a message for a few seconds. */
    public void show(String message) {
        if (running != null) running.stop();
        label.setText(message);
        FadeTransition in = new FadeTransition(Duration.millis(150), label);
        in.setToValue(1);
        PauseTransition hold = new PauseTransition(Duration.seconds(Math.max(2, message.length() / 18.0)));
        FadeTransition out = new FadeTransition(Duration.millis(400), label);
        out.setToValue(0);
        running = new SequentialTransition(in, hold, out);
        running.play();
    }

    /** The message showing, or "" (for tests). */
    public String getMessage() {
        return label.getOpacity() > 0 ? label.getText() : "";
    }
}
