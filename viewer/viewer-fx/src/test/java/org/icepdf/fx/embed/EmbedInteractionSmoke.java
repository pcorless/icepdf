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
package org.icepdf.fx.embed;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.PickResult;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.icepdf.core.pobjects.annotations.Annotation;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;
import org.icepdf.fx.ri.AnnotationContextMenu;
import org.icepdf.fx.ri.actions.ActionControls;
import org.icepdf.fx.ri.actions.StandardActions;
import org.icepdf.fx.ri.actions.ViewerContext;
import org.icepdf.fx.ri.document.DocumentSession;
import org.icepdf.fx.ri.document.SignatureFlow;
import org.icepdf.fx.signature.DocumentSigning;
import org.icepdf.fx.signature.SignatureStatus;
import org.icepdf.fx.view.FitMode;
import org.icepdf.fx.view.PdfView;
import org.icepdf.fx.view.ToolMode;
import org.icepdf.fx.view.ViewMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

import static org.icepdf.fx.embed.SmokeSupport.fx;
import static org.icepdf.fx.embed.SmokeSupport.onFx;

/**
 * An application embedding the bare page view, as {@link EmbedBasicSmoke}, that also lets its users
 * work on the document: the usual keys for selecting and copying text (and pasting it into the
 * application's own notes field), undo and redo, a right-click menu on an annotation with its
 * properties dialog, and signing an empty signature field - ICEpdf's dialogs, opened from the
 * application's own handlers.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:embedInteractionSmoke -PsmokeArgs="outdir;doc.pdf[;keystore.pfx]"}
 * (the keystore's password is "changeit"; src/test/resources/signing/certificate.pfx by default).
 */
