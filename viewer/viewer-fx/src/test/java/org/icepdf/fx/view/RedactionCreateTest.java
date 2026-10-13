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
package org.icepdf.fx.view;

import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.RedactionAnnotation;
import org.icepdf.core.util.redaction.RedactionReport;
import org.icepdf.core.util.updater.WriteMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The redaction tool's annotation, through the burn: what the view makes is what core removes. */
class RedactionCreateTest {

    // "alpha bravo charlie" in 12pt Helvetica at (20, 100); core's redaction fixture.
    private static final Path FIXTURE = Paths.get("../../core/core-awt/src/test/resources/redaction/simple_tj.pdf");

    @TempDir
    Path temp;

    @DisplayName("a box over a word, exported with FULL_UPDATE, removes that word and only that word")
    @Test
    void boxRedactionBurns() throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE.toString());
        Path out = temp.resolve("redacted.pdf");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            AffineTransform toPageSpace = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1f);
            // "bravo" spans roughly x 54..86 on the baseline at y 100 (page space, y up).
            Rectangle2D pageBox = new Rectangle2D.Double(53, 96, 34, 16);
            Rectangle2D viewBox = toPageSpace.createInverse().createTransformedShape(pageBox).getBounds2D();
            RedactionAnnotation redaction = AnnotationCreator.redaction(page.getLibrary(), List.of(viewBox),
                    toPageSpace, "tester");
            assertEquals(Color.BLACK, redaction.getColor());
            AnnotationEdits.add((index, mutation) -> mutation.run(), page, 0, redaction, null);
            assertTrue(document.hasRedactions());
            try (OutputStream stream = Files.newOutputStream(out)) {
                document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
            }
            RedactionReport report = document.getRedactionReport();
            assertNotNull(report, "a full write reports");
            assertTrue(report.getGlyphsRemoved() >= 5, "removed " + report.getGlyphsRemoved());
        } finally {
            document.dispose();
        }

        Document redacted = new Document();
        redacted.setFile(out.toString());
        try {
            String text = redacted.getPageText(0).toString();
            assertFalse(text.contains("bravo"), text);
            assertTrue(text.contains("alpha") && text.contains("charlie"), text);
        } finally {
            redacted.dispose();
        }
    }

    @DisplayName("a redaction undone and redone still burns (redo restores what the delete took away)")
    @Test
    void undoRedoStillBurns() throws Exception {
        Document document = new Document();
        document.setFile(FIXTURE.toString());
        Path out = temp.resolve("redone.pdf");
        try {
            Page page = document.getPageTree().getPage(0);
            page.init();
            AffineTransform toPageSpace = page.getToPageSpaceTransform(Page.BOUNDARY_CROPBOX, 0f, 1f);
            Rectangle2D viewBox = toPageSpace.createInverse()
                    .createTransformedShape(new Rectangle2D.Double(53, 96, 34, 16)).getBounds2D();
            AnnotationEdits.Edit edit = AnnotationEdits.add((index, mutation) -> mutation.run(), page, 0,
                    AnnotationCreator.redaction(page.getLibrary(), List.of(viewBox), toPageSpace, "tester"), null);
            edit.undo();
            edit.redo();
            try (OutputStream stream = Files.newOutputStream(out)) {
                document.saveToOutputStream(stream, WriteMode.FULL_UPDATE);
            }
            assertTrue(document.getRedactionReport().getGlyphsRemoved() >= 5,
                    "removed " + document.getRedactionReport().getGlyphsRemoved());
        } finally {
            document.dispose();
        }
    }
}
