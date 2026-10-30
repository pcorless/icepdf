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

import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PrivateAnnotations;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.internal.annot.AnnotationCreator;
import org.icepdf.fx.view.internal.annot.AnnotationEdits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The annotation board's custom order: moved cards stay where the user put them, outside the document. */
class AnnotationBoardOrderTest {

    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/redact/test_print.pdf");
    private static final AnnotationEdits.Locker NO_LOCK = (page, mutation) -> mutation.run();
    private static final ColorLabels.Label RED = new ColorLabels.Label(new Color(255, 0, 0), "Rejected");
    private static final ColorLabels.Label GREEN = new ColorLabels.Label(new Color(0, 176, 80), "Approved");
    private static final ColorLabels.Label BLUE = new ColorLabels.Label(new Color(0, 112, 192), "Later");

    private Document document;
    private PdfView view;
    private AnnotationBoard board;
    private final BoardStore store = BoardStore.inMemory();
    private final List<MarkupAnnotation> reds = new ArrayList<>();
    private MarkupAnnotation green;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @AfterEach
    void dispose() {
        PrivateAnnotations.reset();
        if (board != null) Fx.run(board::dispose);
        if (view != null) Fx.run(() -> view.setDocument(null));
        if (document != null) document.dispose();
    }

    /** Two red notes and a green one, on a board with three labels (the blue column empty). */
    private void open() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        document = new Document();
        document.setFile(FIXTURE.toString());
        Page page = document.getPageTree().getPage(0);
        page.init();
        Color[] colours = {RED.color(), RED.color(), RED.color(), GREEN.color()};
        for (int i = 0; i < colours.length; i++) {
            MarkupAnnotation note = AnnotationCreator.note(page.getLibrary(), 60 + 80 * i, 60,
                    page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                    page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                    new AnnotationCreator.Style(System.getProperty("user.name"), colours[i], 255, 1f));
            note.setContents("note " + i);
            AnnotationEdits.add(NO_LOCK, page, 0, note, null);
            if (i < 3) reds.add(note);
            else green = note;
        }
        ColorLabels labels = ColorLabels.inMemory();
        labels.setLabels(List.of(RED, GREEN, BLUE));
        Fx.run(() -> {
            view = new PdfView();
            board = new AnnotationBoard(view, labels, store);
            SplitPane root = new SplitPane(board, view);
            Fx.scene(root, 1200, 700);
            view.setDocument(document);
            Fx.layout(root);
        });
        Fx.waitFor("the board to read the comments", () -> Fx.call(() -> columns().size() == 3
                && columns().get(0).getItems().size() == 3 && columns().get(1).getItems().size() == 1));
        // the columns appeared after the last layout: one more pass puts them in the scene, with cells.
        Fx.run(() -> Fx.layout((javafx.scene.Parent) board.getScene().getRoot()));
    }

    private List<ListView<BoardModel.Card>> columns() {
        return Fx.call(board::columnLists);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, boolean shortcut) {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        return new MouseEvent(type, 5, 5, 5, 5, MouseButton.PRIMARY, 1, false, !mac && shortcut, false,
                mac && shortcut, true, false, false, false, false, false, null);
    }

    /** Picks a card as a Shortcut+click would: added to the selection, other columns kept. */
    private void addToSelection(ListView<BoardModel.Card> list, int index) {
        Fx.run(() -> {
            Event.fireEvent(list, mouse(MouseEvent.MOUSE_PRESSED, true));
            list.getSelectionModel().select(index);
            Event.fireEvent(list, mouse(MouseEvent.MOUSE_RELEASED, true));
        });
    }

    private List<MarkupAnnotation> shown(int column) {
        return Fx.call(() -> columns().get(column).getItems().stream().map(c -> c.entry().annotation()).toList());
    }

    private BoardModel.Column column(int i) {
        return Fx.call(() -> board.columnModel().get(i));
    }

    private BoardModel.Card card(int column, int row) {
        return Fx.call(() -> columns().get(column).getItems().get(row));
    }

    @DisplayName("moving a card switches to Custom, is kept in the store, and survives a re-read and a new board")
    @Test
    void moveKeepsOrder() throws Exception {
        open();
        assertEquals(reds, shown(0), "page order first");
        Fx.run(() -> board.moveCards(column(0), List.of(card(0, 2)), card(0, 0)));
        List<MarkupAnnotation> moved = List.of(reds.get(2), reds.get(0), reds.get(1));
        assertEquals(moved, shown(0));
        assertTrue(Fx.call(board::isCustom), "the board shows the custom order");
        assertEquals(3, store.getCardOrder(BoardOrder.documentKey(document), "label:ff0000").size());
        assertEquals(1, (int) Fx.call(() -> board.selected().size()), "the moved card stays picked");

        Fx.run(board::refresh);
        Fx.waitFor("the re-read", () -> Fx.call(() -> !columns().get(0).getItems().isEmpty()));
        assertEquals(moved, shown(0), "a re-read keeps it");

        // a new board for the same document opens in the user's order.
        Fx.run(board::dispose);
        Fx.run(() -> {
            board = new AnnotationBoard(view, labels(), store);
            Fx.scene(board, 900, 500);
            Fx.layout(board);
        });
        Fx.waitFor("the new board", () -> Fx.call(() -> !board.columnLists().isEmpty()
                && board.columnLists().get(0).getItems().size() == 3));
        assertTrue(Fx.call(board::isCustom));
        assertEquals(moved, shown(0));
    }

    @DisplayName("Alt+Down and Alt+Up step the picked card; Reset Order goes back to page order")
    @Test
    void stepAndReset() throws Exception {
        open();
        ListView<BoardModel.Card> red = columns().get(0);
        Fx.run(() -> {
            red.getSelectionModel().clearAndSelect(0);
            javafx.event.Event.fireEvent(red, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", KeyCode.DOWN, false, false, true, false));
        });
        assertEquals(List.of(reds.get(1), reds.get(0), reds.get(2)), shown(0));
        Fx.run(() -> javafx.event.Event.fireEvent(columns().get(0), new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", KeyCode.UP, false, false, true, false)));
        assertEquals(reds, shown(0));

        Fx.run(() -> board.moveCards(column(0), List.of(card(0, 2)), card(0, 0)));
        Fx.run(() -> board.resetOrder(column(0)));
        assertEquals(reds, shown(0));
        assertTrue(store.getCardOrder(BoardOrder.documentKey(document), "label:ff0000").isEmpty());
    }

    @DisplayName("with a filter on, a move keeps the hidden cards where they were")
    @Test
    void filteredMove() throws Exception {
        open();
        reds.get(0).setContents("alpha 0");
        reds.get(2).setContents("alpha 2");
        Fx.run(board::refresh);
        Fx.waitFor("the re-read", () -> Fx.call(() -> columns().get(0).getItems().size() == 3
                && columns().get(0).getItems().get(0).entry().contents().startsWith("alpha")));
        Fx.run(() -> ((javafx.scene.control.TextField) board.lookup(".text-field")).setText("alpha"));
        assertEquals(List.of(reds.get(0), reds.get(2)), shown(0), "note 1 filtered out");
        Fx.run(() -> board.moveCards(column(0), List.of(card(0, 1)), card(0, 0)));
        Fx.run(() -> ((javafx.scene.control.TextField) board.lookup(".text-field")).setText(""));
        assertEquals(List.of(reds.get(2), reds.get(0), reds.get(1)), shown(0),
                "note 2 went in front of note 0; the hidden note 1 stayed after note 0");
    }

    @DisplayName("a new comment the order doesn't know slots in after the card it follows by page")
    @Test
    void newCardSlotsIn() throws Exception {
        open();
        Fx.run(() -> board.moveCards(column(0), List.of(card(0, 0)), null)); // note 0 last: 1, 2, 0
        Page page = document.getPageTree().getPage(0);
        MarkupAnnotation late = AnnotationCreator.note(page.getLibrary(), 300, 300,
                page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                new AnnotationCreator.Style("someone", RED.color(), 255, 1f));
        AnnotationEdits.add(NO_LOCK, page, 0, late, null);
        Fx.run(board::refresh);
        Fx.waitFor("the new card", () -> Fx.call(() -> columns().get(0).getItems().size() == 4));
        List<MarkupAnnotation> now = shown(0);
        assertEquals(List.of(reds.get(1), reds.get(2), reds.get(0)), now.stream().filter(reds::contains).toList(),
                "the user's order holds");
        assertTrue(now.contains(late));
    }

    @DisplayName("the document and card keys are the same after a full save to another file")
    @Test
    void keysSurviveSaveAs() throws Exception {
        open();
        String documentKey = BoardOrder.documentKey(document);
        assertNotNull(documentKey);
        java.util.Map<MarkupAnnotation, String> before = BoardOrder.keys(
                org.icepdf.fx.panels.AnnotationSummary.collect(document, () -> false));
        List<String> mine = reds.stream().map(before::get).toList();

        Path copy = java.nio.file.Files.createTempFile("board-order", ".pdf");
        try {
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(copy)) {
                document.saveToOutputStream(out, org.icepdf.core.util.updater.WriteMode.FULL_UPDATE);
            }
            Document reopened = new Document();
            try {
                reopened.setFile(copy.toString());
                if (documentKey.startsWith("id:")) {
                    assertEquals(documentKey, BoardOrder.documentKey(reopened), "the file identifier is kept");
                }
                java.util.Set<String> after = new java.util.HashSet<>(BoardOrder.keys(
                        org.icepdf.fx.panels.AnnotationSummary.collect(reopened, () -> false)).values());
                assertTrue(after.containsAll(mine), "card keys " + mine + " in " + after);
            } finally {
                reopened.dispose();
            }
        } finally {
            java.nio.file.Files.deleteIfExists(copy);
        }
    }

    private static ColorLabels labels() {
        ColorLabels labels = ColorLabels.inMemory();
        labels.setLabels(List.of(RED, GREEN, BLUE));
        return labels;
    }

}
