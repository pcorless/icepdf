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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * A markup annotation's popup note as a native control: title bar (author, date, minimise), the
 * contents in a {@link TextArea}, and a resize grip.
 * <p>
 * It is laid out in PDF points (its /Rect size) and scaled by the zoom, so it grows and shrinks with
 * the page like the Swing viewer's popups; it stays upright when the page is rotated.  It lives in
 * the page's {@link AnnotationUiLayer}, which is not clipped to the page, so a popup can sit past
 * the page edge.  Mouse gestures on it are consumed so they never reach the page tools.
 */
final class PopupNode extends BorderPane {

    /** Edits the node reports; the view turns them into undoable document changes. */
    interface Listener {
        /** The popup was dragged or resized: (dx, dy, dw, dh) in view px since the gesture began. */
        void reshaped(PopupNode node, double dx, double dy, double dw, double dh);

        /** Live feedback during a drag (not yet committed). */
        void reshaping(PopupNode node, double dx, double dy, double dw, double dh);

        void contentsEdited(PopupNode node, String contents);

        void minimised(PopupNode node);
    }

    static final Color BACKGROUND = Color.rgb(255, 255, 204);
    static final double MIN_WIDTH = 120;
    static final double MIN_HEIGHT = 80;

    private final PopupAnnotation popup;
    private final MarkupAnnotation markup;
    private final Scale scale = new Scale(1, 1, 0, 0);
    private final TextArea text = new TextArea();
    private double pressX;
    private double pressY;
    private boolean resizing;
    private boolean dragging;
    private boolean editable = true;
    private final HBox header;
    private final Region grip = new Region();

    PopupNode(PopupAnnotation popup, MarkupAnnotation markup, Listener listener) {
        this.popup = popup;
        this.markup = markup;
        getStyleClass().add("pdf-annotation-popup");
        // lets applications (and tests) find the node for a given popup annotation.
        setUserData(popup);
        getTransforms().add(scale);
        setBackground(new Background(new BackgroundFill(BACKGROUND, CornerRadii.EMPTY, Insets.EMPTY)));
        setBorder(new Border(new BorderStroke(Color.rgb(153, 153, 153), BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(1))));
        setPadding(new Insets(2));

        Label title = new Label(markup.getTitleText() != null ? markup.getTitleText() : "");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 9px;");
        Label date = new Label(format(markup.getCreationDate()));
        date.setStyle("-fx-font-size: 8px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minimise = new Button("_");
        minimise.setStyle("-fx-font-size: 8px; -fx-padding: 0 4 0 4;");
        minimise.setFocusTraversable(false);
        minimise.setOnAction(e -> listener.minimised(this));
        header = new HBox(4, title, spacer, date, minimise);
        header.getStyleClass().add("pdf-annotation-popup-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(1, 2, 1, 2));
        header.setCursor(Cursor.MOVE);
        setTop(header);

        text.setText(markup.getContents() != null ? markup.getContents() : "");
        text.setWrapText(true);
        text.setStyle("-fx-font-size: 9px;");
        text.focusedProperty().addListener((obs, was, now) -> {
            if (!now) listener.contentsEdited(this, text.getText());
        });
        setCenter(text);

        grip.setPrefSize(8, 8);
        grip.setCursor(Cursor.SE_RESIZE);
        grip.getStyleClass().add("pdf-annotation-popup-grip");
        BorderPane.setAlignment(grip, Pos.BOTTOM_RIGHT);
        setBottom(grip);

        // drags: the header moves, the grip resizes; both report in view px (this node's local
        // coordinates times the scale).
        header.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> begin(e, false));
        grip.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> begin(e, true));
        for (javafx.scene.Node handle : new javafx.scene.Node[]{header, grip}) {
            handle.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
                e.consume();
                if (!dragging) return;
                double[] d = delta(e);
                if (resizing) listener.reshaping(this, 0, 0, d[0], d[1]);
                else listener.reshaping(this, d[0], d[1], 0, 0);
            });
            handle.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
                e.consume();
                if (!dragging) return;
                dragging = false;
                double[] d = delta(e);
                if (resizing) listener.reshaped(this, 0, 0, d[0], d[1]);
                else listener.reshaped(this, d[0], d[1], 0, 0);
            });
        }
        // nothing on the popup reaches the page tools underneath.
        addEventHandler(MouseEvent.ANY, MouseEvent::consume);
    }

    private void begin(MouseEvent e, boolean resize) {
        e.consume();
        dragging = editable;
        resizing = resize;
        pressX = e.getSceneX();
        pressY = e.getSceneY();
    }

    /** Scene delta since the press: view px (the viewport isn't scaled). */
    private double[] delta(MouseEvent e) {
        return new double[]{e.getSceneX() - pressX, e.getSceneY() - pressY};
    }

    /**
     * Read-only popups (the document doesn't permit annotating) show their note but can't be
     * edited, moved or resized; they can still be minimised.
     */
    void setEditable(boolean editable) {
        if (this.editable == editable) return;
        this.editable = editable;
        text.setEditable(editable);
        grip.setVisible(editable);
        header.setCursor(editable ? Cursor.MOVE : Cursor.DEFAULT);
    }

    PopupAnnotation getPopup() {
        return popup;
    }

    MarkupAnnotation getMarkup() {
        return markup;
    }

    TextArea textArea() {
        return text;
    }

    /**
     * Places the popup: top-left at a view point, sized in points and scaled by the zoom, so it
     * keeps its /Rect proportions at any zoom.
     */
    void place(double viewX, double viewY, double widthPoints, double heightPoints, double zoom) {
        setLayoutX(viewX);
        setLayoutY(viewY);
        double w = Math.max(MIN_WIDTH, widthPoints);
        double h = Math.max(MIN_HEIGHT, heightPoints);
        setMinSize(w, h);
        setPrefSize(w, h);
        setMaxSize(w, h);
        resize(w, h);
        scale.setX(zoom);
        scale.setY(zoom);
    }

    private static String format(PDate date) {
        if (date == null) return "";
        try {
            return date.asLocalDateTime().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT));
        } catch (RuntimeException e) {
            return date.toString();
        }
    }
}
