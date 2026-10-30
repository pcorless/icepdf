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
package org.icepdf.fx.view.internal.annot;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import org.icepdf.core.pobjects.PDate;
import org.icepdf.core.pobjects.annotations.MarkupAnnotation;
import org.icepdf.core.pobjects.annotations.PopupAnnotation;
import org.icepdf.core.pobjects.annotations.PrivateAnnotations;
import org.icepdf.fx.i18n.Messages;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * A markup annotation's popup note as a native control: title bar (author, date, minimise), the
 * contents in a {@link TextArea}, and a resize grip.  Like the Swing viewer's popups, the frame and
 * title bar take the markup annotation's colour (falling back to a pale yellow note when it has
 * none), with the title text in black or white for contrast.
 * <p>
 * It is laid out in PDF points (its /Rect size) and scaled by the zoom, so it grows and shrinks with
 * the page like the Swing viewer's popups; it stays upright when the page is rotated.  It lives in
 * the page's {@link AnnotationUiLayer}, which is not clipped to the page, so a popup can sit past
 * the page edge.  Mouse gestures on it are consumed so they never reach the page tools.
 */
public final class PopupNode extends BorderPane {

    /** Edits the node reports; the view turns them into undoable document changes. */
    public interface Listener {
        /** The popup was dragged or resized: (dx, dy, dw, dh) in view px since the gesture began. */
        void reshaped(PopupNode node, double dx, double dy, double dw, double dh);

        /** Live feedback during a drag (not yet committed). */
        void reshaping(PopupNode node, double dx, double dy, double dw, double dh);

        void contentsEdited(PopupNode node, String contents);

        void minimised(PopupNode node);

        /** The lock in the title bar was toggled: make the note private or public. */
        default void privacyChanged(PopupNode node, boolean priv) {
        }
    }

    static final Color BACKGROUND = Color.rgb(255, 255, 204);
    static final Color BORDER = Color.rgb(153, 153, 153);
    public static final double MIN_WIDTH = 120;
    public static final double MIN_HEIGHT = 80;

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
    private final Label title;
    private final Label date;
    private final Button minimise = new Button();
    private final Line minimiseGlyph = new Line(0, 0, 6, 0);
    /** Private/public lock, only with the private annotation flag enabled; null otherwise. */
    private final ToggleButton privacy;
    private final SVGPath shackle = new SVGPath();
    private final Rectangle lockBody = new Rectangle(1, 6, 8, 6);
    private Color colour;

