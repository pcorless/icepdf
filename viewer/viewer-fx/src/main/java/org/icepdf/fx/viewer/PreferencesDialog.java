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
package org.icepdf.fx.viewer;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ViewMode;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * The viewer's settings: General (page display on open, signatures), Annotations (author, colour),
 * Search (defaults for the find bar and search panel) and Fonts - the system fonts core substitutes
 * with, the extra directories to look in, and a rescan.  OK writes the settings and applies them to
 * the window's view; the font tab's actions take effect at once.
 */
public class PreferencesDialog extends Dialog<ButtonType> {

    private final ViewerPreferences preferences;

    public PreferencesDialog(Window owner, ViewerPreferences preferences, PdfView view) {
        this.preferences = preferences;
        initOwner(owner);
        setTitle("Preferences");
        setResizable(true);

        // General
        ComboBox<ViewMode> viewMode = new ComboBox<>(FXCollections.observableArrayList(ViewMode.values()));
        viewMode.setValue(preferences.getEnum(ViewerPreferences.VIEW_MODE, ViewMode.class, ViewMode.CONTINUOUS));
        ComboBox<FitMode> fitMode = new ComboBox<>(FXCollections.observableArrayList(FitMode.values()));
        fitMode.setValue(preferences.getEnum(ViewerPreferences.FIT_MODE, FitMode.class, FitMode.WIDTH));
        Spinner<Integer> zoom = new Spinner<>(5, 6400, (int) Math.round(preferences.getDouble(ViewerPreferences.ZOOM, 1) * 100), 25);
        zoom.setEditable(true);
        zoom.disableProperty().bind(fitMode.valueProperty().isNotEqualTo(FitMode.NONE));
        CheckBox sidePanel = new CheckBox("Show the side panel");
        sidePanel.setSelected(preferences.getBoolean(ViewerPreferences.SIDE_PANEL_VISIBLE, true));
        CheckBox verify = new CheckBox("Check signatures when a document opens");
        verify.setSelected(preferences.getBoolean(ViewerPreferences.VERIFY_SIGNATURES, true));
        GridPane general = grid();
        general.addRow(0, new Label("Page display:"), viewMode);
        general.addRow(1, new Label("Zoom:"), fitMode);
        general.addRow(2, new Label("Zoom percent:"), zoom);
        general.add(sidePanel, 0, 3, 2, 1);
        general.add(verify, 0, 4, 2, 1);

        // Annotations
        TextField author = new TextField(preferences.get(ViewerPreferences.ANNOTATION_AUTHOR, System.getProperty("user.name")));
        ColorPicker colour = new ColorPicker(colour(preferences.get(ViewerPreferences.ANNOTATION_COLOR, "#ffff00")));
        CheckBox showAnnotations = new CheckBox("Show annotations");
        showAnnotations.setSelected(preferences.getBoolean(ViewerPreferences.PAINT_ANNOTATIONS, true));
        CheckBox highlightFields = new CheckBox("Highlight form fields");
        highlightFields.setSelected(preferences.getBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, false));
        GridPane annotations = grid();
        annotations.addRow(0, new Label("Author:"), author);
        annotations.addRow(1, new Label("Default colour:"), colour);
        annotations.add(showAnnotations, 0, 2, 2, 1);
        annotations.add(highlightFields, 0, 3, 2, 1);

        // Search
        CheckBox matchCase = check("Match case", ViewerPreferences.SEARCH_CASE, false);
        CheckBox wholeWord = check("Whole words only", ViewerPreferences.SEARCH_WHOLE_WORD, false);
        CheckBox accents = check("Ignore accents", ViewerPreferences.SEARCH_FOLD_ACCENTS, true);
        CheckBox regex = check("Regular expression", ViewerPreferences.SEARCH_REGEX, false);
        CheckBox cumulative = check("Add each search to the previous terms", ViewerPreferences.SEARCH_CUMULATIVE, false);
        CheckBox comments = check("Comments", ViewerPreferences.SEARCH_COMMENTS, false);
        CheckBox forms = check("Form fields", ViewerPreferences.SEARCH_FORMS, false);
        CheckBox outlines = check("Bookmarks", ViewerPreferences.SEARCH_OUTLINES, false);
        CheckBox destinations = check("Named destinations", ViewerPreferences.SEARCH_DESTINATIONS, false);
        VBox search = new VBox(8, new Label("Defaults for a new search:"), matchCase, wholeWord, accents, regex,
                cumulative, new Separator(), new Label("Also search:"), comments, forms, outlines, destinations);
        search.setPadding(new Insets(12));

