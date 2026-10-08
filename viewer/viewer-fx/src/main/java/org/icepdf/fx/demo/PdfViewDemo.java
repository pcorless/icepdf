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
package org.icepdf.fx.demo;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.PRectangle;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.core.search.SearchTerm;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PageOverlayFactory;
import org.icepdf.fx.view.PasswordPrompt;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;

import java.io.File;
import java.util.List;

/**
 * Minimal host for {@link PdfView}: a toolbar driving the control's public properties and nothing
 * else.  It doubles as the "drop it into your app" example - anything it can't do through the
 * public API is an API gap to fix in the control, not to work around here.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:run --args="/path/to/file.pdf"}
 */
public class PdfViewDemo extends Application {

    private final PdfView view = new PdfView();
    // progress of the last print job.
    private final Label printStatus = new Label();
    private Document document;
    private Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        BorderPane root = new BorderPane(view);
        root.setTop(new javafx.scene.layout.VBox(buildToolBar(), buildSearchBar(), buildSignatureBanner(root)));
        root.setBottom(buildStatusBar());
        Scene scene = new Scene(root, 1200, 900);
        acceptDroppedFiles(scene);
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.P,
                javafx.scene.input.KeyCombination.SHORTCUT_DOWN), () -> {
            if (view.isPrintAllowed()) print();
        });
        stage.setScene(scene);
        stage.setTitle("ICEpdf PdfView demo");
        stage.show();
        view.requestFocus();

        // links that leave the document are the application's call; the demo opens URIs.
        view.setOnAnnotationAction(event -> {
            if (event.action() instanceof org.icepdf.core.pobjects.actions.URIAction uri && uri.getURI() != null) {
                getHostServices().showDocument(uri.getURI());
            }
        });

        List<String> args = getParameters().getUnnamed();
        if (!args.isEmpty()) open(new File(args.get(0)));
    }

    @Override
    public void stop() {
        view.setDocument(null);
        if (document != null) document.dispose();
    }

    private ToolBar buildToolBar() {
        Button open = new Button("Open…");
        open.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf", "*.PDF"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) open(file);
        });

        Button save = new Button("Save as…");
        save.setTooltip(new Tooltip("Saves the document with your annotation changes (incremental update)."));
        save.disableProperty().bind(view.documentProperty().isNull());
        save.setOnAction(e -> saveAs());

        Button print = new Button("Print…");
        print.setTooltip(new Tooltip("Print the document (Ctrl+P)"));
        print.disableProperty().bind(view.printAllowedProperty().not());
        print.setOnAction(e -> print());

        Button previous = new Button("◀");
        previous.setOnAction(e -> view.previousPage());
        Button next = new Button("▶");
        next.setOnAction(e -> view.nextPage());
        TextField pageField = new TextField();
        pageField.setPrefColumnCount(4);
        pageField.setOnAction(e -> {
            try {
                view.setCurrentPageIndex(Integer.parseInt(pageField.getText().trim()) - 1);
            } catch (NumberFormatException ignored) {
                // leave as is; the listener below rewrites the field
            }
        });
        Label pageCount = new Label();
        view.currentPageIndexProperty().addListener((obs, o, n) -> pageField.setText(String.valueOf(n.intValue() + 1)));
        view.pageCountProperty().addListener((obs, o, n) -> pageCount.setText("/ " + n));

        Button zoomOut = new Button("−");
        zoomOut.setOnAction(e -> view.zoomOut());
        Button zoomIn = new Button("+");
        zoomIn.setOnAction(e -> view.zoomIn());
        Label zoom = new Label();
        zoom.setMinWidth(56);
        zoom.setAlignment(Pos.CENTER);
        zoom.textProperty().bind(view.zoomProperty().multiply(100).asString("%.0f%%"));

        ToggleGroup fitGroup = new ToggleGroup();
        ToggleButton fitWidth = new ToggleButton("Fit width");
        ToggleButton fitPage = new ToggleButton("Fit page");
        fitWidth.setToggleGroup(fitGroup);
        fitPage.setToggleGroup(fitGroup);
        fitGroup.selectedToggleProperty().addListener((obs, o, n) ->
                view.setFitMode(n == fitWidth ? FitMode.WIDTH : n == fitPage ? FitMode.PAGE : FitMode.NONE));
        view.fitModeProperty().addListener((obs, o, n) -> {
            fitWidth.setSelected(n == FitMode.WIDTH);
            fitPage.setSelected(n == FitMode.PAGE);
        });

        Button rotateLeft = new Button("⟲");
        rotateLeft.setOnAction(e -> view.rotateCounterClockwise());
        Button rotateRight = new Button("⟳");
        rotateRight.setOnAction(e -> view.rotateClockwise());

        ComboBox<ViewMode> mode = new ComboBox<>();
        mode.getItems().setAll(ViewMode.values());
        mode.valueProperty().bindBidirectional(view.viewModeProperty());
        CheckBox cover = new CheckBox("Cover page");
        cover.selectedProperty().bindBidirectional(view.coverPageProperty());

        ComboBox<ToolMode> tool = new ComboBox<>();
        tool.getItems().setAll(ToolMode.values());
        tool.valueProperty().bindBidirectional(view.toolModeProperty());
        // annotation tools are greyed out when the document doesn't permit annotating; the view
        // refuses them anyway, so put the box back if one is picked.
        tool.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ToolMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                disableProperty().unbind();
                if (item != null && item.createsAnnotations()) {
                    disableProperty().bind(view.annotationEditingAllowedProperty().not());
                } else {
                    setDisable(false);
                }
            }
        });
        tool.valueProperty().addListener((obs, was, now) -> {
            if (now != view.getToolMode()) Platform.runLater(() -> tool.setValue(view.getToolMode()));
        });
        tool.setTooltip(new Tooltip("TEXT_SELECT selects text and annotations; PAN drags the page; the rest create "
                + "annotations.  Middle-drag or Space+drag pans in any tool."));
        Button highlight = new Button("Highlight");
        highlight.setOnAction(e -> view.highlightSelection());
        Button underline = new Button("Underline");
        underline.setOnAction(e -> view.underlineSelection());
        Button strikeOut = new Button("Strike out");
        strikeOut.setOnAction(e -> view.strikeOutSelection());
        for (Button b : new Button[]{highlight, underline, strikeOut}) {
            b.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    () -> view.getTextSelection() == null || view.getTextSelection().isCollapsed()
                            || !view.isAnnotationEditingAllowed(),
                    view.textSelectionProperty(), view.annotationEditingAllowedProperty()));
        }

        Button copy = new Button("Copy");
        copy.setTooltip(new Tooltip("Copy the selected text (Ctrl+C)"));
        copy.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> view.getTextSelection() == null || view.getTextSelection().isCollapsed() || !view.isCopyAllowed(),
                view.textSelectionProperty(), view.copyAllowedProperty()));
        copy.setOnAction(e -> view.copySelection());
        Button selectAll = new Button("Select all");
        selectAll.setTooltip(new Tooltip("Select all text (Ctrl+A)"));
        selectAll.disableProperty().bind(view.pageCountProperty().isEqualTo(0));
        selectAll.setOnAction(e -> {
            view.setToolMode(ToolMode.TEXT_SELECT);
            view.selectAll();
            view.requestFocus();
        });

        Button undo = new Button("Undo");
        undo.disableProperty().bind(view.canUndoProperty().not());
        undo.setOnAction(e -> view.undo());
        Button redo = new Button("Redo");
        redo.disableProperty().bind(view.canRedoProperty().not());
        redo.setOnAction(e -> view.redo());
        Button delete = new Button("Delete");
        delete.setTooltip(new Tooltip("Delete the selected annotation (Del)"));
        delete.disableProperty().bind(view.selectedAnnotationProperty().isNull()
                .or(view.annotationEditingAllowedProperty().not()));
        delete.setOnAction(e -> view.deleteSelectedAnnotation());

        CheckBox overlay = new CheckBox("Overlay test");
        overlay.setTooltip(new Tooltip("Draws a frame 36pt inside each page's crop box, in PDF user space,\n"
                + "to check native overlays stay locked to the content through zoom and rotation."));
        overlay.selectedProperty().addListener((obs, o, on) -> view.setPageOverlayFactory(on ? cropFrame() : null));

        return new ToolBar(open, save, print, new Separator(), previous, pageField, pageCount, next, new Separator(),
                zoomOut, zoom, zoomIn, fitWidth, fitPage, new Separator(), rotateLeft, rotateRight,
                new Separator(), mode, cover, new Separator(), tool, copy, selectAll,
                new Separator(), highlight, underline, strikeOut, undo, redo, delete, new Separator(), overlay);
    }

    /** Find bar: Enter searches (or goes to the next hit for the same term), arrows step through hits. */
    private ToolBar buildSearchBar() {
        TextField field = new TextField();
        field.setPromptText("Find in document");
        field.setPrefColumnCount(24);
        CheckBox matchCase = new CheckBox("Match case");
        CheckBox wholeWord = new CheckBox("Whole word");
        CheckBox accents = new CheckBox("Ignore accents");
        String[] lastQuery = {null};
        Runnable find = () -> {
            String text = field.getText();
            String query = text + "|" + matchCase.isSelected() + wholeWord.isSelected() + accents.isSelected();
            if (query.equals(lastQuery[0]) && !view.getSearchHits().isEmpty()) {
                view.nextSearchHit();
                return;
            }
            lastQuery[0] = query;
            SearchTerm term = new SearchTerm(text, null, matchCase.isSelected(), wholeWord.isSelected(), false);
            term.setFoldDiacritics(accents.isSelected());
            view.search(term);
        };
        field.setOnAction(e -> find.run());
        Button previous = new Button("▲");
        previous.setOnAction(e -> view.previousSearchHit());
        Button next = new Button("▼");
        next.setOnAction(e -> view.nextSearchHit());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            lastQuery[0] = null;
            view.clearSearch();
        });
        Label count = new Label();
        count.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            int hits = view.getSearchHits().size();
            int current = view.getCurrentSearchHitIndex();
            String position = hits == 0 ? "no hits" : (current >= 0 ? (current + 1) + " of " : "") + hits;
            return view.isSearching()
                    ? String.format("%s  (searching %.0f%%)", position, view.getSearchProgress() * 100)
                    : (lastQuery[0] == null ? "" : position);
        }, view.getSearchHits(), view.currentSearchHitIndexProperty(), view.searchingProperty(),
                view.searchProgressProperty()));
        // forms: the public form API, as an application would use it.
        CheckBox highlightFields = new CheckBox("Highlight fields");
        highlightFields.selectedProperty().bindBidirectional(view.highlightFormFieldsProperty());
        Button resetForm = new Button("Reset form");
        resetForm.setOnAction(e -> view.resetForm());
        return new ToolBar(field, previous, next, matchCase, wholeWord, accents, clear, count, new Separator(),
                highlightFields, resetForm);
    }

    private Node buildStatusBar() {
        Label memory = new Label();
        Timeline poll = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            memory.setText(String.format("heap %d / %d MB", used, rt.maxMemory() >> 20));
        }));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        // selection span, to show the public textSelection property in use.
        Label selection = new Label();
        selection.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            DocumentSelection s = view.getTextSelection();
            if (s == null) return "";
            if (s.isCollapsed()) return "caret p" + (s.getFocusPage() + 1) + ":" + s.getFocusOffset();
            return s.startPage() == s.endPage() ? "selected p" + (s.startPage() + 1)
                    : "selected p" + (s.startPage() + 1) + "-" + (s.endPage() + 1);
        }, view.textSelectionProperty()));
        // the last form field change, to show onFormFieldChanged in use.
        Label fieldChange = new Label();
        view.setOnFormFieldChanged(change ->
                fieldChange.setText(change.name() + ": " + change.oldValue() + " → " + change.newValue()));
        HBox bar = new HBox(16, memory, selection, fieldChange, printStatus);
        bar.setPadding(new Insets(2, 8, 2, 8));
        return bar;
    }

    /** Overlay test: a red frame inset 36pt from the crop box, drawn in PDF user space. */
    private static PageOverlayFactory cropFrame() {
        return (pageIndex, page) -> {
            PRectangle crop = page.getPageBoundary(Page.BOUNDARY_CROPBOX);
            // PRectangle is stored top-left with y up: y is the top edge.
            double x = crop.getX() + 36;
            double y = crop.getY() - crop.getHeight() + 36;
            Rectangle frame = new Rectangle(x, y, crop.getWidth() - 72, crop.getHeight() - 72);
            frame.setFill(Color.color(1, 0, 0, 0.06));
            frame.setStroke(Color.RED);
            frame.setStrokeWidth(2);
            // a marker in the frame's lower-left (user space) corner shows orientation.
            Rectangle corner = new Rectangle(x, y, 36, 36);
            corner.setFill(Color.RED);
            return new javafx.scene.Group(frame, corner);
        };
    }

    /** Writes the document, with every edit, as an incremental update - core's StateManager tracks them. */
    private void saveAs() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(stage);
        if (file == null || document == null) return;
        try (java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file))) {
            document.saveToOutputStream(out);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Could not save " + file + ":\n" + e.getMessage()).showAndWait();
        }
    }

    /**
     * Opens a PDF dropped anywhere on the window.  Loading files is the application's job - the view
     * shows a document but never owns one - so the drop target is the scene, not the control.
     */
    private void acceptDroppedFiles(Scene scene) {
        scene.setOnDragOver(event -> {
            if (droppedPdf(event.getDragboard()) != null) event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            File file = droppedPdf(event.getDragboard());
            event.setDropCompleted(file != null);
            event.consume();
            // open after the drop gesture finishes: open() may show a modal error dialog.
            if (file != null) Platform.runLater(() -> open(file));
        });
    }

    /** The first dropped file that looks like a PDF, or null. */
    private static File droppedPdf(Dragboard dragboard) {
        if (!dragboard.hasFiles()) return null;
        for (File file : dragboard.getFiles()) {
            if (file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) return file;
        }
        return null;
    }

    /**
     * The signature banner (as Acrobat's): the overall verdict once the view has checked the
     * document's signatures, and a button showing the signatures panel on the left.
     */
    private Node buildSignatureBanner(BorderPane root) {
        javafx.scene.layout.StackPane icon = new javafx.scene.layout.StackPane();
        Label text = new Label();
        ToggleButton panel = new ToggleButton("Signature panel");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox banner = new HBox(8, icon, text, spacer, panel);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(4, 8, 4, 8));
        banner.setStyle("-fx-background-color: #e8eef7; -fx-border-color: #c5d3e8; -fx-border-width: 0 0 1 0;");
        banner.managedProperty().bind(banner.visibleProperty());

        ListView<org.icepdf.fx.signature.SignatureStatus> list = new ListView<>();
        list.setPrefWidth(280);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(org.icepdf.fx.signature.SignatureStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                String who = item.isSigned() ? (item.signerName() != null ? item.signerName() : "Unknown signer")
                        : "Empty field " + item.fieldName();
                String when = item.signingTime() != null ? java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(item.signingTime()) : "";
                setText(who + (when.isEmpty() ? "" : "\n" + when) + (item.pageIndex() >= 0 ? "  (page "
                        + (item.pageIndex() + 1) + ")" : ""));
                setGraphic(item.isSigned() ? org.icepdf.fx.signature.SignatureIcons.icon(item.verdict(), 16) : null);
                setTooltip(new Tooltip(item.summary()));
            }
        });
        list.setItems(view.getSignatures());
        list.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> view.revealSignature(now));
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                view.showSignatureProperties(list.getSelectionModel().getSelectedItem());
            }
        });
        panel.selectedProperty().addListener((obs, was, now) -> root.setLeft(now ? list : null));

        Runnable update = () -> {
            List<org.icepdf.fx.signature.SignatureStatus> signed = view.getSignatures().stream()
                    .filter(org.icepdf.fx.signature.SignatureStatus::isSigned).toList();
            banner.setVisible(view.isVerifyingSignatures() || !signed.isEmpty());
            if (view.isVerifyingSignatures()) {
                icon.getChildren().clear();
                text.setText("Checking signatures\u2026");
                return;
            }
            org.icepdf.fx.signature.SignatureStatus.Verdict worst = org.icepdf.fx.signature.SignatureStatus.Verdict.VALID;
            for (org.icepdf.fx.signature.SignatureStatus st : signed) {
                if (st.verdict() == org.icepdf.fx.signature.SignatureStatus.Verdict.INVALID
                        || st.verdict() == org.icepdf.fx.signature.SignatureStatus.Verdict.ERROR) {
                    worst = org.icepdf.fx.signature.SignatureStatus.Verdict.INVALID;
                } else if (st.verdict() == org.icepdf.fx.signature.SignatureStatus.Verdict.UNKNOWN
                        && worst == org.icepdf.fx.signature.SignatureStatus.Verdict.VALID) {
                    worst = org.icepdf.fx.signature.SignatureStatus.Verdict.UNKNOWN;
                }
            }
            icon.getChildren().setAll(org.icepdf.fx.signature.SignatureIcons.icon(worst, 18));
            text.setText(switch (worst) {
                case VALID -> "Signed and all signatures are valid.";
                case UNKNOWN -> "At least one signature's identity can't be verified.";
                default -> "At least one signature has problems.";
            });
            if (signed.isEmpty() && panel.isSelected()) panel.setSelected(false);
        };
        view.getSignatures().addListener((javafx.collections.ListChangeListener<org.icepdf.fx.signature.SignatureStatus>) c -> update.run());
        view.verifyingSignaturesProperty().addListener((obs, was, now) -> update.run());
        update.run();
        return banner;
    }

    /** Print dialog, then the job in the background with its progress in the status bar. */
    private void print() {
        view.showPrintDialog().ifPresent(task -> {
            printStatus.textProperty().bind(task.messageProperty());
            task.setOnFailed(e -> {
                printStatus.textProperty().unbind();
                printStatus.setText("");
                new Alert(Alert.AlertType.ERROR, "Printing failed: " + task.getException().getMessage()).showAndWait();
            });
        });
    }

    private void open(File file) {
        Document next = new Document();
        // encrypted documents with a user password ask for it while opening.
        PasswordPrompt prompt = new PasswordPrompt(stage, file.getName());
        next.setSecurityCallback(prompt);
        try {
            next.setFile(file.getAbsolutePath());
        } catch (org.icepdf.core.exceptions.PDFSecurityException e) {
            next.dispose();
            if (!prompt.isCancelled()) {
                new Alert(Alert.AlertType.ERROR, "Could not open " + file.getName() + ": incorrect password.")
                        .showAndWait();
            }
            return;
        } catch (Exception e) {
            next.dispose();
            new Alert(Alert.AlertType.ERROR, "Could not open " + file + ":\n" + e.getMessage()).showAndWait();
            return;
        }
        Document old = document;
        document = next;
        view.setDocument(next);
        if (old != null) old.dispose();
        stage.setTitle(file.getName() + " - ICEpdf PdfView demo");
    }
}
