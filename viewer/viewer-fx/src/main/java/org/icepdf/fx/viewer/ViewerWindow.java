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
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.core.pobjects.annotations.SignatureWidgetAnnotation;
import org.icepdf.fx.panels.*;
import org.icepdf.fx.ri.SidePanel;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.ri.actions.app.*;
import org.icepdf.fx.ri.actions.document.*;
import org.icepdf.fx.ri.actions.edit.*;
import org.icepdf.fx.ri.actions.forms.ResetFormAction;
import org.icepdf.fx.ri.actions.navigation.*;
import org.icepdf.fx.ri.actions.search.*;
import org.icepdf.fx.ri.actions.tools.ToolModeAction;
import org.icepdf.fx.ri.actions.view.*;
import org.icepdf.fx.print.PrintDefaults;
import org.icepdf.fx.print.PrintSettings;
import org.icepdf.fx.signature.DocumentSigning;
import org.icepdf.fx.signature.SignDialog;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final Menu recentMenu = new Menu("Open Recent");

    private ThumbnailPanel thumbnails;
    private OutlinePanel outline;
    private AttachmentPanel attachments;
    private LayersPanel layers;
    private SignaturePanel signatures;
    private SearchPanel search;
    private AnnotationListPanel comments;
    private Tab thumbnailsTab, outlineTab, attachmentsTab, layersTab, signaturesTab, searchTab, commentsTab;
    // full screen (presentation): the layout to go back to, null while not presenting.
    private Presentation presentation;
    // shown state of the side panel and full screen, as the actions see them.
    private final BooleanProperty sidePanelVisible = new SimpleBooleanProperty(this, "sidePanelVisible");
    private final BooleanProperty fullScreen = new SimpleBooleanProperty(this, "fullScreen");
    private final ActionControls actions;
    private final ViewerFeatures features;

    private final DocumentSession session;
    // false only for scripted runs (ViewerSmoke): unsaved changes are dropped without asking.
    boolean askBeforeDiscard = true;

    ViewerWindow(PdfViewerApp app, Stage stage, ViewerPreferences preferences, ViewerFeatures features) {
        this.app = app;
        this.stage = stage;
        this.preferences = preferences;
        this.features = features;
        session = new DocumentSession(view);
        session.setOwner(stage);
        session.setConfirmDiscard(this::askAboutChanges);
        session.setOnError(this::error);
        session.setOnLoaded(this::loaded);
        applyPreferences();
        buildPanels();
        actions = new ActionControls(StandardActions.registry(), new Context(), features::allows);
        sidePanelVisible.addListener((o, was, now) -> setSidePanelVisible(now));
        fullScreen.addListener((o, was, now) -> {
            if (now) enterFullScreen();
            else exitFullScreen();
        });

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

        view.setOnSignatureClicked(event -> {
            if (event.getStatus().isSigned()) view.showSignatureProperties(event.getStatus());
            else sign(event.getStatus().widget());
        });
        view.setOnAnnotationAction(event -> {
            if (event.getAction() instanceof URIAction uri && uri.getURI() != null) app.showDocument(uri.getURI());
        });

        view.modifiedProperty().addListener((obs, was, now) -> updateTitle());
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
        return view.modifiedProperty();
    }

    public boolean isModified() {
        return view.isModified();
    }

    /** The open file, or null. */
    public Path getFile() {
        return session.getFile();
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
        if (presentation != null) exitFullScreen();
        if (!stage.isMaximized() && !stage.isIconified() && !stage.isFullScreen()) {
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
        comments = new AnnotationListPanel(view);
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
        commentsTab = tab("comments", "Comments", comments);
        sideTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sideTabs.setMinWidth(140);
        sideTabs.setSide(javafx.geometry.Side.TOP);
        outline.hasOutlineProperty().addListener((o, a, b) -> updateTabs());
        attachments.hasAttachmentsProperty().addListener((o, a, b) -> updateTabs());
        comments.hasCommentsProperty().addListener((o, a, b) -> updateTabs());
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

    /** Pages and search always; bookmarks, comments, attachments, layers and signatures only when the document has them. */
    private void updateTabs() {
        Tab selected = sideTabs.getSelectionModel().getSelectedItem();
        List<Tab> tabs = new java.util.ArrayList<>();
        if (features.allows(SidePanel.THUMBNAILS)) tabs.add(thumbnailsTab);
        if (outline.hasOutline() && features.allows(SidePanel.BOOKMARKS)) tabs.add(outlineTab);
        if (comments.hasComments() && features.allows(SidePanel.COMMENTS)) tabs.add(commentsTab);
        if (attachments.hasAttachments() && features.allows(SidePanel.ATTACHMENTS)) tabs.add(attachmentsTab);
        if (layers.hasLayers() && features.allows(SidePanel.LAYERS)) tabs.add(layersTab);
        if (signatures.hasSignaturesBinding().get() && features.allows(SidePanel.SIGNATURES)) tabs.add(signaturesTab);
        if (features.allows(SidePanel.SEARCH)) tabs.add(searchTab);
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
        sidePanelVisible.set(isSidePanelVisible());
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
        recentMenu.setOnShowing(e -> rebuildRecentMenu());
        rebuildRecentMenu();
        Menu file = actions.menu("File", OpenAction.ID, NewWindowAction.ID, null, SaveAction.ID, SaveAsAction.ID,
                null, PrintAction.ID, null, PropertiesAction.ID, null, CloseAction.ID, ExitAction.ID);
        file.getItems().add(1, recentMenu);
        Menu edit = actions.menu("Edit", UndoAction.ID, RedoAction.ID, null, CopyAction.ID, SelectAllAction.ID, null,
                FindAction.ID, SearchPanelAction.ID, null, DeleteAnnotationAction.ID, null, PreferencesAction.ID);
        Menu pageDisplay = actions.menu("Page Display", viewModeIds());
        pageDisplay.getItems().add(new SeparatorMenuItem());
        pageDisplay.getItems().addAll(actions.items(CoverPageAction.ID));
        Menu viewMenu = actions.menu("View", SidePanelAction.ID, ShowCommentsAction.ID, null, FullScreenAction.ID, null,
                ZoomInAction.ID, ZoomOutAction.ID, ActualSizeAction.ID, FitPageAction.ID, FitWidthAction.ID, null,
                RotateClockwiseAction.ID, RotateCounterclockwiseAction.ID, null);
        viewMenu.getItems().add(pageDisplay);
        viewMenu.getItems().addAll(actions.items(ShowAnnotationsAction.ID, HighlightFieldsAction.ID));
        Menu documentMenu = actions.menu("Document", FirstPageAction.ID, PreviousPageAction.ID, NextPageAction.ID,
                LastPageAction.ID, GoToPageAction.ID, null, ResetFormAction.ID);
        Menu tools = actions.menu("Tools", toolIds(true));
        Menu help = actions.menu("Help", AboutAction.ID);
        // what's shown is remembered.
        view.paintAnnotationsProperty().addListener((o, a, b) -> preferences.putBoolean(ViewerPreferences.PAINT_ANNOTATIONS, b));

        MenuBar bar = new MenuBar(file, edit, viewMenu, documentMenu, tools, help);
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    private static String[] viewModeIds() {
        return java.util.Arrays.stream(ViewMode.values()).map(ViewModeAction::id).toArray(String[]::new);
    }

    /** The tools: select and hand, the annotation tools, the signature tool (separated when {@code grouped}). */
    private static String[] toolIds(boolean grouped) {
        List<String> ids = new java.util.ArrayList<>();
        for (ToolMode mode : ToolMode.values()) {
            if (grouped && (mode == ToolMode.HIGHLIGHT || mode == ToolMode.NOTE || mode == ToolMode.SIGNATURE)) ids.add(null);
            ids.add(ToolModeAction.id(mode));
        }
        return ids.toArray(new String[0]);
    }

    /** The commands this window offers, as actions: for its menus and bars, and for an embedding application. */
    public ActionControls getActions() {
        return actions;
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

        // the annotation tools and the signature tool, under one button.
        MenuButton annotate = new MenuButton("Annotate");
        List<String> annotationTools = new java.util.ArrayList<>(actions.registry().idsStartingWith("annotation."));
        annotationTools.add(null);
        annotationTools.add(ToolModeAction.id(ToolMode.SIGNATURE));
        annotate.getItems().setAll(actions.items(annotationTools.toArray(new String[0])));
        annotate.disableProperty().bind(view.documentProperty().isNull());

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
        for (Control control : List.of(pageField, zoom, findField)) {
            control.disableProperty().bind(view.documentProperty().isNull());
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // only what the product offers; a group whose commands are all gone leaves no separator behind.
        List<Node> items = new java.util.ArrayList<>();
        addGroup(items, OpenAction.ID, SaveAction.ID, PrintAction.ID);
        addGroup(items, SidePanelAction.ID);
        if (actions.isAvailable(PreviousPageAction.ID)) {
            addSeparator(items);
            items.addAll(List.of(actions.button(PreviousPageAction.ID), pageField, pageCount, actions.button(NextPageAction.ID)));
        }
        if (actions.isAvailable(ZoomInAction.ID)) {
            addSeparator(items);
            items.addAll(List.of(actions.button(ZoomOutAction.ID), zoom, actions.button(ZoomInAction.ID)));
        }
        if (actions.isAvailable(RotateClockwiseAction.ID)) items.add(actions.button(RotateClockwiseAction.ID));
        addGroup(items, ToolModeAction.id(ToolMode.TEXT_SELECT), ToolModeAction.id(ToolMode.PAN));
        if (!annotate.getItems().isEmpty()) items.add(annotate);
        items.add(spacer);
        if (actions.isAvailable(FindNextAction.ID)) {
            items.addAll(List.of(findField, actions.button(FindPreviousAction.ID), actions.button(FindNextAction.ID)));
        }
        ToolBar bar = new ToolBar(items.toArray(new Node[0]));
        return bar;
    }

    /** Adds the available ones of a group of tool bar buttons, after a separator. */
    private void addGroup(List<Node> items, String... ids) {
        boolean first = true;
        for (String id : ids) {
            if (!actions.isAvailable(id)) continue;
            if (first) addSeparator(items);
            first = false;
            items.add(actions.button(id));
        }
    }

    private static void addSeparator(List<Node> items) {
        if (!items.isEmpty() && !(items.get(items.size() - 1) instanceof Separator)) items.add(new Separator());
    }

    /** Opens the side panel on the search tab, ready to type. */
    private void showSearch() {
        if (!isSidePanelVisible()) setSidePanelVisible(true);
        showSideTab("search");
        search.focusQuery();
    }

    // ---- full screen --------------------------------------------------------------------------

    /** What full screen changed, to put back. */
    private record Presentation(Node top, Node bottom, boolean sidePanel, double sideWidth, double splitWidth,
                                ViewMode viewMode, FitMode fitMode,
                                double zoom, String style, javafx.event.EventHandler<javafx.scene.input.KeyEvent> keys,
                                javafx.event.EventHandler<javafx.scene.input.MouseEvent> mouse,
                                javafx.animation.PauseTransition hideCursor) {
    }

    /** True while the document is shown full screen. */
    public boolean isFullScreen() {
        return presentation != null;
    }

    /**
     * Shows the document full screen, one page at a time, fitted, on black, with the menus, toolbar
     * and panels out of the way - as a presentation.  Arrow keys, Page Up/Down, Space and Backspace
     * turn pages; Home/End go to the ends; Esc (or F11) comes back.  The pointer hides when idle.
     */
    public void enterFullScreen() {
        if (presentation != null || view.getDocument() == null) return;
        javafx.animation.PauseTransition hideCursor = new javafx.animation.PauseTransition(Duration.seconds(2));
        hideCursor.setOnFinished(e -> view.setCursor(javafx.scene.Cursor.NONE));
        javafx.event.EventHandler<javafx.scene.input.MouseEvent> mouse = e -> {
            view.setCursor(null);
            hideCursor.playFromStart();
        };
        javafx.event.EventHandler<javafx.scene.input.KeyEvent> keys = e -> {
            // typing in a note or a form field keeps its keys.
            if (e.getTarget() instanceof TextInputControl) return;
            if (presentationKey(e.getCode(), e.isShiftDown())) e.consume();
        };
        presentation = new Presentation(root.getTop(), root.getBottom(), isSidePanelVisible(), sideTabs.getWidth(),
                split.getWidth(), view.getViewMode(),
                view.getFitMode(), view.getZoom(), view.getStyle(), keys, mouse, hideCursor);
        if (isSidePanelVisible() && split.getDividerPositions().length > 0) {
            preferences.putDouble(ViewerPreferences.SIDE_PANEL_DIVIDER, split.getDividerPositions()[0]);
        }
        root.setTop(null);
        root.setBottom(null);
        split.getItems().remove(sideTabs);
        view.clearAnnotationSelection();
        view.clearSelection();
        view.setViewMode(ViewMode.SINGLE_PAGE);
        view.setFitMode(FitMode.PAGE);
        view.setStyle("-fx-background-color: black;");
        stage.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, keys);
        stage.getScene().addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, mouse);
        stage.setFullScreenExitHint("Press Esc to leave full screen");
        stage.setFullScreenExitKeyCombination(KeyCombination.keyCombination("Esc"));
        stage.fullScreenProperty().addListener(fullScreenListener);
        stage.setFullScreen(true);
        fullScreen.set(true);
        hideCursor.playFromStart();
        view.requestFocus();
    }

    /** Leaves full screen, putting the window's layout back as it was. */
    public void exitFullScreen() {
        Presentation was = presentation;
        if (was == null) return;
        presentation = null;
        stage.fullScreenProperty().removeListener(fullScreenListener);
        stage.getScene().removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, was.keys());
        stage.getScene().removeEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, was.mouse());
        was.hideCursor().stop();
        view.setCursor(null);
        if (stage.isFullScreen()) stage.setFullScreen(false);
        root.setTop(was.top());
        root.setBottom(was.bottom());
        if (was.sidePanel()) {
            split.getItems().add(0, sideTabs);
            SplitPane.setResizableWithParent(sideTabs, false);
            keepSideWidth(was.sideWidth(), was.splitWidth());
        }
        view.setStyle(was.style());
        view.setViewMode(was.viewMode());
        view.setFitMode(was.fitMode());
        if (was.fitMode() == FitMode.NONE) view.setZoom(was.zoom());
        fullScreen.set(false);
        view.requestFocus();
    }

    /**
     * Puts the side panel back at the width it had.  The window is still screen-sized when full
     * screen ends and shrinks back some time later (when, depends on the window manager), so the
     * panel is restored as its old fraction of the split and scales with the window while that
     * happens - landing on its old width whenever the resize comes.  A few seconds on, it keeps a
     * fixed width again as the window resizes.
     */
    private void keepSideWidth(double width, double splitWidth) {
        SplitPane.setResizableWithParent(sideTabs, true);
        double fraction = splitWidth > 0 ? Math.min(0.9, width / splitWidth) : 0.2;
        // the split pane rebuilds its dividers (at 0.5) when the re-added panel is laid out, which
        // can be a pulse or two later: hold the fraction until things settle.
        javafx.beans.value.ChangeListener<Number> hold = (o, was, now) -> {
            if (Math.abs(now.doubleValue() - fraction) > 0.002) split.setDividerPositions(fraction);
        };
        javafx.collections.ListChangeListener<SplitPane.Divider> rebuilt = change -> {
            split.getDividers().forEach(d -> d.positionProperty().addListener(hold));
            split.setDividerPositions(fraction);
        };
        split.getDividers().addListener(rebuilt);
        split.getDividers().forEach(d -> d.positionProperty().addListener(hold));
        split.setDividerPositions(fraction);
        javafx.animation.PauseTransition settle = new javafx.animation.PauseTransition(Duration.seconds(3));
        settle.setOnFinished(e -> {
            split.getDividers().removeListener(rebuilt);
            split.getDividers().forEach(d -> d.positionProperty().removeListener(hold));
            SplitPane.setResizableWithParent(sideTabs, false);
        });
        settle.play();
    }

    // Esc (the stage's own exit key) or the window manager can end full screen too.
    private final javafx.beans.value.ChangeListener<Boolean> fullScreenListener = (o, was, now) -> {
        if (!now) exitFullScreen();
    };

    /** Page turning while presenting; true when the key was used. */
    private boolean presentationKey(KeyCode code, boolean shift) {
        switch (code) {
            case RIGHT, DOWN, PAGE_DOWN, ENTER, N -> view.nextPage();
            case SPACE -> {
                if (shift) view.previousPage();
                else view.nextPage();
            }
            case LEFT, UP, PAGE_UP, BACK_SPACE, P -> view.previousPage();
            case HOME -> view.setCurrentPageIndex(0);
            case END -> view.setCurrentPageIndex(view.getPageCount() - 1);
            case F11 -> exitFullScreen();
            default -> {
                return false;
            }
        }
        return true;
    }

    AnnotationListPanel getCommentsPanel() {
        return comments;
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
        Path file = session.getFile();
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
        Path file = session.getFile();
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
        return session.open(path);
    }

    /** A file opened (or reopened after a save): remember it, pick its first panel, retitle. */
    private void loaded(Path file) {
        preferences.addRecentFile(file);
        if (file.getParent() != null) preferences.put(ViewerPreferences.LAST_DIRECTORY, file.getParent().toString());
        preferences.save();
        applyPageMode(session.getDocument());
        updateTitle();
        showStatus("");
    }

    /** Closes the document, keeping the window; asks about unsaved changes first. */
    public boolean closeDocument() {
        boolean closed = session.close();
        if (closed) updateTitle();
        return closed;
    }

    /**
     * Closes the window, after asking about unsaved changes.
     *
     * @return false if the user cancelled
     */
    public boolean close() {
        if (!session.confirmDiscard()) return false;
        storeWindowState();
        preferences.save();
        session.dispose();
        thumbnails.dispose();
        comments.dispose();
        stage.hide();
        app.windowClosed(this);
        return true;
    }

    /**
     * Asks what to do with unsaved changes: save them, discard them, or cancel.
     *
     * @return true to go ahead (saved or discarded), false to stay
     */
    private boolean askAboutChanges() {
        if (!askBeforeDiscard) return true;
        Path file = session.getFile();
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
     * Saves to the open file (see {@link DocumentSession#save()}); asks for a name when it can't be
     * written in place.
     *
     * @return true if saved
     */
    public boolean save() {
        if (session.getDocument() == null) return false;
        if (!session.canSaveInPlace()) return saveAs();
        boolean saved = session.save();
        if (saved) showStatus("Saved " + session.getFile().getFileName());
        return saved;
    }

    /** Saves to a file the user picks, which then becomes the open file. */
    public boolean saveAs() {
        if (session.getDocument() == null) return false;
        FileChooser chooser = pdfChooser("Save As");
        Path file = session.getFile();
        chooser.setInitialFileName(file != null ? file.getFileName().toString() : "document.pdf");
        File chosen = chooser.showSaveDialog(stage);
        if (chosen == null) return false;
        boolean saved = session.saveAs(chosen.toPath());
        if (saved) showStatus("Saved " + session.getFile().getFileName());
        return saved;
    }

    /** The print dialog starts from the remembered choices, and remembers what was printed with. */
    private void customisePrintDialog(org.icepdf.fx.print.PdfPrintDialog dialog) {
        dialog.setDefaults(printDefaults());
        dialog.resultProperty().addListener((o, was, settings) -> {
            if (settings != null) savePrintDefaults(PrintDefaults.of(settings));
        });
    }

    /** A print's progress in the status bar. */
    private void printStarted(javafx.concurrent.Task<Void> task) {
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            status.textProperty().unbind();
            showStatus("Printed.");
        });
        task.setOnCancelled(e -> {
            status.textProperty().unbind();
            showStatus("Printing cancelled.");
        });
        task.setOnFailed(e -> {
            status.textProperty().unbind();
            showStatus("");
            error("Printing failed: " + task.getException().getMessage());
        });
    }

    private PrintDefaults printDefaults() {
        return new PrintDefaults(preferences.get(ViewerPreferences.PRINT_PRINTER, null),
                preferences.get(ViewerPreferences.PRINT_PAPER, null),
                preferences.getEnum(ViewerPreferences.PRINT_SCALING, PrintSettings.Scaling.class, PrintSettings.Scaling.FIT),
                preferences.getEnum(ViewerPreferences.PRINT_ORIENTATION, PrintSettings.Orientation.class,
                        PrintSettings.Orientation.AUTO),
                preferences.get(ViewerPreferences.PRINT_SIDES, null),
                preferences.getBoolean(ViewerPreferences.PRINT_ANNOTATIONS, true));
    }

    private void savePrintDefaults(PrintDefaults defaults) {
        preferences.put(ViewerPreferences.PRINT_PRINTER, defaults.printer());
        preferences.put(ViewerPreferences.PRINT_PAPER, defaults.paper());
        preferences.putEnum(ViewerPreferences.PRINT_SCALING, defaults.scaling());
        preferences.putEnum(ViewerPreferences.PRINT_ORIENTATION, defaults.orientation());
        preferences.put(ViewerPreferences.PRINT_SIDES, defaults.sides());
        preferences.putBoolean(ViewerPreferences.PRINT_ANNOTATIONS, defaults.annotations());
        preferences.save();
    }

    private void showPreferences() {
        new PreferencesDialog(stage, preferences, view).showAndWait();
        loadSearchOptions();
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
        Document document = session.getDocument();
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
        Path file = session.getFile();
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
                    session.openAt(target.toPath(), view.getCurrentPageIndex());
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

    /** What the actions see of this window. */
    private final class Context implements ViewerContext {
        @Override
        public PdfView view() {
            return view;
        }

        @Override
        public javafx.stage.Window window() {
            return stage;
        }

        @Override
        public boolean has(Capability capability) {
            return true;
        }

        @Override
        public String applicationName() {
            return APP_NAME;
        }

        @Override
        public void openDocument() {
            chooseAndOpen();
        }

        @Override
        public void saveDocument() {
            save();
        }

        @Override
        public void saveDocumentAs() {
            saveAs();
        }

        @Override
        public void closeDocument() {
            ViewerWindow.this.closeDocument();
        }

        @Override
        public void newWindow() {
            app.newWindow(null);
        }

        @Override
        public void exit() {
            app.exit();
        }

        @Override
        public void showPreferences() {
            ViewerWindow.this.showPreferences();
        }

        @Override
        public BooleanProperty sidePanelVisibleProperty() {
            return sidePanelVisible;
        }

        @Override
        public void showSidePanel(String panel) {
            showSideTab(panel);
        }

        @Override
        public javafx.beans.value.ObservableBooleanValue sidePanelAvailable(String panel) {
            return switch (panel) {
                case "comments" -> comments.hasCommentsProperty();
                case "bookmarks" -> outline.hasOutlineProperty();
                case "attachments" -> attachments.hasAttachmentsProperty();
                case "layers" -> layers.hasLayersProperty();
                case "signatures" -> signatures.hasSignaturesBinding();
                default -> view.documentProperty().isNotNull();
            };
        }

        @Override
        public BooleanProperty fullScreenProperty() {
            return fullScreen;
        }

        @Override
        public void focusFind() {
            ViewerWindow.this.focusFind();
        }

        @Override
        public void showSearch() {
            ViewerWindow.this.showSearch();
        }

        @Override
        public void customisePrintDialog(org.icepdf.fx.print.PdfPrintDialog dialog) {
            ViewerWindow.this.customisePrintDialog(dialog);
        }

        @Override
        public void printStarted(javafx.concurrent.Task<Void> task) {
            ViewerWindow.this.printStarted(task);
        }
    }
}
