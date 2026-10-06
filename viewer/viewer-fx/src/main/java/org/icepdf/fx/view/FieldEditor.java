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

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.icepdf.core.pobjects.acroform.ChoiceFieldDictionary;
import org.icepdf.core.pobjects.acroform.TextFieldDictionary;
import org.icepdf.core.pobjects.acroform.VariableTextFieldDictionary;
import org.icepdf.core.pobjects.annotations.ChoiceWidgetAnnotation;
import org.icepdf.core.pobjects.annotations.TextWidgetAnnotation;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The native editor laid over a field while it has focus, as the Swing viewer's
 * {@code TextWidgetComponent} / {@code ChoiceComboComponent} / {@code ChoiceListComponent} do:
 * <ul>
 * <li>text: a TextField, TextArea or PasswordField by the field's type, /MaxLen enforced, aligned
 * by its quadding;</li>
 * <li>combo: a ComboBox, editable for /Ff Edit;</li>
 * <li>list: a ListView, multi-select for /Ff MultiSelect.</li>
 * </ul>
 * Sized by the /DA font size (auto-size 0 fits the field).  Enter (Ctrl+Enter in a multi-line
 * field), Tab and losing focus commit; picking a combo entry or a single-select list entry commits
 * at once; Esc cancels.  The value itself is applied by the caller through {@link FormController}:
 * a String (text, or typed into an editable combo) or a List of option indexes (a choice).
 */
final class FieldEditor {

    /** What the editor reports. */
    interface Listener {
        /** Commit the value; {@code advance} is +1/-1 after Tab/Shift+Tab, else 0. */
        void commit(Object value, int advance);

        void cancel();
    }

    private final Control control;
    private final Supplier<Object> value;
    private final Listener listener;
    private boolean finished;