        TabPane tabs = new TabPane(new Tab("General", general), new Tab("Annotations", annotations),
                new Tab("Search", search), new Tab("Fonts", fontsTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefSize(620, 480);
        getDialogPane().setContent(tabs);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button == ButtonType.OK) {
                preferences.putEnum(ViewerPreferences.VIEW_MODE, viewMode.getValue());
                preferences.putEnum(ViewerPreferences.FIT_MODE, fitMode.getValue());
                preferences.putDouble(ViewerPreferences.ZOOM, zoom.getValue() / 100.0);
                preferences.putBoolean(ViewerPreferences.SIDE_PANEL_VISIBLE, sidePanel.isSelected());
                preferences.putBoolean(ViewerPreferences.VERIFY_SIGNATURES, verify.isSelected());
                preferences.put(ViewerPreferences.ANNOTATION_AUTHOR, author.getText().trim());
                preferences.put(ViewerPreferences.ANNOTATION_COLOR, web(colour.getValue()));
                preferences.putBoolean(ViewerPreferences.PAINT_ANNOTATIONS, showAnnotations.isSelected());
                preferences.putBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, highlightFields.isSelected());
                for (CheckBox box : List.of(matchCase, wholeWord, accents, regex, cumulative, comments, forms, outlines, destinations)) {
                    preferences.putBoolean((String) box.getUserData(), box.isSelected());
                }
                preferences.save();
                // what applies to the open view now; page display and zoom apply to the next document.
                view.setVerifySignaturesOnOpen(verify.isSelected());
                view.setAnnotationAuthor(author.getText().trim());
                view.setAnnotationColor(colour.getValue());
                view.setPaintAnnotations(showAnnotations.isSelected());
                view.setHighlightFormFields(highlightFields.isSelected());
            }
            return button;
        });
    }

    private CheckBox check(String text, String key, boolean defaultValue) {
        CheckBox box = new CheckBox(text);
        box.setSelected(preferences.getBoolean(key, defaultValue));
        box.setUserData(key);
        return box;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        return grid;
    }

    private static Color colour(String web) {
        try {
            return Color.web(web);
        } catch (IllegalArgumentException e) {
            return Color.YELLOW;
        }
    }

    static String web(Color colour) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(colour.getRed() * 255),
                Math.round(colour.getGreen() * 255), Math.round(colour.getBlue() * 255));
    }

    /** The fonts core substitutes with: a filterable list, the extra directories, rescan and clear. */
    private Node fontsTab() {
        ObservableList<FontSettings.FontEntry> fonts = FXCollections.observableArrayList(FontSettings.fonts());
        FilteredList<FontSettings.FontEntry> filtered = new FilteredList<>(fonts);
        TextField filter = new TextField();
        filter.setPromptText("Filter fonts");
        filter.textProperty().addListener((o, a, text) -> {
            String needle = text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(needle.isEmpty() ? null : f -> f.name().toLowerCase(Locale.ROOT).contains(needle)
                    || f.family().toLowerCase(Locale.ROOT).contains(needle)
                    || f.path().toLowerCase(Locale.ROOT).contains(needle));
        });
        TableView<FontSettings.FontEntry> table = new TableView<>(filtered);
        TableColumn<FontSettings.FontEntry, String> name = new TableColumn<>("Font");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        name.setPrefWidth(200);
        TableColumn<FontSettings.FontEntry, String> family = new TableColumn<>("Family");
        family.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().family()));
        family.setPrefWidth(140);
        TableColumn<FontSettings.FontEntry, String> path = new TableColumn<>("File");
        path.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path()));
        path.setPrefWidth(240);
        table.getColumns().setAll(List.of(name, family, path));
        table.setPlaceholder(new Label("No fonts found yet."));
        Label count = new Label();
        Runnable updateCount = () -> count.setText(fonts.size() + " fonts"
                + (FontSettings.isScanning() ? " (scanning…)" : ""));
        updateCount.run();

        ListView<String> directories = new ListView<>(FXCollections.observableArrayList(FontSettings.directories(preferences)));
        directories.setPrefHeight(80);
        Button add = new Button("Add Folder…");
        add.setOnAction(e -> {
            File chosen = new DirectoryChooser().showDialog(getOwner());
            if (chosen != null && !directories.getItems().contains(chosen.getPath())) {
                directories.getItems().add(chosen.getPath());
                FontSettings.setDirectories(preferences, directories.getItems());
            }
        });
        Button remove = new Button("Remove");
        remove.disableProperty().bind(directories.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            directories.getItems().remove(directories.getSelectionModel().getSelectedItem());
            FontSettings.setDirectories(preferences, directories.getItems());
        });
        Button rescan = new Button("Rescan Fonts");
        rescan.setTooltip(new Tooltip("Read the system font folders and the folders above again"));
        rescan.setOnAction(e -> {
            preferences.save();
            rescan.setDisable(true);
            FontSettings.rescanInBackground(preferences, () -> Platform.runLater(() -> {
                fonts.setAll(FontSettings.fonts());
                rescan.setDisable(false);
                updateCount.run();
            }));
            updateCount.run();
        });
        Button clear = new Button("Clear Cache");
        clear.setTooltip(new Tooltip("Forget the saved font list; the next start scans again"));
        clear.setOnAction(e -> FontSettings.clearCache());

        HBox filterRow = new HBox(8, filter, count);
        HBox.setHgrow(filter, Priority.ALWAYS);
        HBox directoryButtons = new HBox(8, add, remove);
        HBox actions = new HBox(8, rescan, clear);
        VBox box = new VBox(8, filterRow, table, new Label("Also look for fonts in:"), directories, directoryButtons,
                new Separator(), actions);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setPadding(new Insets(12));
        return box;
    }
}
