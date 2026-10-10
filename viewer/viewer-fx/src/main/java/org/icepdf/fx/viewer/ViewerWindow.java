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

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.icepdf.core.exceptions.PDFSecurityException;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.fx.panels.*;
import org.icepdf.fx.signature.DocumentSigning;
import org.icepdf.fx.signature.SignDialog;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PasswordPrompt;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One viewer window: a {@link PdfView} with a menu bar, a tool bar, a side panel (thumbnails,
 * bookmarks, attachments, layers, signatures) and a status bar, and the file handling an
 * application owns - open (with a password prompt), open recent, save (to the same file), save as,
 * print, properties, close.  The window tracks whether the document has unsaved changes, shows it
 * in the title, and asks before throwing them away.
 */
public class ViewerWindow {

    private static final Logger logger = Logger.getLogger(ViewerWindow.class.getName());
    private static final String APP_NAME = "ICEpdf Viewer";

    private final PdfViewerApp app;
    private final Stage stage;
    private final ViewerPreferences preferences;
    private final PdfView view = new PdfView();
    private final BorderPane root = new BorderPane();
    private final SplitPane split = new SplitPane();
    private final TabPane sideTabs = new TabPane();
    private final Label status = new Label();
    private final ReadOnlyBooleanWrapper modified = new ReadOnlyBooleanWrapper(this, "modified", false);
    private final Menu recentMenu = new Menu("Open Recent");

    private ThumbnailPanel thumbnails;
    private OutlinePanel outline;
    private AttachmentPanel attachments;
    private LayersPanel layers;
    private SignaturePanel signatures;
    private SearchPanel search;
    private Tab thumbnailsTab, outlineTab, attachmentsTab, layersTab, signaturesTab, searchTab;

    private Document document;
    private Path file;
    private ToggleButton sideToggle;

    ViewerWindow(PdfViewerApp app, Stage stage, ViewerPreferences preferences) {
        this.app = app;
        this.stage = stage;
        this.preferences = preferences;
        applyPreferences();
        buildPanels();

        VBox top = new VBox(buildMenuBar(), buildToolBar());
        root.setTop(top);
        split.getItems().setAll(view);
        root.setCenter(split);
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, preferences.getDouble(ViewerPreferences.WINDOW_WIDTH, 1200),
                preferences.getDouble(ViewerPreferences.WINDOW_HEIGHT, 900));
        acceptDroppedFiles(scene);
        stage.setScene(scene);
        restoreWindowBounds();
        stage.setOnCloseRequest(e -> {
            if (!close()) e.consume();
        });
        setSidePanelVisible(preferences.getBoolean(ViewerPreferences.SIDE_PANEL_VISIBLE, true));

        view.setOnSignatureClicked(statusOf -> {
            if (statusOf.isSigned()) view.showSignatureProperties(statusOf);
            else sign(statusOf.widget());
        });
        view.setOnAnnotationAction(event -> {
            if (event.action() instanceof URIAction uri && uri.getURI() != null) app.showDocument(uri.getURI());
        });

