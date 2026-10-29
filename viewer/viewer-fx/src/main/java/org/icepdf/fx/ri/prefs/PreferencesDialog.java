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
package org.icepdf.fx.ri.prefs;

import org.icepdf.fx.i18n.Messages;

import org.icepdf.fx.fonts.FontSettings;

import org.icepdf.core.pobjects.graphics.images.references.ImageDownsampler;
import org.icepdf.core.pobjects.graphics.images.references.ImageReferenceFactory;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.icepdf.fx.ri.Theme;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ViewMode;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * The viewer's settings: General (page display on open, signatures), Annotations (author, colour),
 * Search (defaults for the find bar and search panel), Imaging (how core draws images: smooth
 * downscaling, redrawn at once, and the image reference type, for documents opened after),
 * Performance (thread counts, on restart) and Fonts - the system fonts core substitutes
 * with, the extra directories to look in, and a rescan.  OK writes the settings and applies them to
 * the window's view; the font tab's actions take effect at once.
 */
public class PreferencesDialog extends Dialog<ButtonType> {

    private final ViewerPreferences preferences;

    public PreferencesDialog(Window owner, ViewerPreferences preferences, PdfView view) {
        this.preferences = preferences;
        initOwner(owner);
        setTitle(Messages.get("preferences.title"));
        setResizable(true);

        // General
        ComboBox<ViewMode> viewMode = new ComboBox<>(FXCollections.observableArrayList(ViewMode.values()));
        viewMode.setConverter(labels(mode -> Messages.get("view.mode." + mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))));
        viewMode.setValue(preferences.getEnum(ViewerPreferences.VIEW_MODE, ViewMode.class, ViewMode.CONTINUOUS));
        ComboBox<FitMode> fitMode = new ComboBox<>(FXCollections.observableArrayList(FitMode.values()));
        fitMode.setConverter(labels(mode -> Messages.get(switch (mode) {
            case WIDTH -> "view.fit-width";
            case PAGE -> "view.fit-page";
            default -> "preferences.fitNone";
        })));
        fitMode.setValue(preferences.getEnum(ViewerPreferences.FIT_MODE, FitMode.class, FitMode.WIDTH));
        Spinner<Integer> zoom = new Spinner<>(5, 6400, (int) Math.round(preferences.getDouble(ViewerPreferences.ZOOM, 1) * 100), 25);
        zoom.setEditable(true);
        zoom.disableProperty().bind(fitMode.valueProperty().isNotEqualTo(FitMode.NONE));
        CheckBox sidePanel = new CheckBox(Messages.get("preferences.sidePanel"));
        sidePanel.setSelected(preferences.getBoolean(ViewerPreferences.SIDE_PANEL_VISIBLE, true));
        CheckBox verify = new CheckBox(Messages.get("preferences.verify"));
        verify.setSelected(preferences.getBoolean(ViewerPreferences.VERIFY_SIGNATURES, true));
        CheckBox rememberFolder = new CheckBox(Messages.get("preferences.rememberFolder"));
        rememberFolder.setSelected(preferences.getBoolean(ViewerPreferences.REMEMBER_LAST_DIRECTORY, true));
        ComboBox<Theme.Mode> theme = new ComboBox<>(FXCollections.observableArrayList(Theme.Mode.values()));
        theme.setConverter(labels(mode -> Messages.get("preferences.theme." + mode.name().toLowerCase(Locale.ROOT).replace('_', '-'))));
        theme.setValue(preferences.getEnum(ViewerPreferences.THEME, Theme.Mode.class, Theme.Mode.SYSTEM));
        GridPane general = grid();
        general.addRow(0, label("preferences.theme", theme), theme);
        general.addRow(1, label("preferences.pageDisplay", viewMode), viewMode);
        general.addRow(2, label("preferences.zoom", fitMode), fitMode);
        general.addRow(3, label("preferences.zoomPercent", zoom), zoom);
        general.add(sidePanel, 0, 4, 2, 1);
        general.add(verify, 0, 5, 2, 1);
        general.add(rememberFolder, 0, 6, 2, 1);

