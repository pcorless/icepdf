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

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.icepdf.fx.view.PdfView;

/** "12 of 340": the current page, typed to jump; follows the view. */
public final class PageField extends HBox {

    public PageField(PdfView view) {
        super(4);
        getStyleClass().add("page-field");
        setAlignment(Pos.CENTER);
        TextField field = new TextField();
        field.setPrefColumnCount(3);
        field.setAlignment(Pos.CENTER_RIGHT);
        Label count = new Label();
        count.getStyleClass().add("dim-label");
        Runnable show = () -> field.setText(view.getDocument() != null ? String.valueOf(view.getCurrentPageIndex() + 1) : "");
        view.currentPageIndexProperty().addListener((o, a, b) -> show.run());
        view.documentProperty().addListener((o, a, b) -> show.run());
        count.textProperty().bind(Bindings.createStringBinding(() -> "of " + view.getPageCount(), view.pageCountProperty()));
        field.setOnAction(e -> {
            try {
                view.setCurrentPageIndex(Integer.parseInt(field.getText().trim()) - 1);
            } catch (NumberFormatException ignored) {
                // put back below
            }
            show.run();
            view.requestFocus();
        });
        disableProperty().bind(view.documentProperty().isNull());
        show.run();
        getChildren().addAll(field, count);
    }
}
