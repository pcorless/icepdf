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

/** Several cards at once on the annotation board, and the board's compact mode and text size. */
class AnnotationBoardBulkTest {

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
        Color[] colours = {RED.color(), RED.color(), GREEN.color()};
        for (int i = 0; i < colours.length; i++) {
            MarkupAnnotation note = AnnotationCreator.note(page.getLibrary(), 60 + 80 * i, 60,
                    page.getPageTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                    page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0, 1),
                    new AnnotationCreator.Style(System.getProperty("user.name"), colours[i], 255, 1f));
            note.setContents("note " + i);
            AnnotationEdits.add(NO_LOCK, page, 0, note, null);
            if (i < 2) reds.add(note);
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
                && columns().get(0).getItems().size() == 2 && columns().get(1).getItems().size() == 1));
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

    private int rgb(MarkupAnnotation annotation) {
        return annotation.getColor().getRGB() & 0xFFFFFF;
    }

    @DisplayName("Shortcut+click picks cards in two columns; filing them under a label is one undo step")
    @Test
    void refileAcrossColumns() throws Exception {
        open();
        Fx.run(() -> columns().get(0).getSelectionModel().select(0));
        addToSelection(columns().get(1), 0);
        assertEquals(2, (int) Fx.call(() -> board.selected().size()), "a selection spans columns");

        int changed = Fx.call(() -> board.refile(board.selected(), BLUE));
        assertEquals(2, changed);
        assertEquals(BLUE.color().getRGB() & 0xFFFFFF, rgb(reds.get(0)));
        assertEquals(BLUE.color().getRGB() & 0xFFFFFF, rgb(green));
        assertEquals(RED.color().getRGB() & 0xFFFFFF, rgb(reds.get(1)), "the card left out stays");
        Fx.waitFor("both cards in Later", () -> Fx.call(() -> columns().get(2).getItems().size() == 2));

        Fx.run(view::undo);
        assertEquals(RED.color().getRGB() & 0xFFFFFF, rgb(reds.get(0)), "one undo puts both back");
        assertEquals(GREEN.color().getRGB() & 0xFFFFFF, rgb(green));
    }

    @DisplayName("Shortcut+digit files every selected card; a plain click starts a new selection")
    @Test
    void keysActOnTheSelection() throws Exception {
        open();
        ListView<BoardModel.Card> red = columns().get(0);
        Fx.run(() -> {
            red.getSelectionModel().selectAll();
            Fx.press(red, KeyCode.DIGIT2, true);
        });
        assertEquals(GREEN.color().getRGB() & 0xFFFFFF, rgb(reds.get(0)));
        assertEquals(GREEN.color().getRGB() & 0xFFFFFF, rgb(reds.get(1)));
        Fx.waitFor("three cards in Approved", () -> Fx.call(() -> columns().get(1).getItems().size() == 3));

        Fx.run(() -> {
            columns().get(1).getSelectionModel().selectAll();
            columns().get(1).getSelectionModel().clearAndSelect(0);
        });
        assertEquals(1, (int) Fx.call(() -> board.selected().size()));
    }

    @DisplayName("Delete removes every selected comment in one undoable step")
    @Test
    void deleteSelection() throws Exception {
        open();
        ListView<BoardModel.Card> red = columns().get(0);
        Fx.run(() -> {
            red.getSelectionModel().selectAll();
            Fx.press(red, KeyCode.DELETE, false);
        });
        Fx.waitFor("the red cards gone", () -> Fx.call(() -> columns().get(0).getItems().isEmpty()));
        Page page = document.getPageTree().getPage(0);
        assertFalse(page.getAnnotations().contains(reds.get(0)));
        assertFalse(page.getAnnotations().contains(reds.get(1)));
        assertTrue(page.getAnnotations().contains(green));

        Fx.run(view::undo);
        assertTrue(page.getAnnotations().contains(reds.get(0)), "one undo brings both back");
        assertTrue(page.getAnnotations().contains(reds.get(1)));
    }

    @DisplayName("the selected comments go private, and public again, as one step each")
    @Test
    void privacyOfTheSelection() throws Exception {
        PrivateAnnotations.configure(true, System.getProperty("user.name"));
        open();
        Fx.run(() -> board.selectAll());
        assertEquals(3, (int) Fx.call(() -> board.selected().size()));
        assertEquals(3, (int) Fx.call(() -> board.setPrivate(board.selected(), true)));
        assertTrue(reds.get(0).getFlagPrivateContents() && reds.get(1).getFlagPrivateContents()
                && green.getFlagPrivateContents());
        Fx.run(view::undo);
        assertFalse(reds.get(0).getFlagPrivateContents() || green.getFlagPrivateContents(), "one undo");
    }

    @DisplayName("text size steps with Shortcut+Plus / Minus, resets with Shortcut+0, and is kept in the store")
    @Test
    void textSize() throws Exception {
        open();
        double start = Fx.call(board::textSize);
        Fx.run(() -> Fx.press(columns().get(0), KeyCode.EQUALS, true));
        assertEquals(Math.round(start) + 1, store.getTextSize(), 0.01);
        Fx.run(() -> Fx.press(columns().get(0), KeyCode.MINUS, true));
        Fx.run(() -> Fx.press(columns().get(0), KeyCode.MINUS, true));
        assertEquals(Math.round(start) - 1, store.getTextSize(), 0.01);
        Fx.run(() -> board.setTextSize(100));
        assertEquals(AnnotationBoard.MAX_TEXT_SIZE, store.getTextSize(), 0.01, "kept in range");
        Fx.run(() -> Fx.press(columns().get(0), KeyCode.DIGIT0, true));
        assertEquals(BoardStore.DEFAULT_TEXT_SIZE, store.getTextSize(), 0.01);
        assertEquals(2, columns().get(0).getItems().size(), "Shortcut+0 isn't a label");
    }

    @DisplayName("compact cards show the comment on one unwrapped line, the rest in a tool tip; the choice is kept")
    @Test
    void compact() throws Exception {
        open();
        assertTrue(Fx.call(() -> textLine(columns().get(0)).isWrapText()), "a full card wraps its text");
        Fx.run(() -> {
            ((javafx.scene.control.ToggleButton) board.lookup(".toggle-button")).setSelected(true);
            Fx.layout((javafx.scene.Parent) board.getScene().getRoot());
        });
        assertTrue(store.isCompact());
        Label line = Fx.call(() -> textLine(columns().get(0)));
        assertFalse(line.isWrapText(), "one line");
        assertEquals("note 0", line.getText());
        assertNotNull(Fx.call(() -> cell(columns().get(0)).getTooltip()), "the whole card on hover");
    }

    private static ListCell<?> cell(ListView<BoardModel.Card> list) {
        for (javafx.scene.Node node : list.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.getIndex() == 0) return cell;
        }
        throw new AssertionError("no first cell");
    }

    /** The first card's last line of text: its comment. */
    private static Label textLine(ListView<BoardModel.Card> list) {
        VBox body = (VBox) cell(list).getGraphic();
        return (Label) body.getChildren().get(body.getChildren().size() - 1);
    }
}
