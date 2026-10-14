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

import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;

/** The zoom: fit width, fit page or a percentage, typed or picked; follows the view. */
public final class ZoomField extends ComboBox<String> {

    private static final String FIT_WIDTH = Messages.get("zoomField.fitWidth"), FIT_PAGE = Messages.get("zoomField.fitPage");

    private final javafx.beans.value.ChangeListener<Object> listener = (o, a, b) -> showValue();
    private PdfView view;

    public ZoomField(PdfView view) {
        this(new javafx.beans.property.SimpleObjectProperty<>(view));
    }

    /** Follows whichever view {@code source} holds, now and as it changes. */
    public ZoomField(javafx.beans.value.ObservableValue<PdfView> source) {
        getStyleClass().add("zoom-field");
        Accessibility.name(this, Messages.get("a11y.zoom"));
        setEditable(true);
        setPrefWidth(104);
        getItems().addAll(FIT_WIDTH, FIT_PAGE, "50%", "75%", "100%", "125%", "150%", "200%", "300%", "400%", "800%");
        setOnAction(e -> {
            if (view == null) return;
            String value = getValue() != null ? getValue().trim() : "";
            if (FIT_WIDTH.equals(value)) view.setFitMode(FitMode.WIDTH);
            else if (FIT_PAGE.equals(value)) view.setFitMode(FitMode.PAGE);
            else {
                try {
                    double percent = Double.parseDouble(value.replace("%", "").trim());
                    view.setFitMode(FitMode.NONE);
                    view.setZoom(Math.max(PdfView.MIN_ZOOM, Math.min(PdfView.MAX_ZOOM, percent / 100)));
                } catch (NumberFormatException ignored) {
                    // not a zoom
                }
            }
            showValue();
            PdfView target = view;
            Platform.runLater(target::requestFocus);
        });
        source.addListener((o, was, now) -> follow(now));
        follow(source.getValue());
    }

    private void follow(PdfView next) {
        if (view != null) {
            view.zoomProperty().removeListener(listener);
            view.fitModeProperty().removeListener(listener);
        }
        view = next;
        disableProperty().unbind();
        if (view != null) {
            view.zoomProperty().addListener(listener);
            view.fitModeProperty().addListener(listener);
            disableProperty().bind(view.documentProperty().isNull());
        } else {
            setDisable(true);
        }
        showValue();
    }

    private void showValue() {
        if (view == null) {
            getEditor().setText("");
            return;
        }
        getEditor().setText(view.getFitMode() == FitMode.WIDTH ? FIT_WIDTH
                : view.getFitMode() == FitMode.PAGE ? FIT_PAGE : Math.round(view.getZoom() * 100) + "%");
    }
}