public final class EmbedInteractionSmoke {

    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args[0]);
        Files.createDirectories(out);
        // a copy: the signing step writes next to it.
        Path doc = out.resolve("embed-interaction.pdf");
        Files.copy(Paths.get(args[1]), doc, StandardCopyOption.REPLACE_EXISTING);
        Path keystore = Paths.get(args.length > 2 ? args[2] : "src/test/resources/signing/certificate.pfx").toAbsolutePath();
        SmokeSupport.startFx();
        SmokeSupport smoke = new SmokeSupport(out);

        Host host = onFx(Host::new);
        host.signedCopy = out.resolve("embed-signed.pdf");
        fx(() -> {
            host.view.setViewMode(ViewMode.SINGLE_PAGE);
            host.view.setFitMode(FitMode.PAGE);
            host.session.open(doc);
        });
        SmokeSupport.waitOpened(host.session, doc);
        int pages = onFx(host.view::getPageCount);
        System.out.println("  " + pages + " pages; permits copy " + onFx(host.view::isCopyAllowed) + ", annotating "
                + onFx(host.view::isAnnotationEditingAllowed) + ", filling in " + onFx(host.view::isFormFillingAllowed));

        System.out.println("keyboard:");
        fx(host.view::requestFocus);
        SmokeSupport.key(host.stage, KeyCode.A, true);
        smoke.check("Ctrl+A in the page selects all its text", DocumentSelection.all(pages).equals(onFx(host.view::getTextSelection)),
                onFx(host.view::getTextSelection));
        fx(() -> Clipboard.getSystemClipboard().clear());
        SmokeSupport.key(host.stage, KeyCode.C, true);
        String copied = clipboard();
        smoke.check("Ctrl+C copies it", copied != null && copied.length() > 20, copied == null ? "nothing" : copied.length() + " chars");

        // paste is the application's: its notes field takes the text.
        fx(host.notes::requestFocus);
        SmokeSupport.key(host.stage, KeyCode.V, true);
        smoke.check("Ctrl+V pastes it into the application's notes", copied != null && copied.equals(onFx(host.notes::getText)),
                onFx(() -> host.notes.getText().length()) + " chars");

        // keys in the application's own field stay its own: the scene's copy / select-all shortcuts don't fire.
        fx(() -> {
            host.view.clearSelection();
            host.notes.setText("a note of my own");
            host.notes.requestFocus();
        });
        SmokeSupport.key(host.stage, KeyCode.A, true);
        SmokeSupport.key(host.stage, KeyCode.C, true);
        smoke.check("Ctrl+A / Ctrl+C in the notes field work on the field", "a note of my own".equals(clipboard())
                && onFx(host.view::getTextSelection) == null, clipboard() + " / " + onFx(host.view::getTextSelection));

        // with the focus on the application's tool bar, the shortcuts installed on the scene reach the view.
        fx(host.selectTool::requestFocus);
        SmokeSupport.key(host.stage, KeyCode.A, true);
        smoke.check("Ctrl+A with a tool bar button focused selects the document (scene shortcut)",
                DocumentSelection.all(pages).equals(onFx(host.view::getTextSelection)), onFx(host.view::getTextSelection));
        fx(host.view::clearSelection);

        System.out.println("annotations:");
        fx(host.rectangleTool::fire);
        smoke.check("the application's Rectangle button picks the tool", onFx(host.view::getToolMode) == ToolMode.RECTANGLE
                && onFx(host.rectangleTool::isSelected));
        double[] size = onFx(() -> new double[]{host.view.getWidth(), host.view.getHeight()});
        double[] from = {size[0] * 0.35, size[1] * 0.30}, to = {size[0] * 0.60, size[1] * 0.42};
        double[] centre = {(from[0] + to[0]) / 2, (from[1] + to[1]) / 2};
        // a bare rectangle is a thin border: hit it on its top edge.
        double[] edge = {centre[0], from[1]};
        SmokeSupport.drag(host.view, from, to);
        Annotation drawn = onFx(() -> host.view.annotationAt(edge[0], edge[1]).orElse(null));
        smoke.check("a drag draws a rectangle", drawn != null, null);
        smoke.check("Undo greys in", onFx(() -> !host.undo.isDisabled()));
        fx(() -> host.view.setToolMode(ToolMode.TEXT_SELECT));

        fx(host.view::requestFocus);
        SmokeSupport.key(host.stage, KeyCode.Z, true);
        smoke.check("Ctrl+Z undoes it", onFx(() -> host.view.annotationAt(edge[0], edge[1]).isEmpty()));
        SmokeSupport.key(host.stage, KeyCode.Y, true);
        Annotation redone = onFx(() -> host.view.annotationAt(edge[0], edge[1]).orElse(null));
        smoke.check("Ctrl+Y redoes it", redone != null);

        if (redone != null) {
            // right-click: the application's menu factory, built from ICEpdf's annotation menu.
            ContextMenu menu = onFx(() -> {
                contextMenu(host.view, edge);
                return javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing())
                        .map(w -> (ContextMenu) w).findFirst().orElse(null);
            });
            MenuItem properties = menu == null ? null : onFx(() -> menu.getItems().stream()
                    .filter(i -> "Properties…".equals(i.getText())).findFirst().orElse(null));
            smoke.check("right-click shows the annotation menu with Properties…", properties != null,
                    menu == null ? "no menu" : onFx(() -> menu.getItems().stream().map(MenuItem::getText).toList()));
            if (properties != null) {
                java.awt.Color before = redone.getColor();
                CompletableFuture<Boolean> answered = SmokeSupport.answerDialog(
                        pane -> pane.getScene().getWindow() instanceof Stage s && s.getTitle().endsWith("Properties"),
                        pane -> {
                            ColorPicker colour = SmokeSupport.find(pane, ColorPicker.class);
                            colour.setValue(Color.RED);
                            ((Button) pane.lookupButton(ButtonType.OK)).fire();
                        });
                Platform.runLater(() -> {
                    menu.hide();
                    properties.fire();
                });
                smoke.check("Properties… opens the properties dialog", SmokeSupport.await(answered));
                Thread.sleep(300);
                java.awt.Color after = onFx(redone::getColor);
                smoke.check("OK applies the colour", after != null && after.getRed() == 255 && after.getGreen() == 0,
                        before + " -> " + after);
                fx(host.undo::fire);
                smoke.check("Undo puts the colour back", sameRgb(before, onFx(redone::getColor)), onFx(redone::getColor));
            }
            fx(() -> {
                host.view.selectAnnotation(redone);
                host.view.requestFocus();
            });
            SmokeSupport.key(host.stage, KeyCode.DELETE, false);
            smoke.check("Delete removes the selected annotation", onFx(() -> host.view.annotationAt(edge[0], edge[1]).isEmpty()));
        }
        SmokeSupport.waitIdle(host.view);
        smoke.snapshot(host.root, "embed-interaction.png");

        System.out.println("signing:");
        if (!onFx(host.view::isFormFillingAllowed)) {
            smoke.check("the document permits signing", false);
        } else {
            fx(host.signTool::fire);
            CompletableFuture<Boolean> opened = SmokeSupport.answerDialog(pane -> pane.getScene().getWindow() instanceof Stage s
                    && s.getTitle() != null && s.getTitle().startsWith("Sign"), pane -> {
                for (Node n : pane.lookupAll(".text-field")) {
                    if (n instanceof TextField t && "Keystore file (.p12, .pfx)".equals(t.getPromptText())) {
                        t.setText(keystore.toString());
                    }
                }
                ((PasswordField) pane.lookup(".password-field")).setText("changeit");
                for (Node n : pane.lookupAll(".button")) {
                    if (n instanceof Button b && "Open".equals(b.getText())) b.fire();
                }
            });
            double[] f = {size[0] * 0.55, size[1] * 0.78}, t = {size[0] * 0.75, size[1] * 0.86};
            SmokeSupport.dragLater(host.view, f, t);
            smoke.check("drawing a field opens the sign dialog (the application's handler)", SmokeSupport.await(opened));
            CompletableFuture<Boolean> signed = SmokeSupport.answerDialog(pane -> pane.lookupAll(".list-view").stream()
                    .anyMatch(n -> !((ListView<?>) n).getItems().isEmpty()
                            && ((ListView<?>) n).getItems().get(0) instanceof DocumentSigning.KeyEntry),
                    pane -> ((Button) pane.lookupButton(pane.getButtonTypes().get(0))).fire());
            smoke.check("the keystore opens and Sign is pressed", SmokeSupport.await(signed));
            // the application writes the signed copy on a worker and opens it.
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline && !host.signedCopy.toAbsolutePath().equals(onFx(host.session::getFile))) {
                Thread.sleep(100);
            }
            smoke.check("the signed copy is the open document", host.signedCopy.toAbsolutePath().equals(onFx(host.session::getFile)),
                    onFx(host.session::getFile));
            deadline = System.currentTimeMillis() + 60_000;
            Thread.sleep(300);
            while (System.currentTimeMillis() < deadline && onFx(host.view::isVerifyingSignatures)) Thread.sleep(100);
            SignatureStatus status = onFx(() -> host.view.getSignatures().stream().filter(SignatureStatus::isSigned)
                    .findFirst().orElse(null));
            smoke.check("it verifies intact", status != null && !status.signedDataModified() && !status.modifiedAfterSigning(),
                    status == null ? "no signature" : status.summary());
            SmokeSupport.waitIdle(host.view);
            smoke.snapshot(host.root, "embed-signed.png");
        }
        fx(host.session::dispose);
        smoke.finish("embed interaction smoke");
    }

    private static boolean sameRgb(java.awt.Color a, java.awt.Color b) {
        return a == null ? b == null : b != null && (a.getRGB() & 0xFFFFFF) == (b.getRGB() & 0xFFFFFF);
    }

    /** A right-click (the context-menu event) at a point in the view, on the node under it. */
    private static void contextMenu(PdfView view, double[] at) {
        Point2D scene = view.localToScene(at[0], at[1]);
        Point2D screen = view.localToScreen(at[0], at[1]);
        Node target = SmokeSupport.pick(view, scene);
        Event.fireEvent(target, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, scene.getX(), scene.getY(),
                screen.getX(), screen.getY(), false, new PickResult(target, scene.getX(), scene.getY())));
    }

    private static String clipboard() throws Exception {
        String text = null;
        for (int i = 0; i < 40 && (text == null || text.isEmpty()); i++) {
            Thread.sleep(50);
            text = onFx(() -> Clipboard.getSystemClipboard().getString());
        }
        return text;
    }

    /** The application: its own tool bar and notes field around a bare {@link PdfView}. */
    static final class Host {
        final Stage stage = new Stage();
        final PdfView view = new PdfView();
        final DocumentSession session = new DocumentSession(view);
        final ActionControls actions = new ActionControls(StandardActions.registry(), ViewerContext.of(view));
        final ToggleGroup tools = new ToggleGroup();
        final ToggleButton selectTool = new ToggleButton("Select"), rectangleTool = new ToggleButton("Box"),
                signTool = new ToggleButton("Sign");
        final Button undo = new Button("Undo"), redo = new Button("Redo"), copy = new Button("Copy");
        final TextArea notes = new TextArea();
        final BorderPane root;
        Path signedCopy;

        Host() {
            for (ToggleButton tool : new ToggleButton[]{selectTool, rectangleTool, signTool}) tool.setToggleGroup(tools);
            actions.bind(selectTool, "tool.select");
            actions.bind(rectangleTool, "annotation.rectangle");
            actions.bind(signTool, "signature.sign");
            actions.bind(undo, "edit.undo");
            actions.bind(redo, "edit.redo");
            actions.bind(copy, "edit.copy");

            // right-click on an annotation: ICEpdf's menu, with its properties dialog.
            AnnotationContextMenu.install(view, null);
            // an empty signature field clicked or drawn is signed; the copy goes where the application says.
            SignatureFlow.install(session).setTargetChooser((owner, suggested) -> signedCopy)
                    .setOnError(message -> new Alert(Alert.AlertType.ERROR, message).show());
            session.setOwner(stage);

            notes.setPromptText("Notes");
            notes.setPrefRowCount(3);
            root = new BorderPane(view);
            root.setTop(new ToolBar(selectTool, rectangleTool, signTool, new Separator(), undo, redo, copy));
            root.setBottom(notes);
            Scene scene = new Scene(root, 1000, 800);
            actions.installAccelerators(scene, "edit.copy", "edit.select-all", "edit.undo", "edit.redo");
            stage.setScene(scene);
            stage.setTitle("Embedded PdfView: editing");
            stage.show();
        }
    }
}
