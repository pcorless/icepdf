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

import org.icepdf.fx.i18n.Messages;

import javafx.animation.PauseTransition;
import javafx.beans.property.*;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Catalog;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Name;
import org.icepdf.fx.print.PdfPrintDialog;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.ri.actions.ActionRegistry;
import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.ri.document.DocumentPane;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.ri.document.SignatureFlow;
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
            "document.open-new-tab", "document.new-tab", "document.save-as", "document.export-redacted", "document.print", null, "document.properties", null,
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
    private final Toast toast;
    private final javafx.scene.control.Label dropHint = new javafx.scene.control.Label();
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
    // what the annotation menus are given: the history set now, whenever it's set.
    private final ColorHistory colors = new ColorHistory() {
        @Override
        public java.util.List<java.awt.Color> recent() {
            return colorHistory.recent();
        }

        @Override
        public void used(java.awt.Color color) {
            colorHistory.used(color);
        }
    };
    private SigningStore signingStore = SigningStore.inMemory();
    private PrintStore printStore = PrintStore.inMemory();
    private java.util.List<javafx.scene.input.KeyCombination> actionKeys = java.util.List.of();
    private final javafx.scene.control.Menu recentMenu = new javafx.scene.control.Menu(Messages.get("viewer.openRecent"));
    private Consumer<String> onLink;
    private TearOff onTearOff;
    private Runnable onEmptied;
    private Consumer<DocumentPane> onDocumentCreated;
    private java.util.function.BooleanSupplier confirmDiscard;
    private javafx.stage.Window owner;
    private Consumer<String> onError = this::showError;
    private ViewerDialogs dialogs = ViewerDialogs.SYSTEM;
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
        toast = new Toast(icons);
        getStyleClass().add("pdf-viewer");
        theme = new Theme(this);
        // the render devtools window, on the browser devtools key; harmless to leave on, and where
        // it is needed when debugging a user's machine.
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (DevToolsWindow.KEY.match(e)) {
                DevToolsWindow.toggle(theme);
                e.consume();
            }
        });
        // an Esc nothing else used (a field editor, a selection, an overlay, the search box) dismisses the error card.
        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && !toast.getError().isEmpty()) {
                toast.dismissError();
                e.consume();
            }
        });
        actions = new ActionControls(registry, context, features::allows).setIcons(icons, 20, 16);

        sceneProperty().addListener((o, a, scene) -> {
            owner = scene != null ? scene.getWindow() : null;
            documents.forEach(d -> d.getSession().setOwner(owner));
            if (a != null) {
                for (int i = 1; i <= 9; i++) a.getAccelerators().remove(recentKey(i));
                actionKeys.forEach(a.getAccelerators()::remove);
            }
            actionKeys = java.util.List.of();
            if (scene != null) {
                // the header button and the drop-down menu are not a menu bar: their shortcuts (Ctrl+O...) go on the
                // scene, with those of the header's and the rail's buttons.  Copy, select all and undo are the
                // view's own keys; zoom and fit are too, but go here as well for when the view hasn't the focus.
                java.util.List<String> ids = new java.util.ArrayList<>(java.util.Arrays.asList(MENU));
                ids.addAll(java.util.List.of("document.open", "document.save", "search.find", "search.next",
                        "search.previous", "search.panel", "view.side-panel", "navigation.go-to", "view.comments",
                        "view.fit-page", "view.fit-width", "view.actual-size", "view.zoom-in", "view.zoom-out"));
                actionKeys = actions.installAccelerators(scene, ids.toArray(new String[0]));
                // Shortcut+Shift+1..9 open the nth recent file, read when pressed (the menu is built only when shown).
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
            strip.setOnFilesDropped(PdfViewer::hasFiles, (pane, board) -> {
                java.util.List<Path> pdfs = droppedPdfs(board.getFiles());
                if (pdfs.isEmpty()) return;
                javafx.application.Platform.runLater(() -> {
                    replaceOrAdd(pdfs.get(0), pane);
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
        utility.setOnActivated(panel -> {
            if (panel == SidePanel.SEARCH) pane().getSearchPanel().focusQuery();
        });
        utility.setOnClosedWithFocus(() -> view().requestFocus());
        if (features.shows(Chrome.HEADER) && actions.isAvailable("search.find")) followSearchVisibility();

        if (features.shows(Chrome.HEADER)) shell.setHeader(header);
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
     * open in another tab shows that tab instead.  The file is read in the background.
     * @return false if the user kept the current document
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
     * already shows its tab.  Without tabs it replaces the document.  The file is read in the
     * background; a tab it can't open is taken away again.  @return false if the user kept the current document
     */
    public boolean openInNewTab(Path file) {
        if (!features.isMultipleDocuments()) return session().open(file);
        DocumentPane existing = paneShowing(file);
        if (existing != null) {
            selectDocument(existing);
            return true;
        }
        if (pane().getSession().getDocument() == null && !pane().getSession().isLoading()) return session().open(file);
        // taken away again if the file doesn't open.
        DocumentPane added = addDocument();
        return added.getSession().open(file, opened -> {
            if (!opened && added.getSession().getDocument() == null && documents.contains(added)) removeDocument(added);
        });
    }

    private DocumentPane paneShowing(Path file) {
        Path real = real(file);
        for (DocumentPane pane : documents) {
            Path open = pane.getSession().getFile();
            Path loading = pane.getSession().getLoadingFile();
            if (open != null && real(open).equals(real) || loading != null && real(loading).equals(real)) return pane;
        }
        return null;
    }

    /** The dropped files that are PDFs; the rest are named in a message. */
    java.util.List<Path> droppedPdfs(java.util.List<File> files) {
        java.util.List<Path> pdfs = new java.util.ArrayList<>();
        java.util.List<String> others = new java.util.ArrayList<>();
        for (File f : files != null ? files : java.util.List.<File>of()) {
            if (isPdf(f)) pdfs.add(f.toPath());
            else if (f.isFile()) others.add(f.getName());
        }
        if (!others.isEmpty()) {
            showMessage(others.size() == 1 ? Messages.format("viewer.notPdf", others.get(0))
                    : Messages.format("viewer.notPdfs", others.size()));
        }
        return pdfs;
    }

    private static boolean hasFiles(javafx.scene.input.Dragboard board) {
        return board != null && board.hasFiles() && board.getFiles().stream().anyMatch(File::isFile);
    }

    /** True for a ".pdf" name, or a file that starts like one (a PDF saved without the extension). */
    static boolean isPdf(File file) {
        if (!file.isFile()) return false;
        if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) return true;
        // the header may follow some junk; readers look in the first 1024 bytes (ISO 32000-1, annex H).
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(file.toPath())) {
            String head = new String(in.readNBytes(1024), java.nio.charset.StandardCharsets.ISO_8859_1);
            return head.contains("%PDF-");
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Where dropped PDFs go.  On the document: the first replaces it, or opens in a new tab when the
     * document has unsaved changes.  On a tab: the same for that tab's document (see the tab strip).
     * Anywhere else on the header (the bar, the + button): new tabs.  Without tabs every drop
     * replaces the document, asking about unsaved changes.  While a drag is over the viewer a hint
     * says which it will be.
     */
    private void acceptDroppedFiles() {
        dropHint.getStyleClass().add("drop-hint");
        dropHint.setMouseTransparent(true);
        dropHint.setVisible(false);
        getChildren().add(dropHint);
        // any file is taken: one that isn't a PDF is then turned away with a message, not silently.
        setOnDragOver(e -> {
            if (hasFiles(e.getDragboard())) e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            e.consume();
        });
        // a filter, so the hint follows the drag over the tabs too (they take the drag event).
        addEventFilter(javafx.scene.input.DragEvent.DRAG_OVER, e -> showDropHint(hasFiles(e.getDragboard())
                ? dropHintText(e.getPickResult().getIntersectedNode()) : null));
        addEventFilter(javafx.scene.input.DragEvent.DRAG_DROPPED, e -> showDropHint(null));
        setOnDragExited(e -> showDropHint(null));
        setOnDragDropped(e -> {
            java.util.List<File> files = hasFiles(e.getDragboard()) ? e.getDragboard().getFiles() : java.util.List.of();
            boolean any = inHeader(e.getPickResult().getIntersectedNode()) ? filesDroppedOnTabBar(files) : filesDropped(files);
            e.setDropCompleted(any);
            e.consume();
        });
    }

    /**
     * Search hits are highlighted while the results are on screen - the header's search bar or the
     * search side panel - and hidden while neither is, as a way to put the highlighting away
     * without losing the results.  Moving to another hit (F3) while they're hidden brings the bar
     * back, so the move isn't to a hit nobody can see.
     */
    private void followSearchVisibility() {
        javafx.beans.binding.BooleanBinding shown = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> header.searchBarShown().get() || utility.getDrawer().isOpen() && utility.getShown() == SidePanel.SEARCH,
                header.searchBarShown(), utility.getDrawer().stateProperty(), utility.shownProperty());
        Runnable apply = () -> {
            PdfView v = currentView.get();
            if (v != null) v.setShowSearchHits(shown.get());
        };
        shown.addListener((o, was, now) -> apply.run());
        javafx.beans.value.ChangeListener<Number> revealOnMove = (o, was, now) -> {
            if (now.intValue() >= 0 && !shown.get()) header.showSearch();
        };
        currentView.addListener((o, was, now) -> {
            if (was != null) was.currentSearchHitIndexProperty().removeListener(revealOnMove);
            if (now != null) now.currentSearchHitIndexProperty().addListener(revealOnMove);
            apply.run();
        });
        if (currentView.get() != null) currentView.get().currentSearchHitIndexProperty().addListener(revealOnMove);
        apply.run();
    }

    /** Files dropped on the document: the first PDF replaces it unless it has unsaved changes (after the drop ends), the rest open in new tabs and the non-PDFs are named.  @return true if any is a PDF */
    boolean filesDropped(java.util.List<File> files) {
        java.util.List<Path> pdfs = droppedPdfs(files);
        if (pdfs.isEmpty()) return false;
        javafx.application.Platform.runLater(() -> {
            replaceOrAdd(pdfs.get(0), pane());
            pdfs.stream().skip(1).forEach(this::openInNewTab);
        });
        return true;
    }

    /** Files dropped on the header, off the tabs: the PDFs open in new tabs.  @return true if any is a PDF */
    boolean filesDroppedOnTabBar(java.util.List<File> files) {
        java.util.List<Path> pdfs = droppedPdfs(files);
        javafx.application.Platform.runLater(() -> pdfs.forEach(this::openInNewTab));
        return !pdfs.isEmpty();
    }

    /** Opens a file in {@code target}, or in a new tab when that has unsaved changes (or is busy). */
    private void replaceOrAdd(Path file, DocumentPane target) {
        if (!features.isMultipleDocuments()) {
            session().open(file);
            return;
        }
        DocumentSession session = target.getSession();
        if (session.isModified() || session.isBusy()) openInNewTab(file);
        else open(file, target);
    }

    /** What a drop on {@code node} would do, or null where the drop shows its own target (a tab). */
    private String dropHintText(Node node) {
        if (!features.isMultipleDocuments()) return Messages.get("viewer.drop.replace");
        for (Node n = node; n != null && n != this; n = n.getParent()) {
            if (n.getStyleClass().contains("doc-tab")) return null;
        }
        if (inHeader(node)) return Messages.get("viewer.drop.newTab");
        DocumentSession session = session();
        return session.isModified() || session.isBusy() ? Messages.get("viewer.drop.newTab")
                : Messages.get("viewer.drop.replace");
    }

    private boolean inHeader(Node node) {
        if (!features.isMultipleDocuments()) return false;
        for (Node n = node; n != null && n != this; n = n.getParent()) {
            if (n == header) return true;
        }
        return false;
    }

    private void showDropHint(String text) {
        dropHint.setVisible(text != null);
        if (text != null) dropHint.setText(text);
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
            if (pane.getSession().getDocument() == null && !pane.getSession().isLoading()) {
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
        if (pane.getSession().isBusy()) {
            selectDocument(pane);
            pane.getSession().reportBusy();
            return false;
        }
        // the prompt is about the selected document: show the one being asked about.
        if (pane.getSession().isModified()) selectDocument(pane);
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
        pane.setOnOpenFile(null);
        pane.setOnExportRedacted(null);
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
                && !documents.get(0).getSession().isLoading()
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
        if (session().getDocument() == null || session().reportBusy()) return false;
        if (!redactionsSettled()) return false;
        if (!session().canSaveInPlace()) return saveAsChecked();
        boolean saved = session().save();
        if (saved) {
            // the save worked: an earlier failure's message no longer applies.
            toast.dismissError();
            showMessage(Messages.get("viewer.saved"));
        }
        return saved;
    }

    /** Saves the selected document under a name the user picks. @return true if saved */
    public boolean saveAs() {
        if (session().getDocument() == null || session().reportBusy()) return false;
        if (!redactionsSettled()) return false;
        return saveAsChecked();
    }

    private boolean saveAsChecked() {
        FileChooser chooser = context.chooser(Messages.get("viewer.saveAsTitle"));
        Path file = session().getFile();
        chooser.setInitialFileName(file != null ? file.getFileName().toString() : "document.pdf");
        File chosen = dialogs.showSaveDialog(chooser, context.window());
        if (chosen == null) return false;
        if (history != null && chosen.getParentFile() != null) history.setLastDirectory(chosen.getParentFile().toPath());
        Path target = writableTarget(PdfTargets.confirm(dialogs, context.window(), chosen));
        if (target == null || !session().saveAs(target)) return false;
        Path saved = session().getLoadingFile();
        toast.dismissError();
        showMessage(Messages.format("viewer.savedFile", (saved != null ? saved : target).getFileName()));
        return true;
    }

    /** Signing in a document: the sign dialog, then a name for the signed copy, which replaces the document. */
    private SignatureFlow signatureFlow(DocumentPane pane) {
        return new SignatureFlow(pane.getSession()).setSigningStore(signingStore).setOnMessage(this::showMessage)
                .setOnError(message -> onError.accept(message)).setTargetChooser((window, suggested) -> {
                    FileChooser chooser = context.chooser(Messages.get("viewer.saveSignedTitle"));
                    chooser.setInitialFileName(suggested.getFileName().toString());
                    File target = dialogs.showSaveDialog(chooser, window);
                    if (target == null) return null;
                    if (history != null && target.getParentFile() != null) history.setLastDirectory(target.getParentFile().toPath());
                    return writableTarget(PdfTargets.confirm(dialogs, window, target));
                });
    }

    /**
     * Before a plain save: a document with redactions marked but not burned in gets a choice, since an
     * incremental save keeps the content under them in the file.  Export Redacted Copy starts the
     * export instead.  @return true to go on with the save
     */
    private boolean redactionsSettled() {
        if (!session().hasRedactions()) return true;
        javafx.scene.control.ButtonType export = new javafx.scene.control.ButtonType(Messages.get("viewer.exportRedacted"),
                javafx.scene.control.ButtonBar.ButtonData.YES);
        javafx.scene.control.ButtonType keep = new javafx.scene.control.ButtonType(Messages.get("viewer.saveWithoutRedacting"),
                javafx.scene.control.ButtonBar.ButtonData.NO);
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                Messages.get("viewer.redactionsPending"),
                export, keep, javafx.scene.control.ButtonType.CANCEL);
        alert.setHeaderText(Messages.get("viewer.redactionsNotApplied"));
        alert.initOwner(context.window());
        javafx.scene.control.ButtonType choice = dialogs.showAndWait(alert).orElse(javafx.scene.control.ButtonType.CANCEL);
        if (choice == export) exportRedacted();
        return choice == keep;
    }

    /**
     * Writes a copy with the redactions burned in (the marked content removed), opens it in place of
     * the marked document and reports what was removed.
     */
    public void exportRedacted() {
        DocumentPane pane = pane();
        DocumentSession session = pane.getSession();
        if (session.getDocument() == null || session.reportBusy()) return;
        FileChooser chooser = context.chooser(Messages.get("viewer.exportRedactedTitle"));
        Path file = session.getFile();
        String base = file != null ? file.getFileName().toString().replaceFirst("(?i)\\.pdf$", "") : "document";
        chooser.setInitialFileName(base + "-redacted.pdf");
        File chosen = dialogs.showSaveDialog(chooser, context.window());
        if (chosen == null) return;
        if (history != null && chosen.getParentFile() != null) history.setLastDirectory(chosen.getParentFile().toPath());
        Path target = writableTarget(PdfTargets.confirm(dialogs, context.window(), chosen));
        if (target == null) return;
        showMessage(Messages.get("viewer.redacting"));
        session.exportRedacted(target, report -> {
            if (report != null) showRedactionReport(target.getFileName().toString(), report);
        });
    }

    private void showRedactionReport(String name, org.icepdf.core.util.redaction.RedactionReport report) {
        StringBuilder text = new StringBuilder();
        text.append(Messages.format("viewer.report.textRemoved", report.getGlyphsRemoved())).append('\n');
        text.append(Messages.format("viewer.report.imagesRedacted", report.getImagesBurned())).append('\n');
        if (report.getStringsRewritten() > 0) {
            text.append(Messages.format("viewer.report.otherMasked", report.getStringsRewritten())).append('\n');
        }
        org.icepdf.core.util.redaction.RedactionConfidence confidence = report.getConfidence();
        text.append('\n').append(Messages.format("viewer.report.check", Messages.get(switch (confidence) {
            case FAILED -> "viewer.report.failed";
            case UNVERIFIED -> "viewer.report.unverified";
            case VERIFIED_WITH_WARNINGS -> "viewer.report.warnings";
            default -> "viewer.report.verified";
        })));
        // what could not be checked (why "unverified"), then the warnings.
        java.util.List<String> details = new java.util.ArrayList<>();
        report.getUnverifiableRegions().forEach(region -> details.add(region.getDetail()));
        report.getWarnings().forEach(warning -> details.add(warning.getDetail()));
        text.append(detailsSummary(details));
        boolean clean = confidence == org.icepdf.core.util.redaction.RedactionConfidence.VERIFIED
                && report.getWarnings().isEmpty();
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(clean
                ? javafx.scene.control.Alert.AlertType.INFORMATION : javafx.scene.control.Alert.AlertType.WARNING,
                text.toString(), javafx.scene.control.ButtonType.OK);
        alert.setHeaderText(Messages.format("viewer.redactedSavedAs", name));
        if (details.size() > REPORT_DETAILS) {
            // the whole list, scrolling, under Show Details: a dialog sized to hundreds of lines
            // outgrows the largest window the graphics card can draw.
            StringBuilder all = new StringBuilder();
            for (String detail : details) {
                all.append("\u2022 ").append(detail).append('\n');
            }
            javafx.scene.control.TextArea list = new javafx.scene.control.TextArea(all.toString());
            list.setEditable(false);
            list.setWrapText(true);
            list.setPrefRowCount(12);
            list.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            alert.getDialogPane().setExpandableContent(list);
        }
        alert.initOwner(context.window());
        dialogs.show(alert);
    }

    /** The redaction report lists this many reasons and warnings; the rest are under Show Details. */
    static final int REPORT_DETAILS = 5;

    /** The first few reasons and warnings as bullets, and how many more there are. */
    static String detailsSummary(java.util.List<String> details) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(REPORT_DETAILS, details.size()); i++) {
            text.append("\n\u2022 ").append(details.get(i));
        }
        if (details.size() > REPORT_DETAILS) {
            text.append('\n').append(Messages.format("viewer.report.moreWarnings", details.size() - REPORT_DETAILS));
        }
        return text.toString();
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
        pane.setOnOpenFile(this::openInNewTab);
        pane.getSearchPanel().setRedactionEnabled(features.allows("annotation.redact"));
        pane.setOnExportRedacted(() -> {
            if (pane == current.get()) exportRedacted();
        });
        pane.getAttachments().setOnOpen(this::openAttachment);
        pane.getAttachments().setOnError(message -> onError.accept(message));
        pane.getAttachments().setTargetChooser((window, name) -> {
            FileChooser chooser = context.chooser(Messages.get("attachmentPanel.saveTitle"));
            chooser.getExtensionFilters().clear();
            chooser.setInitialFileName(name);
            File chosen = dialogs.showSaveDialog(chooser, window);
            if (chosen == null) return null;
            if (history != null && chosen.getParentFile() != null) history.setLastDirectory(chosen.getParentFile().toPath());
            return chosen.toPath();
        });
        // a new flow per click: it picks up the signing store as it is then.
        pane.getView().setOnSignatureClicked(event -> signatureFlow(pane).handle(event));
        AnnotationContextMenu.install(pane.getView(), colors);
        pane.titleProperty().addListener((o, a, b) -> {
            if (pane == current.get()) updateTitle();
        });
    }

    /** Opens an embedded PDF in a new tab, from a temporary copy; the panel saves anything else. */
    private void openAttachment(org.icepdf.fx.panels.Attachment attachment) {
        // opened before: the same copy, so its tab is found again rather than a second one opened.
        Path copied = attachmentCopies.get(attachment);
        if (copied != null && java.nio.file.Files.isRegularFile(copied)) {
            openInNewTab(copied);
            return;
        }
        try {
            Path dir = java.nio.file.Files.createTempDirectory("icepdf-attachment");
            Path file = dir.resolve(java.nio.file.Paths.get(attachment.name()).getFileName().toString());
            attachment.saveTo(file);
            file.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            attachmentCopies.put(attachment, file);
            temporaryCopies.add(file.toAbsolutePath());
            openInNewTab(file);
        } catch (java.io.IOException | RuntimeException e) {
            onError.accept(Messages.format("viewer.attachmentOpenFailed", attachment.name(), Messages.reason(e)));
        }
    }

    // attachments opened from a document: their temporary copies, kept out of Open Recent.
    private final java.util.Map<org.icepdf.fx.panels.Attachment, Path> attachmentCopies = new java.util.HashMap<>();
    private final java.util.Set<Path> temporaryCopies = new java.util.HashSet<>();

    /**
     * A file to write a copy to (save as, export, a signed copy), unless it is open in another tab:
     * writing it would replace that document's file under it.
     *
     * @return null (after saying why) when it can't be used
     */
    private Path writableTarget(Path target) {
        if (target == null) return null;
        DocumentPane other = paneShowing(target);
        if (other != null && other != pane()) {
            onError.accept(Messages.format("viewer.openInAnotherTab", target.getFileName()));
            return null;
        }
        return target;
    }

    private static String panelLabel(SidePanel panel) {
        return switch (panel) {
            case THUMBNAILS -> Messages.get("viewer.panel.thumbnails");
            case BOOKMARKS -> Messages.get("viewer.panel.bookmarks");
            case COMMENTS -> Messages.get("viewer.panel.comments");
            case ATTACHMENTS -> Messages.get("viewer.panel.attachments");
            case LAYERS -> Messages.get("viewer.panel.layers");
            case SIGNATURES -> Messages.get("viewer.panel.signatures");
            case SEARCH -> Messages.get("viewer.panel.search");
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
            case SIGNATURES -> pane.getSignatures().hasSignaturesProperty();
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

    /** The viewer's look: light, dark, high contrast, or following the system. */
    public Theme getTheme() {
        return theme;
    }

    // the shell's parts, for the smoke runs; ri.ui is not exported.
    ToolRail getToolRail() {
        return rail;
    }

    UtilityPanel getUtilityPanel() {
        return utility;
    }

    Toast getToast() {
        return toast;
    }

    ViewerShell getShell() {
        return shell;
    }

    HeaderBar getHeaderBar() {
        return header;
    }

    /** Shows a short message that fades away. */
    public void showMessage(String message) {
        toast.show(message);
    }

    /**
     * Shows an error that stays, in the error colours, until the user dismisses it (its ×, or Esc
     * anywhere in the viewer that nothing else uses); it can be copied.  A newer error replaces it,
     * and a successful save clears it.  (Opening doesn't: a failed save reopens the copy it kept,
     * and its message says where that is.)
     */
    public void showError(String message) {
        toast.showError(message);
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

    /** Remembers the keystore, certificate, appearance and drawn signature from one signing to the next. */
    public void setSigningStore(SigningStore signingStore) {
        this.signingStore = signingStore != null ? signingStore : SigningStore.inMemory();
    }

    /** Remembers the printer, paper and layout chosen for the last print; the print dialog starts from them. */
    public void setPrintStore(PrintStore printStore) {
        this.printStore = printStore != null ? printStore : PrintStore.inMemory();
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
                javafx.scene.input.KeyCombination.SHORTCUT_DOWN, javafx.scene.input.KeyCombination.SHIFT_DOWN);
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
            javafx.scene.control.MenuItem none = new javafx.scene.control.MenuItem(Messages.get("viewer.noRecent"));
            none.setDisable(true);
            recentMenu.getItems().add(none);
        } else {
            javafx.scene.control.MenuItem clear = new javafx.scene.control.MenuItem(Messages.get("viewer.clearRecent"));
            clear.setOnAction(e -> history.clearRecent());
            recentMenu.getItems().addAll(new javafx.scene.control.SeparatorMenuItem(), clear);
        }
    }

    /** True if any document has unsaved changes. */
    public boolean hasUnsavedChanges() {
        return documents.stream().anyMatch(d -> d.getSession().isModified());
    }

    /**
     * Asks about each document with unsaved changes (showing it first), for closing the window.
     *
     * @return false if the user cancelled
     */
    public boolean confirmClose() {
        // a copy still being written or a print still reading would be cut off.
        for (DocumentPane pane : documents) {
            if (pane.getSession().isBusy()) {
                selectDocument(pane);
                pane.getSession().reportBusy();
                return false;
            }
        }
        for (DocumentPane pane : new java.util.ArrayList<>(documents)) {
            if (!pane.getSession().isModified()) continue;
            selectDocument(pane);
            if (!pane.getSession().confirmDiscard()) return false;
        }
        return true;
    }

    /** Shows the file choosers and questions; {@link ViewerDialogs#SYSTEM} by default. */
    public void setDialogs(ViewerDialogs dialogs) {
        this.dialogs = dialogs != null ? dialogs : ViewerDialogs.SYSTEM;
    }

    public ViewerDialogs getDialogs() {
        return dialogs;
    }

    /** Receives errors (could not open, could not save); {@link #showError} by default. */
    public void setOnError(Consumer<String> onError) {
        this.onError = onError != null ? onError : this::showError;
    }

    /** Asked before unsaved changes are dropped; false to stay.  By default they're dropped. */
    public void setConfirmDiscard(java.util.function.BooleanSupplier confirm) {
        confirmDiscard = confirm;
        documents.forEach(d -> d.getSession().setConfirmDiscard(confirm));
    }

    /** True while the viewer presents the document full screen: one fitted page on black, chrome hidden. */
    public final BooleanProperty fullScreenProperty() {
        return fullScreen;
    }

    public final boolean isFullScreen() {
        return fullScreen.get();
    }

    public final void setFullScreen(boolean value) {
        fullScreen.set(value);
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
        if (features.shows(Chrome.TOOL_RAIL)) buildRailDrawer();
        if (features.shows(Chrome.SIDE_PANELS)) {
            // the utility panel sits opposite the rail.
            utility.getDrawer().setSide(layout.getPanelSide());
            shell.setDrawer(utility.getDrawer());
        }
    }

    private void buildRailDrawer() {
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
            if (b && !panel.isOpen()) utility.openShown();
            else if (!b && panel.isOpen()) {
                panel.pinnedProperty().set(false);
                panel.close();
            }
        });
        saveLayoutLater.setOnFinished(e -> layout.save());
    }

    private void loaded(DocumentPane pane) {
        pane.refreshTitle();
        pane.applyPageLayout(PageLayout.of(pane.getSession().getDocument().getCatalog()));
        Path opened = pane.getSession().getFile();
        if (history != null && opened != null && !temporaryCopies.contains(opened.toAbsolutePath())) history.opened(opened);
        if (pane != current.get()) return;
        updateTitle();
        // the document's /PageMode picks the panel it opens with, as Acrobat does.
        Name mode = pane.getSession().getDocument().getCatalog().getPageMode();
        SidePanel wanted = Catalog.PAGE_MODE_USE_OUTLINES_VALUE.equals(mode) ? SidePanel.BOOKMARKS
                : Catalog.PAGE_MODE_USE_THUMBS_VALUE.equals(mode) ? SidePanel.THUMBNAILS
                : Catalog.PAGE_MODE_USE_ATTACHMENTS_VALUE.equals(mode) ? SidePanel.ATTACHMENTS
                : Catalog.PAGE_MODE_OPTIONAL_CONTENT_VALUE.equals(mode) ? SidePanel.LAYERS : null;
        if (wanted != null && features.shows(Chrome.SIDE_PANELS)) javafx.application.Platform.runLater(() -> utility.open(wanted));
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
            stage.setFullScreenExitHint(Messages.get("viewer.fullScreenHint"));
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
                case FIND -> features.shows(Chrome.HEADER);
                case PAGE_NUMBER -> features.shows(Chrome.HEADER) && header != null && header.hasPageField();
                case SIDE_PANEL -> features.shows(Chrome.SIDE_PANELS);
                default -> true;
            };
        }

        @Override
        public void openDocument() {
            File chosen = dialogs.showOpenDialog(chooser(Messages.get("viewer.openTitle")), window());
            if (chosen != null) {
                rememberFolder(chosen);
                open(chosen.toPath());
            }
        }

        @Override
        public void openDocumentInNewTab() {
            File chosen = dialogs.showOpenDialog(chooser(Messages.get("viewer.openNewTabTitle")), window());
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
        public void exportRedacted() {
            PdfViewer.this.exportRedacted();
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
        public void focusPageNumber() {
            header.focusPageNumber();
        }

        @Override
        public void focusFind() {
            header.showSearch();
        }

        @Override
        public void showSearch() {
            utility.open(SidePanel.SEARCH); // focuses the query once it shows
        }

        @Override
        public void printStarted(Task<Void> task) {
            // the document stays open (no close, replace or save) until the job has been sent.
            session().hold(task);
            task.setOnSucceeded(e -> showMessage(Messages.get("viewer.printed")));
            task.setOnFailed(e -> onError.accept(Messages.format("viewer.printFailed", Messages.reason(task.getException()))));
        }

        @Override
        public void customisePrintDialog(PdfPrintDialog dialog) {
            dialog.setDefaults(printStore.load());
            dialog.resultProperty().addListener((obs, was, settings) -> {
                if (settings != null) printStore.save(org.icepdf.fx.print.PrintDefaults.of(settings));
            });
        }

        FileChooser chooser(String title) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(Messages.get("viewer.pdfFilter"), "*.pdf", "*.PDF"),
                    new FileChooser.ExtensionFilter(Messages.get("viewer.allFilter"), "*.*"));
            Path file = session().getFile();
            Path folder = history != null ? history.lastDirectory() : null;
            if (folder == null && file != null) folder = file.getParent();
            if (folder != null && java.nio.file.Files.isDirectory(folder)) chooser.setInitialDirectory(folder.toFile());
            return chooser;
        }
    }
}
