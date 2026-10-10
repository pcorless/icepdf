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
package org.icepdf.fx.panels;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import org.icepdf.fx.signature.SignatureIcons;
import org.icepdf.fx.signature.SignatureStatus;
import org.icepdf.fx.view.PdfView;

import java.text.DateFormat;

/**
 * A {@link PdfView}'s signature fields, signed and empty, with each signature's verdict: selecting
 * one shows it on its page, double-clicking a signed one shows its properties.
 */
public class SignaturePanel extends BorderPane {

    private final ListView<SignatureStatus> list = new ListView<>();
    private final BooleanBinding hasSignatures;

    public SignaturePanel(PdfView view) {
        getStyleClass().add("signature-panel");
        list.setItems(view.getSignatures());
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(SignatureStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                String who = item.isSigned() ? (item.signerName() != null ? item.signerName() : "Unknown signer")
                        : "Empty field " + item.fieldName();
                String when = item.signingTime() != null ? DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT).format(item.signingTime()) : "";
                setText(who + (when.isEmpty() ? "" : "\n" + when)
                        + (item.pageIndex() >= 0 ? "  (page " + (item.pageIndex() + 1) + ")" : ""));
                setGraphic(item.isSigned() ? SignatureIcons.icon(item.verdict(), 16) : null);
                setTooltip(new Tooltip(item.summary()));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> {
            if (now != null) view.revealSignature(now);
        });
        list.setOnMouseClicked(e -> {
            SignatureStatus selected = list.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && selected != null && selected.isSigned()) view.showSignatureProperties(selected);
        });
        Label empty = new Label("This document has no signature fields.");
        empty.setWrapText(true);
        empty.setPadding(new Insets(8));
        list.setPlaceholder(empty);
        setCenter(list);
        hasSignatures = Bindings.isNotEmpty(view.getSignatures());
    }

    /** True when the document has signature fields. */
    public final BooleanBinding hasSignaturesBinding() {
        return hasSignatures;
    }
}
