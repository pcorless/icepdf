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
package org.icepdf.fx.ri.app;

import javafx.scene.control.ButtonBar;
import org.icepdf.core.pobjects.Page;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.annotations.LinkAnnotation;
import org.icepdf.fx.i18n.Messages;
import org.icepdf.fx.ri.PdfViewer;
import org.icepdf.fx.ri.UserLayout;
import org.icepdf.fx.ri.ViewerFeatures;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.testing.Fx;
import org.icepdf.fx.testing.ScriptedDialogs;
import org.icepdf.fx.testing.TinyPdf;
import org.icepdf.fx.view.AnnotationActionEvent;
import org.icepdf.fx.view.PdfView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a clicked link does in the viewer application: web links go to the browser, other schemes
 * are asked about, relative ones can't be followed, GoToR opens the PDF, Launch only opens PDFs.
 */
class LinkFollowingTest {

    @TempDir
    Path dir;
    private PdfViewer viewer;
    private final ScriptedDialogs dialogs = new ScriptedDialogs();
    private final List<String> browsed = new ArrayList<>();
    private final List<LinkAnnotation> links = new ArrayList<>();

    @BeforeAll
    static void fx() {
        Fx.start();
    }

    @BeforeEach
    void build() throws IOException {
        TinyPdf.onePage("", "").write(dir.resolve("other.pdf"));
        Files.writeString(dir.resolve("notes.txt"), "hello");
        String[] actions = {
                "<< /S /GoToR /F (other.pdf) /D [0 /Fit] >>",
                "<< /S /GoToR /F (missing.pdf) /D [0 /Fit] >>",
                "<< /S /Launch /F (notes.txt) >>",
                "<< /S /Launch /F (other.pdf) >>",
                "<< /S /URI /URI (https://example.com/a) >>",
                "<< /S /URI /URI (file:///etc/hosts) >>",
                "<< /S /URI /URI (smb://server/share) >>",
                "<< /S /URI /URI (chapter2.html) >>",
        };
        StringBuilder annots = new StringBuilder("/Annots [");
        for (int i = 0; i < actions.length; i++) annots.append(4 + i).append(" 0 R ");
        TinyPdf pdf = TinyPdf.onePage(annots.append(']').toString(), "");
        for (int i = 0; i < actions.length; i++) {
            pdf.add("<< /Type /Annot /Subtype /Link /Rect [0 " + (i * 20) + " 100 " + (i * 20 + 15) + "] /Border [0 0 0] /A "
                    + actions[i] + " >>");
        }
        Path file = pdf.write(dir.resolve("links.pdf"));

        viewer = Fx.call(() -> {
            PdfViewer v = PdfViewer.create(ViewerFeatures.builder(ViewerFeatures.Preset.FULL).multipleDocuments(true).build(),
                    UserLayout.inMemory());
            v.setDialogs(dialogs);
            v.setOnLink(uri -> ViewerApp.followLink(v, null, uri, browsed::add));
            Fx.scene(v, 1000, 700);
            v.open(file);
            return v;
        });
        Fx.waitFor("links.pdf", () -> viewer.getSession().getDocument() != null && !viewer.getSession().isLoading());
        Fx.run(() -> {
            Page page = viewer.getSession().getDocument().getPageTree().getPage(0);
            page.init();
            for (Annotation a : page.getAnnotations()) if (a instanceof LinkAnnotation l) this.links.add(l);
        });
        assertEquals(actions.length, this.links.size());
    }

    @AfterEach
    void dispose() {
        if (viewer != null) Fx.run(viewer::dispose);
    }

    /** Clicks link {@code index} as the view reports it. */
    private void click(int index) {
        Fx.run(() -> {
            PdfView view = viewer.getView();
            LinkAnnotation link = links.get(index);
            view.getOnAnnotationAction().handle(new AnnotationActionEvent(view, view, link, link.getAction()));
        });
    }

    // the viewer's message layer (its accessor is the ri package's).
    private org.icepdf.fx.ri.ui.Toast toast() {
        return (org.icepdf.fx.ri.ui.Toast) viewer.getChildrenUnmodifiable().stream()
                .filter(org.icepdf.fx.ri.ui.Toast.class::isInstance).findFirst().orElseThrow();
    }

    private String message() {
        Fx.waitFor("a message", () -> !toast().getMessage().isEmpty());
        return Fx.call(() -> toast().getMessage());
    }

    private DocumentSession current() {
        return Fx.call(viewer::getSession);
    }

    @DisplayName("GoToR opens the linked PDF in a new tab")
    @Test
    void goToR() {
        click(0);
        Fx.waitFor("other.pdf", () -> viewer.getDocuments().size() == 2 && viewer.getSession().getDocument() != null
                && !viewer.getSession().isLoading());
        assertEquals("other.pdf", current().getFile().getFileName().toString());
    }

    @DisplayName("GoToR to a missing file says so")
    @Test
    void goToRMissing() {
        click(1);
        assertEquals(Messages.format("documentPane.linkedFileMissing", "missing.pdf"),
                Fx.call(() -> toast().getError()));
        assertEquals(1, Fx.call(() -> viewer.getDocuments().size()));
    }

    @DisplayName("Launch of a non-PDF is refused with a message; of a PDF it opens")
    @Test
    void launch() {
        click(2);
        assertEquals(Messages.format("documentPane.launchRefused", "notes.txt"), message());
        assertTrue(browsed.isEmpty());
        click(3);
        Fx.waitFor("other.pdf", () -> viewer.getDocuments().size() == 2);
    }

    @DisplayName("a web link goes straight to the browser")
    @Test
    void web() {
        click(4);
        assertEquals(List.of("https://example.com/a"), browsed);
        assertTrue(dialogs.asked.isEmpty());
    }

    @DisplayName("file: and smb: links are asked about; Cancel doesn't follow, Open does")
    @Test
    void otherSchemesConfirmed() {
        click(5);
        assertEquals(List.of(Messages.format("viewerApp.confirmLink", "file:///etc/hosts")), dialogs.asked);
        assertTrue(browsed.isEmpty(), "cancelled");

        dialogs.answer(ButtonBar.ButtonData.YES);
        click(6);
        assertEquals(List.of("smb://server/share"), browsed);
    }

    @DisplayName("a relative link can't be followed, and says so")
    @Test
    void relative() {
        click(7);
        assertEquals(Messages.format("viewerApp.linkNotFollowed", "chapter2.html"), message());
        assertTrue(browsed.isEmpty());
        assertTrue(dialogs.asked.isEmpty());
    }
}