    private FieldEditor(Control control, Supplier<Object> value, boolean multiLine, Rectangle2D viewBounds,
                        double fontSize, Listener listener) {
        this.control = control;
        this.value = value;
        this.listener = listener;
        control.getStyleClass().add("pdf-field-editor");
        control.setStyle(String.format(Locale.ROOT,
                "-fx-font-size: %.1fpx; -fx-padding: 0 2 0 2; -fx-background-radius: 0; -fx-border-width: 0;",
                fontSize));
        control.setLayoutX(viewBounds.getX());
        control.setLayoutY(viewBounds.getY());
        control.setPrefSize(viewBounds.getWidth(), viewBounds.getHeight());
        control.setMinSize(viewBounds.getWidth(), viewBounds.getHeight());
        control.setMaxSize(viewBounds.getWidth(), viewBounds.getHeight());
        // the UI layer doesn't auto-size its children (popups keep their own size): size it here.
        control.resize(viewBounds.getWidth(), viewBounds.getHeight());

        control.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                finish(listener::cancel);
                e.consume();
            } else if (e.getCode() == KeyCode.TAB) {
                commit(e.isShiftDown() ? -1 : 1);
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && (!multiLine || e.isShortcutDown())) {
                commit(0);
                e.consume();
            }
        });
        control.focusedProperty().addListener((obs, was, now) -> {
            if (!now) commit(0);
        });
        // clicks inside the editor are the editor's, not the page tools'.
        control.addEventHandler(MouseEvent.ANY, MouseEvent::consume);
    }

    /** A text field's editor. */
    static FieldEditor text(TextWidgetAnnotation widget, String text, Rectangle2D viewBounds, double zoom,
                            Listener listener) {
        TextFieldDictionary field = widget.getFieldDictionary();
        boolean multiLine = field.getTextFieldType() == TextFieldDictionary.TextFieldType.TEXT_AREA;
        boolean password = field.getTextFieldType() == TextFieldDictionary.TextFieldType.TEXT_PASSWORD;
        TextInputControl control;
        if (multiLine) {
            TextArea area = new TextArea();
            area.setWrapText(true);
            control = area;
        } else {
            TextField single = password ? new PasswordField() : new TextField();
            single.setAlignment(alignment(field.getQuadding()));
            control = single;
        }
        int maxLength = field.getMaxLength();
        if (maxLength > 0) {
            // over-long input (a paste) is cut to fit rather than refused outright.
            control.setTextFormatter(new TextFormatter<String>(change -> {
                int over = change.getControlNewText().length() - maxLength;
                if (over <= 0) return change;
                String added = change.getText();
                if (over >= added.length()) return null;
                change.setText(added.substring(0, added.length() - over));
                int caret = change.getRangeStart() + change.getText().length();
                change.selectRange(caret, caret);
                return change;
            }));
        }
        control.setText(maxLength > 0 && text.length() > maxLength ? text.substring(0, maxLength) : text);
        double fontSize = field.getSize() > 0 ? field.getSize() * zoom
                : Math.max(6, viewBounds.getHeight() * (multiLine ? 0.25 : 0.6));
        return new FieldEditor(control, control::getText, multiLine, viewBounds, fontSize, listener);
    }

    /**
     * A combo or list field's editor.
     *
     * @param openPopup show a combo's drop-down at once (it was clicked, not tabbed to)
     */
    static FieldEditor choice(ChoiceWidgetAnnotation widget, Rectangle2D viewBounds, double zoom, boolean openPopup,
                              Listener listener) {
        ChoiceFieldDictionary field = widget.getFieldDictionary();
        List<String> labels = new ArrayList<>();
        if (field.getOptions() != null) {
            for (ChoiceFieldDictionary.ChoiceOption option : field.getOptions()) labels.add(option.getLabel());
        }
        List<Integer> selected = selectedIndexes(field);
        ChoiceFieldDictionary.ChoiceFieldType type = field.getChoiceFieldType();
        boolean combo = type == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_COMBO
                || type == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_EDITABLE_COMBO;
        FieldEditor[] self = new FieldEditor[1];
        if (combo) {
            ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(labels));
            boolean editable = type == ChoiceFieldDictionary.ChoiceFieldType.CHOICE_EDITABLE_COMBO;
            box.setEditable(editable);
            if (!selected.isEmpty() && selected.get(0) < labels.size()) {
                box.getSelectionModel().select(selected.get(0));
            } else if (editable) {
                Object v = field.getFieldValue();
                box.setValue(v instanceof String s ? s : v != null ? v.toString() : "");
            }
            box.setVisibleRowCount(Math.min(10, Math.max(1, labels.size())));
            // the field is only a line high: modena's cell padding would clip the text away.
            box.setButtonCell(compactCell());
            Supplier<Object> value = () -> {
                String text = editable ? box.getEditor().getText() : box.getValue();
                int index = text != null ? labels.indexOf(text) : -1;
                if (index >= 0) return List.of(index);
                return editable ? (text != null ? text : "") : List.of();
            };
            double fontSize = field.getSize() > 0 ? field.getSize() * zoom : Math.max(6, viewBounds.getHeight() * 0.6);
            FieldEditor editor = new FieldEditor(box, value, false, viewBounds, fontSize, listener);
            self[0] = editor;
            // picking an entry from the drop-down commits; typing commits on Enter, Tab or leaving.
            box.getSelectionModel().selectedIndexProperty().addListener((obs, was, now) -> {
                if (box.isShowing() || !editable) self[0].commit(0);
            });
            if (openPopup) {
                box.sceneProperty().addListener((obs, was, now) -> {
                    if (now != null) javafx.application.Platform.runLater(() -> {
                        if (!editor.finished) box.show();
                    });
                });
            }
            return editor;
        }
        ListView<String> list = new ListView<>(FXCollections.observableArrayList(labels));
        boolean multi = field.isMultiSelect();
        list.getSelectionModel().setSelectionMode(multi ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
        for (int i : selected) if (i >= 0 && i < labels.size()) list.getSelectionModel().select(i);
        if (!selected.isEmpty()) list.scrollTo(selected.get(0));
        double fontSize = field.getSize() > 0 ? field.getSize() * zoom : 12 * zoom;
        list.setFixedCellSize(Math.ceil(fontSize * 1.3));
        list.setCellFactory(v -> compactCell());
        Supplier<Object> value = () -> {
            List<Integer> indexes = new ArrayList<>(list.getSelectionModel().getSelectedIndices());
            indexes.removeIf(i -> i < 0);
            indexes.sort(null);
            return indexes;
        };
        FieldEditor editor = new FieldEditor(list, value, false, viewBounds, fontSize, listener);
        self[0] = editor;
        if (!multi || field.isCommitOnSetChange()) {
            list.getSelectionModel().getSelectedIndices().addListener(
                    (javafx.collections.ListChangeListener<Integer>) c -> self[0].commit(0));
        }
        return editor;
    }

    /**
     * The selected option indexes: /I when present, else the options whose export value (or label)
     * matches /V; many writers set only /V.
     */
    static List<Integer> selectedIndexes(ChoiceFieldDictionary field) {
        if (field.getIndexes() != null && !field.getIndexes().isEmpty()) return field.getIndexes();
        List<ChoiceFieldDictionary.ChoiceOption> options = field.getOptions();
        Object v = field.getFieldValue();
        if (options == null || v == null) return List.of();
        List<String> values = new ArrayList<>();
        if (v instanceof List<?> many) {
            for (Object o : many) values.add(text(o, field));
        } else {
            values.add(text(v, field));
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            ChoiceFieldDictionary.ChoiceOption option = options.get(i);
            if (values.contains(option.getValue()) || values.contains(option.getLabel())) out.add(i);
        }
        return out;
    }

    private static String text(Object o, ChoiceFieldDictionary field) {
        return o instanceof org.icepdf.core.pobjects.StringObject str
                ? str.getDecryptedLiteralString(field.getLibrary().getSecurityManager()) : String.valueOf(o);
    }

    /** A list cell with no vertical padding, so a field-sized row shows its text. */
    private static ListCell<String> compactCell() {
        ListCell<String> cell = new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
        cell.setStyle("-fx-padding: 0 2 0 2;");
        return cell;
    }

    private static Pos alignment(VariableTextFieldDictionary.Quadding quadding) {
        return quadding == VariableTextFieldDictionary.Quadding.CENTERED ? Pos.CENTER
                : quadding == VariableTextFieldDictionary.Quadding.RIGHT_JUSTIFIED ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT;
    }

    private void commit(int advance) {
        Object v = value.get();
        finish(() -> listener.commit(v, advance));
    }

    /** Runs the end action once: a commit and a focus loss can both try. */
    private void finish(Runnable action) {
        if (finished) return;
        finished = true;
        if (control instanceof ComboBox<?> box && box.isShowing()) box.hide();
        javafx.application.Platform.runLater(action);
    }

    Control control() {
        return control;
    }

    /** Commits whatever is entered (focus moving elsewhere); no-op once finished. */
    void commitNow() {
        commit(0);
    }

    boolean isFinished() {
        return finished;
    }
}
