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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.icepdf.core.pobjects.Document;
import org.icepdf.fx.view.PdfView;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The files embedded in a {@link PdfView}'s document - name, then size, date and description - with Save
 * and Open.  Saving asks where; opening is the application's call ({@link #onOpenProperty()}) - a PDF
 * attachment might open in the viewer itself, anything else with the desktop's own application.
 */
public class AttachmentPanel extends BorderPane {

    private final PdfView view;
    private final ListView<Attachment> list = new ListView<>();
    private final Label empty = new Label("This document has no attachments.");
    private final ReadOnlyBooleanWrapper hasAttachments = new ReadOnlyBooleanWrapper(this, "hasAttachments", false);
    private final ObjectProperty<Consumer<Attachment>> onOpen = new SimpleObjectProperty<>(this, "onOpen");

    public AttachmentPanel(PdfView view) {
        this.view = view;
        getStyleClass().add("attachment-panel");
        DateTimeFormatter dates = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
        list.setCellFactory(l -> new ListCell<>() {
            private final Label name = new Label();
            private final Label details = new Label();
            private final VBox box = new VBox(2, name, details);

            {
                name.setStyle("-fx-font-weight: bold;");
                details.setStyle("-fx-opacity: 0.75; -fx-font-size: 0.9em;");
                setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && !isEmpty()) open(getItem());
                });
            }

            @Override
            protected void updateItem(Attachment attachment, boolean empty) {
                super.updateItem(attachment, empty);
                if (empty || attachment == null) {
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                name.setText(attachment.name());
                List<String> parts = new ArrayList<>();
                String size = formatSize(attachment.size());
                if (!size.isEmpty()) parts.add(size);
                if (attachment.modified() != null) parts.add(dates.format(attachment.modified()));
                String description = attachment.description();
                if (description != null && !description.isBlank()) parts.add(description.trim());
                details.setText(String.join(" \u00b7 ", parts));
                details.setVisible(!parts.isEmpty());
                details.setManaged(!parts.isEmpty());
                setTooltip(description != null && !description.isBlank()
                        ? new Tooltip(attachment.name() + "\n" + description.trim()) : null);
                setGraphic(box);
            }
        });
        list.setPlaceholder(new Label(""));
        list.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) open(list.getSelectionModel().getSelectedItem());
        });

        Button save = new Button("Save…");
        save.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        save.setOnAction(e -> save(list.getSelectionModel().getSelectedItem()));
        Button open = new Button("Open");
        open.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull().or(onOpen.isNull()));
        open.setOnAction(e -> open(list.getSelectionModel().getSelectedItem()));
        HBox buttons = new HBox(6, open, save);
        buttons.setPadding(new Insets(4));
        setBottom(buttons);
        empty.setWrapText(true);
        empty.setPadding(new Insets(8));

        view.documentProperty().addListener((obs, was, now) -> onDocument(now));
        onDocument(view.getDocument());
    }

    /** True when the current document has embedded files. */
    public final ReadOnlyBooleanProperty hasAttachmentsProperty() {
        return hasAttachments.getReadOnlyProperty();
    }

    public final boolean hasAttachments() {
        return hasAttachments.get();
    }

    /** Opens an attachment (double-click or Open); Open is disabled while it is null. */
    public final ObjectProperty<Consumer<Attachment>> onOpenProperty() {
        return onOpen;
    }

    public final void setOnOpen(Consumer<Attachment> handler) {
        onOpen.set(handler);
    }

    private void onDocument(Document document) {
        var attachments = Attachment.of(document);
        list.setItems(FXCollections.observableArrayList(attachments));
        hasAttachments.set(!attachments.isEmpty());
        setCenter(attachments.isEmpty() ? empty : list);
    }

    private void open(Attachment attachment) {
        if (attachment != null && onOpen.get() != null) onOpen.get().accept(attachment);
    }

    private void save(Attachment attachment) {
        if (attachment == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save attachment");
        chooser.setInitialFileName(attachment.name());
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            attachment.saveTo(file.toPath());
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Could not save " + attachment.name() + ":\n" + e.getMessage())
                    .showAndWait();
        }
    }

    /** "12 KB" style sizes; empty when unknown. */
    static String formatSize(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