        // unsaved changes: core's StateManager knows; it has no events, so look twice a second.
        Timeline poll = new Timeline(new KeyFrame(Duration.millis(500), e -> modified.set(
                document != null && document.getStateManager().hasUnsavedUserChanges())));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        modified.addListener((obs, was, now) -> updateTitle());
        updateTitle();
    }

    /** The view, for an application that wants to drive it. */
    public PdfView getView() {
        return view;
    }

    public Stage getStage() {
        return stage;
    }

    /** True while the document has changes that haven't been saved. */
    public final ReadOnlyBooleanProperty modifiedProperty() {
        return modified.getReadOnlyProperty();
    }

    public boolean isModified() {
        return modified.get();
    }

    /** The open file, or null. */
    public Path getFile() {
        return file;
    }

    void show() {
        stage.show();
        view.requestFocus();
    }

    // ---- settings -----------------------------------------------------------------------------

    private void applyPreferences() {
        view.setViewMode(preferences.getEnum(ViewerPreferences.VIEW_MODE, ViewMode.class, ViewMode.CONTINUOUS));
        FitMode fit = preferences.getEnum(ViewerPreferences.FIT_MODE, FitMode.class, FitMode.WIDTH);
        view.setFitMode(fit);
        if (fit == FitMode.NONE) view.setZoom(preferences.getDouble(ViewerPreferences.ZOOM, 1));
        view.setVerifySignaturesOnOpen(preferences.getBoolean(ViewerPreferences.VERIFY_SIGNATURES, true));
        view.setAnnotationAuthor(preferences.get(ViewerPreferences.ANNOTATION_AUTHOR, System.getProperty("user.name")));
        try {
            view.setAnnotationColor(javafx.scene.paint.Color.web(
                    preferences.get(ViewerPreferences.ANNOTATION_COLOR, "#ffff00")));
        } catch (IllegalArgumentException ignored) {
            // keep the view's default
        }
        view.setHighlightFormFields(preferences.getBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, false));
        view.setPaintAnnotations(preferences.getBoolean(ViewerPreferences.PAINT_ANNOTATIONS, true));
        // remember what the user changes.
        view.viewModeProperty().addListener((o, a, b) -> preferences.putEnum(ViewerPreferences.VIEW_MODE, b));
        view.fitModeProperty().addListener((o, a, b) -> preferences.putEnum(ViewerPreferences.FIT_MODE, b));
        view.zoomProperty().addListener((o, a, b) -> preferences.putDouble(ViewerPreferences.ZOOM, b.doubleValue()));
        view.highlightFormFieldsProperty().addListener((o, a, b) ->
                preferences.putBoolean(ViewerPreferences.HIGHLIGHT_FIELDS, b));
    }

    private void restoreWindowBounds() {
        double x = preferences.getDouble(ViewerPreferences.WINDOW_X, Double.NaN);
        double y = preferences.getDouble(ViewerPreferences.WINDOW_Y, Double.NaN);
        if (!Double.isNaN(x) && !Double.isNaN(y) && onSomeScreen(x, y)) {
            stage.setX(x);
            stage.setY(y);
        }
        stage.setMaximized(preferences.getBoolean(ViewerPreferences.WINDOW_MAXIMIZED, false));
    }

    private static boolean onSomeScreen(double x, double y) {
        return !javafx.stage.Screen.getScreensForRectangle(x, y, 40, 40).isEmpty();
    }

    private void storeWindowState() {
        if (!stage.isMaximized() && !stage.isIconified()) {
            preferences.putDouble(ViewerPreferences.WINDOW_X, stage.getX());
            preferences.putDouble(ViewerPreferences.WINDOW_Y, stage.getY());
            preferences.putDouble(ViewerPreferences.WINDOW_WIDTH, stage.getScene().getWidth());
            preferences.putDouble(ViewerPreferences.WINDOW_HEIGHT, stage.getScene().getHeight());
        }
        preferences.putBoolean(ViewerPreferences.WINDOW_MAXIMIZED, stage.isMaximized());
        preferences.putBoolean(ViewerPreferences.SIDE_PANEL_VISIBLE, isSidePanelVisible());
        if (isSidePanelVisible() && split.getDividerPositions().length > 0) {
            preferences.putDouble(ViewerPreferences.SIDE_PANEL_DIVIDER, split.getDividerPositions()[0]);
        }
        Tab tab = sideTabs.getSelectionModel().getSelectedItem();
        if (tab != null) preferences.put(ViewerPreferences.SIDE_PANEL_TAB, tab.getId());
    }

    // ---- side panel ---------------------------------------------------------------------------

    private void buildPanels() {
        thumbnails = new ThumbnailPanel(view);
        outline = new OutlinePanel(view);
        attachments = new AttachmentPanel(view);
        attachments.setOnOpen(this::openAttachment);
        layers = new LayersPanel(view);
        signatures = new SignaturePanel(view);
        search = new SearchPanel(view);
        loadSearchOptions();
        bindSearchOption(search.caseSensitiveProperty(), ViewerPreferences.SEARCH_CASE);
        bindSearchOption(search.wholeWordProperty(), ViewerPreferences.SEARCH_WHOLE_WORD);
        bindSearchOption(search.foldAccentsProperty(), ViewerPreferences.SEARCH_FOLD_ACCENTS);
        bindSearchOption(search.regexProperty(), ViewerPreferences.SEARCH_REGEX);
        bindSearchOption(search.cumulativeProperty(), ViewerPreferences.SEARCH_CUMULATIVE);
        bindSearchOption(search.commentsProperty(), ViewerPreferences.SEARCH_COMMENTS);
        bindSearchOption(search.formFieldsProperty(), ViewerPreferences.SEARCH_FORMS);
        bindSearchOption(search.outlinesProperty(), ViewerPreferences.SEARCH_OUTLINES);
        bindSearchOption(search.destinationsProperty(), ViewerPreferences.SEARCH_DESTINATIONS);
        thumbnailsTab = tab("thumbnails", "Pages", thumbnails);
        outlineTab = tab("bookmarks", "Bookmarks", outline);
        attachmentsTab = tab("attachments", "Attachments", attachments);
        layersTab = tab("layers", "Layers", layers);
        signaturesTab = tab("signatures", "Signatures", signatures);
        searchTab = tab("search", "Search", search);
        sideTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sideTabs.setMinWidth(140);
        sideTabs.setSide(javafx.geometry.Side.TOP);
        outline.hasOutlineProperty().addListener((o, a, b) -> updateTabs());
        attachments.hasAttachmentsProperty().addListener((o, a, b) -> updateTabs());
        layers.hasLayersProperty().addListener((o, a, b) -> updateTabs());
        signatures.hasSignaturesBinding().addListener((o, a, b) -> updateTabs());
        updateTabs();
    }

    /** The search panel's options from the preferences (at start, and after the preferences dialog). */
    private void loadSearchOptions() {
        search.caseSensitiveProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_CASE, false));
        search.wholeWordProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_WHOLE_WORD, false));
        search.foldAccentsProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_FOLD_ACCENTS, true));
        search.regexProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_REGEX, false));
        search.cumulativeProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_CUMULATIVE, false));
        search.commentsProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_COMMENTS, false));
        search.formFieldsProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_FORMS, false));
        search.outlinesProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_OUTLINES, false));
        search.destinationsProperty().set(preferences.getBoolean(ViewerPreferences.SEARCH_DESTINATIONS, false));
    }

    /** An option changed in the panel is remembered. */
    private void bindSearchOption(javafx.beans.property.BooleanProperty option, String key) {
        option.addListener((o, a, b) -> preferences.putBoolean(key, b));
    }

    private static Tab tab(String id, String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setId(id);
        return tab;
    }

    /** Pages and search always; bookmarks, attachments, layers and signatures only when the document has them. */
    private void updateTabs() {
        Tab selected = sideTabs.getSelectionModel().getSelectedItem();
        List<Tab> tabs = new java.util.ArrayList<>(List.of(thumbnailsTab));
        if (outline.hasOutline()) tabs.add(outlineTab);
        if (attachments.hasAttachments()) tabs.add(attachmentsTab);
        if (layers.hasLayers()) tabs.add(layersTab);
        if (signatures.hasSignaturesBinding().get()) tabs.add(signaturesTab);
        tabs.add(searchTab);
        sideTabs.getTabs().setAll(tabs);
        if (selected != null && tabs.contains(selected)) sideTabs.getSelectionModel().select(selected);
    }

    public boolean isSidePanelVisible() {
        return split.getItems().contains(sideTabs);
    }

    public void setSidePanelVisible(boolean visible) {
        if (visible == isSidePanelVisible()) return;
        if (visible) {
            split.getItems().add(0, sideTabs);
            SplitPane.setResizableWithParent(sideTabs, false);
            split.setDividerPositions(preferences.getDouble(ViewerPreferences.SIDE_PANEL_DIVIDER, 0.2));
        } else {
            if (split.getDividerPositions().length > 0) {
                preferences.putDouble(ViewerPreferences.SIDE_PANEL_DIVIDER, split.getDividerPositions()[0]);
            }
            split.getItems().remove(sideTabs);
        }
        if (sideToggle != null) sideToggle.setSelected(visible);
    }

    /** Shows the side panel at a tab, by id ("thumbnails", "bookmarks", ...) when it is there. */
    public void showSideTab(String id) {
        for (Tab tab : sideTabs.getTabs()) {
            if (tab.getId().equals(id)) {
                setSidePanelVisible(true);
                sideTabs.getSelectionModel().select(tab);
                return;
            }
        }
    }

    /** The document's /PageMode picks the panel it opens with, as Acrobat does. */
    private void applyPageMode(Document document) {
        Name mode = document.getCatalog().getPageMode();
        if (Catalog.PAGE_MODE_USE_OUTLINES_VALUE.equals(mode)) showSideTab("bookmarks");
        else if (Catalog.PAGE_MODE_USE_THUMBS_VALUE.equals(mode)) showSideTab("thumbnails");
        else if (Catalog.PAGE_MODE_USE_ATTACHMENTS_VALUE.equals(mode)) showSideTab("attachments");
        else if (Catalog.PAGE_MODE_OPTIONAL_CONTENT_VALUE.equals(mode)) showSideTab("layers");
        else {
            String last = preferences.get(ViewerPreferences.SIDE_PANEL_TAB, "thumbnails");
            for (Tab tab : sideTabs.getTabs()) {
                if (tab.getId().equals(last)) sideTabs.getSelectionModel().select(tab);
            }
        }
    }

    // ---- menus and tool bar -------------------------------------------------------------------

    private MenuBar buildMenuBar() {
        MenuItem open = item("Open…", "Shortcut+O", this::chooseAndOpen);
        recentMenu.setOnShowing(e -> rebuildRecentMenu());
        rebuildRecentMenu();
        MenuItem newWindow = item("New Window", "Shortcut+N", () -> app.newWindow(null));
        MenuItem save = item("Save", "Shortcut+S", this::save);
        save.disableProperty().bind(modified.not());
        MenuItem saveAs = item("Save As…", "Shortcut+Shift+S", this::saveAs);
        saveAs.disableProperty().bind(view.documentProperty().isNull());
        MenuItem print = item("Print…", "Shortcut+P", this::print);
        print.disableProperty().bind(view.printAllowedProperty().not());
        MenuItem properties = item("Document Properties…", "Shortcut+D", this::showProperties);
        properties.disableProperty().bind(view.documentProperty().isNull());
        MenuItem closeDocument = item("Close Document", "Shortcut+W", this::closeDocument);
        closeDocument.disableProperty().bind(view.documentProperty().isNull());
        MenuItem exit = item("Exit", "Shortcut+Q", app::exit);
        Menu file = new Menu("File", null, open, recentMenu, newWindow, new SeparatorMenuItem(), save, saveAs,
                new SeparatorMenuItem(), print, new SeparatorMenuItem(), properties, new SeparatorMenuItem(),
                closeDocument, exit);

        MenuItem undo = item("Undo", "Shortcut+Z", view::undo);
        undo.disableProperty().bind(view.canUndoProperty().not());
        MenuItem redo = item("Redo", "Shortcut+Shift+Z", view::redo);
        redo.disableProperty().bind(view.canRedoProperty().not());
        MenuItem copy = item("Copy", "Shortcut+C", view::copySelection);
        copy.disableProperty().bind(Bindings.createBooleanBinding(() -> view.getTextSelection() == null
                || view.getTextSelection().isCollapsed() || !view.isCopyAllowed(),
                view.textSelectionProperty(), view.copyAllowedProperty()));
        MenuItem selectAll = item("Select All", "Shortcut+A", () -> {
            view.setToolMode(ToolMode.TEXT_SELECT);
            view.selectAll();
        });
        MenuItem find = item("Find…", "Shortcut+F", this::focusFind);
        MenuItem advancedSearch = item("Search…", "Shortcut+Shift+F", this::showSearch);
        MenuItem delete = item("Delete Annotation", null, view::deleteSelectedAnnotation);
        delete.disableProperty().bind(view.selectedAnnotationProperty().isNull()
                .or(view.annotationEditingAllowedProperty().not()));
        MenuItem preferencesItem = item("Preferences…", "Shortcut+Comma", this::showPreferences);
        Menu edit = new Menu("Edit", null, undo, redo, new SeparatorMenuItem(), copy, selectAll,
                new SeparatorMenuItem(), find, advancedSearch, new SeparatorMenuItem(), delete, new SeparatorMenuItem(), preferencesItem);

        CheckMenuItem side = new CheckMenuItem("Side Panel");
        side.setAccelerator(KeyCombination.keyCombination("F4"));
        side.setSelected(isSidePanelVisible());
        side.setOnAction(e -> setSidePanelVisible(side.isSelected()));
        split.getItems().addListener((javafx.collections.ListChangeListener<Node>) c -> side.setSelected(isSidePanelVisible()));
        MenuItem zoomIn = item("Zoom In", "Shortcut+Equals", view::zoomIn);
        MenuItem zoomOut = item("Zoom Out", "Shortcut+Minus", view::zoomOut);
        MenuItem actual = item("Actual Size", "Shortcut+0", () -> {
            view.setFitMode(FitMode.NONE);
            view.setZoom(1);
        });
        MenuItem fitPage = item("Fit Page", "Shortcut+1", () -> view.setFitMode(FitMode.PAGE));
        MenuItem fitWidth = item("Fit Width", "Shortcut+2", () -> view.setFitMode(FitMode.WIDTH));
        MenuItem rotateRight = item("Rotate Clockwise", "Shortcut+Shift+Plus", view::rotateClockwise);
        MenuItem rotateLeft = item("Rotate Counterclockwise", "Shortcut+Shift+Minus", view::rotateCounterClockwise);
        Menu layout = new Menu("Page Display");
        ToggleGroup layoutGroup = new ToggleGroup();
        for (ViewMode mode : ViewMode.values()) {
            RadioMenuItem modeItem = new RadioMenuItem(label(mode));
            modeItem.setToggleGroup(layoutGroup);
            modeItem.setSelected(view.getViewMode() == mode);
            modeItem.setOnAction(e -> view.setViewMode(mode));
            view.viewModeProperty().addListener((o, a, b) -> modeItem.setSelected(b == mode));
            layout.getItems().add(modeItem);
        }
        CheckMenuItem cover = new CheckMenuItem("Show Cover Page");
        cover.selectedProperty().bindBidirectional(view.coverPageProperty());
        layout.getItems().addAll(new SeparatorMenuItem(), cover);
        CheckMenuItem annotationsShown = new CheckMenuItem("Show Annotations");
        annotationsShown.selectedProperty().bindBidirectional(view.paintAnnotationsProperty());
        view.paintAnnotationsProperty().addListener((o, a, b) -> preferences.putBoolean(ViewerPreferences.PAINT_ANNOTATIONS, b));
        CheckMenuItem fields = new CheckMenuItem("Highlight Form Fields");
        fields.selectedProperty().bindBidirectional(view.highlightFormFieldsProperty());
        Menu viewMenu = new Menu("View", null, side, new SeparatorMenuItem(), zoomIn, zoomOut, actual, fitPage, fitWidth,
                new SeparatorMenuItem(), rotateRight, rotateLeft, new SeparatorMenuItem(), layout, annotationsShown, fields);

        MenuItem first = item("First Page", "Home", () -> view.setCurrentPageIndex(0));
        MenuItem previous = item("Previous Page", null, view::previousPage);
        MenuItem next = item("Next Page", null, view::nextPage);
        MenuItem last = item("Last Page", "End", () -> view.setCurrentPageIndex(view.getPageCount() - 1));
        MenuItem goTo = item("Go to Page…", "Shortcut+G", this::goToPage);
        MenuItem resetForm = item("Reset Form", null, view::resetForm);
        Menu documentMenu = new Menu("Document", null, first, previous, next, last, goTo, new SeparatorMenuItem(), resetForm);
        for (MenuItem item : documentMenu.getItems()) {
            if (!(item instanceof SeparatorMenuItem)) item.disableProperty().bind(view.documentProperty().isNull());
        }

        Menu tools = new Menu("Tools");
        ToggleGroup toolGroup = new ToggleGroup();
        for (ToolMode mode : ToolMode.values()) {
            RadioMenuItem toolItem = new RadioMenuItem(label(mode));
            toolItem.setToggleGroup(toolGroup);
            toolItem.setSelected(view.getToolMode() == mode);
            toolItem.setOnAction(e -> {
                view.setToolMode(mode);
                toolItem.setSelected(view.getToolMode() == mode);
            });
            view.toolModeProperty().addListener((o, a, b) -> toolItem.setSelected(b == mode));
            if (mode == ToolMode.SIGNATURE) toolItem.disableProperty().bind(view.formFillingAllowedProperty().not());
            else if (mode.createsAnnotations()) toolItem.disableProperty().bind(view.annotationEditingAllowedProperty().not());
            if (mode == ToolMode.HIGHLIGHT || mode == ToolMode.NOTE || mode == ToolMode.SIGNATURE) {
                tools.getItems().add(new SeparatorMenuItem());
            }
            tools.getItems().add(toolItem);
        }

        MenuItem about = item("About", null, () -> new Alert(Alert.AlertType.INFORMATION,
                APP_NAME + "\nJavaFX viewer for ICEpdf " + org.icepdf.core.pobjects.Document.getLibraryVersion())
                .showAndWait());
        Menu help = new Menu("Help", null, about);

        MenuBar bar = new MenuBar(file, edit, viewMenu, documentMenu, tools, help);
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    private static MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) item.setAccelerator(KeyCombination.keyCombination(accelerator));
        item.setOnAction(e -> action.run());
        return item;
    }

    private static String label(Enum<?> value) {
        String name = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private void rebuildRecentMenu() {
        recentMenu.getItems().clear();
        List<Path> recent = preferences.getRecentFiles();
        for (Path path : recent) {
            MenuItem item = new MenuItem(path.getFileName() + "  —  " + path.getParent());
            item.setMnemonicParsing(false);
            item.setOnAction(e -> open(path));
            recentMenu.getItems().add(item);
        }
        if (recent.isEmpty()) {
            MenuItem none = new MenuItem("No recent files");
            none.setDisable(true);
            recentMenu.getItems().add(none);
        } else {
            MenuItem clear = new MenuItem("Clear Recent Files");
            clear.setOnAction(e -> {
                preferences.clearRecentFiles();
                preferences.save();
            });
            recentMenu.getItems().addAll(new SeparatorMenuItem(), clear);
        }
    }

    private TextField findField;

    private ToolBar buildToolBar() {
        Button open = button("Open", "Open a document (Ctrl+O)", this::chooseAndOpen);
        Button save = button("Save", "Save (Ctrl+S)", this::save);
        save.disableProperty().bind(modified.not());
        Button print = button("Print", "Print (Ctrl+P)", this::print);
        print.disableProperty().bind(view.printAllowedProperty().not());

        sideToggle = new ToggleButton("☰");
        sideToggle.setTooltip(new Tooltip("Show or hide the side panel (F4)"));
        sideToggle.setSelected(isSidePanelVisible());
        sideToggle.setOnAction(e -> setSidePanelVisible(sideToggle.isSelected()));

        Button previous = button("◀", "Previous page", view::previousPage);
        Button next = button("▶", "Next page", view::nextPage);
        TextField pageField = new TextField();
        pageField.setPrefColumnCount(4);
        pageField.setAlignment(Pos.CENTER_RIGHT);
        pageField.setOnAction(e -> {
            try {
                view.setCurrentPageIndex(Integer.parseInt(pageField.getText().trim()) - 1);
            } catch (NumberFormatException ignored) {
                // rewritten by the listener below
            }
            pageField.setText(String.valueOf(view.getCurrentPageIndex() + 1));
            view.requestFocus();
        });
        Label pageCount = new Label();
        Runnable showPage = () -> pageField.setText(view.getDocument() != null
                ? String.valueOf(view.getCurrentPageIndex() + 1) : "");
        view.currentPageIndexProperty().addListener((o, a, b) -> showPage.run());
        view.documentProperty().addListener((o, a, b) -> showPage.run());
        pageCount.textProperty().bind(Bindings.createStringBinding(() -> "of " + view.getPageCount(), view.pageCountProperty()));

        Button zoomOut = button("−", "Zoom out (Ctrl+-)", view::zoomOut);
        Button zoomIn = button("+", "Zoom in (Ctrl+=)", view::zoomIn);
        ComboBox<String> zoom = new ComboBox<>();
        zoom.setEditable(true);
        zoom.setPrefWidth(110);
        zoom.getItems().addAll("Fit Width", "Fit Page", "50%", "75%", "100%", "125%", "150%", "200%", "300%", "400%", "800%");
        Runnable showZoom = () -> zoom.getEditor().setText(view.getFitMode() == FitMode.WIDTH ? "Fit Width"
                : view.getFitMode() == FitMode.PAGE ? "Fit Page" : Math.round(view.getZoom() * 100) + "%");
        view.zoomProperty().addListener((o, a, b) -> showZoom.run());
        view.fitModeProperty().addListener((o, a, b) -> showZoom.run());
        showZoom.run();
        zoom.setOnAction(e -> {
            String value = zoom.getValue() != null ? zoom.getValue().trim() : "";
            if ("Fit Width".equals(value)) view.setFitMode(FitMode.WIDTH);
            else if ("Fit Page".equals(value)) view.setFitMode(FitMode.PAGE);
            else {
                try {
                    double percent = Double.parseDouble(value.replace("%", "").trim());
                    view.setFitMode(FitMode.NONE);
                    view.setZoom(Math.max(PdfView.MIN_ZOOM, Math.min(PdfView.MAX_ZOOM, percent / 100)));
                } catch (NumberFormatException ignored) {
                    // not a zoom
                }
            }
            showZoom.run();
            Platform.runLater(view::requestFocus);
        });

        Button rotate = button("⟳", "Rotate clockwise", view::rotateClockwise);

        ToggleGroup tools = new ToggleGroup();
        ToggleButton select = new ToggleButton("Select");
        select.setTooltip(new Tooltip("Select text and annotations"));
        ToggleButton pan = new ToggleButton("Hand");
        pan.setTooltip(new Tooltip("Drag to scroll"));
        select.setToggleGroup(tools);
        pan.setToggleGroup(tools);
        Runnable showTool = () -> {
            select.setSelected(view.getToolMode() == ToolMode.TEXT_SELECT);
            pan.setSelected(view.getToolMode() == ToolMode.PAN);
        };
        select.setOnAction(e -> {
            view.setToolMode(ToolMode.TEXT_SELECT);
            showTool.run();
        });
        pan.setOnAction(e -> {
            view.setToolMode(ToolMode.PAN);
            showTool.run();
        });
        view.toolModeProperty().addListener((o, a, b) -> showTool.run());
        showTool.run();
        MenuButton annotate = new MenuButton("Annotate");
        for (ToolMode mode : ToolMode.values()) {
            if (!mode.createsAnnotations()) continue;
            MenuItem toolItem = new MenuItem(label(mode));
            toolItem.setOnAction(e -> view.setToolMode(mode));
            if (mode == ToolMode.SIGNATURE) toolItem.disableProperty().bind(view.formFillingAllowedProperty().not());
            else toolItem.disableProperty().bind(view.annotationEditingAllowedProperty().not());
            annotate.getItems().add(toolItem);
        }

        findField = new TextField();
        findField.setPromptText("Find");
        findField.setPrefColumnCount(16);
        String[] lastFind = {null};
        findField.setOnAction(e -> {
            String text = findField.getText();
            if (text.equals(lastFind[0]) && !view.getSearchHits().isEmpty()) {
                view.nextSearchHit();
                return;
            }
            lastFind[0] = text;
            // the panel's search, with its options, so the results are listed there too.
            search.search(text);
        });
        findField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                lastFind[0] = null;
                search.clear();
                view.requestFocus();
            }
        });
        Button findPrevious = button("▲", "Previous match", view::previousSearchHit);
        Button findNext = button("▼", "Next match", view::nextSearchHit);
        findPrevious.disableProperty().bind(Bindings.isEmpty(view.getSearchHits()));
        findNext.disableProperty().bind(Bindings.isEmpty(view.getSearchHits()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToolBar bar = new ToolBar(open, save, print, new Separator(), sideToggle, new Separator(), previous, pageField,
                pageCount, next, new Separator(), zoomOut, zoom, zoomIn, rotate, new Separator(), select, pan, annotate,
                spacer, findField, findPrevious, findNext);
        for (Node node : bar.getItems()) {
            if (node == open || node == sideToggle || node instanceof Separator || node == spacer) continue;
            if (node instanceof Control control && !control.disableProperty().isBound()) {
                control.disableProperty().bind(view.documentProperty().isNull());
            }
        }
        return bar;
    }

    private static Button button(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    /** Opens the side panel on the search tab, ready to type. */
    private void showSearch() {
        if (!isSidePanelVisible()) setSidePanelVisible(true);
        showSideTab("search");
        search.focusQuery();
    }

    SearchPanel getSearchPanel() {
        return search;
    }

    private void focusFind() {
        if (findField != null) {
            findField.requestFocus();
            findField.selectAll();
        }
    }

    private Node buildStatusBar() {
        Label page = new Label();
        page.textProperty().bind(Bindings.createStringBinding(() -> view.getDocument() == null ? ""
                        : "Page " + (view.getCurrentPageIndex() + 1) + " of " + view.getPageCount(),
                view.currentPageIndexProperty(), view.pageCountProperty(), view.documentProperty()));
        Label searchStatus = new Label();
        searchStatus.textProperty().bind(Bindings.createStringBinding(() -> {
            int hits = view.getSearchHits().size();
            if (view.isSearching()) return String.format("Searching… %.0f%%  (%d found)", view.getSearchProgress() * 100, hits);
            if (hits == 0) return "";
            int current = view.getCurrentSearchHitIndex();
            return (current >= 0 ? (current + 1) + " of " : "") + hits + " matches";
        }, view.getSearchHits(), view.searchingProperty(), view.searchProgressProperty(), view.currentSearchHitIndexProperty()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(16, page, searchStatus, spacer, status);
        bar.setPadding(new Insets(3, 8, 3, 8));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void showStatus(String text) {
        status.textProperty().unbind();
        status.setText(text);
    }

    private void updateTitle() {
        String name = file != null ? file.getFileName().toString() : null;
        stage.setTitle(name == null ? APP_NAME : (isModified() ? "*" : "") + name + " — " + APP_NAME);
    }

    // ---- files --------------------------------------------------------------------------------

    private void chooseAndOpen() {
        FileChooser chooser = pdfChooser("Open");
        File chosen = chooser.showOpenDialog(stage);
        if (chosen != null) open(chosen.toPath());
    }

    private FileChooser pdfChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF documents", "*.pdf", "*.PDF"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        String last = preferences.get(ViewerPreferences.LAST_DIRECTORY, null);
        if (file != null && file.getParent() != null) chooser.setInitialDirectory(file.getParent().toFile());
        else if (last != null && new File(last).isDirectory()) chooser.setInitialDirectory(new File(last));
        return chooser;
    }

    /**
     * Opens a file in this window, after asking about unsaved changes to the current one.
     *
     * @return true if it opened
     */
    public boolean open(Path path) {
        if (!confirmDiscard()) return false;
        return load(path, -1);
    }

    /** Loads without asking; {@code page} >= 0 restores a page (after a save). */
    private boolean load(Path path, int page) {
        Document next = new Document();
        PasswordPrompt prompt = new PasswordPrompt(stage, path.getFileName().toString());
        next.setSecurityCallback(prompt);
        try {
            next.setFile(path.toString());
        } catch (PDFSecurityException e) {
            next.dispose();
            if (!prompt.isCancelled()) error("Could not open " + path.getFileName() + ": incorrect password.");
            return false;
        } catch (Exception e) {
            next.dispose();
            error("Could not open " + path + ":\n" + e.getMessage());
            return false;
        }
        Document old = document;
        document = next;
        file = path.toAbsolutePath();
        view.setDocument(next);
        if (old != null) old.dispose();
        if (page >= 0) view.setCurrentPageIndex(Math.min(page, view.getPageCount() - 1));
        modified.set(false);
        preferences.addRecentFile(file);
        if (file.getParent() != null) preferences.put(ViewerPreferences.LAST_DIRECTORY, file.getParent().toString());
        preferences.save();
        applyPageMode(next);
        updateTitle();
        showStatus("");
        return true;
    }

    /** Closes the document, keeping the window; asks about unsaved changes first. */
    public boolean closeDocument() {
        if (!confirmDiscard()) return false;
        Document old = document;
        document = null;
        file = null;
        view.setDocument(null);
        if (old != null) old.dispose();
        modified.set(false);
        updateTitle();
        return true;
    }

    /**
     * Closes the window, after asking about unsaved changes.
     *
     * @return false if the user cancelled
     */
    public boolean close() {
        if (!confirmDiscard()) return false;
        storeWindowState();
        preferences.save();
        Document old = document;
        document = null;
        view.setDocument(null);
        if (old != null) old.dispose();
        thumbnails.dispose();
        stage.hide();
        app.windowClosed(this);
        return true;
    }

    /**
     * Asks what to do with unsaved changes: save them, discard them, or cancel.
     *
     * @return true to go ahead (saved or discarded), false to stay
     */
    private boolean confirmDiscard() {
        if (document == null || !document.getStateManager().hasUnsavedUserChanges()) return true;
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Do you want to save the changes you made to " + (file != null ? file.getFileName() : "the document") + "?",
                saveButton, discard, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.initOwner(stage);
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.isEmpty() || answer.get() == ButtonType.CANCEL) return false;
        if (answer.get() == saveButton) return save();
        return true;
    }

    /**
     * Saves to the open file: the document is written (an incremental update) to a temporary file
     * beside it, closed, and the temporary file moved over the original - the open file is the
     * document's backing store, so it can't be written in place - then reopened at the same page.
     *
     * @return true if saved
     */
    public boolean save() {
        if (document == null) return false;
        if (file == null || !Files.isWritable(file)) return saveAs();
        return writeAndReopen(file);
    }

    /** Saves to a file the user picks, which then becomes the open file. */
    public boolean saveAs() {
        if (document == null) return false;
        FileChooser chooser = pdfChooser("Save As");
        chooser.setInitialFileName(file != null ? file.getFileName().toString() : "document.pdf");
        File chosen = chooser.showSaveDialog(stage);
        if (chosen == null) return false;
        Path target = chosen.toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            target = target.resolveSibling(target.getFileName() + ".pdf");
        }
        return writeAndReopen(target);
    }

    private boolean writeAndReopen(Path target) {
        Path directory = target.toAbsolutePath().getParent();
        Path temp = null;
        try {
            temp = Files.createTempFile(directory, "." + target.getFileName(), ".tmp");
            try (OutputStream out = new java.io.BufferedOutputStream(Files.newOutputStream(temp))) {
                document.saveToOutputStream(out);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Save failed", e);
            deleteQuietly(temp);
            error("Could not save " + target.getFileName() + ":\n" + e.getMessage());
            return false;
        }
        int page = view.getCurrentPageIndex();
        Document old = document;
        document = null;
        view.setDocument(null);
        old.dispose();
        try {
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Save failed", e);
            error("Could not replace " + target.getFileName() + ":\n" + e.getMessage()
                    + "\nThe document was saved as " + temp);
            load(file != null ? file : temp, page);
            return false;
        }
        boolean reopened = load(target, page);
        if (reopened) showStatus("Saved " + target.getFileName());
        return reopened;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // a stray temporary file
        }
    }

    private void print() {
        view.showPrintDialog().ifPresent(task -> {
            status.textProperty().bind(task.messageProperty());
            task.setOnFailed(e -> {
                status.textProperty().unbind();
                showStatus("");
                error("Printing failed: " + task.getException().getMessage());
            });
        });
    }

    private void showProperties() {
        if (document != null) new DocumentPropertiesDialog(stage, document).show();
    }

    private void showPreferences() {
        new PreferencesDialog(stage, preferences, view).showAndWait();
        loadSearchOptions();
    }

    private void goToPage() {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(view.getCurrentPageIndex() + 1));
        dialog.initOwner(stage);
        dialog.setTitle("Go to Page");
        dialog.setHeaderText(null);
        dialog.setContentText("Page (1-" + view.getPageCount() + "):");
        dialog.showAndWait().ifPresent(text -> {
            try {
                view.setCurrentPageIndex(Integer.parseInt(text.trim()) - 1);
            } catch (NumberFormatException ignored) {
                // not a page
            }
        });
    }

    private void openAttachment(Attachment attachment) {
        try {
            Path directory = Files.createTempDirectory("icepdf-attachment");
            Path target = directory.resolve(attachment.name().isEmpty() ? "attachment" : attachment.name());
            attachment.saveTo(target);
            target.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            if (attachment.isPdf()) app.newWindow(target);
            else app.showDocument(target.toUri().toString());
        } catch (IOException e) {
            error("Could not open " + attachment.name() + ":\n" + e.getMessage());
        }
    }

    private void sign(SignatureWidgetAnnotation field) {
        if (document == null) return;
        SignDialog dialog = new SignDialog(stage, document, field);
        SignDialog.Result result = dialog.showAndWait().orElse(null);
        if (result == null) return;
        try {
            DocumentSigning.prepare(field, result.signer(), result.request(), result.appearance());
        } catch (IllegalStateException e) {
            DocumentSigning.cancel(field);
            error(e.getMessage());
            return;
        }
        FileChooser chooser = pdfChooser("Save Signed Document");
        String base = file != null ? file.getFileName().toString().replaceFirst("(?i)\\.pdf$", "") : "document";
        chooser.setInitialFileName(base + "-signed.pdf");
        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            DocumentSigning.cancel(field);
            return;
        }
        showStatus("Signing…");
        Document signing = document;
        Thread worker = new Thread(() -> {
            try {
                DocumentSigning.saveSigned(signing, target.toPath());
                Platform.runLater(() -> {
                    // the signed copy is the document now; the original's pending signature is gone.
                    signing.getStateManager().setChangesSnapshot();
                    load(target.toPath(), view.getCurrentPageIndex());
                    showStatus("Signed " + target.getName());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showStatus("");
                    error("Signing failed: " + e.getMessage());
                });
            }
        }, "pdf-sign");
        worker.setDaemon(true);
        worker.start();
    }

    private void acceptDroppedFiles(Scene scene) {
        scene.setOnDragOver(event -> {
            if (droppedPdf(event.getDragboard().getFiles()) != null) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            File dropped = event.getDragboard().hasFiles() ? droppedPdf(event.getDragboard().getFiles()) : null;
            event.setDropCompleted(dropped != null);
            event.consume();
            if (dropped != null) Platform.runLater(() -> open(dropped.toPath()));
        });
    }

    private static File droppedPdf(List<File> files) {
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) return f;
        }
        return null;
    }

    private void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.initOwner(stage);
        alert.showAndWait();
    }
}
