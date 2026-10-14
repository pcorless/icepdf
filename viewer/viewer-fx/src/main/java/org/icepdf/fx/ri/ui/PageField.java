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

import org.icepdf.fx.i18n.Messages;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.icepdf.core.pobjects.Document;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.icepdf.fx.view.PdfView;

/** "12 of 340": the current page, typed to jump; follows the view. */
public final class PageField extends HBox {

    private final ObservableValue<PdfView> source;
    private final TextField field = new TextField();
    private final Label count = new Label();
    private final ChangeListener<Number> pageListener = (o, a, b) -> show();
    private final ChangeListener<Document> documentListener = (o, a, b) -> show();
    private PdfView view;

    public PageField(PdfView view) {
        this(new SimpleObjectProperty<>(view));
    }

    /** Follows whichever view {@code source} holds, now and as it changes. */
    public PageField(ObservableValue<PdfView> source) {
        super(4);
        this.source = source;
        getStyleClass().add("page-field");
        setAlignment(Pos.CENTER);
        field.setPrefColumnCount(3);
        field.setAlignment(Pos.CENTER_RIGHT);
        count.getStyleClass().add("dim-label");
        field.setOnAction(e -> {
            try {
                view.setCurrentPageIndex(Integer.parseInt(field.getText().trim()) - 1);
            } catch (NumberFormatException ignored) {
                // put back below
            }
            show();
            view.requestFocus();
        });
        getChildren().addAll(field, count);
        source.addListener((o, was, now) -> follow(now));
        follow(source.getValue());
    }

    private void follow(PdfView next) {
        if (view != null) {
            view.currentPageIndexProperty().removeListener(pageListener);
            view.documentProperty().removeListener(documentListener);
        }
        view = next;
        disableProperty().unbind();
        count.textProperty().unbind();
        if (view != null) {
            view.currentPageIndexProperty().addListener(pageListener);
            view.documentProperty().addListener(documentListener);
            count.textProperty().bind(Bindings.createStringBinding(() -> Messages.format("pageField.of", view.getPageCount()), view.pageCountProperty()));
            disableProperty().bind(view.documentProperty().isNull());
        } else {
            count.setText("");
            setDisable(true);
        }
        show();
    }

    private void show() {
        field.setText(view != null && view.getDocument() != null ? String.valueOf(view.getCurrentPageIndex() + 1) : "");
    }
}
