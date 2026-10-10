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
package org.icepdf.fx.ri;

import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Name;
import org.icepdf.core.pobjects.actions.URIAction;
import org.icepdf.fx.panels.*;
import org.icepdf.fx.print.PdfPrintDialog;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.ri.actions.ActionRegistry;
import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.ri.icons.IconProvider;
import org.icepdf.fx.ri.icons.SvgIcons;
import org.icepdf.fx.ri.ui.*;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ViewMode;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The ICEpdf viewer as one component: a {@link PdfView} with a header bar, a tool rail, a utility
 * panel and full screen, built from the {@linkplain StandardActions actions}, shaped by what the
 * product offers ({@link ViewerFeatures}) and arranged as the user left it ({@link UserLayout}).
 * <pre>{@code
 * PdfViewer viewer = PdfViewer.create(ViewerFeatures.full(), UserLayout.load(settingsDir.resolve("layout.properties")));
 * stage.setScene(new Scene(viewer, 1200, 900));
 * viewer.open(Path.of("file.pdf"));
 * }</pre>
 * The viewer owns its document ({@link #getSession()}); {@link #dispose()} when it's thrown away.
 */
public class PdfViewer extends StackPane {

    private static final String[] MENU = {
            "document.save-as", "document.print", null, "document.properties", null,
            "view.full-screen", "view.mode.single-page", "view.mode.continuous", "view.mode.facing",
            "view.mode.facing-continuous", "view.cover-page", null, "view.rotate-clockwise",
            "view.rotate-counterclockwise", "view.show-annotations", "view.highlight-fields", null,
            "forms.reset", null, "document.close", null, "app.new-window", "app.preferences", "app.about", "app.exit"};

    private final ViewerFeatures features;
    private final UserLayout layout;
    private final PdfView view = new PdfView();
    private final DocumentSession session = new DocumentSession(view);
    private final ActionControls actions;
    private final IconProvider icons;
    private final ViewerShell shell = new ViewerShell();
    private final Toast toast = new Toast();
    private final Theme theme;
    private final HeaderBar header;
    private final ToolRail rail;
    private final UtilityPanel utility;
    private final SearchPanel searchPanel;
    private final ThumbnailPanel thumbnails;
    private final AnnotationListPanel comments;
    private final OutlinePanel outline;
    private final AttachmentPanel attachments;
    private final LayersPanel layers;
    private final SignaturePanel signatures;
    private final BooleanProperty sidePanelVisible = new SimpleBooleanProperty(this, "sidePanelVisible");
    private final BooleanProperty fullScreen = new SimpleBooleanProperty(this, "fullScreen");
    private final PauseTransition saveLayoutLater = new PauseTransition(Duration.millis(600));
    private Runnable onNewWindow, onExit, onPreferences;
    private Consumer<String> onError = this::showMessage;
    private Presenting presenting;

    /** Everything, with a layout that isn't saved. */
    public static PdfViewer create() {
        return create(ViewerFeatures.full(), UserLayout.inMemory());
    }

    public static PdfViewer create(ViewerFeatures features, UserLayout layout) {
        return new PdfViewer(features, layout, StandardActions.registry(), SvgIcons.placeholders());
    }

    /**
     * @param registry the commands (built-ins plus the product's own)
     * @param icons    icons for them (chain a product's set before the placeholders)
     */
    public PdfViewer(ViewerFeatures features, UserLayout layout, ActionRegistry registry, IconProvider icons) {
        this.features = features;
        this.layout = layout;
        this.icons = icons;
        getStyleClass().add("pdf-viewer");
        theme = new Theme(this);
        actions = new ActionControls(registry, new Context(), features::allows).setIcons(icons, 20, 16);

        session.setOnError(message -> onError.accept(message));
        session.setOnLoaded(this::loaded);
        sceneProperty().addListener((o, a, scene) -> session.setOwner(scene != null ? scene.getWindow() : null));
        view.setOnAnnotationAction(event -> {
            if (event.getAction() instanceof URIAction uri && uri.getURI() != null) showMessage("Link: " + uri.getURI());
        });

        searchPanel = new SearchPanel(view);
        thumbnails = new ThumbnailPanel(view);
        comments = new AnnotationListPanel(view);
        outline = new OutlinePanel(view);
        attachments = new AttachmentPanel(view);
        layers = new LayersPanel(view);
        signatures = new SignaturePanel(view);

        header = new HeaderBar(actions, view, searchPanel::search, MENU);
        rail = new ToolRail(actions, features, layout);
        rail.setOnLayoutChanged(l -> {
            buildDrawers();
            saveLayoutLater.playFromStart();
        });
        utility = new UtilityPanel(layout.getPanelSide(), icons);
        addPanel(SidePanel.THUMBNAILS, "Pages", thumbnails, view.documentProperty().isNotNull());
        addPanel(SidePanel.BOOKMARKS, "Bookmarks", outline, outline.hasOutlineProperty());
        addPanel(SidePanel.COMMENTS, "Comments", comments, comments.hasCommentsProperty());
        addPanel(SidePanel.ATTACHMENTS, "Attachments", attachments, attachments.hasAttachmentsProperty());
        addPanel(SidePanel.LAYERS, "Layers", layers, layers.hasLayersProperty());
        addPanel(SidePanel.SIGNATURES, "Signatures", signatures, signatures.hasSignaturesBinding());
        addPanel(SidePanel.SEARCH, "Search", searchPanel, view.documentProperty().isNotNull());
        SidePanel.of(layout.getLastPanel()).ifPresent(utility::select);

        shell.setHeader(header);
        shell.setCenter(view);
        buildDrawers();
        getChildren().addAll(shell, toast);
        rememberLayout();
        view.modifiedProperty().addListener((o, a, b) -> updateTitle());
        updateTitle();
    }

    // ---- API --------------------------------------------------------------------------------------

    /** Opens a file (asking about unsaved changes first).  @return true if it opened */
    public boolean open(Path file) {
        return session.open(file);
    }

    public PdfView getView() {
        return view;
    }

    /** The open document and file; open, save, close. */
    public DocumentSession getSession() {
        return session;
    }

    /** The commands, as controls - for an application's own menus. */
    public ActionControls getActions() {
        return actions;
    }

    public ViewerFeatures getFeatures() {
        return features;
    }

    public UserLayout getLayout() {
        return layout;
    }

    public Theme getTheme() {
        return theme;
    }

    public ToolRail getToolRail() {
        return rail;
    }

    public UtilityPanel getUtilityPanel() {
        return utility;
    }

    public ViewerShell getShell() {
        return shell;
    }

    public HeaderBar getHeaderBar() {
        return header;
    }

    /** Shows a short message that fades away. */
    public void showMessage(String message) {
        toast.show(message);
    }

    public Toast getToast() {
        return toast;
    }

    /** Offers "New Window" (app.new-window) and runs this for it. */
    public void setOnNewWindow(Runnable onNewWindow) {
        this.onNewWindow = onNewWindow;
    }

    /** Offers "Exit" (app.exit). */
    public void setOnExit(Runnable onExit) {
        this.onExit = onExit;
    }

    /** Offers "Preferences" (app.preferences). */
    public void setOnPreferences(Runnable onPreferences) {
        this.onPreferences = onPreferences;
    }

    /** Receives errors (could not open, could not save); a message toast by default. */
    public void setOnError(Consumer<String> onError) {
        this.onError = onError != null ? onError : this::showMessage;
    }

    /** Asked before unsaved changes are dropped; false to stay.  By default they're dropped. */
    public void setConfirmDiscard(java.util.function.BooleanSupplier confirm) {
        session.setConfirmDiscard(confirm);
    }

    public BooleanProperty fullScreenProperty() {
        return fullScreen;
    }

    /** Closes the document without asking and stops background work. */
    public void dispose() {
        if (presenting != null) exitFullScreen();
        session.dispose();
        thumbnails.dispose();
        comments.dispose();
        theme.dispose();
        saveLayoutLater.stop();
        layout.save();
    }

    // ---- layout ---------------------------------------------------------------------------------

    private void addPanel(SidePanel panel, String label, javafx.scene.Node node, ObservableBooleanValue available) {
        if (features.allows(panel)) utility.addPanel(panel, label, node, available);
    }

    /** Puts the rail and the utility panel on their sides, as the layout says. */
    private void buildDrawers() {
        UserLayout.Side railSide = layout.getRailSide();
        EdgeDrawer railDrawer;
        if (layout.isRailAutoShow()) {
            Region hotEdge = new Region();
            hotEdge.getStyleClass().add("rail-hot-edge");
            railDrawer = new EdgeDrawer(railSide, hotEdge, rail);
        } else {
            railDrawer = new EdgeDrawer(railSide, null, rail);
            railDrawer.pinnedProperty().set(true);
        }
        railDrawer.setResizable(false);
        railDrawer.setFitContent(true);
        shell.setDrawer(railDrawer);
        // the utility panel sits opposite the rail.
        utility.getDrawer().setSide(layout.getPanelSide());
        shell.setDrawer(utility.getDrawer());
    }

    /** The utility panel's state is the user's: remember it. */
    private void rememberLayout() {
        EdgeDrawer panel = utility.getDrawer();
        panel.pinnedProperty().set(layout.isPanelPinned());
        panel.hoverOpenProperty().set(layout.isPanelHoverOpen());
        panel.widthProperty().set(layout.getPanelWidth());
        panel.pinnedProperty().addListener((o, a, b) -> {
            layout.setPanelPinned(b);
            saveLayoutLater.playFromStart();
        });
        panel.widthProperty().addListener((o, a, b) -> {
            layout.setPanelWidth(b.doubleValue());
            saveLayoutLater.playFromStart();
        });
        panel.stateProperty().addListener((o, a, b) -> {
            sidePanelVisible.set(b != EdgeDrawer.State.COLLAPSED);
            if (utility.getShown() != null) layout.setLastPanel(utility.getShown().id());
        });
        sidePanelVisible.addListener((o, a, b) -> {
            if (b && !panel.isOpen()) panel.open(true);
            else if (!b && panel.isOpen()) {
                panel.pinnedProperty().set(false);
                panel.close();
            }
        });
        saveLayoutLater.setOnFinished(e -> layout.save());
    }

    private void loaded(Path file) {
        updateTitle();
        // the document's /PageMode picks the panel it opens with, as Acrobat does.
        Name mode = session.getDocument().getCatalog().getPageMode();
        SidePanel wanted = Catalog.PAGE_MODE_USE_OUTLINES_VALUE.equals(mode) ? SidePanel.BOOKMARKS
                : Catalog.PAGE_MODE_USE_THUMBS_VALUE.equals(mode) ? SidePanel.THUMBNAILS
                : Catalog.PAGE_MODE_USE_ATTACHMENTS_VALUE.equals(mode) ? SidePanel.ATTACHMENTS
                : Catalog.PAGE_MODE_OPTIONAL_CONTENT_VALUE.equals(mode) ? SidePanel.LAYERS : null;
        if (wanted != null) javafx.application.Platform.runLater(() -> utility.open(wanted));
    }

    private void updateTitle() {
        Path file = session.getFile();
        header.setTitle(file == null ? "" : (view.isModified() ? "• " : "") + file.getFileName());
    }

    // ---- full screen ----------------------------------------------------------------------------

    private record Presenting(ViewMode viewMode, FitMode fitMode, double zoom, EventHandler<KeyEvent> keys) {
    }

    private void enterFullScreen() {
        if (presenting != null || view.getDocument() == null) return;
        EventHandler<KeyEvent> keys = e -> {
            if (e.getTarget() instanceof javafx.scene.control.TextInputControl) return;
            switch (e.getCode()) {
                case RIGHT, DOWN, PAGE_DOWN, ENTER, N -> view.nextPage();
                case SPACE -> {
                    if (e.isShiftDown()) view.previousPage();
                    else view.nextPage();
                }
                case LEFT, UP, PAGE_UP, BACK_SPACE, P -> view.previousPage();
                case HOME -> view.setCurrentPageIndex(0);
                case END -> view.setCurrentPageIndex(view.getPageCount() - 1);
                case ESCAPE, F11 -> fullScreen.set(false);
                default -> {
                    return;
                }
            }
            e.consume();
        };
        presenting = new Presenting(view.getViewMode(), view.getFitMode(), view.getZoom(), keys);
        shell.closeOverlays();
        shell.setChromeVisible(false);
        getStyleClass().add("presenting");
        view.clearAnnotationSelection();
        view.setViewMode(ViewMode.SINGLE_PAGE);
        view.setFitMode(FitMode.PAGE);
        addEventFilter(KeyEvent.KEY_PRESSED, keys);
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            stage.setFullScreenExitHint("Press Esc to leave full screen");
            stage.setFullScreen(true);
            stage.fullScreenProperty().addListener(stageFullScreen);
        }
        view.requestFocus();
    }

    private final javafx.beans.value.ChangeListener<Boolean> stageFullScreen = (o, was, now) -> {
        if (!now) fullScreen.set(false);
    };

    private void exitFullScreen() {
        Presenting was = presenting;
        if (was == null) return;
        presenting = null;
        removeEventFilter(KeyEvent.KEY_PRESSED, was.keys());
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            stage.fullScreenProperty().removeListener(stageFullScreen);
            if (stage.isFullScreen()) stage.setFullScreen(false);
        }
        getStyleClass().remove("presenting");
        shell.setChromeVisible(true);
        view.setViewMode(was.viewMode());
        view.setFitMode(was.fitMode());
        if (was.fitMode() == FitMode.NONE) view.setZoom(was.zoom());
        view.requestFocus();
    }

    {
        fullScreen.addListener((o, was, now) -> {
            if (now) enterFullScreen();
            else exitFullScreen();
            if (now && presenting == null) fullScreen.set(false);
        });
    }

    // ---- what the actions see ------------------------------------------------------------------

    private final class Context implements ViewerContext {
        @Override
        public PdfView view() {
            return view;
        }

        @Override
        public Window window() {
            return getScene() != null ? getScene().getWindow() : null;
        }

        @Override
        public boolean has(Capability capability) {
            return switch (capability) {
                case NEW_WINDOW -> onNewWindow != null;
                case EXIT -> onExit != null;
                case PREFERENCES -> onPreferences != null;
                default -> true;
            };
        }

        @Override
        public void openDocument() {
            File chosen = chooser("Open").showOpenDialog(window());
            if (chosen != null) open(chosen.toPath());
        }

        @Override
        public void saveDocument() {
            if (session.canSaveInPlace()) {
                if (session.save()) showMessage("Saved");
            } else {
                saveDocumentAs();
            }
        }

        @Override
        public void saveDocumentAs() {
            FileChooser chooser = chooser("Save As");
            Path file = session.getFile();
            chooser.setInitialFileName(file != null ? file.getFileName().toString() : "document.pdf");
            File chosen = chooser.showSaveDialog(window());
            if (chosen != null && session.saveAs(chosen.toPath())) showMessage("Saved " + session.getFile().getFileName());
        }

        @Override
        public void closeDocument() {
            session.close();
            updateTitle();
        }

        @Override
        public void newWindow() {
            onNewWindow.run();
        }

        @Override
        public void exit() {
            onExit.run();
        }

        @Override
        public void showPreferences() {
            onPreferences.run();
        }

        @Override
        public BooleanProperty sidePanelVisibleProperty() {
            return sidePanelVisible;
        }

        @Override
        public void showSidePanel(String panel) {
            SidePanel.of(panel).ifPresent(utility::open);
        }

        @Override
        public ObservableBooleanValue sidePanelAvailable(String panel) {
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
            header.showSearch();
        }

        @Override
        public void showSearch() {
            utility.open(SidePanel.SEARCH);
            searchPanel.focusQuery();
        }

        @Override
        public void printStarted(Task<Void> task) {
            task.setOnSucceeded(e -> showMessage("Printed"));
            task.setOnFailed(e -> onError.accept("Printing failed: " + task.getException().getMessage()));
        }

        @Override
        public void customisePrintDialog(PdfPrintDialog dialog) {
        }

        private FileChooser chooser(String title) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF documents", "*.pdf", "*.PDF"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            Path file = session.getFile();
            if (file != null && file.getParent() != null) chooser.setInitialDirectory(file.getParent().toFile());
            return chooser;
        }
    }
}