    PopupNode(PopupAnnotation popup, MarkupAnnotation markup, Listener listener) {
        this.popup = popup;
        this.markup = markup;
        getStyleClass().add("pdf-annotation-popup");
        // lets applications (and tests) find the node for a given popup annotation.
        setUserData(popup);
        getTransforms().add(scale);
        setBorder(new Border(new BorderStroke(BORDER, BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY, new BorderWidths(1))));
        setPadding(new Insets(2, 6, 2, 6));

        title = new Label(markup.getTitleText() != null ? markup.getTitleText() : "");
        date = new Label(format(markup.getCreationDate()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // the minimise button is a small outlined box with a dash, on the note's own colour.
        minimiseGlyph.setStrokeWidth(1);
        minimise.setGraphic(minimiseGlyph);
        minimise.setTooltip(new Tooltip(Messages.get("popupNote.minimise")));
        minimise.setFocusTraversable(false);
        minimise.setOnAction(e -> listener.minimised(this));
        minimise.hoverProperty().addListener((obs, was, now) -> styleMinimise());
        minimise.armedProperty().addListener((obs, was, now) -> styleMinimise());
        header = new HBox(4, title, spacer, date);
        if (PrivateAnnotations.isEnabled()) {
            privacy = new ToggleButton();
            shackle.setFill(null);
            shackle.setStrokeWidth(1.2);
            privacy.setGraphic(new Group(shackle, lockBody));
            privacy.setFocusTraversable(false);
            privacy.setOnAction(e -> {
                listener.privacyChanged(this, privacy.isSelected());
                // the edit may be refused (locked, read only); show what the annotation says.
                refreshPrivacy();
            });
            privacy.hoverProperty().addListener((obs, was, now) -> styleMinimise());
            header.getChildren().add(privacy);
        } else {
            privacy = null;
        }
        header.getChildren().add(minimise);
        header.getStyleClass().add("pdf-annotation-popup-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 0, 3, 0));
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
        refreshColour();
        refreshPrivacy();

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
    public void setEditable(boolean editable) {
        if (this.editable == editable) return;
        this.editable = editable;
        text.setEditable(editable);
        grip.setVisible(editable);
        if (privacy != null) privacy.setDisable(!editable);
        header.setCursor(editable ? Cursor.MOVE : Cursor.DEFAULT);
    }

    /**
     * Re-reads the markup annotation's colour (it can change in the properties panel while the
     * popup is open).
     */
    void refreshColour() {
        java.awt.Color c = markup.getColor();
        Color next = c != null ? Color.rgb(c.getRed(), c.getGreen(), c.getBlue()) : BACKGROUND;
        if (next.equals(colour)) return;
        colour = next;
        setBackground(new Background(new BackgroundFill(colour, CornerRadii.EMPTY, Insets.EMPTY)));
        String fill = web(contrast(colour));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 9px; -fx-text-fill: " + fill + ";");
        date.setStyle("-fx-font-size: 8px; -fx-text-fill: " + fill + ";");
        styleMinimise();
    }

    /** Re-reads whether the annotation is private (it can change from a menu while the popup is open). */
    void refreshPrivacy() {
        if (privacy == null) return;
        boolean priv = markup.getFlagPrivateContents();
        privacy.setSelected(priv);
        // closed shackle when private, open (right leg lifted) when public.
        shackle.setContent(priv ? "M2.5,6 V3.5 A2.5,2.5 0 0 1 7.5,3.5 V6" : "M2.5,6 V3.5 A2.5,2.5 0 0 1 7.5,3.5 V4.5");
        privacy.setTooltip(new Tooltip(Messages.get(priv ? "popupNote.private" : "popupNote.public")));
    }

    /** The note's colour: the markup annotation's, or the default note yellow. */
    public Color colour() {
        return colour;
    }

    private void styleMinimise() {
        if (colour == null) return;
        Color ink = contrast(colour);
        // subtle: the border only picks up the contrast colour while the pointer is over it.
        Color border = minimise.isHover() ? ink : BORDER;
        Color fill = minimise.isArmed() ? colour.interpolate(ink, 0.15) : colour;
        minimiseGlyph.setStroke(ink);
        minimise.setStyle("-fx-background-color: " + web(fill) + "; -fx-background-radius: 0; "
                + "-fx-background-insets: 0; -fx-border-color: " + web(border) + "; -fx-border-width: 1; "
                + "-fx-border-radius: 0; -fx-padding: 3 3 3 3; -fx-min-width: 14; -fx-min-height: 10;");
        if (privacy != null) {
            shackle.setStroke(ink);
            lockBody.setFill(ink);
            privacy.setStyle("-fx-background-color: " + web(colour) + "; -fx-background-radius: 0; "
                    + "-fx-background-insets: 0; -fx-border-color: " + web(privacy.isHover() ? ink : colour)
                    + "; -fx-border-width: 1; -fx-border-radius: 0; -fx-padding: 1 2 1 2;");
        }
    }

    /** Black or white, whichever reads better on the colour (relative luminance). */
    static Color contrast(Color c) {
        double l = 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
        return l < 0.5 ? Color.WHITE : Color.rgb(26, 26, 26);
    }

    private static String web(Color c) {
        return String.format("#%02x%02x%02x", Math.round(c.getRed() * 255),
                Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    public PopupAnnotation getPopup() {
        return popup;
    }

    public MarkupAnnotation getMarkup() {
        return markup;
    }

    TextArea textArea() {
        return text;
    }

    /**
     * Places the popup: top-left at a view point, sized in points and scaled by the zoom, so it
     * keeps its /Rect proportions at any zoom.
     */
    public void place(double viewX, double viewY, double widthPoints, double heightPoints, double zoom) {
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
