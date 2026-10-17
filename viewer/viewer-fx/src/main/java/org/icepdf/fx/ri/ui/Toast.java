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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.ri.icons.IconProvider;

/**
 * Messages near the bottom of the viewer, in place of a status bar.  A plain message ("Saved",
 * "Printed") fades away by itself and never takes the pointer.  An error ({@link #showError})
 * stays, in the error colours with an icon, until the user dismisses it (or Esc while it has the
 * focus): it may name something to act on, a file kept under another name say, so it can also be
 * copied.  A newer error replaces it; plain messages show below it without hiding it.
 */
public final class Toast extends StackPane {

    private final Label label = new Label();
    private final Label errorText = new Label();
    private final HBox error;
    private SequentialTransition running;

    public Toast() {
        this(null);
    }

    /** @param icons the error card's icons ({@code ui.error}, {@code ui.close}); may be null */
    public Toast(IconProvider icons) {
        getStyleClass().add("toast-layer");
        setPickOnBounds(false);
        label.getStyleClass().add("toast");
        label.setOpacity(0);
        label.setMouseTransparent(true);
        label.setWrapText(true);

        errorText.setWrapText(true);
        errorText.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox.setHgrow(errorText, Priority.ALWAYS);
        Button copy = new Button(Messages.get("toast.copy"));
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(errorText.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        Node closeIcon = icons != null ? icons.icon("ui.close", 14) : null;
        Button close = new Button(closeIcon != null ? null : "\u00d7", closeIcon);
        close.setTooltip(new Tooltip(Messages.get("toast.dismiss")));
        close.setAccessibleText(Messages.get("toast.dismiss"));
        close.setOnAction(e -> dismissError());
        Node errorIcon = icons != null ? icons.icon("ui.error", 20) : null;
        error = errorIcon != null ? new HBox(errorIcon, errorText, copy, close) : new HBox(errorText, copy, close);
        error.getStyleClass().add("toast-error");
        error.setFillHeight(false);
        error.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dismissError();
                e.consume();
            }
        });
        // wide enough to read a path, never wider than the viewer.
        error.maxWidthProperty().bind(widthProperty().multiply(0.7));
        error.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        error.setVisible(false);
        error.managedProperty().bind(error.visibleProperty());

        VBox column = new VBox(8, error, label);
        column.setAlignment(Pos.BOTTOM_CENTER);
        column.setPickOnBounds(false);
        column.setFillWidth(false);
        label.maxWidthProperty().bind(widthProperty().multiply(0.7));
        StackPane.setAlignment(column, Pos.BOTTOM_CENTER);
        StackPane.setMargin(column, new Insets(0, 16, 32, 16));
        getChildren().add(column);
    }

    /** Shows a message for a few seconds; a blank one is ignored. */
    public void show(String message) {
        if (message == null || message.isBlank()) return;
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

    /** Shows an error until it is dismissed, replacing one showing; a blank one is ignored. */
    public void showError(String message) {
        if (message == null || message.isBlank()) return;
        errorText.setText(message);
        error.setAccessibleText(message);
        error.setVisible(true);
    }

    /** Takes the error away. */
    public void dismissError() {
        error.setVisible(false);
        errorText.setText("");
    }

    /** The message showing, or "" (for tests). */
    public String getMessage() {
        return label.getOpacity() > 0 ? label.getText() : "";
    }

    /** The error showing, or "" (for tests). */
    public String getError() {
        return error.isVisible() ? errorText.getText() : "";
    }
}
