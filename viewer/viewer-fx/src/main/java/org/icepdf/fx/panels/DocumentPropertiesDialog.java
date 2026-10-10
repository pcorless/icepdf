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

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.icepdf.core.pobjects.Document;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A document's properties, as Acrobat's and the Swing viewer's dialogs show them: Description
 * (information dictionary, file, version, pages), Security (encryption and permissions), Fonts
 * (found page by page in the background) and Custom (the information dictionary's other entries).
 */
public class DocumentPropertiesDialog extends Dialog<Void> {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public DocumentPropertiesDialog(Window owner, Document document) {
        initOwner(owner);
        setTitle("Document Properties");
        setResizable(true);
        TabPane tabs = new TabPane(
                tab("Description", rows(DocumentProperties.description(document))),
                tab("Security", rows(DocumentProperties.security(document))),
                tab("Fonts", fonts(document)),
                tab("Custom", custom(DocumentProperties.custom(document))));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefSize(560, 460);
        getDialogPane().setContent(tabs);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        setOnHidden(e -> cancelled.set(true));
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    /** Label / value rows; values are read-only text fields so they can be selected and copied. */
    private static javafx.scene.Node rows(List<DocumentProperties.Row> rows) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        grid.setPadding(new Insets(12));
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(150);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labels, values);
        int r = 0;
        for (DocumentProperties.Row row : rows) {
            Label label = new Label(row.label() + ":");
            TextField value = new TextField(row.value());
            value.setEditable(false);
            value.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
            grid.addRow(r++, label, value);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private javafx.scene.Node fonts(Document document) {
        TableView<DocumentFonts.FontInfo> table = new TableView<>();
        ObservableList<DocumentFonts.FontInfo> items = FXCollections.observableArrayList();
        table.setItems(items);
        TableColumn<DocumentFonts.FontInfo, String> name = new TableColumn<>("Font");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        name.setPrefWidth(200);
        TableColumn<DocumentFonts.FontInfo, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().type()));
        TableColumn<DocumentFonts.FontInfo, String> encoding = new TableColumn<>("Encoding");
        encoding.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().encoding()));
        TableColumn<DocumentFonts.FontInfo, String> embedded = new TableColumn<>("Embedded");
        embedded.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().embedded()
                ? (c.getValue().subset() ? "Subset" : "Yes") : "No"));
        TableColumn<DocumentFonts.FontInfo, String> actual = new TableColumn<>("Shown with");
        actual.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().substitute()));
        table.getColumns().setAll(List.of(name, type, encoding, embedded, actual));
        table.setPlaceholder(new Label("Looking for fonts…"));

        ProgressBar progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        BorderPane pane = new BorderPane(table);
        pane.setBottom(progress);
        BorderPane.setMargin(progress, new Insets(4));
        int pages = document.getNumberOfPages();
        Thread scan = new Thread(() -> {
            try {
                DocumentFonts.scan(document, cancelled::get,
                        done -> Platform.runLater(() -> progress.setProgress(pages == 0 ? 1 : done / (double) pages)),
                        font -> Platform.runLater(() -> items.add(font)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                // show what was found
            }
            Platform.runLater(() -> {
                pane.setBottom(null);
                table.setPlaceholder(new Label("No fonts found."));
            });
        }, "icepdf-fx-fonts");
        scan.setDaemon(true);
        scan.start();
        return pane;
    }

    private static javafx.scene.Node custom(Map<String, String> custom) {
        TableView<Map.Entry<String, String>> table = new TableView<>(FXCollections.observableArrayList(custom.entrySet()));
        TableColumn<Map.Entry<String, String>, String> key = new TableColumn<>("Name");
        key.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        key.setPrefWidth(180);
        TableColumn<Map.Entry<String, String>, String> value = new TableColumn<>("Value");
        value.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
        value.setPrefWidth(320);
        table.getColumns().setAll(List.of(key, value));
        table.setPlaceholder(new Label("No custom properties."));
        return table;
    }
}
