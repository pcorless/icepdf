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
package org.icepdf.fx.view;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.icepdf.core.pobjects.acroform.TextFieldDictionary;
import org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;

import java.awt.geom.Rectangle2D;
import java.util.Locale;

/**
 * The native editor laid over a text field while it has focus, as the Swing viewer's
 * {@code TextWidgetComponent} does: a TextField, TextArea or PasswordField by the field's type,
 * /MaxLen enforced, aligned by its quadding, sized by its /DA font size (auto-size 0 fits the
 * field's height).  Enter (Ctrl+Enter in a multi-line field), Tab and losing focus commit; Esc
 * cancels.  The value itself is applied by the caller through {@link FormController}.
 */
final class FieldEditor {

    /** What the editor reports. */
    interface Listener {
        /** Commit the text; {@code advance} is +1/-1 after Tab/Shift+Tab, else 0. */
        void commit(String text, int advance);

        void cancel();
    }

    private final TextInputControl control;
    private boolean finished;

    FieldEditor(TextWidgetAnnotation widget, String text, Rectangle2D viewBounds, double zoom, Listener listener) {
        TextFieldDictionary field = widget.getFieldDictionary();
        boolean multiLine = field.getTextFieldType() == TextFieldDictionary.TextFieldType.TEXT_AREA;
        boolean password = field.getTextFieldType() == TextFieldDictionary.TextFieldType.TEXT_PASSWORD;
        if (multiLine) {
            TextArea area = new TextArea();
            area.setWrapText(true);
            control = area;
        } else {
            TextField single = password ? new PasswordField() : new TextField();
            VariableTextFieldDictionary.Quadding quadding = field.getQuadding();
            single.setAlignment(quadding == VariableTextFieldDictionary.Quadding.CENTERED ? Pos.CENTER
                    : quadding == VariableTextFieldDictionary.Quadding.RIGHT_JUSTIFIED ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            control = single;
        }
        control.getStyleClass().add("pdf-field-editor");
        int maxLength = field.getMaxLength();
        if (maxLength > 0) {
            control.setTextFormatter(new TextFormatter<String>(change ->
                    change.getControlNewText().length() <= maxLength ? change : null));
        }
        control.setText(maxLength > 0 && text.length() > maxLength ? text.substring(0, maxLength) : text);
        double fontSize = field.getSize() > 0 ? field.getSize() * zoom
                : Math.max(6, viewBounds.getHeight() * (multiLine ? 0.25 : 0.6));
        control.setStyle(String.format(Locale.ROOT,
                "-fx-font-size: %.1fpx; -fx-padding: 0 2 0 2; -fx-background-radius: 0; -fx-border-width: 0;",
                fontSize));
        control.setLayoutX(viewBounds.getX());
        control.setLayoutY(viewBounds.getY());
        control.setPrefSize(viewBounds.getWidth(), viewBounds.getHeight());
        control.setMinSize(viewBounds.getWidth(), viewBounds.getHeight());
        control.setMaxSize(viewBounds.getWidth(), viewBounds.getHeight());

        control.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                finish(() -> listener.cancel());
                e.consume();
            } else if (e.getCode() == KeyCode.TAB) {
                String value = control.getText();
                int advance = e.isShiftDown() ? -1 : 1;
                finish(() -> listener.commit(value, advance));
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && (!multiLine || e.isShortcutDown())) {
                String value = control.getText();
                finish(() -> listener.commit(value, 0));
                e.consume();
            }
        });
        control.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                String value = control.getText();
                finish(() -> listener.commit(value, 0));
            }
        });
        // clicks inside the editor are the editor's, not the page tools'.
        control.addEventHandler(MouseEvent.ANY, MouseEvent::consume);
    }

    /** Runs the end action once: a commit and a focus loss can both try. */
    private void finish(Runnable action) {
        if (finished) return;
        finished = true;
        javafx.application.Platform.runLater(action);
    }

    TextInputControl control() {
        return control;
    }

    /** Commits whatever is typed (focus moving elsewhere); no-op once finished. */
    void commitNow(Listener listener) {
        String value = control.getText();
        finish(() -> listener.commit(value, 0));
    }

    boolean isFinished() {
        return finished;
    }
}
