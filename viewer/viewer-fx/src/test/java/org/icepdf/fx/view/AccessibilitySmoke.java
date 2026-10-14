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

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.icepdf.core.pobjects.Document;
import org.icepdf.core.pobjects.graphics.text.DocumentSelection;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * What a screen reader sees of {@link PdfView}, asked the way JavaFX's Windows (UI Automation) and
 * macOS bridges ask: there is no screen reader on the build machine (and JavaFX has no Linux
 * bridge), so this queries the attributes directly and stands in for a connected assistive
 * technology where the view's behaviour depends on one.
 * <p>
 * {@code ./gradlew :viewer:viewer-fx:a11ySmoke -PsmokeArgs="doc.pdf"} (a document of two or more
 * pages with text on the first two).
 */
public final class AccessibilitySmoke {

    private static int failures;

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("org.apache.fontbox").setLevel(java.util.logging.Level.SEVERE);
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();

        Document document = new Document();
        document.setFile(args[0]);
        PdfView view = onFx(PdfView::new);
        Button elsewhere = onFx(() -> new Button("elsewhere"));
        fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(new BorderPane(view, elsewhere, null, null, null), 900, 700));
            stage.show();
            elsewhere.requestFocus();
            view.setViewMode(ViewMode.SINGLE_PAGE);
            view.setFitMode(FitMode.PAGE);
            view.setDocument(document);
        });
        int pages = onFx(view::getPageCount);

        System.out.println("what it is:");
        check("role is a text area", onFx(view::getAccessibleRole) == AccessibleRole.TEXT_AREA);
        check("read-only", Boolean.FALSE.equals(query(view, AccessibleAttribute.EDITABLE)));
        check("named after the page", ("Page 1 of " + pages).equals(name(view)), name(view));

        System.out.println("the page's text:");
        String text = awaitText(view, 0);
        check("TEXT is page 1's text", !text.isEmpty() && text.equals(sequenceText(view, 0)),
                text.length() + " chars");
        int firstBreak = text.indexOf('\n');
        check("line 0 starts at 0", Integer.valueOf(0).equals(query(view, AccessibleAttribute.LINE_START, 0)));
        check("line 0 ends after its newline", Integer.valueOf(firstBreak + 1).equals(
                query(view, AccessibleAttribute.LINE_END, 0)), query(view, AccessibleAttribute.LINE_END, 0));
        check("line 1 starts there", Integer.valueOf(firstBreak + 1).equals(query(view, AccessibleAttribute.LINE_START, 1)));
        check("offset in line 1 maps to line 1", Integer.valueOf(1).equals(
                query(view, AccessibleAttribute.LINE_FOR_OFFSET, firstBreak + 1)));
        long lines = text.chars().filter(c -> c == '\n').count() + 1;
        check("no line past the last", query(view, AccessibleAttribute.LINE_START, (int) lines) == null);

        System.out.println("geometry:");
        Bounds[] bounds = (Bounds[]) query(view, AccessibleAttribute.BOUNDS_FOR_RANGE, 0, 3);
        Bounds screen = onFx(() -> view.localToScreen(view.getBoundsInLocal()));
        check("the first characters have screen bounds inside the view", bounds != null && bounds.length > 0
                && screen.contains(bounds[0].getCenterX(), bounds[0].getCenterY()),
                bounds == null || bounds.length == 0 ? "none" : bounds[0] + " in " + screen);
        if (bounds != null && bounds.length > 0) {
            Point2D centre = new Point2D(bounds[0].getMinX() + 1, bounds[0].getCenterY());
            Object offset = query(view, AccessibleAttribute.OFFSET_AT_POINT, centre);
            check("a point on them hits back to the start", offset instanceof Integer i && i <= 1, offset);
        }

        System.out.println("selection, set by a reader:");
        fx(() -> view.executeAccessibleAction(AccessibleAction.SET_TEXT_SELECTION, 2, 9));
        check("SET_TEXT_SELECTION selects in the view", DocumentSelection.of(0, 2, 0, 9).equals(onFx(view::getTextSelection)),
                onFx(view::getTextSelection));
        check("and reads back", Integer.valueOf(2).equals(query(view, AccessibleAttribute.SELECTION_START))
                && Integer.valueOf(9).equals(query(view, AccessibleAttribute.SELECTION_END))
                && Integer.valueOf(9).equals(query(view, AccessibleAttribute.CARET_OFFSET)));

        System.out.println("with a screen reader connected:");
        PdfViewAccessibility.active = () -> true;
        fx(() -> {
            view.clearSelection();
            elsewhere.requestFocus();
        });
        fx(view::requestFocus);
        check("focus puts a caret at the page's start", DocumentSelection.collapsed(0, 0).equals(onFx(view::getTextSelection)),
                onFx(view::getTextSelection));
        if (pages > 1) {
            fx(view::nextPage);
            settle();
            check("the next page moves the caret to its start", DocumentSelection.collapsed(1, 0).equals(onFx(view::getTextSelection)),
                    onFx(view::getTextSelection));
            check("and renames the view", ("Page 2 of " + pages).equals(name(view)), name(view));
            String second = awaitText(view, 1);
            check("TEXT is now page 2's", !second.isEmpty() && second.equals(sequenceText(view, 1)) && !second.equals(text),
                    second.length() + " chars");
        }
        PdfViewAccessibility.active = () -> false;
        fx(() -> {
            view.clearSelection();
            elsewhere.requestFocus();
        });
        fx(view::requestFocus);
        check("without one, focus leaves the selection alone", onFx(view::getTextSelection) == null);

        System.out.println("an unlabelled field, named for a reader:");
        String fieldName = onFx(() -> {
            javafx.scene.control.TextField field = new javafx.scene.control.TextField();
            org.icepdf.fx.ri.ui.Accessibility.name(field, "Page number");
            Node label = (Node) field.queryAccessibleAttribute(AccessibleAttribute.LABELED_BY);
            return label == null ? null : (String) label.queryAccessibleAttribute(AccessibleAttribute.TEXT);
        });
        check("its label answers with the name", "Page number".equals(fieldName), fieldName);

        System.out.println(failures == 0 ? "a11y smoke: all passed" : "a11y smoke: " + failures + " FAILED");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static String awaitText(PdfView view, int page) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (sequenceText(view, page) != null) return (String) query(view, AccessibleAttribute.TEXT);
            query(view, AccessibleAttribute.TEXT);   // asks for the page's text, as a reader would
            Thread.sleep(50);
        }
        return "";
    }

    private static String sequenceText(PdfView view, int page) throws Exception {
        return onFx(() -> view.getSkin() instanceof PdfViewSkin skin && skin.textSequence(page) != null
                ? skin.textSequence(page).text().toString() : null);
    }

    private static String name(PdfView view) throws Exception {
        Node label = (Node) query(view, AccessibleAttribute.LABELED_BY);
        return label == null ? null : (String) onFx(() -> label.queryAccessibleAttribute(AccessibleAttribute.TEXT));
    }

    private static Object query(PdfView view, AccessibleAttribute attribute, Object... parameters) throws Exception {
        return onFx(() -> view.queryAccessibleAttribute(attribute, parameters));
    }

    private static void settle() throws Exception {
        Thread.sleep(200);
        fx(() -> { });
    }

    private static void check(String what, boolean ok) {
        check(what, ok, null);
    }

    private static void check(String what, boolean ok, Object detail) {
        System.out.println((ok ? "  PASS " : "  FAIL ") + what + (ok || detail == null ? "" : "  [" + detail + "]"));
        if (!ok) failures++;
    }

    private static <T> T onFx(Callable<T> call) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(call.call());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(60, TimeUnit.SECONDS);
    }

    private static void fx(Runnable run) throws Exception {
        onFx(() -> {
            run.run();
            return null;
        });
    }
}
