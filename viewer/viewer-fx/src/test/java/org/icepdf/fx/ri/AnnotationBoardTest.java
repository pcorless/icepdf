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

import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The annotation board beside a view: columns from labels, and selection both ways. */
class AnnotationBoardTest {

    private static final Path FIXTURE = Paths.get("../viewer-awt/src/test/resources/redact/test_print.pdf");
    private static final AnnotationEdits.Locker NO_LOCK = (page, mutation) -> mutation.run();
    private static final ColorLabels.Label RED = new ColorLabels.Label(new Color(255, 0, 0), "Rejected");
    private static final ColorLabels.Label GREEN = new ColorLabels.Label(new Color(0, 176, 80), "Approved");

    private Document document;
    private PdfView view;
    private AnnotationBoard board;

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @AfterEach
    void dispose() {
        if (board != null) Fx.run(board::dispose);
        if (view != null) Fx.run(() -> view.setDocument(null));
        if (document != null) document.dispose();
    }

    private List<ListView<BoardModel.Card>> columns() {
        return Fx.call(board::columnLists);
    }

    @DisplayName("a red and a green note land in their columns; picking a card selects it in the view and back")
    @Test
    void columnsAndSelection() throws Exception {
        assumeTrue(Files.exists(FIXTURE), "fixture not found");
        document = new Document();
        document.setFile(FIXTURE.toString());
        var page = document.getPageTree().getPage(0);
        page.init();
        MarkupAnnotation[] notes = new MarkupAnnotation[2];
        Color[] colours = {RED.color(), GREEN.color()};
        for (int i = 0; i < 2; i++) {
            notes[i] = AnnotationCreator.note(page.getLibrary(), 60 + 80 * i, 60,
                    page.getPageTransform(org.icepdf.core.pobjects.Page.BOUNDARY_CROPBOX, 0, 1),
                    page.getToPageSpaceTransform(org.icepdf.core.pobjects.Page.BOUNDARY_CROPBOX, 0, 1),
                    new AnnotationCreator.Style("t", colours[i], 255, 1f));
            AnnotationEdits.add(NO_LOCK, page, 0, notes[i], null);
        }
        ColorLabels labels = ColorLabels.inMemory();
        labels.setLabels(List.of(RED, GREEN));
        Fx.run(() -> {
            view = new PdfView();
            board = new AnnotationBoard(view, labels);
            SplitPane root = new SplitPane(board, view);
            Fx.scene(root, 1200, 700);
            view.setDocument(document);
            Fx.layout(root);
        });
        Fx.waitFor("the board to read the comments", () -> columns().size() >= 2
                && Fx.call(() -> !columns().get(0).getItems().isEmpty() && !columns().get(1).getItems().isEmpty()));

        List<ListView<BoardModel.Card>> columns = columns();
        assertSame(notes[0], Fx.call(() -> columns.get(0).getItems().get(0).entry().annotation()), "red under Rejected");
        assertSame(notes[1], Fx.call(() -> columns.get(1).getItems().get(0).entry().annotation()), "green under Approved");

        Fx.run(() -> columns.get(1).getSelectionModel().select(0));
        assertSame(notes[1], Fx.call(view::getSelectedAnnotation), "the card selects its comment");

        Fx.run(() -> view.selectAnnotation(notes[0]));
        assertEquals(0, (int) Fx.call(() -> columns.get(0).getSelectionModel().getSelectedIndex()));
        assertTrue(Fx.call(() -> columns.get(1).getSelectionModel().isEmpty()), "one card picked at a time");
    }
}