        // Annotations
        TextField author = new TextField(preferences.get(ViewerPreferences.ANNOTATION_AUTHOR, System.getProperty("user.name")));
        // private annotations are matched to user.name, so the author can't be chosen then (see ViewerApp).
        boolean authorFixed = org.icepdf.core.util.SystemProperties.PRIVATE_PROPERTY_ENABLED;
        if (authorFixed) {
            author.setText(org.icepdf.core.util.SystemProperties.USER_NAME);
            author.setDisable(true);
            author.setTooltip(new javafx.scene.control.Tooltip(Messages.get("preferences.authorFixed")));
        }
        CheckBox showAnnotations = new CheckBox(Messages.get("preferences.showAnnotations"));
        showAnnotations.setSelected(preferences.getBoolean(ViewerPreferences.PAINT_ANNOTATIONS, true));
        CheckBox highlightFields = new CheckBox(Messages.get("preferences.highlightFields"));
        highlightFields.setSelected(preferences.getBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, false));
        GridPane annotations = grid();
        annotations.addRow(0, label("preferences.author", author), author);
        annotations.add(showAnnotations, 0, 1, 2, 1);
        annotations.add(highlightFields, 0, 2, 2, 1);
        CheckBox newPrivate = new CheckBox(Messages.get("preferences.newAnnotationsPrivate"));
        newPrivate.setSelected(preferences.getBoolean(ViewerPreferences.ANNOTATION_NEW_PRIVATE, false));
        if (org.icepdf.core.util.SystemProperties.PRIVATE_PROPERTY_ENABLED) annotations.add(newPrivate, 0, 3, 2, 1);
        ColorLabelsEditor labels = new ColorLabelsEditor(preferences.getColorLabels());
        Label labelsTitle = new Label(Messages.get("preferences.labels"));
        labelsTitle.setStyle("-fx-font-weight: bold;");
        annotations.add(labelsTitle, 0, 4, 2, 1);
        annotations.add(labels, 0, 5, 2, 1);
        GridPane.setHgrow(labels, javafx.scene.layout.Priority.ALWAYS);

        // Search
        CheckBox matchCase = check(Messages.get("preferences.matchCase"), ViewerPreferences.SEARCH_CASE, false);
        CheckBox wholeWord = check(Messages.get("preferences.wholeWords"), ViewerPreferences.SEARCH_WHOLE_WORD, false);
        CheckBox accents = check(Messages.get("preferences.ignoreAccents"), ViewerPreferences.SEARCH_FOLD_ACCENTS, true);
        CheckBox regex = check(Messages.get("preferences.regex"), ViewerPreferences.SEARCH_REGEX, false);
        CheckBox cumulative = check(Messages.get("preferences.cumulative"), ViewerPreferences.SEARCH_CUMULATIVE, false);
        CheckBox comments = check(Messages.get("preferences.comments"), ViewerPreferences.SEARCH_COMMENTS, true);
        CheckBox forms = check(Messages.get("preferences.formFields"), ViewerPreferences.SEARCH_FORMS, true);
        CheckBox outlines = check(Messages.get("preferences.bookmarks"), ViewerPreferences.SEARCH_OUTLINES, false);
        CheckBox destinations = check(Messages.get("preferences.destinations"), ViewerPreferences.SEARCH_DESTINATIONS, false);
        VBox search = new VBox(8, new Label(Messages.get("preferences.searchDefaults")), matchCase, wholeWord, accents, regex,
                cumulative, new Separator(), new Label(Messages.get("preferences.alsoSearch")), comments, forms, outlines, destinations);
        search.setPadding(new Insets(12));

        // Imaging: core's image reference type, and drawing shrunk images from a filtered mip level
        ComboBox<String> imageReference = new ComboBox<>(FXCollections.observableArrayList(
                ImageReferenceFactory.getRegisteredTypeKeys()));
        imageReference.setConverter(labels(PreferencesDialog::imageReferenceLabel));
        imageReference.setValue(ImageReferenceFactory.getImageReferenceType());
        CheckBox downsample = new CheckBox(Messages.get("preferences.imaging.downsample"));
        downsample.setSelected(ImageDownsampler.isEnabled());
        Label downsampleHelp = help("preferences.imaging.downsampleHelp");
        Label referenceHelp = help("preferences.imaging.imageReferenceHelp");
        GridPane imaging = grid();
        imaging.add(downsample, 0, 0, 2, 1);
        imaging.add(downsampleHelp, 0, 1, 2, 1);
        imaging.add(new Separator(), 0, 2, 2, 1);
        imaging.addRow(3, label("preferences.imaging.imageReference", imageReference), imageReference);
        imaging.add(referenceHelp, 0, 4, 2, 1);

        // Performance: thread counts, automatic or set; the pools are sized at start.
        GridPane performance = grid();
        java.util.Map<PerformanceSettings.Pool, ThreadCount> threadCounts = new java.util.EnumMap<>(PerformanceSettings.Pool.class);
        int row = 0;
        for (PerformanceSettings.Pool pool : PerformanceSettings.Pool.values()) {
            ThreadCount count = new ThreadCount(pool);
            threadCounts.put(pool, count);
            Label name = label("preferences.performance." + pool.name().toLowerCase(Locale.ROOT), count.spinner);
            // translations run long: labels keep their full width, and what the pool runs with now
            // gets its own line under the controls.
            name.setMinWidth(Region.USE_PREF_SIZE);
            count.automatic.setMinWidth(Region.USE_PREF_SIZE);
            performance.addRow(row++, name, count.automatic, count.spinner);
            performance.add(count.inEffect, 1, row++, 2, 1);
        }
        Label performanceHelp = new Label(Messages.format("preferences.performance.help",
                Runtime.getRuntime().availableProcessors()));
        performanceHelp.setWrapText(true);
        performanceHelp.setMaxWidth(560);
        performance.add(performanceHelp, 0, row, 3, 1);

        TabPane tabs = new TabPane(new Tab(Messages.get("preferences.tab.general"), general), new Tab(Messages.get("preferences.tab.annotations"), annotations),
                new Tab(Messages.get("preferences.tab.search"), search), new Tab(Messages.get("preferences.tab.imaging"), imaging),
                new Tab(Messages.get("preferences.tab.performance"), performance),
                new Tab(Messages.get("preferences.tab.fonts"), fontsTab()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setPrefSize(620, 480);
        getDialogPane().setContent(tabs);
        getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(button -> {
            if (button == ButtonType.OK) {
                preferences.putEnum(ViewerPreferences.THEME, theme.getValue());
                preferences.putEnum(ViewerPreferences.VIEW_MODE, viewMode.getValue());
                preferences.putEnum(ViewerPreferences.FIT_MODE, fitMode.getValue());
                preferences.putDouble(ViewerPreferences.ZOOM, zoom.getValue() / 100.0);
                preferences.putBoolean(ViewerPreferences.SIDE_PANEL_VISIBLE, sidePanel.isSelected());
                preferences.putBoolean(ViewerPreferences.VERIFY_SIGNATURES, verify.isSelected());
                preferences.putBoolean(ViewerPreferences.REMEMBER_LAST_DIRECTORY, rememberFolder.isSelected());
                if (!authorFixed) preferences.put(ViewerPreferences.ANNOTATION_AUTHOR, author.getText().trim());
                preferences.putBoolean(ViewerPreferences.PAINT_ANNOTATIONS, showAnnotations.isSelected());
                preferences.putBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, highlightFields.isSelected());
                preferences.setColorLabels(labels.labels());
                if (org.icepdf.core.util.SystemProperties.PRIVATE_PROPERTY_ENABLED) {
                    preferences.putBoolean(ViewerPreferences.ANNOTATION_NEW_PRIVATE, newPrivate.isSelected());
                    view.setNewAnnotationsPrivate(newPrivate.isSelected());
                }
                for (CheckBox box : List.of(matchCase, wholeWord, accents, regex, cumulative, comments, forms, outlines, destinations)) {
                    preferences.putBoolean((String) box.getUserData(), box.isSelected());
                }
                preferences.put(ViewerPreferences.IMAGE_REFERENCE, imageReference.getValue());
                preferences.putBoolean(ViewerPreferences.IMAGE_DOWNSAMPLE, downsample.isSelected());
                threadCounts.forEach((pool, count) -> PerformanceSettings.save(preferences, pool, count.value()));
                preferences.save();
                boolean redraw = downsample.isSelected() != ImageDownsampler.isEnabled();
                ImagingSettings.apply(preferences);
                if (redraw) view.refreshContent();
                // what applies to the open view now; page display, zoom and the image reference type
                // apply to the next document.
                view.setVerifySignaturesOnOpen(verify.isSelected());
                if (!authorFixed) view.setAnnotationAuthor(author.getText().trim());
                view.setPaintAnnotations(showAnnotations.isSelected());
                view.setHighlightFormFields(highlightFields.isSelected());
            }
            return button;
        });
    }

    /** One pool's thread count: automatic, or a number; and what the pool runs with now. */
    private final class ThreadCount {
        final CheckBox automatic = new CheckBox(Messages.get("preferences.performance.automatic"));
        final Spinner<Integer> spinner;
        final Label inEffect = new Label();

        ThreadCount(PerformanceSettings.Pool pool) {
            Integer saved = PerformanceSettings.saved(preferences, pool);
            org.icepdf.core.util.PoolSizing.Size now = PerformanceSettings.inEffect(pool);
            spinner = new Spinner<>(1, PerformanceSettings.MAX_THREADS, saved != null ? saved : now.threads());
            spinner.setEditable(true);
            spinner.setPrefWidth(90);
            automatic.setSelected(saved == null);
            inEffect.getStyleClass().add("pool-in-effect");
            spinner.disableProperty().bind(automatic.selectedProperty());
            inEffect.setText(switch (now.source()) {
                case AUTO -> Messages.format("preferences.performance.now.auto", now.threads());
                case CAP -> Messages.format("preferences.performance.now.cap", now.threads(), now.cap());
                case EXPLICIT -> Messages.format("preferences.performance.now.set", now.threads());
            });
        }

        /** The count to save; null for automatic. */
        Integer value() {
            return automatic.isSelected() ? null : spinner.getValue();
        }
    }

    /** A field's label, which a screen reader announces with the field and Alt+mnemonic focuses. */
    private static Label label(String key, Node field) {
        Label label = new Label(Messages.get(key));
        label.setLabelFor(field);
        return label;
    }

    /** A wrapped line of explanation under a setting. */
    private static Label help(String key) {
        Label help = new Label(Messages.get(key));
        help.setWrapText(true);
        help.setMaxWidth(560);
        return help;
    }

    /** An image reference type's name: translated for the built-in types, as registered otherwise. */
    private static String imageReferenceLabel(String key) {
        ImageReferenceFactory.RegistryEntry entry = ImageReferenceFactory.getRegisteredTypes().get(key);
        return Messages.get("preferences.imaging.type." + key, entry != null ? entry.getDisplayName() : key);
    }

    private CheckBox check(String text, String key, boolean defaultValue) {
        CheckBox box = new CheckBox(text);
        box.setSelected(preferences.getBoolean(key, defaultValue));
        box.setUserData(key);
        return box;
    }

    /** Shows a choice by its label rather than its constant name. */
    private static <T> javafx.util.StringConverter<T> labels(java.util.function.Function<T, String> label) {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        return grid;
    }

    /** The fonts core substitutes with: a filterable list, the extra directories, rescan and clear. */
    private Node fontsTab() {
        ObservableList<FontSettings.FontEntry> fonts = FXCollections.observableArrayList(FontSettings.fonts());
        FilteredList<FontSettings.FontEntry> filtered = new FilteredList<>(fonts);
        TextField filter = new TextField();
        filter.setPromptText(Messages.get("preferences.filterFonts"));
        filter.textProperty().addListener((o, a, text) -> {
            String needle = text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(needle.isEmpty() ? null : f -> f.name().toLowerCase(Locale.ROOT).contains(needle)
                    || f.family().toLowerCase(Locale.ROOT).contains(needle)
                    || f.path().toLowerCase(Locale.ROOT).contains(needle));
        });
        TableView<FontSettings.FontEntry> table = new TableView<>(filtered);
        TableColumn<FontSettings.FontEntry, String> name = new TableColumn<>(Messages.get("preferences.font"));
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        name.setPrefWidth(200);
        TableColumn<FontSettings.FontEntry, String> family = new TableColumn<>(Messages.get("preferences.family"));
        family.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().family()));
        family.setPrefWidth(140);
        TableColumn<FontSettings.FontEntry, String> path = new TableColumn<>(Messages.get("preferences.file"));
        path.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path()));
        path.setPrefWidth(240);
        table.getColumns().setAll(List.of(name, family, path));
        table.setPlaceholder(new Label(Messages.get("preferences.noFonts")));
        Label count = new Label();
        Runnable updateCount = () -> count.setText(Messages.format("preferences.fontCount", fonts.size())
                + (FontSettings.isScanning() ? " " + Messages.get("preferences.scanning") : ""));
        updateCount.run();

        ListView<String> directories = new ListView<>(FXCollections.observableArrayList(FontDirectories.of(preferences)));
        directories.setPrefHeight(80);
        Button add = new Button(Messages.get("preferences.addFolder"));
        add.setOnAction(e -> {
            File chosen = new DirectoryChooser().showDialog(getOwner());
            if (chosen != null && !directories.getItems().contains(chosen.getPath())) {
                directories.getItems().add(chosen.getPath());
                FontDirectories.set(preferences, directories.getItems());
            }
        });
        Button remove = new Button(Messages.get("preferences.remove"));
        remove.disableProperty().bind(directories.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            directories.getItems().remove(directories.getSelectionModel().getSelectedItem());
            FontDirectories.set(preferences, directories.getItems());
        });
        Button rescan = new Button(Messages.get("preferences.rescan"));
        rescan.setTooltip(new Tooltip(Messages.get("preferences.rescanTooltip")));
        rescan.setOnAction(e -> {
            preferences.save();
            rescan.setDisable(true);
            FontSettings.rescanInBackground(FontDirectories.of(preferences), () -> Platform.runLater(() -> {
                fonts.setAll(FontSettings.fonts());
                rescan.setDisable(false);
                updateCount.run();
            }));
            updateCount.run();
        });
        Button clear = new Button(Messages.get("preferences.clearCache"));
        clear.setTooltip(new Tooltip(Messages.get("preferences.clearCacheTooltip")));
        clear.setOnAction(e -> FontSettings.clearCache());

        HBox filterRow = new HBox(8, filter, count);
        HBox.setHgrow(filter, Priority.ALWAYS);
        HBox directoryButtons = new HBox(8, add, remove);
        HBox actions = new HBox(8, rescan, clear);
        VBox box = new VBox(8, filterRow, table, new Label(Messages.get("preferences.alsoLookIn")), directories, directoryButtons,
                new Separator(), actions);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setPadding(new Insets(12));
        return box;
    }
}
