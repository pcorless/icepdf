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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.icepdf.fx.panels.*;
import org.icepdf.fx.print.PdfPrintDialog;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.ri.actions.ActionRegistry;
import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.ri.document.DocumentPane;
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
            "document.open-new-tab", "document.new-tab", "document.save-as", "document.print", null, "document.properties", null,
            "view.full-screen", "view.mode.single-page", "view.mode.continuous", "view.mode.facing",
            "view.mode.facing-continuous", "view.cover-page", null, "view.rotate-clockwise",
            "view.rotate-counterclockwise", "view.show-annotations", "view.highlight-fields", null,
            "forms.reset", null, "document.close", null, "app.new-window", "app.preferences", "app.about", "app.exit"};

    private final ViewerFeatures features;
    private final UserLayout layout;
    private final ObservableList<DocumentPane> documents = FXCollections.observableArrayList();
    private final ObjectProperty<DocumentPane> current = new SimpleObjectProperty<>(this, "current");
    // the selected document's view: what the header, the actions and the shell follow.
    private final ObjectProperty<PdfView> currentView = new SimpleObjectProperty<>(this, "currentView");
    private final ActionControls actions;
    private final IconProvider icons;
    private final ViewerShell shell = new ViewerShell();
    private final Toast toast = new Toast();
    private final Theme theme;
    private final HeaderBar header;
    private final ToolRail rail;
    private final UtilityPanel utility;
    private final BooleanProperty sidePanelVisible = new SimpleBooleanProperty(this, "sidePanelVisible");
    private final BooleanProperty fullScreen = new SimpleBooleanProperty(this, "fullScreen");
    private final PauseTransition saveLayoutLater = new PauseTransition(Duration.millis(600));
    private Runnable onNewWindow, onExit, onPreferences;
    private final Context context = new Context();
    private FileHistory history;
    private ColorHistory colorHistory = ColorHistory.inMemory();
    private final javafx.scene.control.Menu recentMenu = new javafx.scene.control.Menu("Open Recent");
    private Consumer<String> onLink;
    private TearOff onTearOff;
    private Runnable onEmptied;
    private Consumer<DocumentPane> onDocumentCreated;
    private java.util.function.BooleanSupplier confirmDiscard;
    private javafx.stage.Window owner;
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
        actions = new ActionControls(registry, context, features::allows).setIcons(icons, 20, 16);

        sceneProperty().addListener((o, a, scene) -> {
            owner = scene != null ? scene.getWindow() : null;
            documents.forEach(d -> d.getSession().setOwner(owner));
            if (a != null) for (int i = 1; i <= 9; i++) a.getAccelerators().remove(recentKey(i));
            if (scene != null) {
                // Shortcut+1..9 open the nth recent file, read when pressed (the menu is built only when shown).
                for (int i = 1; i <= 9; i++) {
                    int index = i - 1;
                    scene.getAccelerators().put(recentKey(i), () -> openRecentAt(index));
                }
            }
        });

        DocumentPane first = newPane();
        documents.add(first);
        current.set(first);
        currentView.set(first.getView());

        header = new HeaderBar(actions, currentView, query -> pane().getSearchPanel().search(query), MENU);
        if (features.isMultipleDocuments()) {
            TabStrip strip = new TabStrip(documents, current, this::selectDocument, this::removeDocument,
                    this::newTab, icons);
            strip.setOnMove(this::moveDocument);
            strip.setOnTransfer(this::detachDocument, this::adoptDocument, this::tearOffRequested,
                    () -> onTearOff != null && documents.size() > 1);
            header.setTabs(strip);
            addEventFilter(KeyEvent.KEY_PRESSED, this::tabKeys);
            strip.setOnFilesDropped(board -> !droppedPdfs(board).isEmpty(), (pane, board) -> {
                java.util.List<Path> pdfs = droppedPdfs(board);
                if (pdfs.isEmpty()) return;
                javafx.application.Platform.runLater(() -> {
                    open(pdfs.get(0), pane);
                    pdfs.stream().skip(1).forEach(this::openInNewTab);
                });
            });
        }
        recentMenu.setMnemonicParsing(false);
        recentMenu.setVisible(false);
        // an empty Menu is treated as a leaf and never fires onShowing, so seed it; the real items are built on showing.
        recentMenu.getItems().add(new javafx.scene.control.MenuItem());
        recentMenu.setOnShowing(e -> rebuildRecentMenu());
        refreshMenu();
        rail = new ToolRail(actions, features, layout);
        rail.setOnCustomise(this::customiseTools);
        // the layout is shared by every viewer in the application: follow its changes, wherever made.
        layout.addListener(layoutListener);
        utility = new UtilityPanel(layout.getPanelSide(), icons);
        for (SidePanel panel : SidePanel.values()) {
            if (features.allows(panel)) utility.addPanel(panel, panelLabel(panel), panelNode(first, panel), panelAvailable(first, panel));
        }
        SidePanel.of(layout.getLastPanel()).ifPresent(utility::select);

        shell.setHeader(header);
        shell.setCenter(first.getNode());
        buildDrawers();
        getChildren().addAll(shell, toast);
        acceptDroppedFiles();
        rememberLayout();
        updateTitle();
    }

    // ---- API --------------------------------------------------------------------------------------

    /**
     * Opens a file in the selected document, asking about unsaved changes first.  A file that is
     * open in another tab shows that tab instead.  @return true if it opened
     */
    public boolean open(Path file) {
        return open(file, pane());
    }

    /** Opens a file in {@code target}, replacing what it shows (the target is selected). */
    public boolean open(Path file, DocumentPane target) {
        if (!features.isMultipleDocuments()) return session().open(file);
        DocumentPane existing = paneShowing(file);
        if (existing != null && existing != target) {
            selectDocument(existing);
            return true;
        }
        selectDocument(target);
        return target.getSession().open(file);
    }

    /**
     * Opens a file in a new tab (an empty tab is used instead of adding one).  A file that is open
     * already shows its tab.  Without tabs it replaces the document.  @return true if it opened
     */
    public boolean openInNewTab(Path file) {
        if (!features.isMultipleDocuments()) return session().open(file);
        DocumentPane existing = paneShowing(file);
        if (existing != null) {
            selectDocument(existing);
            return true;
        }
        if (pane().getSession().getDocument() == null) return session().open(file);
        // taken away again if the file doesn't open.
        DocumentPane added = addDocument();
        if (added.getSession().open(file)) return true;
        if (added.getSession().getDocument() == null) removeDocument(added);
        return false;
    }

    private DocumentPane paneShowing(Path file) {
        Path real = real(file);
        for (DocumentPane pane : documents) {
            Path open = pane.getSession().getFile();
            if (open != null && real(open).equals(real)) return pane;
        }
        return null;
    }

    private static java.util.List<Path> droppedPdfs(javafx.scene.input.Dragboard board) {
        java.util.List<Path> pdfs = new java.util.ArrayList<>();
        if (board != null && board.hasFiles()) {
            for (File f : board.getFiles()) {
                if (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) pdfs.add(f.toPath());
            }
        }
        return pdfs;
    }

    /**
     * PDFs dropped on the viewer open in new tabs (or replace the document, without tabs); dropped
     * on a tab they replace that tab's document.
     */
    private void acceptDroppedFiles() {
        setOnDragOver(e -> {
            if (!droppedPdfs(e.getDragboard()).isEmpty()) e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            e.consume();
        });
        setOnDragDropped(e -> {
            java.util.List<Path> pdfs = droppedPdfs(e.getDragboard());
            e.setDropCompleted(!pdfs.isEmpty());
            e.consume();
            javafx.application.Platform.runLater(() -> pdfs.forEach(this::openInNewTab));
        });
    }

    private static Path real(Path file) {
        try {
            return file.toRealPath();
        } catch (java.io.IOException e) {
            return file.toAbsolutePath().normalize();
        }
    }

    /** A new empty tab, with the focus on the open button's job: the user picks a file next. */
    public void newTab() {
        if (!features.isMultipleDocuments()) return;
        // one empty tab is enough.
        for (DocumentPane pane : documents) {
            if (pane.getSession().getDocument() == null) {
                selectDocument(pane);
                return;
            }
        }
        addDocument();
    }

    private void tabKeys(KeyEvent e) {
        if (!e.isShortcutDown() || documents.size() < 2) return;
        // Ctrl+Shift+PageUp/PageDown moves the tab itself.
        if (e.isShiftDown() && (e.getCode() == KeyCode.PAGE_DOWN || e.getCode() == KeyCode.PAGE_UP)) {
            moveDocument(current.get(), documents.indexOf(current.get()) + (e.getCode() == KeyCode.PAGE_DOWN ? 1 : -1));
            e.consume();
            return;
        }
        int by = switch (e.getCode()) {
            case TAB -> e.isShiftDown() ? -1 : 1;
            case PAGE_DOWN -> 1;
            case PAGE_UP -> -1;
            default -> 0;
        };
        if (by == 0) return;
        int at = documents.indexOf(current.get());
        selectDocument(documents.get(Math.floorMod(at + by, documents.size())));
        e.consume();
    }

    /** The selected document's view. */
    public PdfView getView() {
        return pane().getView();
    }

    /** The selected document's file: open, save, close. */
    public DocumentSession getSession() {
        return session();
    }

    // ---- documents --------------------------------------------------------------------------------

    /** The open documents, in tab order.  Change it through {@link #addDocument} and {@link #removeDocument}. */
    public ObservableList<DocumentPane> getDocuments() {
        return FXCollections.unmodifiableObservableList(documents);
    }

    /** The selected document. */
    public ReadOnlyObjectProperty<DocumentPane> currentDocumentProperty() {
        return current;
    }

    public DocumentPane getCurrentDocument() {
        return current.get();
    }

    /** Adds an empty document after the selected one and selects it. */
    public DocumentPane addDocument() {
        DocumentPane added = newPane();
        documents.add(documents.indexOf(current.get()) + 1, added);
        selectDocument(added);
        return added;
    }

    /** Adds a document that already exists (one moved from another window) and selects it. */
    public void addDocument(DocumentPane pane, int index) {
        wire(pane);
        documents.add(Math.max(0, Math.min(index, documents.size())), pane);
        selectDocument(pane);
    }

    /**
     * Shows another document: the page, the panels, and the header, rail and menus follow it.
     * Leaves full screen first.
     */
    public void selectDocument(DocumentPane pane) {
        if (pane == null || pane == current.get() || !documents.contains(pane)) return;
        if (presenting != null) fullScreen.set(false);
        current.set(pane);
        currentView.set(pane.getView());
        shell.setCenter(pane.getNode());
        for (SidePanel panel : SidePanel.values()) {
            if (features.allows(panel)) utility.retarget(panel, panelNode(pane, panel), panelAvailable(pane, panel));
        }
        actions.refresh();
        updateTitle();
        pane.getView().requestFocus();
    }

    /** Moves a document to {@code index} among the others (tab order); the selection stays. */
    public void moveDocument(DocumentPane pane, int index) {
        int from = documents.indexOf(pane);
        if (from < 0) return;
        int to = Math.max(0, Math.min(index, documents.size() - 1));
        if (to == from) return;
        documents.remove(from);
        documents.add(to, pane);
    }

    /**
     * Closes a document, asking about unsaved changes first; the next one is selected.  The last
     * one is not removed but emptied.
     *
     * @return false if the user kept it
     */
    public boolean removeDocument(DocumentPane pane) {
        if (!documents.contains(pane)) return false;
        // the prompt is about the selected document: show the one being asked about.
        if (pane.getSession().hasUnsavedChanges()) selectDocument(pane);
        if (!pane.getSession().confirmDiscard()) return false;
        if (documents.size() == 1) {
            pane.getSession().close();
            pane.refreshTitle();
            return true;
        }
        int at = documents.indexOf(pane);
        if (pane == current.get()) selectDocument(documents.get(at > 0 ? at - 1 : 1));
        documents.remove(pane);
        pane.dispose();
        return true;
    }

    /**
     * Takes a document out without closing it, to put in another window.  The last one can leave
     * too: this viewer is left with an empty document and {@link #setOnEmptied} is told.
     */
    public boolean detachDocument(DocumentPane pane) {
        if (!documents.contains(pane)) return false;
        boolean last = documents.size() == 1;
        if (last) {
            DocumentPane placeholder = newPane();
            documents.add(placeholder);
        }
        int at = documents.indexOf(pane);
        if (pane == current.get()) selectDocument(documents.get(at > 0 ? at - 1 : at + 1));
        documents.remove(pane);
        pane.setOnMessage(null);
        pane.setOnLink(null);
        pane.getSession().setOnError(null);
        pane.getSession().setOnLoaded(null);
        if (last && onEmptied != null) onEmptied.run();
        return true;
    }

    /**
     * Takes a document from another window in at {@code index}; an empty document this viewer only
     * has as a placeholder gives way to it.
     */
    public void adoptDocument(DocumentPane pane, int index) {
        DocumentPane placeholder = documents.size() == 1 && documents.get(0).getSession().getDocument() == null
                ? documents.get(0) : null;
        addDocument(pane, index);
        if (placeholder != null) {
            documents.remove(placeholder);
            placeholder.dispose();
        }
    }

    /** What the application does with a document pulled out to a window of its own; at is the screen position or null. */
    @FunctionalInterface
    public interface TearOff {
        void tearOff(DocumentPane pane, javafx.geometry.Point2D at);
    }

    /** Offers "Move to New Window" and dropping a tab outside the window; the application opens the window. */
    public void setOnTearOff(TearOff onTearOff) {
        this.onTearOff = onTearOff;
    }

    /** Called when the last document has been moved to another window: this one has nothing left to show. */
    public void setOnEmptied(Runnable onEmptied) {
        this.onEmptied = onEmptied;
    }

    private void tearOffRequested(DocumentPane pane, javafx.geometry.Point2D at) {
        if (onTearOff == null || documents.size() < 2 || !detachDocument(pane)) return;
        onTearOff.tearOff(pane, at);
    }

    /** Saves the selected document in place, or asks for a name when it can't be. @return true if saved */
    public boolean save() {
        if (session().getDocument() == null) return false;
        if (!session().canSaveInPlace()) return saveAs();
        boolean saved = session().save();
        if (saved) showMessage("Saved");
        return saved;
    }

    /** Saves the selected document under a name the user picks. @return true if saved */
    public boolean saveAs() {
        if (session().getDocument() == null) return false;
        FileChooser chooser = context.chooser("Save As");
        Path file = session().getFile();
        chooser.setInitialFileName(file != null ? file.getFileName().toString() : "document.pdf");
        File chosen = chooser.showSaveDialog(context.window());
        if (chosen == null) return false;
        if (history != null && chosen.getParentFile() != null) history.setLastDirectory(chosen.getParentFile().toPath());
        if (!session().saveAs(chosen.toPath())) return false;
        showMessage("Saved " + session().getFile().getFileName());
        return true;
    }

    private DocumentPane pane() {
        return current.get();
    }

    private PdfView view() {
        return current.get().getView();
    }

    private DocumentSession session() {
        return current.get().getSession();
    }

    private DocumentPane newPane() {
        DocumentPane pane = new DocumentPane();
        wire(pane);
        if (onDocumentCreated != null) onDocumentCreated.accept(pane);
        return pane;
    }

    /** Connects a document to this window's messages, errors, owner and discard rule. */
    private void wire(DocumentPane pane) {
        DocumentSession session = pane.getSession();
        session.setOnError(message -> onError.accept(message));
        session.setOnLoaded(file -> loaded(pane));
        session.setOwner(owner);
        if (confirmDiscard != null) session.setConfirmDiscard(confirmDiscard);
        pane.setOnMessage(this::showMessage);
        pane.setOnLink(onLink);
        pane.getAttachments().setOnOpen(this::openAttachment);
        pane.getView().setAnnotationMenuFactory(annotation -> AnnotationContextMenu.create(pane.getView(), annotation,
                colorHistory, target -> AnnotationPropertiesDialog.show(getScene() != null ? getScene().getWindow() : null, target)
                .ifPresent(change -> pane.getView().applyAnnotationProperties(target, change))));
        pane.titleProperty().addListener((o, a, b) -> {
            if (pane == current.get()) updateTitle();
        });
    }

    /** Opens an embedded PDF in a new tab, from a temporary copy; the panel saves anything else. */
    private void openAttachment(org.icepdf.fx.panels.Attachment attachment) {
        try {
            Path dir = java.nio.file.Files.createTempDirectory("icepdf-attachment");
            Path file = dir.resolve(java.nio.file.Paths.get(attachment.name()).getFileName().toString());
            attachment.saveTo(file);
            file.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            openInNewTab(file);
        } catch (java.io.IOException | java.nio.file.InvalidPathException e) {
            onError.accept("Could not open " + attachment.name() + ": " + e.getMessage());
        }
    }

    private static String panelLabel(SidePanel panel) {
        return switch (panel) {
            case THUMBNAILS -> "Pages";
            case BOOKMARKS -> "Bookmarks";
            case COMMENTS -> "Comments";
            case ATTACHMENTS -> "Attachments";
            case LAYERS -> "Layers";
            case SIGNATURES -> "Signatures";
            case SEARCH -> "Search";
        };
    }

    private static javafx.scene.Node panelNode(DocumentPane pane, SidePanel panel) {
        return switch (panel) {
            case THUMBNAILS -> pane.getThumbnails();
            case BOOKMARKS -> pane.getOutline();
            case COMMENTS -> pane.getComments();
            case ATTACHMENTS -> pane.getAttachments();
            case LAYERS -> pane.getLayers();
            case SIGNATURES -> pane.getSignatures();
            case SEARCH -> pane.getSearchPanel();
        };
    }

    private static ObservableBooleanValue panelAvailable(DocumentPane pane, SidePanel panel) {
        return switch (panel) {
            case BOOKMARKS -> pane.getOutline().hasOutlineProperty();
            case COMMENTS -> pane.getComments().hasCommentsProperty();
            case ATTACHMENTS -> pane.getAttachments().hasAttachmentsProperty();
            case LAYERS -> pane.getLayers().hasLayersProperty();
            case SIGNATURES -> pane.getSignatures().hasSignaturesBinding();
            default -> pane.getView().documentProperty().isNotNull();
        };
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
        refreshMenu();
    }

    /** Offers "Exit" (app.exit). */
    public void setOnExit(Runnable onExit) {
        this.onExit = onExit;
        refreshMenu();
    }

    /** Offers "Preferences" (app.preferences). */
    public void setOnPreferences(Runnable onPreferences) {
        this.onPreferences = onPreferences;
        refreshMenu();
    }

    /** Remembers the colours the user picks for annotations (shown in the annotation context menu). */
    public void setColorHistory(ColorHistory colorHistory) {
        this.colorHistory = colorHistory != null ? colorHistory : ColorHistory.inMemory();
    }

    /** Remembers recent files and the chooser's folder; shows "Open Recent" in the menu. */
    public void setFileHistory(FileHistory history) {
        this.history = history;
        recentMenu.setVisible(history != null);
    }

    /** Receives the URI of a link the user follows (to open in a browser); otherwise it's shown as a message. */
    public void setOnLink(Consumer<String> onLink) {
        this.onLink = onLink;
        documents.forEach(d -> d.setOnLink(onLink));
    }

    /**
     * Called for every document this viewer holds, now and as they're added: the place to apply the
     * user's preferences to its view and panels.
     */
    public void setOnDocumentCreated(Consumer<DocumentPane> onDocumentCreated) {
        this.onDocumentCreated = onDocumentCreated;
        if (onDocumentCreated != null) documents.forEach(onDocumentCreated);
    }

    /** Builds the primary menu again: an item is left out while its capability (new window, exit, preferences) is unset. */
    private void refreshMenu() {
        java.util.List<javafx.scene.control.MenuItem> items = new java.util.ArrayList<>(actions.items(MENU));
        items.add(0, recentMenu);
        header.getMenu().getItems().setAll(items);
    }

    private static javafx.scene.input.KeyCombination recentKey(int number) {
        return new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.valueOf("DIGIT" + number),
                javafx.scene.input.KeyCombination.SHORTCUT_DOWN);
    }

    private void openRecentAt(int index) {
        java.util.List<Path> recent = history != null ? history.recent() : java.util.List.of();
        if (index < recent.size()) open(recent.get(index));
    }

    private void rebuildRecentMenu() {
        recentMenu.getItems().clear();
        java.util.List<Path> recent = history != null ? history.recent() : java.util.List.of();
        for (int i = 0; i < recent.size(); i++) {
            Path path = recent.get(i);
            javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(path.getFileName().toString());
            item.setMnemonicParsing(false);
            if (i < 9) item.setAccelerator(recentKey(i + 1)); // shown beside the name; the scene handles the key
            item.setOnAction(e -> open(path));
            recentMenu.getItems().add(item);
        }
        if (recent.isEmpty()) {
            javafx.scene.control.MenuItem none = new javafx.scene.control.MenuItem("No recent files");
            none.setDisable(true);
            recentMenu.getItems().add(none);
        } else {
            javafx.scene.control.MenuItem clear = new javafx.scene.control.MenuItem("Clear Recent Files");
            clear.setOnAction(e -> history.clearRecent());
            recentMenu.getItems().addAll(new javafx.scene.control.SeparatorMenuItem(), clear);
        }
    }

    /** True if any document has unsaved changes. */
    public boolean hasUnsavedChanges() {
        return documents.stream().anyMatch(d -> d.getSession().hasUnsavedChanges());
    }

    /**
     * Asks about each document with unsaved changes (showing it first), for closing the window.
     *
     * @return false if the user cancelled
     */
    public boolean confirmClose() {
        for (DocumentPane pane : new java.util.ArrayList<>(documents)) {
            if (!pane.getSession().hasUnsavedChanges()) continue;
            selectDocument(pane);
            if (!pane.getSession().confirmDiscard()) return false;
        }
        return true;
    }

    /** Receives errors (could not open, could not save); a message toast by default. */
    public void setOnError(Consumer<String> onError) {
        this.onError = onError != null ? onError : this::showMessage;
    }

    /** Asked before unsaved changes are dropped; false to stay.  By default they're dropped. */
    public void setConfirmDiscard(java.util.function.BooleanSupplier confirm) {
        confirmDiscard = confirm;
        documents.forEach(d -> d.getSession().setConfirmDiscard(confirm));
    }

    public BooleanProperty fullScreenProperty() {
        return fullScreen;
    }

    /** Closes the document without asking and stops background work. */
    public void dispose() {
        if (presenting != null) exitFullScreen();
        documents.forEach(DocumentPane::dispose);
        theme.dispose();
        saveLayoutLater.stop();
        layout.removeListener(layoutListener);
        rail.dispose();
        layout.save();
    }

    // ---- layout ---------------------------------------------------------------------------------

    private final Runnable layoutListener = this::layoutChanged;

    private void layoutChanged() {
        buildDrawers();
        utility.getDrawer().hoverOpenProperty().set(layout.isPanelHoverOpen());
        saveLayoutLater.playFromStart();
    }

    /** Opens the Customise Tools dialog; OK changes the layout, and so every viewer sharing it. */
    public void customiseTools() {
        if (!features.isUserCustomisable()) return;
        CustomiseToolsPane.showDialog(getScene() != null ? getScene().getWindow() : null, this, actions, features,
                layout);
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

    private void loaded(DocumentPane pane) {
        pane.refreshTitle();
        if (history != null && pane.getSession().getFile() != null) history.opened(pane.getSession().getFile());
        if (pane != current.get()) return;
        updateTitle();
        // the document's /PageMode picks the panel it opens with, as Acrobat does.
        Name mode = pane.getSession().getDocument().getCatalog().getPageMode();
        SidePanel wanted = Catalog.PAGE_MODE_USE_OUTLINES_VALUE.equals(mode) ? SidePanel.BOOKMARKS
                : Catalog.PAGE_MODE_USE_THUMBS_VALUE.equals(mode) ? SidePanel.THUMBNAILS
                : Catalog.PAGE_MODE_USE_ATTACHMENTS_VALUE.equals(mode) ? SidePanel.ATTACHMENTS
                : Catalog.PAGE_MODE_OPTIONAL_CONTENT_VALUE.equals(mode) ? SidePanel.LAYERS : null;
        if (wanted != null) javafx.application.Platform.runLater(() -> utility.open(wanted));
    }

    private void updateTitle() {
        header.setTitle(pane().getTitle());
    }

    // ---- full screen ----------------------------------------------------------------------------

    private record Presenting(ViewMode viewMode, FitMode fitMode, double zoom, EventHandler<KeyEvent> keys) {
    }

    private void enterFullScreen() {
        if (presenting != null || view().getDocument() == null) return;
        EventHandler<KeyEvent> keys = e -> {
            if (e.getTarget() instanceof javafx.scene.control.TextInputControl) return;
            switch (e.getCode()) {
                case RIGHT, DOWN, PAGE_DOWN, ENTER, N -> view().nextPage();
                case SPACE -> {
                    if (e.isShiftDown()) view().previousPage();
                    else view().nextPage();
                }
                case LEFT, UP, PAGE_UP, BACK_SPACE, P -> view().previousPage();
                case HOME -> view().setCurrentPageIndex(0);
                case END -> view().setCurrentPageIndex(view().getPageCount() - 1);
                case ESCAPE, F11 -> fullScreen.set(false);
                default -> {
                    return;
                }
            }
            e.consume();
        };
        presenting = new Presenting(view().getViewMode(), view().getFitMode(), view().getZoom(), keys);
        shell.closeOverlays();
        shell.setChromeVisible(false);
        getStyleClass().add("presenting");
        view().clearAnnotationSelection();
        view().setViewMode(ViewMode.SINGLE_PAGE);
        view().setFitMode(FitMode.PAGE);
        addEventFilter(KeyEvent.KEY_PRESSED, keys);
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            stage.setFullScreenExitHint("Press Esc to leave full screen");
            stage.setFullScreen(true);
            stage.fullScreenProperty().addListener(stageFullScreen);
        }
        view().requestFocus();
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
        view().setViewMode(was.viewMode());
        view().setFitMode(was.fitMode());
        if (was.fitMode() == FitMode.NONE) view().setZoom(was.zoom());
        view().requestFocus();
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
            return PdfViewer.this.view();
        }

        @Override
        public Window window() {
            return getScene() != null ? getScene().getWindow() : null;
        }

        @Override
        public boolean has(Capability capability) {
            return switch (capability) {
                case NEW_TAB -> features.isMultipleDocuments();
                case NEW_WINDOW -> onNewWindow != null;
                case EXIT -> onExit != null;
                case PREFERENCES -> onPreferences != null;
                default -> true;
            };
        }

        @Override
        public void openDocument() {
            File chosen = chooser("Open").showOpenDialog(window());
            if (chosen != null) {
                rememberFolder(chosen);
                open(chosen.toPath());
            }
        }

        @Override
        public void openDocumentInNewTab() {
            File chosen = chooser("Open in New Tab").showOpenDialog(window());
            if (chosen != null) {
                rememberFolder(chosen);
                openInNewTab(chosen.toPath());
            }
        }

        private void rememberFolder(File chosen) {
            if (history != null && chosen.getParentFile() != null) history.setLastDirectory(chosen.getParentFile().toPath());
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
        public void newTab() {
            PdfViewer.this.newTab();
        }

        @Override
        public void closeDocument() {
            if (features.isMultipleDocuments() && documents.size() > 1) {
                removeDocument(pane());
                return;
            }
            session().close();
            pane().refreshTitle();
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
                case "comments" -> panelAvailable(pane(), SidePanel.COMMENTS);
                case "bookmarks" -> panelAvailable(pane(), SidePanel.BOOKMARKS);
                case "attachments" -> panelAvailable(pane(), SidePanel.ATTACHMENTS);
                case "layers" -> panelAvailable(pane(), SidePanel.LAYERS);
                case "signatures" -> panelAvailable(pane(), SidePanel.SIGNATURES);
                default -> PdfViewer.this.view().documentProperty().isNotNull();
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
            pane().getSearchPanel().focusQuery();
        }

        @Override
        public void printStarted(Task<Void> task) {
            task.setOnSucceeded(e -> showMessage("Printed"));
            task.setOnFailed(e -> onError.accept("Printing failed: " + task.getException().getMessage()));
        }

        @Override
        public void customisePrintDialog(PdfPrintDialog dialog) {
        }

        FileChooser chooser(String title) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF documents", "*.pdf", "*.PDF"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            Path file = session().getFile();
            Path folder = history != null ? history.lastDirectory() : null;
            if (folder == null && file != null) folder = file.getParent();
            if (folder != null && java.nio.file.Files.isDirectory(folder)) chooser.setInitialDirectory(folder.toFile());
            return chooser;
        }
    }
}
